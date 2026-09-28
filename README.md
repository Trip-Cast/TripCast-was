# TripCast

관광지를 검색하고, 관광코스 단위로 날씨와 관광기후지수를 통합 조회하는 백엔드 API입니다.

---

## 목차

- [주요 기능](#주요-기능)
- [기술 스택](#기술-스택)
- [실행 방법](#실행-방법)
- [프로젝트 구조](#프로젝트-구조)
- [ERD](#erd)
- [API 문서](#api-문서)
- [컨벤션](#컨벤션)

---

## 주요 기능

- **관광지 조회**: 이름으로 관광지 검색, 관광지별 기상청 단기예보(동네예보) 조회
- **관광코스 조회**: 코스 상세, 코스 내 관광지별 통합 날씨(동네예보 + 지역 관광기후지수)
- **관광기후지수**: 시군구별 최근/기간별 관광기후지수 조회, 매일 새벽 배치로 공공데이터 자동 수집
- **코스 추천**: 관광기후지수가 높은 순으로 오늘 가기 좋은 코스 추천
- **관리자 API**: JWT 인증 기반, 배치를 기다리지 않고 관광기후지수 수동 동기화

## 기술 스택

| 분야 | 기술 |
| --- | --- |
| Language | ![Java](https://img.shields.io/badge/Java_21-007396?style=flat-square&logo=openjdk&logoColor=white) |
| Framework | ![Spring Boot](https://img.shields.io/badge/Spring_Boot_4.1.1-6DB33F?style=flat-square&logo=springboot&logoColor=white) ![Spring Security](https://img.shields.io/badge/Spring_Security-6DB33F?style=flat-square&logo=springsecurity&logoColor=white) |
| Database | ![MySQL](https://img.shields.io/badge/MySQL_8-4479A1?style=flat-square&logo=mysql&logoColor=white) ![Flyway](https://img.shields.io/badge/Flyway-CC0200?style=flat-square&logo=flyway&logoColor=white) ![H2](https://img.shields.io/badge/H2-0078D4?style=flat-square) |
| Auth | ![JWT](https://img.shields.io/badge/JWT-000000?style=flat-square&logo=jsonwebtokens&logoColor=white) |
| API 문서 | ![Swagger](https://img.shields.io/badge/Swagger-85EA2D?style=flat-square&logo=swagger&logoColor=black) |
| 테스트 | ![JUnit5](https://img.shields.io/badge/JUnit5-25A162?style=flat-square&logo=junit5&logoColor=white) ![Testcontainers](https://img.shields.io/badge/Testcontainers-0EA5E9?style=flat-square) ![Mockito](https://img.shields.io/badge/Mockito-78A641?style=flat-square) |
| 빌드/배포 | ![Gradle](https://img.shields.io/badge/Gradle-02303A?style=flat-square&logo=gradle&logoColor=white) ![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white) ![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white) |

<details>
<summary><h2> 실행 방법</h2></summary>

### 사전 준비

- JDK 21
- 로컬 MySQL 8.x (DB명 `TripCast`)
- (선택) Docker Desktop — 컨테이너 실행 및 Testcontainers 기반 테스트용

### 환경변수 설정

`DB_PASSWORD`, `WEATHER_API_KEY`, `TOUR_CLIMATE_API_KEY`, `JWT_SECRET`, `ADMIN_USERNAME`, `ADMIN_PASSWORD`가 필요합니다.

```powershell
Copy-Item .env.example .env
# .env를 열어서 실제 값 채우기 (이 파일은 git에 올라가지 않음)
. .\load-env.ps1
```

새 터미널을 열 때마다 `. .\load-env.ps1`을 다시 실행해야 한다. 각 값의 발급 방법은 [환경변수](#환경변수) 섹션 참고.

### 실행

```powershell
.\gradlew.bat bootRun
```

기동하면 Flyway가 스키마를 자동으로 맞추고(`docs/flyway-migration.md` 참고), `http://localhost:8080/swagger-ui.html`에서 전체 API를 확인/호출할 수 있다.

### 테스트

```powershell
.\gradlew.bat clean test
```

Docker가 실행 중이면 Testcontainers로 실제 MySQL에서 검증하는 테스트까지 포함해서 돌고, Docker가 없으면 해당 테스트만 자동으로 건너뛴다.

## 환경변수

| 변수 | 설명 | 비고 |
| --- | --- | --- |
| `DB_PASSWORD` | 로컬 MySQL 비밀번호 | |
| `DB_HOST`, `DB_PORT` | MySQL 접속 주소 | 생략 시 `localhost:3306` |
| `DB_USERNAME` | MySQL 접속 계정 | 생략 시 `root`. RDS는 `root`를 마스터 사용자명으로 쓸 수 없으므로 배포 시 필수 |
| `WEATHER_API_KEY` | 기상청 API 허브(단기예보) 인증키 | [apihub.kma.go.kr](https://apihub.kma.go.kr)에서 발급 |
| `TOUR_CLIMATE_API_KEY` | 공공데이터포털 관광기후지수 API 키 | [data.go.kr](https://www.data.go.kr) 서비스 `15056912` 활용신청 후 발급 |
| `JWT_SECRET` | JWT 서명 키 | 32자(256비트) 이상 필수 |
| `JWT_EXPIRATION_MINUTES` | 토큰 만료 시간(분) | 생략 시 60 |
| `ADMIN_USERNAME`, `ADMIN_PASSWORD` | 관리자 로그인 계정 | 회원가입 없이 단일 계정만 사용 |

## Docker로 실행

```powershell
docker build -t tripcast .
docker run -p 8080:8080 `
  -e DB_HOST=host.docker.internal `
  -e DB_PASSWORD=<MySQL 비밀번호> `
  -e WEATHER_API_KEY=<기상청 API 키> `
  tripcast
```

컨테이너 안에서 `localhost`는 컨테이너 자기 자신을 가리키므로, 호스트 PC에서 돌고 있는 MySQL에 붙으려면 `DB_HOST=host.docker.internal`(Docker Desktop이 제공하는 호스트 접근용 주소)을 넘겨야 한다. MySQL을 별도 컨테이너로 띄울 경우엔 그 컨테이너 이름을 `DB_HOST`로 넘기면 된다.

이미지에는 API 키/비밀번호를 넣지 않는다 — 항상 `docker run -e`로 실행 시점에 주입한다.


</details>

## 프로젝트 구조

```
src/main/java/com/tripcast/tourweather/
├── tourspot/     # 관광지, 관광코스 (검색, 조회, CSV 적재)
├── weather/      # 기상청 동네예보 연동
├── climate/      # 관광기후지수 (배치 수집, 조회)
├── auth/         # JWT 인증, Spring Security 설정
├── admin/        # 관리자 전용 API
└── common/       # 공통 설정, 예외 처리
```

## ERD

<img width="100%" alt="ERD" src="https://github.com/user-attachments/assets/ff113130-4cac-4d06-ac2c-acc5d57fa14c" />

컬럼별 상세 설명과 DDL은 [docs/db-table-spec.md](docs/db-table-spec.md) 참고.

## API 문서

- 전체 API 목록/테스트: 앱 실행 후 `/swagger-ui.html`
- DB 테이블 명세: [docs/db-table-spec.md](docs/db-table-spec.md)
- 관광기후지수 연동(외부 API, 배치, 환경변수) 가이드: [docs/tour-climate-api.md](docs/tour-climate-api.md)
- DB 스키마 관리(Flyway 마이그레이션, 기존 DB 편입) 가이드: [docs/flyway-migration.md](docs/flyway-migration.md)
- 인증(JWT, 관리자 API) 가이드: [docs/auth.md](docs/auth.md)
- AWS 배포(EC2 + RDS + GitHub Actions) 가이드: [docs/aws-deploy.md](docs/aws-deploy.md)

<details>
<summary><h2> 컨벤션 </h2></summary>



## 커밋 컨벤션

형식: `type: 한글 설명`

| type | 설명 |
| --- | --- |
| feat | 새로운 기능 추가 |
| fix | 버그 수정 |
| docs | 문서 수정 (README 등) |
| style | 코드 포맷팅, 세미콜론 누락 등 (로직 변경 없음) |
| refactor | 코드 리팩토링 (기능 변경 없는 코드 개선) |
| test | 테스트 코드 추가/수정 |
| chore | 빌드 설정, 패키지 매니저 등 기타 변경 |
| merge | 브랜치 병합 또는 PR 병합 |

예시: `feat: 날씨 조회 API 추가`

## 브랜치 컨벤션

형식: `type/작업내용` (작업내용은 영문, 하이픈으로 구분)

| 브랜치 | 설명 |
| --- | --- |
| main | 배포 가능한 안정 버전 |
| feature | 기능 개발 브랜치 |
| refactor | 코드 리팩토링 브랜치 |
| test | 테스트 코드 추가/보강 브랜치 |
| chore | 빌드 설정, 의존성 등 기타 변경 브랜치 |
| ci | CI/CD 설정 변경 브랜치 |

예시: `feature/admin-auth`, `chore/add-dockerfile`, `refactor/remove-spot-weather-index`

</details>
