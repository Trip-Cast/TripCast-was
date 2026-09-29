package com.tripcast.tourweather.tourspot.dto;

import com.tripcast.tourweather.tourspot.TourSpot;

import io.swagger.v3.oas.annotations.media.Schema;

public record TourSpotResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Long id,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Double latitude,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Double longitude
) {
    public static TourSpotResponse from(TourSpot tourSpot) {
        return new TourSpotResponse(
                tourSpot.getId(),
                tourSpot.getName(),
                tourSpot.getLatitude(),
                tourSpot.getLongitude()
        );
    }
}
