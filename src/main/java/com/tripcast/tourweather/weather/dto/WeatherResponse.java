package com.tripcast.tourweather.weather.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record WeatherResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "20260923")
        String forecastDate,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "0600")
        String forecastTime,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        double temperature,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "강수확률(%)")
        int rainProbability,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "하늘상태. 기상청 코드를 우리 쪽에서 변환한 고정된 값으로, 이 4개 외의 값은 나오지 않는다.",
                allowableValues = {"맑음", "구름많음", "흐림", "알 수 없음"}
        )
        String sky
) {
}
