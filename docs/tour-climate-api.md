# 관광기후지수 연동 가이드

## 사용 API

TripCast는 공공데이터포털의 `기상청_관광코스별 관광지 상세 날씨 조회서비스`에서 시군구별 관광기후지수를 수집한다.

- 서비스 ID: `15056912`
- Base URL: `https://apis.data.go.kr/1360000/TourStnInfoService1`
- 기능: `GET /getCityTourClmIdx1`
- 인증 파라미터: `ServiceKey`
- 주요 응답 필드: `tm`, `cityAreaId`, `kmaTci`, `TCI_GRADE`

2026-09-21 실제 호출에서 `tm=2026-09-22 00:00`,
`cityAreaId=5013000000`, `kmaTci=0.44`, `TCI_GRADE=매우좋음`을
확인했다. `kmaTci`는 정수가 아닌 소수이므로 DB와 API 응답에서
`DECIMAL(5, 2)`/`BigDecimal`로 보존한다.

공공데이터포털의 현재 Swagger에는 동네예보와 시군구별 관광기후지수 기능만 공개되어 있다. 관광지별 체감온도·자외선지수에 대응하는 응답 필드가 없어서, 한때 있었던 `spot_weather_index` 테이블은 실제로 채운 적 없이 Flyway `V3`로 삭제했다.

## 실행 설정

API 키를 환경 변수로 설정한 뒤 배치를 활성화한다. 키는 설정 파일이나 Git에 직접 기록하지 않는다.

```powershell
$env:TOUR_CLIMATE_API_KEY="발급받은 일반 인증키 Decoding 값"
```

```properties
# 수동 동기화 API (POST /api/admin/climate-index/sync)
app.climate-index-sync.api-enabled=true
# 자동 배치 (둘은 독립적으로 켜고 끈다)
app.climate-index-sync.scheduler-enabled=true
```

환경변수는 각각 `CLIMATE_INDEX_SYNC_API_ENABLED`, `CLIMATE_INDEX_SYNC_SCHEDULER_ENABLED`이며, 하위 호환용 `CLIMATE_INDEX_SYNC_ENABLED`는 두 값의 기본값으로만 쓰인다.

배치는 기본적으로 매일 오전 5시(`Asia/Seoul`)에 실행된다. 두 플래그가 모두 꺼진 상태에서는 외부 API 클라이언트와 스케줄러가 등록되지 않으므로 API 키 없이도 앱과 테스트가 실행된다.

CSV의 `지역 아이디`는 읍·면·동 코드가 포함될 수 있다. 배치와 조회에서는 10자리 행정구역 코드의 앞 5자리를 유지하고 뒤 5자리를 `00000`으로 바꿔 관광기후지수 API가 요구하는 시군구 코드로 정규화한다. 예를 들어 `4822051000`은 `4822000000`으로 조회한다.

## 실제 API PoC

API 활용신청 승인과 키가 준비된 환경에서 다음 테스트를 실행한다.

```powershell
.\gradlew.bat test --tests "*TourWeatherIndexClientPocTest"
```

테스트는 서귀포시 코드 `5013000000`으로 실제 API를 한 번 호출하고 `tm`, `cityAreaId`, `kmaTci`, `tciGrade`를 로그로 남긴다. `TOUR_CLIMATE_API_KEY`가 없으면 일반 테스트 실행에서는 자동으로 건너뛴다.

## 제공 API

| Method | Endpoint | 설명 |
| --- | --- | --- |
| GET | `/api/regions/{regionId}/climate-index` | 지역의 최근 관광기후지수 |
| GET | `/api/regions/{regionId}/climate-index/range?from=YYYY-MM-DD&to=YYYY-MM-DD` | 기간 내 저장된 지수 |
| GET | `/api/tour-courses?page=0&size=20` | 관광코스 목록(페이지네이션). 코스에는 별도 이름이 없어 첫 관광지명/테마/정류지 수를 요약 정보로 반환 |
| GET | `/api/tour-courses/{courseId}` | 코스와 관광지 목록 |
| GET | `/api/tour-courses/{courseId}/weather` | 관광지별 동네예보와 지역 지수 |
| GET | `/api/tour-courses/recommendations?limit=10` | 관광기후지수가 높은 순으로 코스 추천 |

기간 조회는 DB에 데이터가 있는 날짜만 반환한다. 코스 통합 조회 중 한 관광지의 동네예보 호출이 실패하면 해당 관광지의 `weather`만 `null`이 되고 나머지 코스 정보와 관광기후지수는 반환된다.

코스 추천은 각 코스의 **첫 번째 관광지가 속한 지역**의 관광기후지수만 기준으로 삼는다(코스 전체 경로의 평균이 아님). 그 지역에 저장된 지수가 없는 코스는 추천 목록에서 제외된다.
