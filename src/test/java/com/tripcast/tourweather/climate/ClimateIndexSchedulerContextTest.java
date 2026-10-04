package com.tripcast.tourweather.climate;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.tripcast.tourweather.climate.scheduler.ClimateIndexSyncScheduler;

@SpringBootTest(properties = {
        "app.climate-index-sync.api-enabled=true",
        "app.climate-index-sync.scheduler-enabled=true",
        "app.climate-index-sync.cron=0 0 0 1 1 *"
})
class ClimateIndexSchedulerContextTest {

    @Autowired
    private ClimateIndexSyncScheduler scheduler;

    @Test
    void 활성화하면_스케줄러가_등록된다() {
        assertNotNull(scheduler);
    }
}
