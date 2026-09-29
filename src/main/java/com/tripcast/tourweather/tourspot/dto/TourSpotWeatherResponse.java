package com.tripcast.tourweather.tourspot.dto;

import com.tripcast.tourweather.climate.dto.ClimateIndexResponse;
import com.tripcast.tourweather.weather.dto.WeatherResponse;

import io.swagger.v3.oas.annotations.media.Schema;

public record TourSpotWeatherResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Long tourSpotId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String tourSpotName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Double latitude,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Double longitude,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "기상청 동네예보. 조회 실패 시 이 API 호출 자체가 에러 응답이 된다(null이 아님)")
        WeatherResponse weather,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "관광지가 속한 지역의 최신 관광기후지수. 저장된 지수가 없으면 null")
        ClimateIndexResponse climateIndex
) {
}
