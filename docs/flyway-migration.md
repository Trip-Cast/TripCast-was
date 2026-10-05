# Flyway 마이그레이션 도입 가이드

TripCast는 `spring.jpa.hibernate.ddl-auto=update`로 스키마를 자동 관리하던 방식에서 Flyway 기반 관리로 전환했다. 스키마 생성/변경은 Flyway가 전담하고, Hibernate는 `ddl-auto=validate`로 엔티티와 실제 스키마가 일치하는지만 검사한다.

## 새 DB와 기존 DB의 적용 방식 차이

| 상황 | 동작 |
|---|---|
| 완전히 빈 DB(새 개발 환경, CI, Testcontainers) | `V1__create_initial_schema.sql`로 다섯 테이블을 생성하고, `V2__change_climate_score_to_decimal.sql`이 `score`를 `DECIMAL(5,2)`로 변경하고, `V3__drop_spot_weather_index.sql`이 미사용 테이블 `spot_weather_index`를 삭제한다. `V4__create_region.sql`이 지역 이름 테이블 `region`을 추가한다(데이터는 `regions.csv` importer가 적재). 최종적으로 다섯 테이블이 남는다. |
| 이미 Hibernate `ddl-auto=update`로 테이블이 만들어진 기존 로컬 DB | Flyway가 관리 이력을 모르는 상태이므로, `baseline` 처리를 먼저 해서 "이미 V1 상태까지는 완료된 것"으로 등록한 뒤 V2, V3, V4가 순서대로 적용된다. |

기존 DB를 처음 Flyway 관리 대상으로 편입할 때만 아래 baseline 절차가 필요하고, 이후로는 새 DB와 동일하게 동작한다.

## 1. 기존 DB 백업

baseline이나 마이그레이션을 실행하기 전에 반드시 백업한다.

```powershell
mysqldump -u root -p TripCast > TripCast_backup_$(Get-Date -Format yyyyMMdd_HHmmss).sql
```

## 2. baseline 전에 기존 스키마 확인

아래 결과가 [docs/db-table-spec.md](db-table-spec.md)와 다르면(테이블 누락, 제약 이름 불일치 등) **baseline을 하지 말고** 먼저 스키마를 맞추거나 마이그레이션 파일을 조정한다.

```sql
SHOW TABLES;

SHOW CREATE TABLE tour_spot;
SHOW CREATE TABLE tour_course;
SHOW CREATE TABLE tour_course_stop;
SHOW CREATE TABLE region_climate_index;
SHOW CREATE TABLE spot_weather_index;
```

`spot_weather_index`도 확인 대상에 포함되는 이유는, baseline이 "V1까지 이미 끝났다"고 가정하는 것이고 V1은 이 테이블을 만드는 내용이기 때문이다. baseline 직후 `V3`가 자동으로 이 테이블을 삭제하므로 최종 상태에는 남지 않는다.

특히 `region_climate_index`의 `score` 타입을 확인한다.

```sql
SELECT DATA_TYPE, NUMERIC_PRECISION, NUMERIC_SCALE
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'region_climate_index'
  AND column_name = 'score';
```

- 결과가 `INT`(정밀도/스케일 없음)면 → 이 저장소의 `V1`이 실제 이력과 일치하므로 그대로 baseline 버전 1로 진행한다.
- 결과가 이미 `decimal(5,2)`면 → 로컬 DB가 최신 엔티티 상태로 이미 생성된 것이다. 이 경우 `V1`을 그대로 baseline 1로 쓰되, **V2는 이미 반영된 변경을 다시 적용하려 하므로 실행 전에 문의할 것** — `ALTER`가 idempotent하지 않아 같은 값으로도 실패하지는 않지만, 실제로는 아무 것도 바꾸지 않는 空변경이 된다.

## 3. baseline 일회성 실행

다섯 테이블이 V1과 호환된다고 확인했을 때만 실행한다.

```powershell
$env:SPRING_FLYWAY_BASELINE_ON_MIGRATE="true"
$env:SPRING_FLYWAY_BASELINE_VERSION="1"

.\gradlew.bat bootRun
```

앱이 정상 기동하면 다음을 의미한다.

- 기존 DB가 "버전 1까지 이미 적용됨"으로 `flyway_schema_history`에 등록됨
- 이어서 `V2`가 실행되어 `score`가 `DECIMAL(5,2)`로 변경됨
- `V3`가 실행되어 미사용 테이블 `spot_weather_index`가 삭제됨
- 기존 관광지·코스·지수 데이터는 그대로 보존됨 (Flyway는 `ALTER`/`DROP`만 수행, 나머지 테이블 재생성 없음)
- Hibernate `ddl-auto=validate`가 통과함

## 4. 일회성 설정 제거

성공을 확인했으면 즉시 환경변수를 지운다. **`application.properties`에 `baseline-on-migrate=true`를 영구적으로 남기지 않는다** — 이 값이 상시 켜져 있으면 스키마가 실제로 다른 손상된 DB도 "정상 DB"로 간주해버려 Flyway의 안전장치가 무력화된다.

```powershell
Remove-Item Env:SPRING_FLYWAY_BASELINE_ON_MIGRATE
Remove-Item Env:SPRING_FLYWAY_BASELINE_VERSION
```

설정 파일의 `spring.flyway.baseline-on-migrate`는 항상 `false`로 유지한다.

## 5. 적용 결과 확인

```sql
SELECT version, description, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank;
```

`version 1`, `version 2`, `version 3` 모두 `success = 1`이어야 한다.

```sql
SELECT DATA_TYPE, NUMERIC_PRECISION, NUMERIC_SCALE
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'region_climate_index'
  AND column_name = 'score';

SHOW TABLES LIKE 'spot_weather_index';
```

첫 번째 쿼리는 `decimal(5,2)`가 나와야 하고, 두 번째 쿼리는 결과가 없어야 한다(V3로 삭제됐으므로).

## 이후 스키마를 바꿀 때 지킬 규칙

- **이미 적용된 마이그레이션 파일(`V1`~`V4`)은 절대 수정하지 않는다.** Flyway는 체크섬으로 검증하므로, 이미 실행된 파일을 고치면 다음 배포에서 검증 실패로 앱이 기동되지 않는다.
- 스키마를 더 바꿔야 하면 `V4__...`처럼 새 버전 파일을 추가한다.
- Flyway Community Edition에는 자동 undo(rollback) 기능이 없다. 되돌려야 할 상황이 생기면 새 마이그레이션으로 원상복구하거나, 1단계에서 만든 백업으로 복원한다.
- `spring.flyway.clean-disabled=true`는 항상 유지한다 — `flyway clean`은 전체 스키마를 삭제하는 명령이라 운영 환경에서 절대 실행하면 안 된다.
