package com.tripcast.tourweather.tourspot.course.dto;

import com.tripcast.tourweather.tourspot.TourSpot;
import com.tripcast.tourweather.tourspot.course.TourCourseStop;
import com.tripcast.tourweather.climate.util.ClimateRegionIdNormalizer;

import io.swagger.v3.oas.annotations.media.Schema;

public record TourCourseStopResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Long stopId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "원본 CSV의 관광지 아이디")
        String sourceSpotId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Long tourSpotId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String tourSpotName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Double latitude,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Double longitude,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "관광기후지수 조회에 쓰는 정규화된 시군구 코드(10자리, 뒤 5자리는 00000)")
        String regionId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "코스 내 방문 순서(1부터 시작)")
        int courseOrder,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "이전 정류지로부터의 이동시간(분)")
        int travelTime,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "실내/실외 구분. CSV 원본 데이터의 오탈자(예: 살내)는 적재 시 정규화되고, 이 셋 외의 값은 나오지 않는다.",
                allowableValues = {"실내", "실외", "미상"}
        )
        String indoorType,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "테마 코드(TH01~TH06)")
        String themeCode,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String themeName
) {
    public static TourCourseStopResponse from(TourCourseStop stop) {
        TourSpot tourSpot = stop.getTourSpot();
        return new TourCourseStopResponse(
                stop.getId(),
                stop.getSourceSpotId(),
                tourSpot.getId(),
                tourSpot.getName(),
                tourSpot.getLatitude(),
                tourSpot.getLongitude(),
                ClimateRegionIdNormalizer.normalize(stop.getRegionId()),
                stop.getCourseOrder(),
                stop.getTravelTime(),
                stop.getIndoorType(),
                stop.getThemeCode(),
                stop.getThemeName()
        );
    }
}
