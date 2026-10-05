package com.tripcast.tourweather.region;

import java.time.Duration;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tripcast.tourweather.region.dto.RegionResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(
        name = "지역",
        description = "관광기후지수를 조회할 수 있는 시군구 목록 API"
)
@RestController
@RequestMapping("/api/regions")
public class RegionController {

    private static final CacheControl CACHE_CONTROL =
            CacheControl.maxAge(Duration.ofHours(1)).cachePublic();

    private final RegionService regionService;

    public RegionController(RegionService regionService) {
        this.regionService = regionService;
    }

    @Operation(
            summary = "지역 목록 조회",
            description = "관광코스에 등록된 시군구 목록을 코드 오름차순으로 반환합니다. "
                    + "결과가 없으면 빈 배열을 반환합니다."
    )
    @GetMapping
    public ResponseEntity<List<RegionResponse>> getRegions(
            @Parameter(description = "지역명 부분 일치 검색(공백 무시). 없으면 전체 목록")
            @RequestParam(required = false)
            String keyword
    ) {
        return ResponseEntity.ok()
                .cacheControl(CACHE_CONTROL)
                .body(regionService.getRegions(keyword));
    }
}
