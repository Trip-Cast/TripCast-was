package com.tripcast.tourweather.tourspot.course.dto;

import com.tripcast.tourweather.climate.dto.ClimateIndexResponse;

import io.swagger.v3.oas.annotations.media.Schema;

public record CourseRecommendationResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Long courseId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String sourceCourseId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "코스 첫 관광지명. 관광지가 없는 코스는 추천 목록에서 제외되므로 항상 존재")
        String representativeSpotName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "코스 첫 관광지의 테마명. 관광지가 없는 코스는 추천 목록에서 제외되므로 항상 존재")
        String representativeThemeName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "코스의 정류지 수")
        long stopCount,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "representativeSpotName에 해당하는 코스 첫 관광지의 위도. 관광지가 없는 코스는 추천 목록에서 제외되므로 항상 존재")
        Double representativeLatitude,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "코스 첫 관광지의 경도. 관광지가 없는 코스는 추천 목록에서 제외되므로 항상 존재")
        Double representativeLongitude,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "코스 첫 관광지가 속한 지역의 최신 관광기후지수. 값이 없는 코스는 추천 목록에서 제외되므로 항상 존재")
        ClimateIndexResponse climateIndex
) {
}
