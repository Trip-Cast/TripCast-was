package com.tripcast.tourweather.admin;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripcast.tourweather.climate.ClimateIndexSyncResult;
import com.tripcast.tourweather.climate.ClimateIndexSyncService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(
        name = "관리자",
        description = "관리자 전용 API (JWT 인증 필요)"
)
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin")
@ConditionalOnProperty(
        name = "app.climate-index-sync.api-enabled",
        havingValue = "true"
)
public class AdminClimateIndexController {

    private final ClimateIndexSyncService climateIndexSyncService;
    private final ZoneId zoneId;
    private final int forecastDays;

    public AdminClimateIndexController(
            ClimateIndexSyncService climateIndexSyncService,
            @Value("${app.climate-index-sync.zone:Asia/Seoul}") String zone,
            @Value("${app.climate-index-sync.forecast-days:1}") int forecastDays
    ) {
        this.climateIndexSyncService = climateIndexSyncService;
        this.zoneId = ZoneId.of(zone);
        this.forecastDays = forecastDays;
    }

    @Operation(
            summary = "관광기후지수 수동 동기화",
            description = "매일 새벽 배치를 기다리지 않고 즉시 관광기후지수를 동기화합니다."
    )
    @PostMapping("/climate-index/sync")
    public ClimateIndexSyncResult sync() {
        return climateIndexSyncService.sync(
                LocalDateTime.now(zoneId),
                forecastDays
        );
    }
}
