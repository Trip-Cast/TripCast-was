package com.tripcast.tourweather.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.tripcast.tourweather.weather.client.KmaWeatherClient;
import com.tripcast.tourweather.weather.dto.KmaWeatherResponse;
import com.tripcast.tourweather.weather.dto.KmaWeatherResponse.Item;

@SpringJUnitConfig({WeatherCacheConfig.class, WeatherService.class})
class WeatherServiceCacheTest {

    @Autowired
    private WeatherService weatherService;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private KmaWeatherClient kmaWeatherClient;

    @Test
    void 같은_격자는_기상청을_한_번만_호출한다() {
        cacheManager.getCache(WeatherCacheConfig.WEATHER_CACHE).clear();
        when(kmaWeatherClient.getVillageForecast(60, 127))
                .thenReturn(forecast());

        weatherService.getWeather(60, 127);
        weatherService.getWeather(60, 127);

        verify(kmaWeatherClient, times(1)).getVillageForecast(60, 127);
    }

    @Test
    void 다른_격자는_각각_조회한다() {
        cacheManager.getCache(WeatherCacheConfig.WEATHER_CACHE).clear();
        when(kmaWeatherClient.getVillageForecast(anyInt(), anyInt()))
                .thenReturn(forecast());

        weatherService.getWeather(60, 127);
        weatherService.getWeather(53, 38);

        verify(kmaWeatherClient, times(2))
                .getVillageForecast(anyInt(), anyInt());
    }

    @Test
    void 실패한_조회는_캐시하지_않고_다음_요청에서_다시_시도한다() {
        cacheManager.getCache(WeatherCacheConfig.WEATHER_CACHE).clear();
        when(kmaWeatherClient.getVillageForecast(60, 127))
                .thenThrow(new IllegalStateException("기상청 API 실패"))
                .thenReturn(forecast());

        assertThrows(
                IllegalStateException.class,
                () -> weatherService.getWeather(60, 127)
        );
        assertEquals(24.0, weatherService.getWeather(60, 127).temperature());

        verify(kmaWeatherClient, times(2)).getVillageForecast(60, 127);
    }

    private KmaWeatherResponse forecast() {
        List<Item> items = List.of(
                item("TMP", "24"),
                item("POP", "20"),
                item("SKY", "1")
        );
        return new KmaWeatherResponse(
                new KmaWeatherResponse.Response(
                        null,
                        new KmaWeatherResponse.Body(
                                "JSON",
                                new KmaWeatherResponse.Items(items),
                                1,
                                1000,
                                items.size()
                        )
                )
        );
    }

    private Item item(String category, String value) {
        return new Item(
                "20260920", "1400", category, "20260920", "1500", value, 60, 127
        );
    }
}
