# 인증(JWT) 가이드

TripCast는 관리자 전용 API를 보호하기 위해 Spring Security + JWT를 사용한다. 회원가입 기능은 없고, 환경변수로 지정한 관리자 계정 1개만 로그인할 수 있다.

## 왜 이렇게 설계했나

- **JWT(stateless)를 선택한 이유**: 세션 기반 인증은 서버가 로그인 상태를 메모리/DB에 저장해야 하지만, JWT는 토큰 자체에 정보(사용자명, 만료시간)가 서명되어 담겨 있어서 서버가 상태를 저장하지 않아도 된다. REST API에서 가장 널리 쓰이는 방식이다.
- **회원가입 없이 관리자 1명만 두는 이유**: 지금은 "인증이 어떻게 동작하는지" 배우는 게 목적이라, `User` 테이블·회원가입·비밀번호 재설정까지 만들면 범위가 너무 커진다. 관리자 계정은 환경변수로만 관리한다.
- **비밀번호도 `BCryptPasswordEncoder`로 인코딩**: 환경변수에 있는 평문 비밀번호를 그대로 비교하지 않고, 앱 기동 시 BCrypt로 해시한 뒤 매 로그인마다 `passwordEncoder.matches()`로 비교한다. 실제 서비스에서 비밀번호를 다루는 표준적인 방식을 그대로 따른 것이다.

## 환경변수

```
JWT_SECRET=<32자 이상의 임의 문자열>
JWT_EXPIRATION_MINUTES=60          # 생략 시 기본 60분
ADMIN_USERNAME=<관리자 아이디>
ADMIN_PASSWORD=<관리자 비밀번호>
```

`JWT_SECRET`은 HMAC-SHA256 서명에 쓰이는데, **256비트(32바이트) 이상이어야 한다.** 이보다 짧으면 앱 기동 시 `WeakKeyException`이 발생한다. PowerShell에서 무작위로 만들려면:

```powershell
-join ((48..57)+(65..90)+(97..122) | Get-Random -Count 40 | ForEach-Object {[char]$_})
```

## 로그인 흐름

```
POST /api/auth/login
Content-Type: application/json

{ "username": "...", "password": "..." }
```

성공하면 `{ "token": "<JWT>" }`를 반환한다. 실패하면 401과 함께 에러 메시지를 반환한다.

발급받은 토큰은 보호된 API를 호출할 때 헤더에 담아 보낸다.

```
Authorization: Bearer <token>
```

## 보호되는 API

| Method | Endpoint | 설명 |
| --- | --- | --- |
| POST | `/api/admin/climate-index/sync` | 배치를 기다리지 않고 관광기후지수를 즉시 동기화 (`app.climate-index-sync.api-enabled=true`일 때만 존재) |

그 외 조회 API(`/api/tour-spots/**`, `/api/tour-courses/**`, `/api/weather/**`, `/api/regions/**`)는 인증 없이 공개돼 있다.

## Swagger UI에서 테스트하기

1. `/swagger-ui.html` 접속
2. "인증" 태그의 `/api/auth/login`으로 로그인 → 응답의 `token` 복사
3. 우측 상단 **Authorize** 버튼 클릭 → `Bearer <token>` 형태로 입력 (또는 토큰만 입력해도 되는 UI도 있음)
4. "관리자" 태그의 API를 호출하면 자동으로 헤더가 붙는다

## 앞으로 확장한다면

- 관리자가 여러 명 필요해지면 `User` 엔티티 + 회원가입/역할(Role) 테이블 도입
- 토큰 탈취 대응이 필요하면 Refresh Token, 토큰 블랙리스트(로그아웃) 추가
- 소셜 로그인이 필요하면 OAuth2 Client 추가
