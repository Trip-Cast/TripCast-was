package com.tripcast.tourweather.tourspot.course;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripcast.tourweather.climate.ClimateIndexService;
import com.tripcast.tourweather.climate.dto.ClimateIndexResponse;
import com.tripcast.tourweather.tourspot.TourSpot;
import com.tripcast.tourweather.tourspot.course.dto.CourseRecommendationResponse;
import com.tripcast.tourweather.tourspot.course.dto.TourCourseResponse;
import com.tripcast.tourweather.tourspot.course.dto.TourCourseStopResponse;
import com.tripcast.tourweather.tourspot.course.dto.TourCourseStopWeatherResponse;
import com.tripcast.tourweather.tourspot.course.dto.TourCourseSummaryResponse;
import com.tripcast.tourweather.tourspot.course.dto.TourCourseWeatherResponse;
import com.tripcast.tourweather.weather.WeatherExecutorConfig;
import com.tripcast.tourweather.weather.WeatherService;
import com.tripcast.tourweather.weather.dto.WeatherResponse;
import com.tripcast.tourweather.weather.util.GridCoordinate;
import com.tripcast.tourweather.weather.util.LatLonToGridConverter;

@Service
public class TourCourseService {

    private static final Logger log =
            LoggerFactory.getLogger(TourCourseService.class);

    private final TourCourseRepository courseRepository;
    private final TourCourseStopRepository courseStopRepository;
    private final ClimateIndexService climateIndexService;
    private final WeatherService weatherService;
    private final Executor weatherExecutor;

    public TourCourseService(
            TourCourseRepository courseRepository,
            TourCourseStopRepository courseStopRepository,
            ClimateIndexService climateIndexService,
            WeatherService weatherService,
            @Qualifier(WeatherExecutorConfig.WEATHER_EXECUTOR)
            Executor weatherExecutor
    ) {
        this.courseRepository = courseRepository;
        this.courseStopRepository = courseStopRepository;
        this.climateIndexService = climateIndexService;
        this.weatherService = weatherService;
        this.weatherExecutor = weatherExecutor;
    }

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
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

    /**
     * 의도적으로 클래스 단위 트랜잭션을 걸지 않는다. 기상청 호출은 수 초가 걸릴 수 있어,
     * 트랜잭션(=DB 커넥션) 안에서 호출하면 동시 요청이 커넥션 풀을 고갈시킨다.
     * DB 조회는 각 리포지토리 호출의 짧은 트랜잭션으로 끝내고, 정류지는 관광지까지
     * 한 번에 fetch join 해 두므로 이후 날씨 호출 중에는 DB에 접근하지 않는다.
     */
    public TourCourseWeatherResponse getCourseWeather(Long courseId) {
        TourCourse course = findCourse(courseId);
        Map<String, Optional<ClimateIndexResponse>> climateByRegion =
                new HashMap<>();

        List<TourCourseStopResponse> spots = findStops(courseId)
                .stream()
                .map(TourCourseStopResponse::from)
                .toList();
        Map<GridCoordinate, Optional<WeatherResponse>> weatherByGrid =
                fetchWeatherByGrid(courseId, spots);

        List<TourCourseStopWeatherResponse> stops = spots.stream()
                .map(spot -> toWeatherResponse(
                        spot,
                        climateByRegion,
                        weatherByGrid
                ))
                .toList();

        return new TourCourseWeatherResponse(
                course.getId(),
                course.getSourceCourseId(),
                stops
        );
    }

    @Transactional(readOnly = true)
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
                .findAllWithTourSpotByCourseIds(List.of(courseId));
    }

    private TourCourseStopWeatherResponse toWeatherResponse(
            TourCourseStopResponse spot,
            Map<String, Optional<ClimateIndexResponse>> climateByRegion,
            Map<GridCoordinate, Optional<WeatherResponse>> weatherByGrid
    ) {
        Optional<ClimateIndexResponse> climateIndex = climateByRegion
                .computeIfAbsent(
                        spot.regionId(),
                        regionId -> Optional.ofNullable(
                                climateIndexService.findLatestOrNull(regionId)
                        )
                );

        GridCoordinate grid = toGridOrNull(spot);
        WeatherResponse weather = grid == null
                ? null
                : weatherByGrid.getOrDefault(grid, Optional.empty())
                        .orElse(null);

        return new TourCourseStopWeatherResponse(
                spot,
                climateIndex.orElse(null),
                weather
        );
    }

    /**
     * 같은 기상청 격자(5km)에 속한 정류지는 예보가 같으므로 격자당 한 번만 조회하고,
     * 서로 다른 격자는 weatherExecutor에서 동시에 조회한다. 어떤 격자가 실패해도
     * 그 격자에 속한 정류지의 weather만 null이 된다.
     */
    private Map<GridCoordinate, Optional<WeatherResponse>> fetchWeatherByGrid(
            Long courseId,
            List<TourCourseStopResponse> spots
    ) {
        Map<GridCoordinate, TourCourseStopResponse> firstSpotByGrid =
                new LinkedHashMap<>();
        for (TourCourseStopResponse spot : spots) {
            GridCoordinate grid = toGridOrNull(spot);
            if (grid != null) {
                firstSpotByGrid.putIfAbsent(grid, spot);
            }
        }

        Map<GridCoordinate, CompletableFuture<Optional<WeatherResponse>>> futures =
                new LinkedHashMap<>();
        firstSpotByGrid.forEach((grid, spot) -> futures.put(
                grid,
                CompletableFuture.supplyAsync(
                        () -> fetchWeather(courseId, spot, grid),
                        weatherExecutor
                )
        ));

        Map<GridCoordinate, Optional<WeatherResponse>> result =
                new HashMap<>();
        futures.forEach((grid, future) -> result.put(grid, future.join()));
        return result;
    }

    private GridCoordinate toGridOrNull(TourCourseStopResponse spot) {
        try {
            return LatLonToGridConverter.convert(
                    spot.latitude(),
                    spot.longitude()
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "코스 관광지 격자 변환 실패. tourSpotId={}",
                    spot.tourSpotId(),
                    exception
            );
            return null;
        }
    }

    private Optional<WeatherResponse> fetchWeather(
            Long courseId,
            TourCourseStopResponse spot,
            GridCoordinate grid
    ) {
        try {
            return Optional.of(weatherService.getWeather(grid.nx(), grid.ny()));
        } catch (RuntimeException exception) {
            log.warn(
                    "코스 관광지 동네예보 조회 실패. courseId={}, tourSpotId={}, nx={}, ny={}",
                    courseId,
                    spot.tourSpotId(),
                    grid.nx(),
                    grid.ny(),
                    exception
            );
            return Optional.empty();
        }
    }
}
