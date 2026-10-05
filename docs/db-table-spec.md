# 테이블 명세서 — TripCast 관광코스 날씨/관광기후지수 서비스

`tour_spot`, `tour_course`, `tour_course_stop`은 기존 구현된 테이블이고,
`region_climate_index`는 관광기후지수 연동을 위해 신규 설계된 테이블이다. `region`은 지역 목록 API(`GET /api/regions`)의 이름 데이터를 담는 테이블이다.

`spot_weather_index`(관광지별 체감온도·자외선지수)는 한때 스키마에 있었지만, 대응하는 공공API 필드가 공개되지 않아 실제로 배치가 채운 적이 없다. Flyway `V3__drop_spot_weather_index.sql`로 삭제했다.

## ERD

```mermaid
erDiagram
    TOUR_COURSE ||--o{ TOUR_COURSE_STOP : "코스에 속한 정류지"
    TOUR_SPOT ||--o{ TOUR_COURSE_STOP : "관광지가 등장하는 지점"
    TOUR_COURSE_STOP }o..|| REGION_CLIMATE_INDEX : "region_id 정규화 후 논리 연결 (FK 아님)"
    TOUR_COURSE_STOP }o..|| REGION : "region_id 정규화 후 논리 연결 (FK 아님)"

    TOUR_SPOT {
        bigint id PK "관광지 ID"
        varchar name "관광지명"
        double latitude "위도"
        double longitude "경도"
    }

    TOUR_COURSE {
        bigint id PK "관광 코스 ID"
        varchar source_course_id UK "공공데이터 원본 코스 ID"
    }

    TOUR_COURSE_STOP {
        bigint id PK "관광 지점 ID"
        varchar source_spot_id UK "공공데이터 원본 지점 번호"
        bigint course_id FK "관광 코스 ID"
        bigint tour_spot_id FK "관광지 ID"
        varchar region_id "시군구 코드"
        int course_order "코스 방문 순서"
        int travel_time "이동 시간(분)"
        varchar indoor_type "실내·실외 구분"
        varchar theme_code "테마 코드"
        varchar theme_name "테마명"
    }

    REGION_CLIMATE_INDEX {
        bigint id PK "기후지수 ID"
        varchar region_id "시군구 코드"
        date base_date "기준 날짜"
        decimal score "관광 기후지수 점수"
        varchar grade "기후지수 등급"
    }

    REGION {
        varchar region_id PK "시군구 코드(10자리)"
        varchar name "시군구명"
        varchar sido_name "시·도명"
    }
```

## DDL (MySQL)

```sql
CREATE TABLE tour_spot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255),
    latitude DOUBLE,
    longitude DOUBLE
);

CREATE TABLE tour_course (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_course_id VARCHAR(20) NOT NULL,
    CONSTRAINT uk_tour_course_source_id UNIQUE (source_course_id)
);

CREATE TABLE tour_course_stop (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_spot_id VARCHAR(20) NOT NULL,
    course_id BIGINT NOT NULL,
    tour_spot_id BIGINT NOT NULL,
    region_id VARCHAR(20) NOT NULL,
    course_order INT NOT NULL,
    travel_time INT NOT NULL,
    indoor_type VARCHAR(10) NOT NULL,
    theme_code VARCHAR(10) NOT NULL,
    theme_name VARCHAR(50) NOT NULL,
    CONSTRAINT uk_course_stop_source_id UNIQUE (source_spot_id),
    CONSTRAINT uk_course_stop_order UNIQUE (course_id, course_order),
    CONSTRAINT fk_course_stop_course FOREIGN KEY (course_id) REFERENCES tour_course(id),
    CONSTRAINT fk_course_stop_spot FOREIGN KEY (tour_spot_id) REFERENCES tour_spot(id)
);

CREATE TABLE region_climate_index (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    region_id VARCHAR(20) NOT NULL,
    base_date DATE NOT NULL,
    score DECIMAL(5, 2) NOT NULL,
    grade VARCHAR(20) NOT NULL,
    CONSTRAINT uk_region_climate_date UNIQUE (region_id, base_date)
);

CREATE TABLE region (
    region_id VARCHAR(10) NOT NULL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    sido_name VARCHAR(50) NOT NULL
);
```

