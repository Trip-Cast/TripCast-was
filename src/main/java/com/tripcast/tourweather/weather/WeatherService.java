package com.tripcast.tourweather.weather;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.tripcast.tourweather.weather.client.KmaWeatherClient;
import com.tripcast.tourweather.weather.dto.KmaWeatherResponse;
import com.tripcast.tourweather.weather.dto.KmaWeatherResponse.Item;
import com.tripcast.tourweather.weather.dto.WeatherResponse;

@Service
public class WeatherService {

        private final KmaWeatherClient kmaWeatherClient;

        public WeatherService(KmaWeatherClient kmaWeatherClient) {
                this.kmaWeatherClient = kmaWeatherClient;
        }

        // 격자(nx, ny)별로 캐시한다. 예외는 캐시되지 않고, sync=true라 같은 격자의 동시 요청은 기상청을 한 번만 호출한다.
        @Cacheable(
                        cacheNames = WeatherCacheConfig.WEATHER_CACHE,
                        key = "#nx + ':' + #ny",
                        sync = true)
        public WeatherResponse getWeather(
                        int nx,
                        int ny) {

                KmaWeatherResponse response = kmaWeatherClient.getVillageForecast(nx, ny);

                List<Item> items = response.response()
                                .body()
                                .items()
                                .item();

                Item temperatureItem = items.stream()
                                .filter(item -> item.category().equals("TMP"))
                                .findFirst()
                                .orElseThrow(() -> new IllegalStateException("기온 데이터가 없습니다."));

                String forecastDate = temperatureItem.fcstDate();
                String forecastTime = temperatureItem.fcstTime();

                String temperature = temperatureItem.fcstValue();

                String rainProbability = findValue(
                                items,
                                "POP",
                                forecastDate,
                                forecastTime);

                String sky = findValue(
                                items,
                                "SKY",
                                forecastDate,
                                forecastTime);

                return new WeatherResponse(
                                forecastDate,
                                forecastTime,
                                Double.parseDouble(temperature),
                                Integer.parseInt(rainProbability),
                                convertSky(sky));
        }

        private String findValue(
                        List<Item> items,
                        String category,
                        String forecastDate,
                        String forecastTime) {

                return items.stream()
                                .filter(item -> item.category().equals(category)
                                                && item.fcstDate().equals(forecastDate)
                                                && item.fcstTime().equals(forecastTime))
                                .map(Item::fcstValue)
                                .findFirst()
                                .orElseThrow(() -> new IllegalStateException(
                                                category + " 데이터가 없습니다."));
        }

        private String convertSky(String sky) {

                return switch (sky) {
                        case "1" -> "맑음";
                        case "3" -> "구름많음";
                        case "4" -> "흐림";
                        default -> "알 수 없음";
                };
        }
}