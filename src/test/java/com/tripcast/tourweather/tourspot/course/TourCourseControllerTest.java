package com.tripcast.tourweather.tourspot.course;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.tripcast.tourweather.climate.dto.ClimateIndexResponse;
import com.tripcast.tourweather.tourspot.course.dto.CourseRecommendationResponse;
import com.tripcast.tourweather.tourspot.course.dto.TourCourseResponse;
import com.tripcast.tourweather.tourspot.course.dto.TourCourseStopResponse;
import com.tripcast.tourweather.tourspot.course.dto.TourCourseStopWeatherResponse;
import com.tripcast.tourweather.tourspot.course.dto.TourCourseSummaryResponse;
import com.tripcast.tourweather.tourspot.course.dto.TourCourseWeatherResponse;

@WebMvcTest(TourCourseController.class)
@AutoConfigureMockMvc(addFilters = false)
class TourCourseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TourCourseService tourCourseService;

    @Test
    void 코스_목록을_요약_정보와_함께_조회한다() throws Exception {
        when(tourCourseService.searchCourses(0, 20))
                .thenReturn(new PageImpl<>(List.of(
                        new TourCourseSummaryResponse(
                                1L,
                                "TH01",
                                "서울시청",
                                "역사",
                                3L,
                                37.5796,
                                126.977,
                                37.5512,
                                126.9882
                        ),
                        new TourCourseSummaryResponse(
                                2L,
                                "TH02",
                                null,
                                null,
                                0L,
                                null,
                                null,
                                null,
                                null
                        )
                )));

        mockMvc.perform(get("/api/tour-courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].courseId").value(1))
                .andExpect(jsonPath("$.content[0].sourceCourseId")
                        .value("TH01"))
                .andExpect(jsonPath("$.content[0].representativeSpotName")
                        .value("서울시청"))
                .andExpect(jsonPath("$.content[0].stopCount").value(3))
                .andExpect(jsonPath("$.content[0].startLatitude")
                        .value(37.5796))
                .andExpect(jsonPath("$.content[0].startLongitude")
                        .value(126.977))
                .andExpect(jsonPath("$.content[0].endLatitude")
                        .value(37.5512))
                .andExpect(jsonPath("$.content[0].endLongitude")
                        .value(126.9882))
                .andExpect(jsonPath("$.content[1].startLatitude").isEmpty())
                .andExpect(jsonPath("$.content[1].endLongitude").isEmpty());
    }

    @Test
    void 코스와_관광지_목록을_조회한다() throws Exception {
        TourCourseStopResponse stop = new TourCourseStopResponse(
                10L,
                "1111000001",
                20L,
                "서울시청",
                37.5665,
                126.9780,
                "1111000000",
                1,
                30,
                "실외",
                "01",
                "역사"
        );
        when(tourCourseService.getCourse(1L))
                .thenReturn(new TourCourseResponse(
                        1L,
                        "TH01",
                        List.of(stop)
                ));

        mockMvc.perform(get("/api/tour-courses/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").value(1))
                .andExpect(jsonPath("$.sourceCourseId").value("TH01"))
                .andExpect(jsonPath("$.stops[0].tourSpotName")
                        .value("서울시청"))
                .andExpect(jsonPath("$.stops[0].courseOrder").value(1));
    }

    @Test
    void 존재하지_않는_코스는_404를_반환한다() throws Exception {
        when(tourCourseService.getCourse(999L))
                .thenThrow(new TourCourseNotFoundException(999L));

        mockMvc.perform(get("/api/tour-courses/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message")
                        .value("관광코스를 찾을 수 없습니다: 999"));
    }

    @Test
    void 코스_통합_날씨의_부분_응답을_반환한다() throws Exception {
        TourCourseStopResponse stop = new TourCourseStopResponse(
                10L,
                "1111000001",
                20L,
                "서울시청",
                37.5665,
                126.9780,
                "1111000000",
                1,
                30,
                "실외",
                "01",
                "역사"
        );
        when(tourCourseService.getCourseWeather(1L))
                .thenReturn(new TourCourseWeatherResponse(
                        1L,
                        "TH01",
                        List.of(new TourCourseStopWeatherResponse(
                                stop,
                                null,
                                null
                        ))
                ));

        mockMvc.perform(get("/api/tour-courses/1/weather"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceCourseId").value("TH01"))
                .andExpect(jsonPath("$.stops[0].spot.tourSpotName")
                        .value("서울시청"))
                .andExpect(jsonPath("$.stops[0].weather").isEmpty());
    }

    @Test
    void 코스_추천_목록을_조회한다() throws Exception {
        when(tourCourseService.getRecommendations(10))
                .thenReturn(List.of(new CourseRecommendationResponse(
                        2L,
                        "TH02",
                        "해운대해수욕장",
                        "해변",
                        3L,
                        35.1587,
                        129.1604,
                        new ClimateIndexResponse(
                                "2611000000",
                                LocalDate.of(2026, 9, 23),
                                new BigDecimal("0.90"),
                                "매우좋음"
                        )
                )));

        mockMvc.perform(get("/api/tour-courses/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].courseId").value(2))
                .andExpect(jsonPath("$[0].representativeLatitude")
                        .value(35.1587))
                .andExpect(jsonPath("$[0].representativeLongitude")
                        .value(129.1604))
                .andExpect(jsonPath("$[0].climateIndex.grade")
                        .value("매우좋음"));
    }

    @Test
    void limit이_범위를_벗어나면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/tour-courses/recommendations")
                        .param("limit", "0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/tour-courses/recommendations")
                        .param("limit", "51"))
                .andExpect(status().isBadRequest());
    }
}
