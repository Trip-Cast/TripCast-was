# 지역 목록 API 설계

> 상태: 1~2단계 골격 구현됨 (브랜치 `feature/region-list-api`). 실제 구현과 달라진 부분은 10절 참고.
> 배경: 프론트엔드 [TripCast-web#20](https://github.com/Trip-Cast/TripCast-web/pull/20)에서 관광기후지수 화면의 지역 선택용 코드 10개를 하드코딩했고, "지역 검색 API가 생기면 셀렉트로 교체"하겠다고 했다. 이 문서는 그 API의 설계안이다.

## 1. 목표와 범위

**목표**: 프론트가 시군구 코드를 직접 입력하거나 하드코딩하지 않고, 서버가 알려주는 목록에서 지역을 고를 수 있게 한다.

**범위**
- 관광기후지수 조회(`/api/regions/{regionId}/climate-index[/range]`)에 쓸 수 있는 시군구 목록 제공
- 이름 검색(`keyword`)

**범위 밖 (이번에 하지 않음)**
- 5자리 코드 허용 등 기존 조회 API 변경 (프론트가 이미 10자리로 보정한다)
- 읍·면·동 단위 지역, 지도/좌표 기반 검색
- 지역별 관광지·코스 조회

## 2. 현재 상태와 제약

| 항목 | 현황 |
| --- | --- |
| 지역 코드 출처 | `tour_course_stop.region_id` (CSV `지역 아이디`). 읍·면·동 코드가 섞여 있다 (예: `4822051000`). |
| 정규화 | `ClimateRegionIdNormalizer`가 10자리를 앞 5자리 + `00000`으로 바꾼다. CSV 기준 정규화 후 고유 코드는 **198개**. |
| 목록 쿼리 | `TourCourseStopRepository.findDistinctRegionIds()`가 이미 있다. 동기화 배치가 같은 쿼리를 쓴다. |
| **지역 이름** | **백엔드 어디에도 없다.** CSV에는 코드만 있고, DB에도 이름 컬럼이 없다. |
| 보안 | `SecurityConfig.PUBLIC_PATHS`에 `/api/regions/**`가 이미 있어 별도 설정 없이 공개 API가 된다. |
| 라우팅 | `GET /api/regions`는 기존 `/{regionId}/climate-index`와 경로가 겹치지 않는다. |

이름 데이터가 없다는 점이 이 설계의 핵심 결정 사항이다.

## 3. API 명세

### `GET /api/regions`

관광기후지수를 조회할 수 있는 시군구 목록을 코드 오름차순으로 반환한다.

**Query**

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `keyword` | string | N | 지역명 부분 일치 검색 (공백 제거 후 대소문자 무시). 없으면 전체 목록. |

**200 응답**

```json
[
  {
    "regionId": "5013000000",
    "name": "서귀포시",
    "sidoName": "제주특별자치도",
    "displayName": "제주특별자치도 서귀포시"
  }
]
```

| 필드 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| `regionId` | string | N | 시군구 코드 10자리. 기후지수 API의 `regionId`와 같은 값이다. |
| `name` | string | N | 시군구명 (예: `서귀포시`, `해운대구`) |
| `sidoName` | string | N | 시·도명 (예: `제주특별자치도`) |
| `displayName` | string | N | 화면 표시용 `sidoName + " " + name` |

**동작 규칙**
- `regionId`는 항상 정규화된 값(앞 5자리 + `00000`)이다. 프론트가 이 값을 그대로 기후지수 API에 넘기면 된다.
- 결과가 없으면 `200 []`을 반환한다. 404가 아니다.
- 인증은 필요 없다.
- 응답이 거의 변하지 않으므로 `Cache-Control: public, max-age=3600` 정도를 붙인다.
- 198건 규모라 페이지네이션은 두지 않는다. 필요해지면 `page/size`를 추가한다. 현재 `/api/tour-courses`는 0-based 페이지네이션을 쓴다.

**목록에 포함되는 지역**: `tour_course_stop`에 등록된 정규화 코드와 이름 매핑이 모두 있는 지역. 동기화 대상과 같은 집합이므로, 목록에 있는 지역은 배치가 지수를 채우는 지역이다.

## 4. 지역 이름 데이터 확보 방식

| 안 | 내용 | 장점 | 단점 |
| --- | --- | --- | --- |
| **A. `region` 테이블 + CSV 시드 (권장)** | 행정구역 코드표를 CSV 리소스로 두고, 기존 `TourSpotDataImporter`처럼 시작 시 적재한다. | 코드 체계(`tour_course_stop`)와 같은 방식이라 일관적이다. 이후 확장(좌표, 정렬 순서)이 쉽다. | Flyway 마이그레이션과 importer가 필요하다. |
| B. CSV를 메모리로만 로드 | 시작 시 CSV를 읽어 `Map`으로 들고 있는다. | 마이그레이션이 없다. 가장 단순하다. | 지역 데이터가 DB에 없어서 조인·검색 확장이 어렵다. |
| C. 프론트가 이름 보유 | 백엔드는 코드만 내려준다. | 백엔드 작업이 없다. | 198개를 프론트가 관리해야 해서 이 API의 목적과 어긋난다. |

**권장: A.** 이 프로젝트가 "CSV 시드 → DB → API" 패턴을 이미 쓰고 있고(`tour_spots.csv`), 데이터 소스 문서화·테스트 방식도 그대로 따를 수 있다. 규모가 작아서 B로 시작해도 되지만, 이름 외 속성이 붙을 가능성이 크면 A가 낫다.

### 테이블 초안 (A안)

```sql
CREATE TABLE region (
    region_id  VARCHAR(10)  NOT NULL PRIMARY KEY,  -- 정규화된 시군구 코드 10자리
    name       VARCHAR(50)  NOT NULL,              -- 시군구명
    sido_name  VARCHAR(50)  NOT NULL               -- 시·도명
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- Flyway `V4__create_region.sql`로 추가한다. `FlywayMigrationTest`(H2)와 `MySqlFlywayMigrationTest`(MySQL)가 모두 돌아가므로 두 DB에서 통과하는 문법만 쓴다.
- `tour_course_stop`과 FK는 걸지 않는다. `region_climate_index`와 마찬가지로 정규화 후 논리 연결이다.
- 시드 CSV 위치 후보: `src/main/resources/data/regions.csv` (컬럼: `지역 아이디,시도명,시군구명`)

### 이름 데이터 출처 (미정)

현재 CSV의 코드가 가리키는 이름을 확정해야 한다. 후보는 다음과 같다.
- 행정안전부 법정동코드 (앞 5자리가 시군구)
- 기상청 관광지수 API 문서에 첨부된 지역 코드표

주의: `4511100000`(전주시 완산구)처럼 **일반구가 있는 시**는 시 코드(`45110`)와 구 코드(`45111`)가 따로 있다. 어느 쪽 코드가 기후지수 API의 `cityAreaId`로 유효한지는 실제 호출로 확인해야 한다. 코드표를 그대로 쓰기보다, CSV에 실제로 등장하는 198개 코드 기준으로 이름을 매핑해야 한다.

## 5. 서버 구성

기존 `climate` 패키지 구조를 따라 `region` 패키지를 추가한다.

```
com.tripcast.tourweather.region
├── RegionController          GET /api/regions
├── RegionService             목록 조회, keyword 필터
├── domain/Region             JPA 엔티티
├── repository/RegionRepository
├── dto/RegionResponse        record (Swagger @Schema 포함)
└── importer/RegionDataImporter   CSV → region 테이블
```

**조회 로직 (RegionService)**
1. `TourCourseStopRepository.findDistinctRegionIds()` → `ClimateRegionIdNormalizer.normalize` → `distinct`
2. `RegionRepository.findAllById(...)`로 이름 조회
3. `keyword`가 있으면 `name`/`sidoName`/`displayName`에서 부분 일치로 필터
4. `regionId` 오름차순 정렬 후 `RegionResponse`로 변환

이름 매핑이 없는 코드는 응답에서 제외하고 `WARN` 로그를 남긴다. 목록에서 코드가 조용히 빠지는 일을 운영에서 알아챌 수 있게 하려는 것이다. 필요하면 이 로그를 시작 시 검증(importer 이후 누락 코드 검사)으로 바꾼다.

## 6. 프론트 연동 계획

1. `climate-page.tsx`의 하드코딩된 `regions` 배열을 `GET /api/regions` 쿼리(TanStack Query)로 교체한다. 데이터가 거의 안 바뀌므로 `staleTime`을 길게 둔다.
2. 코드 입력창의 `datalist`를 이름 검색이 되는 셀렉트(콤보박스)로 바꾼다. 값은 `regionId`(10자리)이고, URL의 `?region=` 형식은 그대로다.
3. 목록 로딩 실패 시 현재의 10자리 직접 입력을 fallback으로 둘 수 있다.

기존 기후지수 API와 응답 형식은 바뀌지 않아서 프론트·백엔드를 독립적으로 배포할 수 있다. 백엔드가 먼저 나가면 된다.

## 7. 테스트 계획

- `RegionServiceTest`: 정규화 후 중복 제거, 이름 매핑 누락 시 제외, keyword 필터, 정렬
- `RegionControllerTest`: 200 응답 스키마, `keyword` 검색, 빈 결과는 `[]`
- `RegionDataImporterTest`: CSV 파싱, 재적재 시 중복 없음
- `OpenApiContractTest`: `/api/regions` 스키마(필수 필드) 추가
- 마이그레이션 테스트: `V4`가 H2/MySQL 양쪽에서 통과하는지
- 데이터 정합성 테스트: CSV에 등장하는 모든 정규화 코드가 `regions.csv`에 있는지

## 8. 문서 갱신

구현 시 함께 고칠 문서:
- `docs/tour-climate-api.md` "제공 API" 표에 `GET /api/regions` 추가
- `docs/db-table-spec.md`에 `region` 테이블과 ERD 추가
- 프론트 `docs/frontend-spec.md`의 "지역 검색 API가 생기면" 문구 갱신 (프론트 저장소)

## 9. 결정이 필요한 항목

| # | 질문 | 권장 |
| --- | --- | --- |
| 1 | 이름 데이터 저장 방식 (4절 A/B/C) | A. `region` 테이블 + CSV 시드 |
| 2 | 이름 데이터 출처와 일반구가 있는 시의 코드 처리 | 실제 `cityAreaId` 호출로 유효 코드 확인 후 결정 |
| 3 | 응답에 `hasClimateIndex`(저장된 지수 유무) 같은 필드를 넣을지 | 1차에는 제외. 지수 없는 지역이 많아서 UX 문제가 되면 추가 |
| 4 | 목록에 지수가 없는 지역도 노출할지 | 노출 (코스 등록 지역이면 배치 대상이다) |
| 5 | `keyword` 외에 `sido` 필터가 필요한지 | 1차에는 제외 |

## 10. 구현 결과와 확정된 사항

**결정**
- 저장 방식: 4절 A안. `region` 테이블(`V4__create_region.sql`) + `regions.csv` 시드 + `RegionDataImporter`.
- 이름 출처: 행정안전부 행정표준코드(법정동코드)의 시군구 이름. 공공데이터포털 법정동 데이터를 정리한 공개 JSON에서 관광지 CSV의 정규화 코드 198개를 추출했고, 198개 모두 현행 코드와 일치했다.
- 일반구가 있는 시: 코드표에 구 단위 코드(`4511100000` 전주시 완산구 등)가 따로 있고, 관광지 CSV도 구 단위 코드를 쓴다. 그래서 이름을 `전주시 완산구`로 붙였다. 18개 코드가 이에 해당한다.
- 세종은 법정동코드에 시군구명이 없어 시·도명과 같은 값(`세종특별자치시`)을 넣고, `displayName`은 한 번만 쓴다.
- 시·도명은 현재 명칭을 쓴다(`강원특별자치도`, `전북특별자치도`). 코드는 기상청 API 기준 구 코드(`42…`, `45…`) 그대로다.
- importer는 재실행해도 같은 결과인 upsert라 `app.region-import.enabled` 기본값을 **true**로 뒀다(관광지 importer와 달리). 끄려면 `REGION_IMPORT_ENABLED=false`.

**아직 확인되지 않은 것**
- 일반구 코드가 기상청 `getCityTourClmIdx1`의 `cityAreaId`로 유효한지는 API 키가 없어 호출해 보지 못했다. 이 문서의 이름 매핑과는 별개로, 동기화 배치가 이미 같은 코드로 호출하고 있으므로 운영 로그의 `NO_DATA`/실패 여부로 확인할 수 있다.
- 이름 데이터는 개편 시 갱신이 필요하다. `RegionCsvReaderTest`가 관광지 CSV의 모든 지역에 이름이 있는지 검사한다.
