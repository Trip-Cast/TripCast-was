package com.tripcast.tourweather.region.dto;

import com.tripcast.tourweather.region.domain.Region;

import io.swagger.v3.oas.annotations.media.Schema;

public record RegionResponse(
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "시군구 코드(10자리). 관광기후지수 API의 regionId에 그대로 사용한다.",
                example = "5013000000"
        )
        String regionId,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "시군구명",
                example = "서귀포시"
        )
        String name,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "시·도명",
                example = "제주특별자치도"
        )
        String sidoName,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "화면 표시용 이름. 시·도명과 시군구명이 같으면(세종) 한 번만 쓴다.",
                example = "제주특별자치도 서귀포시"
        )
        String displayName
) {
    public static RegionResponse from(Region region) {
        String displayName = region.getName().equals(region.getSidoName())
                ? region.getName()
                : region.getSidoName() + " " + region.getName();

        return new RegionResponse(
                region.getRegionId(),
                region.getName(),
                region.getSidoName(),
                displayName
        );
    }
}
