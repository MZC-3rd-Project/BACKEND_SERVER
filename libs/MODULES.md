# Shared Modules Guide (First-Time Friendly)

이 문서는 "처음 이 저장소를 보는 개발자"를 위한 공통 모듈 안내서입니다.

## 1) 처음 10분 온보딩
1. 먼저 `:libs:api:rest-starter`를 봅니다. (기본 API 응답/예외 처리)
2. 다음으로 `:libs:data:jpa-base`와 `:libs:data:rw-routing`을 봅니다. (DB 기본)
3. 이벤트가 필요하면 `:libs:event:domain` -> `:libs:event:outbox-api` -> `:libs:event:outbox-jpa-kafka` 순서로 봅니다.
4. 보안이 필요하면 런타임 기준으로 선택합니다.
   - MVC 서비스: `:libs:security:security-starter-servlet`
   - Gateway(WebFlux): `:libs:security:security-starter-webflux`

## 2) 신규 서비스 시작 체크리스트
- 기본 API: `:libs:api:rest-starter`
- 공통 예외: `:libs:core:exception`
- 데이터 기본: `:libs:data:jpa-base`
- 이벤트 발행 필요 시: `:libs:event:outbox-jpa-kafka`
- 외부 호출 필요 시: `:libs:config:webclient` + `:libs:config:resilience`
- 동시성 제어 필요 시: `:libs:config:locking`
- 보안:
  - MVC면 `:libs:security:security-starter-servlet`
  - WebFlux면 `:libs:security:security-starter-webflux`

## 3) 모듈 맵 (역할별)

### API
| Module | 역할 | 추천 상황 |
|---|---|---|
| `:libs:api:rest-starter` | API 기본 조합(response + exception-handler) | 신규 서비스 기본값 |
| `:libs:api:response` | 성공/실패 응답 포맷 | 응답 스키마 표준화 |
| `:libs:api:exception-handler` | 전역 예외 변환 | 예외 응답 일관화 |

### Core
| Module | 역할 | 추천 상황 |
|---|---|---|
| `:libs:core:exception` | 공통 예외/에러코드 | 비즈니스/기술 예외 표준화 |
| `:libs:core:util` | 공통 유틸(JSON 등) | 공통 유틸 재사용 |
| `:libs:core:id` | Snowflake ID | 분산 환경 유니크 ID |
| `:libs:core:pagination` | 페이징 응답 모델 | 목록 API 표준화 |

### Data
| Module | 역할 | 추천 상황 |
|---|---|---|
| `:libs:data:jpa-base` | BaseEntity + Auditing | JPA 엔티티 공통 필드 |
| `:libs:data:rw-routing` | read/write 라우팅 | 읽기/쓰기 DB 분리 |
| `:libs:data:entity` | 레거시 wrapper | 기존 코드 호환 유지 |

### Event
| Module | 역할 | 추천 상황 |
|---|---|---|
| `:libs:event:domain` | 이벤트 계약 | 도메인 이벤트 발행 |
| `:libs:event:inbox` | inbox 처리 프레임워크 | 소비 이벤트 안정 처리 |
| `:libs:event:outbox-api` | outbox 계약 | 구현체와 경계 분리 |
| `:libs:event:outbox-jpa-kafka` | outbox 구현체 | 트랜잭션 안전 발행 |
| `:libs:event:outbox` | 레거시 wrapper | 기존 코드 호환 유지 |

### Security
| Module | 역할 | 추천 상황 |
|---|---|---|
| `:libs:security:context` | AuthContext 저장/정리 | 요청 단위 사용자 컨텍스트 |
| `:libs:security:signature` | HMAC 서명/검증 | Gateway 헤더 신뢰 검증 |
| `:libs:security:security-starter-servlet` | MVC용 Gateway 보안 starter | `@CurrentUserId` + 헤더 검증 |
| `:libs:security:security-starter-webflux` | WebFlux용 context codec | Gateway 헤더 생성/파싱 |
| `:libs:security:security-starter` | 레거시 wrapper | 기존 코드 호환 유지 |
| `:libs:security:crypto` | 암복호화/마스킹 | 민감정보 보호 |

### Config
| Module | 역할 | 추천 상황 |
|---|---|---|
| `:libs:config:webclient` | WebClient 공통 설정 | 서비스/외부 HTTP 호출 |
| `:libs:config:resilience` | CircuitBreaker/Retry | 외부 장애 격리 |
| `:libs:config:redis` | Redis 공통 설정 | 캐시/세션/큐 |
| `:libs:config:tracing` | MDC tracing | 추적 로그 표준화 |
| `:libs:config:lock` | 분산락 AOP 계약 | 락 추상화 필요 |
| `:libs:config:lock-redisson` | Redis 락 구현 | 락 실제 구현 필요 |
| `:libs:config:locking` | lock 조합 starter | 분산락 빠른 적용 |
| `:libs:config:shedlock` | 스케줄 단일 실행 | 멀티 인스턴스 스케줄 락 |
| `:libs:config:kafka-core` | Kafka 공통 속성 베이스 | Kafka 설정 공통화 |
| `:libs:config:kafka-idempotency-jpa` | 멱등 + DLQ 저장 | 이벤트 중복/실패 관리 |
| `:libs:config:kafka` | 레거시 wrapper | 기존 코드 호환 유지 |

### Contract / OpenAPI / Clients
| Module | 역할 | 추천 상황 |
|---|---|---|
| `:libs:contracts:http` | 공통 헤더 계약 | 헤더 이름 하드코딩 제거 |
| `:libs:openapi:config` | OpenAPI 공통 설정 | Swagger 문서 표준화 |
| `:libs:clients:*` | 서비스 간 HTTP 클라이언트 | 도메인 간 통신 모듈화 |

## 4) 레거시 wrapper 모듈 안내
아래 모듈은 "호환성 유지"가 주목적입니다. 신규 코드는 역할별 분리 모듈을 직접 사용하는 것을 권장합니다.
- `:libs:data:entity`
- `:libs:config:kafka`
- `:libs:event:outbox`
- `:libs:security:security-starter`
