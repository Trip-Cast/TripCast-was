package com.tripcast.tourweather;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true",
        "app.climate-index-sync.api-enabled=true"
})
@AutoConfigureMockMvc
class OpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void Swagger_UI_진입_경로가_정상적으로_열린다() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
    }

    @Test
    void 신규_관광코스와_관광기후지수_API가_OpenAPI에_노출된다()
            throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/regions/{regionId}/climate-index']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/regions/{regionId}/climate-index/range']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/tour-courses/{courseId}']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/tour-courses/{courseId}/weather']"
                ).exists());
    }

    @Test
    void 코스_추천_API가_OpenAPI에_노출된다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/tour-courses/recommendations']"
                ).exists());
    }

    @Test
    void 코스_목록_API가_OpenAPI에_노출된다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/tour-courses']"
                ).exists());
    }

    @Test
    void 인증_및_관리자_API가_OpenAPI에_노출된다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/auth/login']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/climate-index/sync']"
                ).exists());
    }
}
