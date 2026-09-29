package com.tripcast.tourweather.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "관리자 전용 API 호출에 사용할 JWT. subject(sub) 클레임에도 username이 담겨 있다.")
        String token,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "로그인한 관리자 계정명")
        String username
) {
}
