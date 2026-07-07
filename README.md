# api-forge

[![CI](https://github.com/hello-pebble/api-forge/actions/workflows/ci.yml/badge.svg)](https://github.com/hello-pebble/api-forge/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen)](https://spring.io/projects/spring-boot)

> **메타데이터 기반 No-Code 동적 Open API 생성 플랫폼**
>
> 관리자가 DB 테이블·칼럼 설정만 등록하면, 코드 수정·배포 없이 즉시 필터·정렬·페이징·멀티포맷을 지원하는 Open API가 생성됩니다.

## 배경

공공데이터 Open API 시스템(Java 8 / Spring MVC 4 / iBatis 기반 레거시)을 운영하며 설계했던 **메타데이터 기반 동적 API 생성 엔진**을, 그 구조적 한계를 되짚어 현대 스택으로 다시 설계한 리팩토링 프로젝트입니다. 실제 운영 시스템의 소스는 비공개이며, 이 저장소는 핵심 아키텍처만 클린룸으로 재구현한 것입니다. 목표는 레거시에서 겪은 문제(문자열 SQL 조립, XML 설정 산재, 타입 불안정)를 설계 차원에서 해결하는 것입니다.

| 레거시 (운영 경험) | api-forge (재설계) |
|---|---|
| 문자열 연결 SQL 조립 + 블랙리스트 인젝션 필터 | **jOOQ 타입 세이프 DSL + 메타데이터 화이트리스트** — 식별자는 등록된 것만, 값은 전부 바인드 파라미터 |
| `switch` 포맷 분기 (수정 시 코드 변경) | **Writer 전략 빈 자동 수집** — 구현체 추가만으로 신규 포맷 등록 |
| XML 설정 + 메서드 명명 규칙 트랜잭션 | Spring Boot 3 자동설정 + `@Transactional` |
| `Map` 기반 파라미터 (타입 불안정) | DTO + Bean Validation |
| 평문 인증키 관리 | **해시 저장 API 키 + 일자별 사용량 집계** (원문 1회 노출, 상수 시간 검증) |
| 수동 테스트 | 단위·통합 테스트 47건 (H2 36 + PostgreSQL 11) + GitHub Actions CI |

## 아키텍처

![api-forge 아키텍처](docs/architecture.svg)

**핵심 흐름** — 레거시와 동일한 개념, 안전한 구현:

1. 관리자가 데이터셋(소스 테이블 + 노출 칼럼 + 필터 유형)을 등록·발행
2. 요청 파라미터를 등록된 칼럼 메타데이터와 대조 (미등록 칼럼 → 400)
3. jOOQ DSL로 SELECT/WHERE/ORDER BY 조립 — 값은 전부 바인드 파라미터
4. 결과셋을 format 파라미터에 맞는 Writer 전략으로 직렬화

## 실행

```bash
./mvnw spring-boot:run
```

시드 데이터(의안 정보 예시, 가상 데이터 15건)가 자동 등록·발행됩니다.

데이터 질의에는 API 키가 필요합니다(`X-API-Key` 헤더). 데모 키가 함께 시드됩니다: `demo-api-key-000000000000000000000000`

```bash
# 카탈로그 — 키 없이 공개 조회 (사용 가능한 데이터셋·필터·정렬 칼럼 확인)
curl http://localhost:8080/api/v1/datasets

# 이하 데이터 질의는 데모 키 사용
KEY="demo-api-key-000000000000000000000000"

# 기본 조회
curl -H "X-API-Key: $KEY" "http://localhost:8080/api/v1/datasets/bills"

# 필터 + 정렬 + 페이징
curl -H "X-API-Key: $KEY" "http://localhost:8080/api/v1/datasets/bills?COMMITTEE=행정안전위원회&sort=PROPOSE_DT,desc&page=0&size=10"

# 의안명 부분 검색 (WORDS) / 날짜 범위 (DATE)
curl -H "X-API-Key: $KEY" "http://localhost:8080/api/v1/datasets/bills?BILL_NM=데이터"
curl -H "X-API-Key: $KEY" "http://localhost:8080/api/v1/datasets/bills?PROPOSE_DT=2026-01-01,2026-03-31"

# CSV / XML 포맷
curl -H "X-API-Key: $KEY" "http://localhost:8080/api/v1/datasets/bills?format=csv"
curl -H "X-API-Key: $KEY" "http://localhost:8080/api/v1/datasets/bills?format=xml"
```

### 새 API를 코드 없이 만들기

```bash
# 1. 데이터셋 등록 (관리자 인증: 기본 admin/admin1234 — 데모용, 환경변수로 재정의)
curl -X POST http://localhost:8080/admin/api/datasets \
  -u admin:admin1234 -H "Content-Type: application/json" \
  -d '{
    "datasetKey": "bills-mini",
    "name": "의안 요약",
    "sourceTable": "NA_BILL",
    "columns": [
      {"sourceColumn": "BILL_ID", "displayName": "의안번호", "filterType": "EQUALS", "sortable": true},
      {"sourceColumn": "BILL_NM", "displayName": "의안명", "filterType": "WORDS", "sortable": false}
    ]
  }'

# 2. 발행 — 소스 테이블·칼럼 실존 검증 후 즉시 노출
curl -X POST http://localhost:8080/admin/api/datasets/bills-mini/publish -u admin:admin1234

# 3. 끝. 배포 없이 새 API가 살아있다
curl http://localhost:8080/api/v1/datasets/bills-mini
```

## 필터 유형

| FilterType | SQL | 요청 예시 |
|---|---|---|
| `EQUALS` | `col = ?` | `?BILL_ID=2200001` |
| `WORDS` | `col ILIKE %?%` | `?BILL_NM=데이터` |
| `CHECK` | `col IN (?, ?)` | `?COMMITTEE=행안위,정무위` |
| `DATE` | `col BETWEEN ? AND ?` | `?PROPOSE_DT=2026-01-01,2026-06-30` |
| `NONE` | 필터 불가 (노출 전용) | — |

## 보안 설계

- **식별자 화이트리스트**: 테이블·칼럼명은 관리자가 등록한 메타데이터에 있는 것만 SQL에 진입. 등록 시에도 `[A-Za-z][A-Za-z0-9_]*` 규칙 검증
- **값 바인딩**: 요청 값은 예외 없이 jOOQ 바인드 파라미터 — `?BILL_ID=' OR '1'='1` 은 그냥 0건짜리 문자열 검색 (통합 테스트로 증명)
- **발행 게이트**: DRAFT 상태는 포털 미노출, 발행 시 소스 실존 프로브 검증
- **RBAC**: `/admin/**`은 ADMIN 권한 필요, 데이터 질의는 API 키 필요, 카탈로그는 공개
- **API 키 저장**: 원문은 발급 시 1회만 노출하고 DB엔 SHA-256 해시만 저장, 인증은 상수 시간 비교

## API 키 & 사용량 통계

데이터 질의 엔드포인트는 API 키로 보호됩니다(공공데이터포털의 인증키 발급 모델). 카탈로그는 공개로 두어 탐색은 자유롭게, 데이터 소비는 키 기반으로 추적합니다.

```bash
# 키 발급 (관리자) — rawKey는 이 응답에서만 확인 가능
curl -X POST http://localhost:8080/admin/api/keys \
  -u admin:admin1234 -H "Content-Type: application/json" \
  -d '{"label":"모바일 앱"}'
# → { "rawKey": "3f9c...(48 hex)", "keyPrefix": "3f9c...", "notice": "..." }

# 발급 키로 데이터 질의
curl -H "X-API-Key: 3f9c...(48 hex)" "http://localhost:8080/api/v1/datasets/bills"

# 키 목록 (원문·해시 미노출)
curl -u admin:admin1234 http://localhost:8080/admin/api/keys

# 사용량 통계 — 총 호출수 + (데이터셋, 일자)별 집계
curl -u admin:admin1234 http://localhost:8080/admin/api/keys/{keyPrefix}/usage

# 키 폐기 — 이후 해당 키 요청은 401
curl -X POST -u admin:admin1234 http://localhost:8080/admin/api/keys/{keyPrefix}/revoke
```

**설계 포인트**
- 키 조회는 원문 앞 12자 `keyPrefix` 인덱스로 O(1), 검증은 `MessageDigest.isEqual`(상수 시간)
- 사용량은 요청 로그를 원본 저장하지 않고 `(키, 데이터셋, 일자)` 단위로 **집계 카운터**만 누적 — 조회·저장 비용 최소화
- 카운터 증가는 조건부 UPDATE→없으면 INSERT (이식성 우선). 운영에선 DB 업서트(`ON CONFLICT`/`MERGE`)로 원자화 가능 — 코드에 주석으로 명시

## 다중 DB 지원 (H2 · PostgreSQL)

레거시가 5종 DB를 설정값 하나로 전환하던 구조를 Spring Profile로 재현했습니다.

```bash
# 기본: H2 인메모리 (설정 불필요)
./mvnw spring-boot:run

# PostgreSQL: 프로필 전환 — 접속 정보는 환경변수로 주입
SPRING_PROFILES_ACTIVE=postgres DB_HOST=localhost DB_USER=apiforge DB_PASSWORD=apiforge ./mvnw spring-boot:run

# PostgreSQL + 앱을 한 번에 (Docker Compose)
docker compose up
```

**식별자 이식성** — PostgreSQL은 미인용 식별자를 소문자로, H2/Oracle은 대문자로 접습니다. 소스 테이블 DDL의 식별자를 인용 대문자로 고정하고 jOOQ가 동일하게 렌더링하도록 맞춰, 같은 메타데이터·같은 쿼리 엔진이 두 DB에서 그대로 동작합니다.

## 테스트 & CI

```bash
./mvnw verify
```

- `DynamicQueryBuilderTest` — 필터·정렬·화이트리스트·인젝션 거부 단위 검증
- `OpenApiIntegrationTest` — 등록→발행→조회 E2E, 포맷·보안·페이징 검증 (H2)
- `ApiKeyIntegrationTest` — 키 발급·인증·폐기·사용량 집계 검증
- `PostgresIntegrationTest` — **Testcontainers**로 실제 PostgreSQL 컨테이너를 띄워 동일 동작·인젝션 방어 이식성 검증 (Docker 없으면 자동 스킵)
- GitHub Actions: push/PR마다 `mvnw verify` (러너의 Docker로 Testcontainers 실행)

> **로컬에서 Testcontainers가 스킵될 때** — Docker Desktop(Windows)에서 Testcontainers가 데몬을 못 찾으면 엔진 파이프를 지정합니다:
> ```bash
> export DOCKER_HOST='npipe:////./pipe/dockerDesktopLinuxEngine'   # Windows Docker Desktop
> ./mvnw verify
> ```
> Testcontainers 없이 실제 Postgres로 수동 확인하려면 `docker compose up`으로 앱+DB를 띄우고 `curl`로 엔드포인트를 호출하면 됩니다.

## 기술 스택

Java 21 · Spring Boot 3.5 · Spring Data JPA (메타데이터 저장) · jOOQ (동적 쿼리) · Spring Security 6 · H2 / PostgreSQL · Testcontainers · Maven · GitHub Actions

## 로드맵

- [x] PostgreSQL 프로필 + Testcontainers 통합 테스트
- [x] API 키 발급·사용량 통계 (레거시의 인증키 관리 재설계)
- [ ] Excel(POI)·RDF Writer 추가
- [ ] 데이터셋 버저닝과 스키마 변경 감지
- [ ] 키별 요청 rate limiting (일자 집계 카운터 재활용)
