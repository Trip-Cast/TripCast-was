package com.tripcast.tourweather.common.error;

import io.swagger.v3.oas.annotations.media.Schema;

public record ErrorResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int status,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String message
) {
}
