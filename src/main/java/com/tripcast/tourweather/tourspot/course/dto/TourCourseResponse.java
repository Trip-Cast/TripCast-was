package com.tripcast.tourweather.tourspot.course.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record TourCourseResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Long courseId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String sourceCourseId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<TourCourseStopResponse> stops
) {
}
