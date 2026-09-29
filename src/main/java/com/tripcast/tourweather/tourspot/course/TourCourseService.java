package com.tripcast.tourweather.tourspot.course;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripcast.tourweather.climate.ClimateIndexService;
import com.tripcast.tourweather.climate.dto.ClimateIndexResponse;
import com.tripcast.tourweather.climate.util.ClimateRegionIdNormalizer;
import com.tripcast.tourweather.tourspot.TourSpot;
import com.tripcast.tourweather.tourspot.course.dto.CourseRecommendationResponse;
import com.tripcast.tourweather.tourspot.course.dto.TourCourseResponse;
import com.tripcast.tourweather.tourspot.course.dto.TourCourseStopResponse;
import com.tripcast.tourweather.tourspot.course.dto.TourCourseStopWeatherResponse;
import com.tripcast.tourweather.tourspot.course.dto.TourCourseSummaryResponse;
import com.tripcast.tourweather.tourspot.course.dto.TourCourseWeatherResponse;
import com.tripcast.tourweather.weather.WeatherService;
import com.tripcast.tourweather.weather.dto.WeatherResponse;
import com.tripcast.tourweather.weather.util.GridCoordinate;
import com.tripcast.tourweather.weather.util.LatLonToGridConverter;

@Service
@Transactional(readOnly = true)
public class TourCourseService {

    private static final Logger log =
            LoggerFactory.getLogger(TourCourseService.class);

    private final TourCourseRepository courseRepository;
    private final TourCourseStopRepository courseStopRepository;
    private final ClimateIndexService climateIndexService;
    private final WeatherService weatherService;

    public TourCourseService(
            TourCourseRepository courseRepository,
            TourCourseStopRepository courseStopRepository,
            ClimateIndexService climateIndexService,
            WeatherService weatherService
    ) {
        this.courseRepository = courseRepository;
        this.courseStopRepository = courseStopRepository;
        this.climateIndexService = climateIndexService;
        this.weatherService = weatherService;
    }

    public Page<TourCourseSummaryResponse> searchCourses(int page, int size) {
        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by("id").ascending()
        );

        return courseRepository.findAll(pageRequest)
                .map(this::toSummary);
    }

    private TourCourseSummaryResponse toSummary(TourCourse course) {
        Optional<TourCourseStop> firstStop = courseStopRepository
                .findFirstByCourseIdOrderByCourseOrderAsc(course.getId());
        long stopCount = courseStopRepository.countByCourseId(course.getId());

        return new TourCourseSummaryResponse(
                course.getId(),
                course.getSourceCourseId(),
                firstStop.map(stop -> stop.getTourSpot().getName())
                        .orElse(null),
                firstStop.map(TourCourseStop::getThemeName)
                        .orElse(null),
                stopCount
        );
    }

    public TourCourseResponse getCourse(Long courseId) {
        TourCourse course = findCourse(courseId);
        List<TourCourseStopResponse> stops = findStops(courseId)
                .stream()
                .map(TourCourseStopResponse::from)
                .toList();

        return new TourCourseResponse(
                course.getId(),
                course.getSourceCourseId(),
                stops
        );
    }

    public TourCourseWeatherResponse getCourseWeather(Long courseId) {
        TourCourse course = findCourse(courseId);
        Map<String, Optional<ClimateIndexResponse>> climateByRegion =
                new HashMap<>();

        List<TourCourseStopWeatherResponse> stops = findStops(courseId)
                .stream()
                .map(stop -> toWeatherResponse(stop, climateByRegion))
                .toList();

        return new TourCourseWeatherResponse(
                course.getId(),
                course.getSourceCourseId(),
                stops
        );
    }

    public List<CourseRecommendationResponse> getRecommendations(int limit) {
        return courseRepository.findAll()
                .stream()
                .map(this::toRecommendationOrNull)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                                (CourseRecommendationResponse response) ->
                                        response.climateIndex().score())
                        .reversed())
                .limit(limit)
                .toList();
    }

    private CourseRecommendationResponse toRecommendationOrNull(
            TourCourse course
    ) {
        return courseStopRepository
                .findFirstByCourseIdOrderByCourseOrderAsc(course.getId())
                .map(firstStop -> {
                    ClimateIndexResponse climateIndex = climateIndexService
                            .findLatestOrNull(firstStop.getRegionId());
                    if (climateIndex == null) {
                        return null;
                    }
                    long stopCount = courseStopRepository
                            .countByCourseId(course.getId());
                    return new CourseRecommendationResponse(
                            course.getId(),
                            course.getSourceCourseId(),
                            firstStop.getTourSpot().getName(),
                            firstStop.getThemeName(),
                            stopCount,
                            climateIndex
                    );
                })
                .orElse(null);
    }

    private TourCourse findCourse(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new TourCourseNotFoundException(courseId));
    }

    private List<TourCourseStop> findStops(Long courseId) {
        return courseStopRepository
                .findByCourseIdOrderByCourseOrderAsc(courseId);
    }

    private TourCourseStopWeatherResponse toWeatherResponse(
            TourCourseStop stop,
            Map<String, Optional<ClimateIndexResponse>> climateByRegion
    ) {
        String climateRegionId = ClimateRegionIdNormalizer.normalize(
                stop.getRegionId()
        );
        Optional<ClimateIndexResponse> climateIndex = climateByRegion
                .computeIfAbsent(
                        climateRegionId,
                        regionId -> Optional.ofNullable(
                                climateIndexService.findLatestOrNull(regionId)
                        )
                );

        return new TourCourseStopWeatherResponse(
                TourCourseStopResponse.from(stop),
                climateIndex.orElse(null),
                getWeatherOrNull(stop)
        );
    }

    private WeatherResponse getWeatherOrNull(TourCourseStop stop) {
        TourSpot tourSpot = stop.getTourSpot();
        try {
            GridCoordinate grid = LatLonToGridConverter.convert(
                    tourSpot.getLatitude(),
                    tourSpot.getLongitude()
            );
            return weatherService.getWeather(grid.nx(), grid.ny());
        } catch (RuntimeException exception) {
            log.warn(
                    "코스 관광지 동네예보 조회 실패. courseId={}, tourSpotId={}",
                    stop.getCourse().getId(),
                    tourSpot.getId(),
                    exception
            );
            return null;
        }
    }
}
