package com.tripcast.tourweather.climate;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.tripcast.tourweather.climate.client.TourWeatherIndexClient;
import com.tripcast.tourweather.climate.client.dto.TourClimateApiResponse.Item;
import com.tripcast.tourweather.climate.domain.RegionClimateIndex;
import com.tripcast.tourweather.climate.repository.RegionClimateIndexRepository;
import com.tripcast.tourweather.tourspot.TourSpot;
import com.tripcast.tourweather.tourspot.TourSpotRepository;
import com.tripcast.tourweather.tourspot.course.TourCourse;
import com.tripcast.tourweather.tourspot.course.TourCourseRepository;
import com.tripcast.tourweather.tourspot.course.TourCourseStop;
import com.tripcast.tourweather.tourspot.course.TourCourseStopRepository;

@SpringBootTest(properties = {
        "app.climate-index-sync.api-enabled=true",
        "app.climate-index-sync.scheduler-enabled=true",
        "app.climate-index-sync.cron=0 0 0 1 1 *"
})
@Transactional
class ClimateIndexSyncIntegrationTest {

    @Autowired
    private ClimateIndexSyncService climateIndexSyncService;

    @Autowired
    private RegionClimateIndexRepository climateIndexRepository;

    @Autowired
    private TourCourseRepository courseRepository;

    @Autowired
    private TourCourseStopRepository courseStopRepository;

    @Autowired
    private TourSpotRepository tourSpotRepository;

    @MockitoBean
    private TourWeatherIndexClient tourWeatherIndexClient;

    @BeforeEach
    void setUp() {
        climateIndexRepository.deleteAll();
        courseStopRepository.deleteAll();
        tourSpotRepository.deleteAll();
        courseRepository.deleteAll();

        TourCourse course = courseRepository.save(new TourCourse("TH-TEST"));
        TourSpot firstSpot = tourSpotRepository.save(new TourSpot(
                "서울 테스트 관광지 1",
                37.5665,
                126.9780
        ));
        TourSpot secondSpot = tourSpotRepository.save(new TourSpot(
                "서울 테스트 관광지 2",
                37.5700,
                126.9800
        ));

        courseStopRepository.saveAll(List.of(
                new TourCourseStop(
                        "TEST-SPOT-1",
                        course,
                        firstSpot,
                        "1111051000",
                        1,
                        10,
                        "실외",
                        "TH01",
                        "테스트"
                ),
                new TourCourseStop(
                        "TEST-SPOT-2",
                        course,
                        secondSpot,
                        "1111061500",
                        2,
                        10,
                        "실외",
                        "TH01",
                        "테스트"
                )
        ));
    }

    @Test
    void 재실행해도_지역과_날짜별_행을_하나만_유지한다() {
        LocalDateTime requestTime = LocalDateTime.of(2026, 9, 21, 5, 0);
        when(tourWeatherIndexClient.getCityTourClimateIndex(
                "1111000000",
                requestTime,
                1
        )).thenReturn(
                List.of(item("0.44", "매우좋음")),
                List.of(item("0.65", "좋음"))
        );

        ClimateIndexSyncResult first = climateIndexSyncService.sync(
                requestTime,
                1
        );
        ClimateIndexSyncResult second = climateIndexSyncService.sync(
                requestTime,
                1
        );

        RegionClimateIndex saved = climateIndexRepository
                .findByRegionIdAndBaseDate(
                        "1111000000",
                        LocalDate.of(2026, 9, 21)
                )
                .orElseThrow();

        assertAll(
                () -> assertEquals(1, first.requestedRegions()),
                () -> assertEquals(1, second.requestedRegions()),
                () -> assertEquals(1, climateIndexRepository.count()),
                () -> assertEquals(
                        new BigDecimal("0.65"),
                        saved.getScore()
                ),
                () -> assertEquals("좋음", saved.getGrade())
        );
    }

    private Item item(String score, String grade) {
        return new Item(
                "2026-09-21 06:00",
                "1111000000",
                "서울 종로구",
                "서울",
                "종로구",
                score,
                grade
        );
    }
}
