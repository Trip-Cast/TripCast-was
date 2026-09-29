package com.tripcast.tourweather.climate.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.tripcast.tourweather.climate.domain.RegionClimateIndex;

import io.swagger.v3.oas.annotations.media.Schema;

public record ClimateIndexResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "시군구 코드(10자리)")
        String regionId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "지수 기준일")
        LocalDate date,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "관광기후지수 점수(kmaTci 원문, 소수)")
        BigDecimal score,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "공공데이터포털 응답의 TCI_GRADE 원문을 그대로 저장한 값. "
                        + "지금까지 실제 호출/테스트에서 확인된 값은 매우좋음/좋음/보통이며, "
                        + "외부 API가 통제하는 필드라 정해진 enum이 아니다 — 제공기관이 값을 바꾸거나 추가하면 다른 문자열이 올 수 있다.",
                example = "매우좋음"
        )
        String grade
) {
    public static ClimateIndexResponse from(RegionClimateIndex climateIndex) {
        return new ClimateIndexResponse(
                climateIndex.getRegionId(),
                climateIndex.getBaseDate(),
                climateIndex.getScore(),
                climateIndex.getGrade()
        );
    }
}
