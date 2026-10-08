package com.tripcast.tourweather.tourspot.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.tripcast.tourweather.climate.ClimateIndexService;
import com.tripcast.tourweather.climate.dto.ClimateIndexResponse;
import com.tripcast.tourweather.tourspot.TourSpot;
import com.tripcast.tourweather.tourspot.course.dto.CourseRecommendationResponse;
import com.tripcast.tourweather.weather.WeatherService;

@ExtendWith(MockitoExtension.class)
class TourCourseRecommendationTest {

    @Mock
    private TourCourseRepository courseRepository;

    @Mock
    private TourCourseStopRepository courseStopRepository;

    @Mock
    private ClimateIndexService climateIndexService;

    @Mock
    private WeatherService weatherService;

    @InjectMocks
    private TourCourseService tourCourseService;

    @Test
    void 관광기후지수가_높은_코스부터_정렬해서_반환하고_지수가_없는_코스는_제외한다() {
        TourSpot spot = new TourSpot("테스트 관광지", 37.0, 127.0);

        TourCourse lowScoreCourse = course(1L, "TH01");
        TourCourse highScoreCourse = course(2L, "TH02");
        TourCourse noDataCourse = course(3L, "TH03");

        when(courseRepository.findAll()).thenReturn(List.of(
                lowScoreCourse,
                highScoreCourse,
                noDataCourse
        ));

        when(courseStopRepository
                .findAllWithTourSpotByCourseIds(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(
                        stop(lowScoreCourse, spot, "1111051000"),
                        stop(highScoreCourse, spot, "2611051000"),
                        stop(noDataCourse, spot, "9999051000")
                ));

        when(climateIndexService.findLatestOrNull("1111051000"))
                .thenReturn(climateIndex("1111000000", "0.30", "보통"));
        when(climateIndexService.findLatestOrNull("2611051000"))
                .thenReturn(climateIndex("2611000000", "0.90", "매우좋음"));
        when(climateIndexService.findLatestOrNull("9999051000"))
                .thenReturn(null);

        List<CourseRecommendationResponse> result =
                tourCourseService.getRecommendations(10);

        assertEquals(2, result.size());
        assertEquals(2L, result.get(0).courseId());
        assertEquals(1L, result.get(1).courseId());
    }

    @Test
    void limit_개수만큼만_반환한다() {
        TourSpot spot = new TourSpot("테스트 관광지", 37.0, 127.0);
        TourCourse first = course(1L, "TH01");
        TourCourse second = course(2L, "TH02");

        when(courseRepository.findAll())
                .thenReturn(List.of(first, second));
        when(courseStopRepository
                .findAllWithTourSpotByCourseIds(List.of(1L, 2L)))
                .thenReturn(List.of(
                        stop(first, spot, "1111051000"),
                        stop(second, spot, "2611051000")
                ));
        when(climateIndexService.findLatestOrNull("1111051000"))
                .thenReturn(climateIndex("1111000000", "0.30", "보통"));
        when(climateIndexService.findLatestOrNull("2611051000"))
                .thenReturn(climateIndex("2611000000", "0.90", "매우좋음"));

        List<CourseRecommendationResponse> result =
                tourCourseService.getRecommendations(1);

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).courseId());
    }

    @Test
    void 대표_관광지의_좌표와_정류지_수를_함께_반환한다() {
        TourCourse course = course(1L, "TH01");
        TourSpot firstSpot = new TourSpot("경복궁", 37.5796, 126.9770);
        TourSpot secondSpot = new TourSpot("남산타워", 37.5512, 126.9882);

        when(courseRepository.findAll()).thenReturn(List.of(course));
        when(courseStopRepository.findAllWithTourSpotByCourseIds(List.of(1L)))
                .thenReturn(List.of(
                        stop(course, firstSpot, "1111051000"),
                        new TourCourseStop(
                                "SPOT-2", course, secondSpot, "1111051000",
                                2, 10, "실외", "01", "역사"
                        )
                ));
        when(climateIndexService.findLatestOrNull("1111051000"))
                .thenReturn(climateIndex("1111000000", "0.30", "보통"));

        CourseRecommendationResponse result =
                tourCourseService.getRecommendations(10).get(0);

        assertEquals("경복궁", result.representativeSpotName());
        assertEquals(37.5796, result.representativeLatitude());
        assertEquals(126.9770, result.representativeLongitude());
        assertEquals(2L, result.stopCount());
    }

    @Test
    void 정류지가_없는_코스는_추천에서_제외한다() {
        TourCourse course = course(1L, "TH01");

        when(courseRepository.findAll()).thenReturn(List.of(course));
        when(courseStopRepository.findAllWithTourSpotByCourseIds(List.of(1L)))
                .thenReturn(List.of());

        assertEquals(0, tourCourseService.getRecommendations(10).size());
    }

    private TourCourse course(Long id, String sourceCourseId) {
        TourCourse course = new TourCourse(sourceCourseId);
        ReflectionTestUtils.setField(course, "id", id);
        return course;
    }

    private TourCourseStop stop(
            TourCourse course,
            TourSpot spot,
            String regionId
    ) {
        return new TourCourseStop(
                "SPOT-" + regionId,
                course,
                spot,
                regionId,
                1,
                10,
                "실외",
                "01",
                "역사"
        );
    }

    private ClimateIndexResponse climateIndex(
            String regionId,
            String score,
            String grade
    ) {
        return new ClimateIndexResponse(
                regionId,
                LocalDate.of(2026, 9, 23),
                new BigDecimal(score),
                grade
        );
    }
}
