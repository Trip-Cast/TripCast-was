package com.tripcast.tourweather.weather.client;

import com.tripcast.tourweather.weather.dto.KmaWeatherResponse;
import com.tripcast.tourweather.weather.util.ForecastTime;
import com.tripcast.tourweather.weather.util.ForecastTimeCalculator;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class KmaWeatherClient {

    private final RestClient restClient;
    private final String apiKey;

    public KmaWeatherClient(
            RestClient.Builder restClientBuilder,
            @Value("${weather.api.base-url}") String baseUrl,
            @Value("${weather.api.key}") String apiKey,
            @Value("${weather.api.connect-timeout:3s}") Duration connectTimeout,
            @Value("${weather.api.read-timeout:5s}") Duration readTimeout
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

    public KmaWeatherResponse getVillageForecast(
            int nx,
            int ny
    ) {

        ForecastTime forecastTime =
                ForecastTimeCalculator.calculate();

        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/getVilageFcst")
                        .queryParam("pageNo", 1)
                        .queryParam("numOfRows", 1000)
                        .queryParam("dataType", "JSON")
                        .queryParam(
                                "base_date",
                                forecastTime.baseDate()
                        )
                        .queryParam(
                                "base_time",
                                forecastTime.baseTime()
                        )
                        .queryParam("nx", nx)
                        .queryParam("ny", ny)
                        .queryParam("authKey", apiKey)
                        .build())
                .retrieve()
                .body(KmaWeatherResponse.class);
    }
}