package com.tripcast.tourweather.climate.scheduler;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.tripcast.tourweather.climate.ClimateIndexSyncResult;
import com.tripcast.tourweather.climate.ClimateIndexSyncService;

@Component
@ConditionalOnProperty(
        name = "app.climate-index-sync.scheduler-enabled",
        havingValue = "true"
)
public class ClimateIndexSyncScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(ClimateIndexSyncScheduler.class);

    private final ClimateIndexSyncService climateIndexSyncService;
    private final ZoneId zoneId;
    private final int forecastDays;

    public ClimateIndexSyncScheduler(
            ClimateIndexSyncService climateIndexSyncService,
            @Value("${app.climate-index-sync.zone:Asia/Seoul}") String zone,
            @Value("${app.climate-index-sync.forecast-days:1}") int forecastDays
    ) {
        this.climateIndexSyncService = climateIndexSyncService;
        this.zoneId = ZoneId.of(zone);
        this.forecastDays = forecastDays;
    }

    @Scheduled(
            cron = "${app.climate-index-sync.cron:0 0 5 * * *}",
            zone = "${app.climate-index-sync.zone:Asia/Seoul}"
    )
    public void sync() {
        ClimateIndexSyncResult result = climateIndexSyncService.sync(
                LocalDateTime.now(zoneId),
                forecastDays
        );
        log.info(
                "관광기후지수 동기화 완료. requested={}, successful={}, saved={}, failed={}",
                result.requestedRegions(),
                result.successfulRegions(),
                result.savedRecords(),
                result.failedRegions()
        );
    }
}
