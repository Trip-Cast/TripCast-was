package com.tripcast.tourweather.weather;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 코스 날씨 조회에서 기상청 격자를 동시에 호출하기 위한 스레드풀.
 * 스레드 수가 곧 기상청 동시 호출 상한이라, 요청이 몰려도 이 값 이상으로는 호출하지 않는다.
 */
@Configuration
public class WeatherExecutorConfig {

    public static final String WEATHER_EXECUTOR = "weatherExecutor";

    @Bean(name = WEATHER_EXECUTOR, destroyMethod = "shutdown")
    public ExecutorService weatherExecutor(
            @Value("${weather.parallelism:4}") int parallelism
    ) {
        AtomicInteger sequence = new AtomicInteger();
        return Executors.newFixedThreadPool(parallelism, runnable -> {
            Thread thread = new Thread(
                    runnable,
                    "weather-" + sequence.incrementAndGet()
            );
            thread.setDaemon(true);
            return thread;
        });
    }
}