## 테이블 명세

### 1. tour_spot (관광지)

**설명**: 관광지 기준정보. 이름과 좌표만 보유하며 CSV 최초 적재 후 거의 변경되지 않음.
**데이터 출처**: `src/main/resources/data/tour_spots.csv` (기상청 관광코스별 관광지 상세날씨 조회 지점 정보)
**갱신 방식**: 수동/1회성 배치 적재 (`TourSpotDataImporter`)

| 컬럼명 | 타입 | PK | FK | NULL | 설명 |
|---|---|---|---|---|---|
| id | BIGINT | ● | | N | 내부 식별자 (Auto Increment) |
| name | VARCHAR(255) | | | Y | 관광지명 |
| latitude | DOUBLE | | | Y | 위도 |
| longitude | DOUBLE | | | Y | 경도 |

**인덱스/제약**: 없음 (단, 서비스 로직에서 `name + latitude + longitude` 조합으로 중복 조회)

---

### 2. tour_course (관광코스)

**설명**: 관광코스 기준정보. 공공데이터 원본 코스 아이디만 보유하고, 실제 구성은 `tour_course_stop`에서 관리.
**데이터 출처**: `tour_spots.csv`의 `코스 아이디` 컬럼
**갱신 방식**: `tour_spot`과 동일

| 컬럼명 | 타입 | PK | FK | NULL | 설명 |
|---|---|---|---|---|---|
| id | BIGINT | ● | | N | 내부 식별자 (Auto Increment) |
| source_course_id | VARCHAR(20) | | | N | 원본(공공데이터) 코스 아이디 |

**인덱스/제약**
- `uk_tour_course_source_id` UNIQUE(`source_course_id`) — 동일 코스 중복 적재 방지

---

### 3. tour_course_stop (코스-관광지 연결)

**설명**: 코스와 관광지를 잇는 연결 테이블. 코스 내 순서·이동시간·실내외 구분·테마 정보를 함께 보유. `tour_course`(N) : `tour_spot`(N) 관계를 중개.
**데이터 출처**: `tour_spots.csv` 전체 행
**갱신 방식**: `tour_spot`과 동일

| 컬럼명 | 타입 | PK | FK | NULL | 설명 |
|---|---|---|---|---|---|
| id | BIGINT | ● | | N | 내부 식별자 (Auto Increment) |
| source_spot_id | VARCHAR(20) | | | N | 원본(공공데이터) 지점번호 |
| course_id | BIGINT | | ● → tour_course.id | N | 소속 코스 |
| tour_spot_id | BIGINT | | ● → tour_spot.id | N | 연결된 관광지 |
| region_id | VARCHAR(20) | | | N | 시군구 코드 (관광기후지수 조회 키) |
| course_order | INT | | | N | 코스 내 방문 순서 |
| travel_time | INT | | | N | 다음 지점까지 이동시간(분) |
| indoor_type | VARCHAR(10) | | | N | 실내/실외/미상 |
| theme_code | VARCHAR(10) | | | N | 테마 분류 코드 |
| theme_name | VARCHAR(50) | | | N | 테마명 |

**인덱스/제약**
- `uk_course_stop_source_id` UNIQUE(`source_spot_id`) — 동일 지점 중복 적재 방지
- `uk_course_stop_order` UNIQUE(`course_id`, `course_order`) — 한 코스 내 순서 중복 방지
- `fk_course_stop_course` FK(`course_id`) REFERENCES `tour_course(id)`
- `fk_course_stop_spot` FK(`tour_spot_id`) REFERENCES `tour_spot(id)`

---

### 4. region_climate_index (시군구 관광기후지수) — 신규

