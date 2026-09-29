package com.tripcast.tourweather.auth;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripcast.tourweather.auth.dto.LoginRequest;
import com.tripcast.tourweather.auth.dto.LoginResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(
        name = "인증",
        description = "관리자 로그인 API"
)
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AdminAuthService adminAuthService;

    public AuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @Operation(
            summary = "관리자 로그인",
            description = "성공 시 관리자 전용 API 호출에 쓸 JWT를 발급합니다."
    )
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        String token = adminAuthService.login(
                request.username(),
                request.password()
        );
        return new LoginResponse(token, request.username());
    }
}
