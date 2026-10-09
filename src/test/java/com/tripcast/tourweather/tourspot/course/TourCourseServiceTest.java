package com.tripcast.tourweather.tourspot.course;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import com.tripcast.tourweather.climate.ClimateIndexService;
import com.tripcast.tourweather.climate.dto.ClimateIndexResponse;
import com.tripcast.tourweather.tourspot.TourSpot;
import com.tripcast.tourweather.tourspot.course.dto.TourCourseSummaryResponse;
import com.tripcast.tourweather.tourspot.course.dto.TourCourseWeatherResponse;
import com.tripcast.tourweather.tourspot.dto.TourSpotCourseResponse;
import com.tripcast.tourweather.weather.WeatherService;
import com.tripcast.tourweather.weather.dto.WeatherResponse;

@ExtendWith(MockitoExtension.class)
class TourCourseServiceTest {

    @Mock
    private TourCourseRepository courseRepository;

    @Mock
    private TourCourseStopRepository courseStopRepository;

    @Mock
    private ClimateIndexService climateIndexService;

    @Mock
    private WeatherService weatherService;

    private TourCourseService tourCourseService;

    @BeforeEach
    void setUp() {
        // 호출 스레드에서 바로 실행해 결과가 결정적이게 한다. 동시 실행은 별도 테스트에서 검증한다.
        tourCourseService = serviceWith(Runnable::run);
    }

    private TourCourseService serviceWith(Executor weatherExecutor) {
        return new TourCourseService(
                courseRepository,
                courseStopRepository,
                climateIndexService,
                weatherService,
                weatherExecutor
        );
    }

