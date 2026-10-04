package com.tripcast.tourweather.climate.client;

public class TourClimateApiException extends RuntimeException {

    public static final String NO_DATA_CODE = "03";

    private final String resultCode;

    public TourClimateApiException(String message) {
        this(message, null);
    }

    public TourClimateApiException(String message, String resultCode) {
        super(message);
        this.resultCode = resultCode;
    }

    public boolean isNoData() {
        return NO_DATA_CODE.equals(resultCode);
    }
}
