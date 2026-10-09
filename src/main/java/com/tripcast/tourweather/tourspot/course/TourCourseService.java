package com.tripcast.tourweather.tourspot.course;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

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
import com.tripcast.tourweather.tourspot.dto.TourSpotCourseResponse;
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

        Page<TourCourse> courses = courseRepository.findAll(pageRequest);
        Map<Long, List<TourCourseStop>> stopsByCourseId =
                findStopsByCourseId(courses.getContent());

        return courses.map(course -> toSummary(
                course,
                stopsByCourseId.getOrDefault(course.getId(), List.of())
        ));
    }

    /**
     * 관광지를 포함하는 코스를 courseId 오름차순으로 반환한다.
     * 관광지 존재 여부는 호출하는 쪽에서 확인한다.
     * 쿼리는 코스 수와 무관하게 3번이다(코스별 방문 순서, 코스, 정류지).
     */
    public List<TourSpotCourseResponse> findCoursesByTourSpotId(
            Long tourSpotId
    ) {
        List<SpotCourseOrder> spotOrders = courseStopRepository
                .findCourseOrdersByTourSpotId(tourSpotId);
        if (spotOrders.isEmpty()) {
            return List.of();
        }

        List<Long> courseIds = spotOrders.stream()
                .map(SpotCourseOrder::courseId)
                .toList();
        Map<Long, TourCourse> coursesById = courseRepository
                .findAllById(courseIds)
                .stream()
                .collect(Collectors.toMap(TourCourse::getId, course -> course));
        Map<Long, List<TourCourseStop>> stopsByCourseId =
                findStopsByCourseId(List.copyOf(coursesById.values()));

        return spotOrders.stream()
                .map(spotOrder -> TourSpotCourseResponse.of(
                        toSummary(
                                coursesById.get(spotOrder.courseId()),
                                stopsByCourseId.getOrDefault(
                                        spotOrder.courseId(),
                                        List.of()
                                )
                        ),
                        spotOrder.courseOrder()
                ))
                .toList();
    }

    private TourCourseSummaryResponse toSummary(
            TourCourse course,
            List<TourCourseStop> stops
    ) {
        if (stops.isEmpty()) {
            return new TourCourseSummaryResponse(
                    course.getId(),
                    course.getSourceCourseId(),
                    null,
                    null,
                    0L,
                    null,
                    null,
                    null,
                    null
            );
        }

        TourCourseStop firstStop = stops.get(0);
        TourCourseStop lastStop = stops.get(stops.size() - 1);
        TourSpot firstSpot = firstStop.getTourSpot();
        TourSpot lastSpot = lastStop.getTourSpot();

        return new TourCourseSummaryResponse(
                course.getId(),
                course.getSourceCourseId(),
                firstSpot.getName(),
                firstStop.getThemeName(),
                stops.size(),
                firstSpot.getLatitude(),
                firstSpot.getLongitude(),
                lastSpot.getLatitude(),
                lastSpot.getLongitude()
        );
    }

    /**
     * 코스들의 정류지를 쿼리 한 번으로 가져와 코스별로 묶는다.
     * 각 리스트는 courseOrder 오름차순이다.
     */
    private Map<Long, List<TourCourseStop>> findStopsByCourseId(
            List<TourCourse> courses
    ) {
        if (courses.isEmpty()) {
            return Map.of();
        }
        List<Long> courseIds = courses.stream()
                .map(TourCourse::getId)
                .toList();

        return courseStopRepository.findAllWithTourSpotByCourseIds(courseIds)
                .stream()
                .collect(Collectors.groupingBy(
                        stop -> stop.getCourse().getId()
                ));
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
        List<TourCourse> courses = courseRepository.findAll();
        Map<Long, List<TourCourseStop>> stopsByCourseId =
                findStopsByCourseId(courses);

        return courses.stream()
                .map(course -> toRecommendationOrNull(
                        course,
                        stopsByCourseId.getOrDefault(course.getId(), List.of())
                ))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                                (CourseRecommendationResponse response) ->
                                        response.climateIndex().score())
                        .reversed())
                .limit(limit)
                .toList();
    }

    private CourseRecommendationResponse toRecommendationOrNull(
            TourCourse course,
            List<TourCourseStop> stops
    ) {
        if (stops.isEmpty()) {
            return null;
        }

        TourCourseStop firstStop = stops.get(0);
        ClimateIndexResponse climateIndex = climateIndexService
                .findLatestOrNull(firstStop.getRegionId());
        if (climateIndex == null) {
            return null;
        }

        TourSpot firstSpot = firstStop.getTourSpot();
        return new CourseRecommendationResponse(
                course.getId(),
                course.getSourceCourseId(),
                firstSpot.getName(),
                firstStop.getThemeName(),
                stops.size(),
                firstSpot.getLatitude(),
                firstSpot.getLongitude(),
                climateIndex
        );
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