    @Test
    void 서로_다른_격자는_동시에_조회한다() {
        TourCourse course = course(1L, "TH01");
        TourSpot seoul = new TourSpot("서울시청", 37.5665, 126.9780);
        TourSpot jeju = new TourSpot("제주시청", 33.4996, 126.5312);
        WeatherResponse weather =
                new WeatherResponse("20260920", "1500", 24.0, 20, "맑음");
        // 두 호출이 동시에 진행 중이어야만 래치가 풀린다. 순차 호출이면 시간 초과로 실패한다.
        CountDownLatch bothInFlight = new CountDownLatch(2);

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseStopRepository.findAllWithTourSpotByCourseIds(List.of(1L)))
                .thenReturn(List.of(
                        stop(course, seoul, 1, "역사"),
                        stop(course, jeju, 2, "역사")
                ));
        when(weatherService.getWeather(anyInt(), anyInt())).thenAnswer(call -> {
            bothInFlight.countDown();
            if (!bothInFlight.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시에 호출되지 않았다");
            }
            return weather;
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            TourCourseWeatherResponse result =
                    serviceWith(pool).getCourseWeather(1L);

            assertAll(
                    () -> assertEquals(weather, result.stops().get(0).weather()),
                    () -> assertEquals(weather, result.stops().get(1).weather())
            );
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void 동네예보가_실패해도_지수를_포함한_부분_응답을_반환한다() {
        TourCourse course = new TourCourse("TH01");
        TourSpot tourSpot = new TourSpot("서울시청", 37.5665, 126.9780);
        TourCourseStop stop = new TourCourseStop(
                "1111000001",
                course,
                tourSpot,
                "1111051000",
                1,
                30,
                "실외",
                "01",
                "역사"
        );
        ClimateIndexResponse climateIndex = new ClimateIndexResponse(
                "1111000000",
                LocalDate.of(2026, 9, 20),
                new BigDecimal("0.82"),
                "매우좋음"
        );

        when(courseRepository.findById(1L))
                .thenReturn(Optional.of(course));
        when(courseStopRepository.findAllWithTourSpotByCourseIds(List.of(1L)))
                .thenReturn(List.of(stop));
        when(climateIndexService.findLatestOrNull("1111000000"))
                .thenReturn(climateIndex);
        when(weatherService.getWeather(60, 127))
                .thenThrow(new IllegalStateException("기상청 API 실패"));

        TourCourseWeatherResponse result =
                tourCourseService.getCourseWeather(1L);

        assertAll(
                () -> assertEquals("TH01", result.sourceCourseId()),
                () -> assertEquals(1, result.stops().size()),
                () -> assertEquals(
                        climateIndex,
                        result.stops().get(0).climateIndex()
                ),
                () -> assertEquals(
                        "1111000000",
                        result.stops().get(0).spot().regionId()
                ),
                () -> assertNull(result.stops().get(0).weather())
        );
    }

    @Test
    void 같은_격자의_정류지는_동네예보를_한_번만_조회한다() {
        TourCourse course = course(1L, "TH01");
        // 서울시청과 덕수궁은 같은 기상청 격자(60,127), 제주시청은 다른 격자
        TourSpot cityHall = new TourSpot("서울시청", 37.5665, 126.9780);
        TourSpot deoksugung = new TourSpot("덕수궁", 37.5658, 126.9751);
        TourSpot jeju = new TourSpot("제주시청", 33.4996, 126.5312);
        WeatherResponse seoul =
                new WeatherResponse("20260920", "1500", 24.0, 20, "맑음");
        WeatherResponse jejuWeather =
                new WeatherResponse("20260920", "1500", 26.0, 30, "흐림");

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseStopRepository.findAllWithTourSpotByCourseIds(List.of(1L)))
                .thenReturn(List.of(
                        stop(course, cityHall, 1, "역사"),
                        stop(course, deoksugung, 2, "역사"),
                        stop(course, jeju, 3, "역사")
                ));
        when(weatherService.getWeather(60, 127)).thenReturn(seoul);
        when(weatherService.getWeather(53, 38)).thenReturn(jejuWeather);

        TourCourseWeatherResponse result =
                tourCourseService.getCourseWeather(1L);

        assertAll(
                () -> assertEquals(seoul, result.stops().get(0).weather()),
                () -> assertEquals(seoul, result.stops().get(1).weather()),
                () -> assertEquals(jejuWeather, result.stops().get(2).weather()),
                () -> verify(weatherService, times(1)).getWeather(60, 127),
                () -> verify(weatherService, times(1)).getWeather(53, 38),
                () -> verify(weatherService, times(2)).getWeather(anyInt(), anyInt())
        );
    }

    @Test
    void 코스_목록_조회시_첫_관광지명과_테마_정류지_수_시작_도착_좌표를_반환한다() {
        TourCourse course = course(1L, "TH01");
        TourCourseStop first = stop(
                course, new TourSpot("서울시청", 37.5665, 126.9780), 1, "역사"
        );
        TourCourseStop middle = stop(
                course, new TourSpot("덕수궁", 37.5658, 126.9751), 2, "역사"
        );
        TourCourseStop last = stop(
                course, new TourSpot("남산타워", 37.5512, 126.9882), 3, "전망"
        );

        when(courseRepository.findAll(
                PageRequest.of(0, 20, Sort.by("id").ascending())
        )).thenReturn(new PageImpl<>(List.of(course)));
        when(courseStopRepository.findAllWithTourSpotByCourseIds(List.of(1L)))
                .thenReturn(List.of(first, middle, last));

        Page<TourCourseSummaryResponse> result =
                tourCourseService.searchCourses(0, 20);

        TourCourseSummaryResponse summary = result.getContent().get(0);
        assertAll(
                () -> assertEquals(1L, summary.courseId()),
                () -> assertEquals("TH01", summary.sourceCourseId()),
                () -> assertEquals("서울시청", summary.representativeSpotName()),
                () -> assertEquals("역사", summary.representativeThemeName()),
                () -> assertEquals(3L, summary.stopCount()),
                () -> assertEquals(37.5665, summary.startLatitude()),
                () -> assertEquals(126.9780, summary.startLongitude()),
                () -> assertEquals(37.5512, summary.endLatitude()),
                () -> assertEquals(126.9882, summary.endLongitude())
        );
    }

    @Test
    void 정류지가_1개면_시작과_도착_좌표가_같다() {
        TourCourse course = course(1L, "TH01");
        TourCourseStop only = stop(
                course, new TourSpot("서울시청", 37.5665, 126.9780), 1, "역사"
        );

        when(courseRepository.findAll(
                PageRequest.of(0, 20, Sort.by("id").ascending())
        )).thenReturn(new PageImpl<>(List.of(course)));
        when(courseStopRepository.findAllWithTourSpotByCourseIds(List.of(1L)))
                .thenReturn(List.of(only));

        TourCourseSummaryResponse summary = tourCourseService
                .searchCourses(0, 20).getContent().get(0);

        assertAll(
                () -> assertEquals(1L, summary.stopCount()),
                () -> assertEquals(37.5665, summary.startLatitude()),
                () -> assertEquals(37.5665, summary.endLatitude()),
                () -> assertEquals(126.9780, summary.startLongitude()),
                () -> assertEquals(126.9780, summary.endLongitude())
        );
    }

    @Test
    void 정류지가_없는_코스는_이름_테마_좌표가_모두_null이다() {
        TourCourse course = course(1L, "TH01");

        when(courseRepository.findAll(
                PageRequest.of(0, 20, Sort.by("id").ascending())
        )).thenReturn(new PageImpl<>(List.of(course)));
        when(courseStopRepository.findAllWithTourSpotByCourseIds(List.of(1L)))
                .thenReturn(List.of());

        TourCourseSummaryResponse summary = tourCourseService
                .searchCourses(0, 20).getContent().get(0);

        assertAll(
                () -> assertEquals(0L, summary.stopCount()),
                () -> assertNull(summary.representativeSpotName()),
                () -> assertNull(summary.representativeThemeName()),
                () -> assertNull(summary.startLatitude()),
                () -> assertNull(summary.startLongitude()),
                () -> assertNull(summary.endLatitude()),
                () -> assertNull(summary.endLongitude())
        );
    }

    @Test
    void 코스_목록_조회시_정류지는_코스_수와_무관하게_쿼리_한_번으로_가져온다() {
        TourCourse first = course(1L, "TH01");
        TourCourse second = course(2L, "TH02");
        TourSpot spot = new TourSpot("서울시청", 37.5665, 126.9780);

        when(courseRepository.findAll(
                PageRequest.of(0, 20, Sort.by("id").ascending())
        )).thenReturn(new PageImpl<>(List.of(first, second)));
        when(courseStopRepository
                .findAllWithTourSpotByCourseIds(List.of(1L, 2L)))
                .thenReturn(List.of(
                        stop(first, spot, 1, "역사"),
                        stop(second, spot, 1, "역사"),
                        stop(second, spot, 2, "역사")
                ));

        Page<TourCourseSummaryResponse> result =
                tourCourseService.searchCourses(0, 20);

        assertEquals(1L, result.getContent().get(0).stopCount());
        assertEquals(2L, result.getContent().get(1).stopCount());
        verify(courseStopRepository, times(1))
                .findAllWithTourSpotByCourseIds(anyCollection());
        verifyNoMoreInteractions(courseStopRepository);
    }

    @Test
    void 관광지가_속한_코스를_요약과_방문_순서로_반환한다() {
        TourCourse first = course(1L, "TH01");
        TourCourse second = course(3L, "TH03");
        TourSpot seoul = new TourSpot("서울시청", 37.5665, 126.9780);
        TourSpot deoksu = new TourSpot("덕수궁", 37.5658, 126.9751);
        TourSpot namsan = new TourSpot("남산타워", 37.5512, 126.9882);

        when(courseStopRepository.findCourseOrdersByTourSpotId(10L))
                .thenReturn(List.of(
                        new SpotCourseOrder(1L, 2),
                        new SpotCourseOrder(3L, 1)
                ));
        when(courseRepository.findAllById(List.of(1L, 3L)))
                .thenReturn(List.of(second, first));
        when(courseStopRepository.findAllWithTourSpotByCourseIds(anyCollection()))
                .thenReturn(List.of(
                        stop(first, seoul, 1, "역사"),
                        stop(first, deoksu, 2, "역사"),
                        stop(first, namsan, 3, "전망"),
                        stop(second, deoksu, 1, "문화")
                ));

        List<TourSpotCourseResponse> result =
                tourCourseService.findCoursesByTourSpotId(10L);

        assertEquals(2, result.size());
        TourSpotCourseResponse firstResult = result.get(0);
        TourSpotCourseResponse secondResult = result.get(1);
        assertAll(
                () -> assertEquals(1L, firstResult.courseId()),
                () -> assertEquals("TH01", firstResult.sourceCourseId()),
                () -> assertEquals("서울시청", firstResult.representativeSpotName()),
                () -> assertEquals("역사", firstResult.representativeThemeName()),
                () -> assertEquals(3L, firstResult.stopCount()),
                () -> assertEquals(37.5665, firstResult.startLatitude()),
                () -> assertEquals(126.9780, firstResult.startLongitude()),
                () -> assertEquals(37.5512, firstResult.endLatitude()),
                () -> assertEquals(126.9882, firstResult.endLongitude()),
                () -> assertEquals(2, firstResult.spotCourseOrder()),
                () -> assertEquals(3L, secondResult.courseId()),
                () -> assertEquals("덕수궁", secondResult.representativeSpotName()),
                () -> assertEquals(1L, secondResult.stopCount()),
                () -> assertEquals(1, secondResult.spotCourseOrder())
        );
    }

    @Test
    void 코스가_없는_관광지는_빈_목록을_반환하고_추가_조회를_하지_않는다() {
        when(courseStopRepository.findCourseOrdersByTourSpotId(10L))
                .thenReturn(List.of());

        assertEquals(List.of(), tourCourseService.findCoursesByTourSpotId(10L));

        verifyNoMoreInteractions(courseRepository);
        verify(courseStopRepository, times(1))
                .findCourseOrdersByTourSpotId(10L);
        verifyNoMoreInteractions(courseStopRepository);
    }

    @Test
    void 관광지의_코스_조회는_코스_수와_무관하게_쿼리가_고정된다() {
        TourCourse first = course(1L, "TH01");
        TourCourse second = course(2L, "TH02");
        TourCourse third = course(3L, "TH03");
        TourSpot spot = new TourSpot("서울시청", 37.5665, 126.9780);

        when(courseStopRepository.findCourseOrdersByTourSpotId(10L))
                .thenReturn(List.of(
                        new SpotCourseOrder(1L, 1),
                        new SpotCourseOrder(2L, 1),
                        new SpotCourseOrder(3L, 1)
                ));
        when(courseRepository.findAllById(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(first, second, third));
        when(courseStopRepository.findAllWithTourSpotByCourseIds(anyCollection()))
                .thenReturn(List.of(
                        stop(first, spot, 1, "역사"),
                        stop(second, spot, 1, "역사"),
                        stop(third, spot, 1, "역사")
                ));

        assertEquals(3, tourCourseService.findCoursesByTourSpotId(10L).size());

        verify(courseRepository, times(1)).findAllById(anyCollection());
        verify(courseStopRepository, times(1))
                .findAllWithTourSpotByCourseIds(anyCollection());
        verify(courseStopRepository, times(1))
                .findCourseOrdersByTourSpotId(10L);
        verifyNoMoreInteractions(courseRepository, courseStopRepository);
    }

    private TourCourse course(Long id, String sourceCourseId) {
        TourCourse course = new TourCourse(sourceCourseId);
        ReflectionTestUtils.setField(course, "id", id);
        return course;
    }

    private TourCourseStop stop(
            TourCourse course,
            TourSpot spot,
            int courseOrder,
            String themeName
    ) {
        return new TourCourseStop(
                "SPOT-" + course.getId() + "-" + courseOrder,
                course,
                spot,
                "1111051000",
                courseOrder,
                30,
                "실외",
                "01",
                themeName
        );
    }
}
