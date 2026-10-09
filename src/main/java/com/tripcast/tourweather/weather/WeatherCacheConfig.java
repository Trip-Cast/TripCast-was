package com.tripcast.tourweather.weather;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * 동네예보 캐시. 예보는 3시간 단위로 발표되므로 같은 격자는 짧게 재사용해도 안전하다.
 * 만료 시간이 지나면 다음 요청이 기상청을 다시 호출한다.
 */
@Configuration
@EnableCaching
public class WeatherCacheConfig {

    public static final String WEATHER_CACHE = "weather";

    @Bean
    public CacheManager cacheManager(
            @Value("${weather.cache.ttl-minutes:30}") long ttlMinutes,
            @Value("${weather.cache.max-size:1000}") long maxSize
    ) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(WEATHER_CACHE);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(ttlMinutes))
                .maximumSize(maxSize));
        return cacheManager;
    }
}
