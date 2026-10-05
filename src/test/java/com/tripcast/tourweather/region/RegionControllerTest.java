package com.tripcast.tourweather.region;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.tripcast.tourweather.region.dto.RegionResponse;

@WebMvcTest(RegionController.class)
@AutoConfigureMockMvc(addFilters = false)
class RegionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegionService regionService;

    @Test
    void 지역_목록을_조회한다() throws Exception {
        when(regionService.getRegions(null)).thenReturn(List.of(
                new RegionResponse(
                        "5013000000",
                        "서귀포시",
                        "제주특별자치도",
                        "제주특별자치도 서귀포시"
                )
        ));

        mockMvc.perform(get("/api/regions"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Cache-Control",
                        containsString("max-age=3600")
                ))
                .andExpect(jsonPath("$[0].regionId").value("5013000000"))
                .andExpect(jsonPath("$[0].name").value("서귀포시"))
                .andExpect(jsonPath("$[0].sidoName").value("제주특별자치도"))
                .andExpect(jsonPath("$[0].displayName")
                        .value("제주특별자치도 서귀포시"));
    }

    @Test
    void keyword를_서비스에_전달한다() throws Exception {
        when(regionService.getRegions("서귀포")).thenReturn(List.of(
                new RegionResponse(
                        "5013000000",
                        "서귀포시",
                        "제주특별자치도",
                        "제주특별자치도 서귀포시"
                )
        ));

        mockMvc.perform(get("/api/regions").param("keyword", "서귀포"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void 결과가_없으면_빈_배열을_반환한다() throws Exception {
        when(regionService.getRegions("없는지역")).thenReturn(List.of());

        mockMvc.perform(get("/api/regions").param("keyword", "없는지역"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
