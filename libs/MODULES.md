# Shared Modules Index

공통 모듈을 책임 단위로 빠르게 찾기 위한 인덱스입니다.

| Module | 역할 | 사용 예시 |
|---|---|---|
| `:libs:core:exception` | 공통 예외/에러코드 모델 | `implementation(project(":libs:core:exception"))` |
| `:libs:core:util` | JSON/유틸 함수 | `implementation(project(":libs:core:util"))` |
| `:libs:core:id` | ID 생성/규약 유틸 | `implementation(project(":libs:core:id"))` |
| `:libs:core:pagination` | 페이징 모델/응답 | `implementation(project(":libs:core:pagination"))` |
| `:libs:contracts:http` | HTTP 헤더/계약 상수 | `implementation(project(":libs:contracts:http"))` |
| `:libs:api:response` | API 성공/실패 응답 모델 | `implementation(project(":libs:api:response"))` |
| `:libs:api:exception-handler` | 전역 예외 처리 | `implementation(project(":libs:api:exception-handler"))` |
| `:libs:api:rest-starter` | response + exception-handler 조합 | `implementation(project(":libs:api:rest-starter"))` |
| `:libs:data:jpa-base` | BaseEntity + auditing | `implementation(project(":libs:data:jpa-base"))` |
| `:libs:data:rw-routing` | read/write DataSource 라우팅 | `implementation(project(":libs:data:rw-routing"))` |
| `:libs:data:entity` | data 레거시 wrapper | `implementation(project(":libs:data:entity"))` |
| `:libs:security:context` | 요청 단위 인증 컨텍스트 | `implementation(project(":libs:security:context"))` |
| `:libs:security:signature` | 서명 헤더 검증 | `implementation(project(":libs:security:signature"))` |
| `:libs:security:security-starter-servlet` | MVC Gateway 헤더 검증 + `@CurrentUserId` | `implementation(project(":libs:security:security-starter-servlet"))` |
| `:libs:security:security-starter-webflux` | Gateway context 헤더 codec | `implementation(project(":libs:security:security-starter-webflux"))` |
| `:libs:security:security-starter` | security 레거시 wrapper | `implementation(project(":libs:security:security-starter"))` |
| `:libs:security:crypto` | 암호화/보안 유틸 | `implementation(project(":libs:security:crypto"))` |
| `:libs:config:kafka-core` | Kafka 기본 설정 | `implementation(project(":libs:config:kafka-core"))` |
| `:libs:config:kafka-idempotency-jpa` | Kafka 멱등 처리 JPA 저장소 | `implementation(project(":libs:config:kafka-idempotency-jpa"))` |
| `:libs:config:kafka` | kafka 레거시 wrapper | `implementation(project(":libs:config:kafka"))` |
| `:libs:config:lock` | 분산락 추상/AOP | `implementation(project(":libs:config:lock"))` |
| `:libs:config:lock-redisson` | Redisson 락 구현 | `implementation(project(":libs:config:lock-redisson"))` |
| `:libs:config:locking` | lock + lock-redisson 조합 | `implementation(project(":libs:config:locking"))` |
| `:libs:config:redis` | Redis 공통 설정 | `implementation(project(":libs:config:redis"))` |
| `:libs:config:resilience` | Resilience4j 기본 설정 | `implementation(project(":libs:config:resilience"))` |
| `:libs:config:shedlock` | 스케줄 분산락(ShedLock) | `implementation(project(":libs:config:shedlock"))` |
| `:libs:config:tracing` | 트레이싱 공통 설정 | `implementation(project(":libs:config:tracing"))` |
| `:libs:config:webclient` | WebClient 공통 설정 | `implementation(project(":libs:config:webclient"))` |
| `:libs:event:domain` | 도메인 이벤트 인터페이스 | `implementation(project(":libs:event:domain"))` |
| `:libs:event:inbox` | inbox 소비 처리 프레임워크 | `implementation(project(":libs:event:inbox"))` |
| `:libs:event:outbox-api` | outbox 계약/설정 | `implementation(project(":libs:event:outbox-api"))` |
| `:libs:event:outbox-jpa-kafka` | outbox JPA+Kafka 구현 | `implementation(project(":libs:event:outbox-jpa-kafka"))` |
| `:libs:event:outbox` | outbox 레거시 wrapper | `implementation(project(":libs:event:outbox"))` |
| `:libs:openapi:config` | OpenAPI 문서화 설정 | `implementation(project(":libs:openapi:config"))` |
| `:libs:clients:auth-client` | Auth 서비스 클라이언트 | `implementation(project(":libs:clients:auth-client"))` |
| `:libs:clients:media-client` | Media 서비스 클라이언트 | `implementation(project(":libs:clients:media-client"))` |
| `:libs:clients:product-client` | Product 서비스 클라이언트 | `implementation(project(":libs:clients:product-client"))` |
| `:libs:clients:profile-client` | Profile 서비스 클라이언트 | `implementation(project(":libs:clients:profile-client"))` |
| `:libs:clients:stock-client` | Stock 서비스 클라이언트 | `implementation(project(":libs:clients:stock-client"))` |
