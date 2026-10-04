package com.tripcast.tourweather.climate.client;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.tripcast.tourweather.climate.ConditionalOnClimateIndexSync;
import com.tripcast.tourweather.climate.client.dto.TourClimateApiResponse;
import com.tripcast.tourweather.climate.client.dto.TourClimateApiResponse.Item;

@Component
@ConditionalOnClimateIndexSync
public class TourWeatherIndexClient {

    private static final DateTimeFormatter REQUEST_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHH");

    private final RestClient restClient;
    private final String apiKey;

    public TourWeatherIndexClient(
            RestClient.Builder restClientBuilder,
            @Value("${tour-climate.api.base-url}") String baseUrl,
            @Value("${tour-climate.api.key}") String apiKey,
            @Value("${tour-climate.api.connect-timeout:3s}") Duration connectTimeout,
            @Value("${tour-climate.api.read-timeout:5s}") Duration readTimeout
    ) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);

        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.apiKey = apiKey;
    }

    public List<Item> getCityTourClimateIndex(
            String regionId,
            LocalDateTime currentDate,
            int forecastDays
    ) {
        TourClimateApiResponse apiResponse = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/getCityTourClmIdx1")
                        .queryParam("pageNo", 1)
                        .queryParam("numOfRows", Math.max(forecastDays, 10))
                        .queryParam("dataType", "JSON")
                        .queryParam(
                                "CURRENT_DATE",
                                currentDate.format(REQUEST_TIME_FORMAT)
                        )
                        .queryParam("DAY", forecastDays)
                        .queryParam("CITY_AREA_ID", regionId)
                        .queryParam("ServiceKey", apiKey)
                        .build())
                .retrieve()
                .body(TourClimateApiResponse.class);

        return extractItems(apiResponse);
    }

    private List<Item> extractItems(TourClimateApiResponse apiResponse) {
        if (apiResponse == null || apiResponse.response() == null) {
            throw new TourClimateApiException("관광기후지수 API 응답이 비어 있습니다.");
        }

        TourClimateApiResponse.Header header = apiResponse.response().header();
        if (header == null
                || !("0".equals(header.resultCode())
                || "00".equals(header.resultCode()))) {
            String code = header == null ? "unknown" : header.resultCode();
            String message = header == null ? "응답 헤더 없음" : header.resultMsg();
            throw new TourClimateApiException(
                    "관광기후지수 API 오류: " + code + " " + message
            );
        }

        TourClimateApiResponse.Body body = apiResponse.response().body();
        if (body == null || body.items() == null || body.items().item() == null) {
            return List.of();
        }
        return body.items().item();
    }
}
