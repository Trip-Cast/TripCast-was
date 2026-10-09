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
| GET | `/api/regions?keyword=` | 관광코스에 등록된 시군구 목록(코드·이름). 기후지수 조회의 `regionId`를 고르는 용도. 설계는 [region-list-api.md](./region-list-api.md) |
| GET | `/api/regions/{regionId}/climate-index` | 지역의 최근 관광기후지수 |
| GET | `/api/regions/{regionId}/climate-index/range?from=YYYY-MM-DD&to=YYYY-MM-DD` | 기간 내 저장된 지수 |
| GET | `/api/tour-courses?page=0&size=20` | 관광코스 목록(페이지네이션). 코스에는 별도 이름이 없어 첫 관광지명/테마/정류지 수를 요약 정보로 반환 |
| GET | `/api/tour-courses/{courseId}` | 코스와 관광지 목록 |
| GET | `/api/tour-courses/{courseId}/weather` | 관광지별 동네예보와 지역 지수 |
| GET | `/api/tour-courses/recommendations?limit=10` | 관광기후지수가 높은 순으로 코스 추천 |

### 코스 상세 화면 호출 방식

코스 상세 화면은 `/api/tour-courses/{courseId}`(DB 조회만, 수백 ms 이내)와 `/api/tour-courses/{courseId}/weather`(기상청 호출 포함, 첫 요청은 수 초 걸릴 수 있음)를 **나눠서 부르는 것을 권장**한다. 상세를 먼저 그려 화면을 띄우고, 날씨는 도착하는 대로 채운다. 날씨가 느리거나 실패해도 코스 정보는 보인다.

```js
const base = '/api/tour-courses/318';

// 1) 코스·관광지 목록은 바로 그린다.
const course = await fetch(base).then(r => r.json());
renderCourse(course);

// 2) 날씨는 기다리지 않고 따로 불러 채운다. 타임아웃/실패는 날씨 영역만 대체 문구로 처리한다.
const ctrl = new AbortController();
const timer = setTimeout(() => ctrl.abort(), 15000);
fetch(`${base}/weather`, { signal: ctrl.signal })
  .then(r => (r.ok ? r.json() : Promise.reject(r.status)))
  .then(w => renderWeather(w.stops))   // stops[i].weather 가 null 일 수 있다
  .catch(() => renderWeatherUnavailable())
  .finally(() => clearTimeout(timer));
```

- `weather`는 정류지별로 `null`일 수 있으니 반드시 null 처리를 한다.
- `/weather` 응답의 `stops[].spot`은 상세 응답의 `stops[].spot`과 같은 형태라, `stopId`로 매칭해 날씨만 덧붙이면 된다.

### 응답 시간 관련 서버 동작

- 동네예보는 기상청 격자(nx, ny)별로 30분간 캐시한다(`weather.cache.ttl-minutes`). 실패한 조회는 캐시하지 않는다.
- 한 코스 안에서 같은 격자에 속한 정류지는 기상청을 한 번만 호출하고, 서로 다른 격자는 동시에 호출한다. 동시 호출 수는 전용 스레드풀 크기(`weather.parallelism`, 기본 4)가 상한이라 요청이 몰려도 기상청에는 그 이상 동시에 나가지 않는다.
- 기상청 호출은 연결 3초·읽기 5초 타임아웃이 있다(`weather.api.connect-timeout`, `weather.api.read-timeout`). 초과하면 해당 정류지의 `weather`만 `null`이 된다.
- `/weather`는 기상청 호출 동안 DB 커넥션을 잡지 않는다(트랜잭션 밖에서 호출, `spring.jpa.open-in-view=false`).

기간 조회는 DB에 데이터가 있는 날짜만 반환한다. 코스 통합 조회 중 한 관광지의 동네예보 호출이 실패하면 해당 관광지의 `weather`만 `null`이 되고 나머지 코스 정보와 관광기후지수는 반환된다.

코스 추천은 각 코스의 **첫 번째 관광지가 속한 지역**의 관광기후지수만 기준으로 삼는다(코스 전체 경로의 평균이 아님). 그 지역에 저장된 지수가 없는 코스는 추천 목록에서 제외된다.
