package com.tripcast.tourweather.climate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import com.tripcast.tourweather.admin.AdminClimateIndexController;
import com.tripcast.tourweather.climate.client.TourWeatherIndexClient;
import com.tripcast.tourweather.climate.scheduler.ClimateIndexSyncScheduler;

/** 수동 동기화 API와 자동 배치 플래그가 서로 독립적으로 동작하는지 검증한다. */
class ClimateIndexSyncToggleTest {

    private static int count(ApplicationContext context, Class<?> type) {
        return context.getBeanNamesForType(type).length;
    }

    @Nested
    @SpringBootTest(properties = {
            "app.climate-index-sync.api-enabled=true",
            "app.climate-index-sync.scheduler-enabled=false"
    })
    class ApiOnly {

        @Autowired
        ApplicationContext context;

        @Test
        void API만_켜면_스케줄러는_등록되지_않는다() {
            assertEquals(1, count(context, AdminClimateIndexController.class));
            assertEquals(1, count(context, ClimateIndexSyncService.class));
            assertEquals(1, count(context, TourWeatherIndexClient.class));
            assertEquals(0, count(context, ClimateIndexSyncScheduler.class));
        }
    }

    @Nested
    @SpringBootTest(properties = {
            "app.climate-index-sync.api-enabled=false",
            "app.climate-index-sync.scheduler-enabled=true",
            "app.climate-index-sync.cron=0 0 0 1 1 *"
    })
    class SchedulerOnly {

        @Autowired
        ApplicationContext context;

        @Test
        void 스케줄러만_켜면_API는_등록되지_않는다() {
            assertEquals(0, count(context, AdminClimateIndexController.class));
            assertEquals(1, count(context, ClimateIndexSyncService.class));
            assertEquals(1, count(context, TourWeatherIndexClient.class));
            assertEquals(1, count(context, ClimateIndexSyncScheduler.class));
        }
    }

    @Nested
    @SpringBootTest(properties = {
            "app.climate-index-sync.api-enabled=false",
            "app.climate-index-sync.scheduler-enabled=false"
    })
    class BothOff {

        @Autowired
        ApplicationContext context;

        @Test
        void 둘_다_끄면_관련_빈이_하나도_없다() {
            assertEquals(0, count(context, AdminClimateIndexController.class));
            assertEquals(0, count(context, ClimateIndexSyncService.class));
            assertEquals(0, count(context, TourWeatherIndexClient.class));
            assertEquals(0, count(context, ClimateIndexSyncScheduler.class));
        }
    }
}
