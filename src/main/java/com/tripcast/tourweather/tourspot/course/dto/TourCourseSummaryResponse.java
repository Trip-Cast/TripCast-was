package com.tripcast.tourweather.tourspot.course.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TourCourseSummaryResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Long courseId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String sourceCourseId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "코스 첫 관광지명. 정류지가 없는 코스는 null")
        String representativeSpotName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "코스 첫 관광지의 테마명. 정류지가 없는 코스는 null")
        String representativeThemeName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "코스의 정류지 수")
        long stopCount,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "첫 정류지(courseOrder 최솟값)의 위도. 정류지가 없는 코스는 null")
        Double startLatitude,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "첫 정류지의 경도. 정류지가 없는 코스는 null")
        Double startLongitude,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "마지막 정류지(courseOrder 최댓값)의 위도. 정류지가 1개면 시작과 같고, 없는 코스는 null")
        Double endLatitude,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "마지막 정류지의 경도. 정류지가 1개면 시작과 같고, 없는 코스는 null")
        Double endLongitude
) {
}