**설명**: 시군구 단위 관광기후지수를 일자별로 적재하는 시계열 캐시 테이블. 하루 한 지역당 한 행.
**데이터 출처**: 기상청 "관광코스별 관광지 상세 날씨 조회서비스" OpenAPI (data.go.kr 15056912)
**갱신 방식**: 매일 배치 (`ClimateIndexSyncScheduler`, 매일 05:00) — `tour_course_stop`에 등록된 `region_id` 목록 대상 upsert

| 컬럼명 | 타입 | PK | FK | NULL | 설명 |
|---|---|---|---|---|---|
| id | BIGINT | ● | | N | 내부 식별자 (Auto Increment) |
| region_id | VARCHAR(20) | | | N | 시군구 코드. `tour_course_stop.region_id`를 `ClimateRegionIdNormalizer`로 정규화(앞 5자리 + `00000`)한 값과 연결, FK 아님 |
| base_date | DATE | | | N | 지수 기준일 |
| score | DECIMAL(5, 2) | | | N | 관광기후지수 점수 (`kmaTci`, 예: `0.44`) |
| grade | VARCHAR(20) | | | N | 공공데이터포털 응답의 `TCI_GRADE` 원문 |

**인덱스/제약**
- `uk_region_climate_date` UNIQUE(`region_id`, `base_date`) — 배치 재실행 시 upsert 보장, 하루 중복 적재 방지
- `region_id`, `base_date` 복합 조회 인덱스 권장 (조회 API의 핵심 WHERE 조건)

**비고**: `tour_course_stop`과 물리적 FK 없음. `tour_course_stop.region_id`는 CSV 원본상 읍·면·동 단위 코드를 포함할 수 있어, 조회 시점에 `ClimateRegionIdNormalizer.normalize()`로 시군구 단위(앞 5자리 + `00000`)로 정규화한 뒤 `region_id + base_date`로 논리 조인한다. 정규화 예시 및 API 연동 상세는 [docs/tour-climate-api.md](./tour-climate-api.md) 참고.

---

### 5. region (시군구 이름)

**설명**: 시군구 코드와 이름 매핑. 지역 목록 API가 코드에 이름을 붙이는 데 쓴다.
**데이터 출처**: `src/main/resources/data/regions.csv` — 행정안전부 행정표준코드(법정동코드)의 시군구 이름. 관광지 CSV에 등장하는 정규화 코드 198개만 담는다.
**갱신 방식**: 앱 시작 시 `RegionDataImporter`가 CSV 기준으로 upsert (`app.region-import.enabled`, 기본 true)

| 컬럼명 | 타입 | PK | FK | NULL | 설명 |
|---|---|---|---|---|---|
| region_id | VARCHAR(10) | ● | | N | 정규화된 시군구 코드(앞 5자리 + `00000`) |
| name | VARCHAR(50) | | | N | 시군구명 (일반구는 `전주시 완산구`처럼 시 이름 포함) |
| sido_name | VARCHAR(50) | | | N | 시·도명 (`강원특별자치도`, `전북특별자치도` 등 현재 명칭) |

**비고**: 코드는 기상청 관광지수 API(`cityAreaId`)가 쓰는 구 코드 체계(강원 `42…`, 전북 `45…`)를 그대로 쓴다. 이름만 현재 명칭이다. `tour_course_stop`과 FK는 없다.

---

## 테이블 간 관계 요약

| 관계 | 유형 | 방식 |
|---|---|---|
| tour_course 1 : N tour_course_stop | 식별관계 | FK (`fk_course_stop_course`) |
| tour_spot 1 : N tour_course_stop | 식별관계 | FK (`fk_course_stop_spot`) |
| tour_course_stop N : 1 region_climate_index | 논리관계 | `region_id` 정규화 후 값 매칭 (FK 없음) |
| tour_course_stop N : 1 region | 논리관계 | `region_id` 정규화 후 값 매칭 (FK 없음) |
