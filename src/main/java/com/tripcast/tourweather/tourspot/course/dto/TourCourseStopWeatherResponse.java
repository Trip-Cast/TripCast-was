package com.tripcast.tourweather.tourspot.course.dto;

import com.tripcast.tourweather.climate.dto.ClimateIndexResponse;
import com.tripcast.tourweather.weather.dto.WeatherResponse;

import io.swagger.v3.oas.annotations.media.Schema;

public record TourCourseStopWeatherResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        TourCourseStopResponse spot,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "관광지가 속한 지역의 최신 관광기후지수. 저장된 지수가 없으면 null")
        ClimateIndexResponse climateIndex,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "기상청 동네예보. 이 정류지의 예보 조회가 실패하면 null(코스 전체 응답은 실패하지 않음)")
        WeatherResponse weather
) {
}
