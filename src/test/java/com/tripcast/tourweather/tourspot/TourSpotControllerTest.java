package com.tripcast.tourweather.tourspot;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.tripcast.tourweather.tourspot.dto.TourSpotCourseResponse;
import com.tripcast.tourweather.tourspot.dto.TourSpotResponse;

@WebMvcTest(TourSpotController.class)
@AutoConfigureMockMvc(addFilters = false)
class TourSpotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TourSpotService tourSpotService;

    @Test
    void 관광지를_페이지로_검색한다() throws Exception {
        TourSpotResponse tourSpot = new TourSpotResponse(
                1L,
                "서울시청",
                37.5665,
                126.9780);

        when(tourSpotService.search("서울", 0, 20))
                .thenReturn(new PageImpl<>(
                        List.of(tourSpot),
                        PageRequest.of(0, 20),
                        1));

        mockMvc.perform(get("/api/tour-spots")
                        .param("keyword", "서울")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].name").value("서울시청"))
                .andExpect(jsonPath("$.content[0].latitude").value(37.5665))
                .andExpect(jsonPath("$.content[0].longitude").value(126.9780))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void 음수_페이지는_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/tour-spots")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("요청 값이 올바르지 않습니다."));

        verifyNoInteractions(tourSpotService);
    }

    @Test
    void 페이지_크기가_100을_넘으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/tour-spots")
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("요청 값이 올바르지 않습니다."));

        verifyNoInteractions(tourSpotService);
    }

    @Test
    void 존재하지_않는_관광지_날씨는_404를_반환한다() throws Exception {
        when(tourSpotService.getWeather(999L))
                .thenThrow(new TourSpotNotFoundException(999L));

        mockMvc.perform(get("/api/tour-spots/999/weather"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message")
                        .value("관광지를 찾을 수 없습니다: 999"));
    }

    @Test
    void 관광지가_포함된_코스_목록을_조회한다() throws Exception {
        when(tourSpotService.getCourses(10L))
                .thenReturn(List.of(
                        new TourSpotCourseResponse(
                                1L, "TH01", "서울시청", "역사", 3L,
                                37.5665, 126.978, 37.5512, 126.9882, 2),
                        new TourSpotCourseResponse(
                                4L, "TH04", null, null, 0L,
                                null, null, null, null, 1)));

        mockMvc.perform(get("/api/tour-spots/10/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].courseId").value(1))
                .andExpect(jsonPath("$[0].sourceCourseId").value("TH01"))
                .andExpect(jsonPath("$[0].representativeSpotName")
                        .value("서울시청"))
                .andExpect(jsonPath("$[0].representativeThemeName")
                        .value("역사"))
                .andExpect(jsonPath("$[0].stopCount").value(3))
                .andExpect(jsonPath("$[0].startLatitude").value(37.5665))
                .andExpect(jsonPath("$[0].endLongitude").value(126.9882))
                .andExpect(jsonPath("$[0].spotCourseOrder").value(2))
                .andExpect(jsonPath("$[1].courseId").value(4))
                .andExpect(jsonPath("$[1].representativeSpotName").doesNotExist())
                .andExpect(jsonPath("$[1].spotCourseOrder").value(1));
    }

    @Test
    void 포함된_코스가_없으면_빈_배열을_반환한다() throws Exception {
        when(tourSpotService.getCourses(10L)).thenReturn(List.of());

        mockMvc.perform(get("/api/tour-spots/10/courses"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(tourSpotService).getCourses(10L);
    }

    @Test
    void 존재하지_않는_관광지의_코스_조회는_404를_반환한다() throws Exception {
        when(tourSpotService.getCourses(999L))
                .thenThrow(new TourSpotNotFoundException(999L));

        mockMvc.perform(get("/api/tour-spots/999/courses"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message")
                        .value("관광지를 찾을 수 없습니다: 999"));
    }

    @Test
    void 관광지_ID가_0_이하이면_코스_조회는_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/tour-spots/0/courses"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("요청 값이 올바르지 않습니다."));

        mockMvc.perform(get("/api/tour-spots/-1/courses"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(tourSpotService);
    }

    @Test
    void 관광지_ID가_숫자가_아니면_코스_조회는_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/tour-spots/abc/courses"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(tourSpotService);
    }
}
