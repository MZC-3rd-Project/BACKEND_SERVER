# 프로젝트 작업 계획서 (2/20 ~ 3/18)

> **담당 영역**: Jira 프로젝트 구축 및 자동화, Auth(Keycloak) 서비스, Order 서비스, Payment 서비스(Toss 연동)
> **담당자**: hyein Heo
> **프로젝트**: MZC 3rd Project — Backend Server

---

## 전체 타임라인 요약

| 주차 | 기간 | 핵심 작업 |
|------|------|-----------|
| Week 0 | 2/20 ~ 2/25 | Jira 프로젝트 구축 및 GitHub-Jira 자동화 |
| Week 1 | 2/20 ~ 2/26 | Keycloak 기반 Auth 서비스 구현 |
| Week 2 | 2/27 ~ 3/3 | Auth 서비스 안정화 / CORS / 테스트 |
| Week 3 | 3/4 ~ 3/10 | Order 서비스 스캐폴딩 및 핵심 API |
| Week 4 | 3/11 ~ 3/14 | Payment 서비스 (Toss 연동) 및 주문-결제 플로우 연결 |
| Week 5 | 3/15 ~ 3/18 | 통합 테스트 / 버그 수정 / 마무리 |

---

## Phase 0: Jira 프로젝트 구축 및 GitHub-Jira 자동화 (2/20 ~ 2/26) ✅ 완료

### Sprint 0 (2/20 ~ 2/26) — 프로젝트 관리 인프라 구축

| 상태 | 작업 | 설명 |
|------|------|------|
| ✅ | Jira 프로젝트 생성 및 보드 구성 | MZC-Project03-Backend 프로젝트, 에픽/스토리/작업 이슈 타입 설정 |
| ✅ | Jira 이슈 타입 및 워크플로우 설계 | 에픽 → 스토리 → 작업 계층 구조 정의, 상태 흐름 설정 |
| ✅ | GitHub Issue → Jira 이슈 자동 동기화 | GitHub Actions 워크플로우 (sync-to-jira.yml) 구현 |
| ✅ | 이슈 생성 시 Jira 자동 생성 + 링크 연결 | Issue opened → Jira 이슈 생성, 타이틀 태그 기반 이슈 타입 자동 매핑 ([EPIC]/[STORY]/[BUGFIX]) |
| ✅ | 이슈 수정/종료/재오픈 시 Jira 상태 동기화 | GitHub Issue 상태 변경 → Jira 이슈 상태 자동 전이 |
| ✅ | GitHub Issue 담당자 변경 시 Jira 담당자 자동 동기화 | GitHub assignee 변경 → Jira assignee 자동 매핑 (#772) |
| ✅ | 상위 이슈(Parent) 자동 링크 | 본문 내 #번호 참조 시 Jira 상위 이슈 자동 연결 |
| ✅ | 팀원 Jira 계정 매핑 설정 | GitHub username → Jira accountId 매핑 |

---

## Phase 1: Auth 서비스 — Keycloak 기반 인증/인가 (2/20 ~ 3/3) ✅ 완료

### Sprint 1 (2/20 ~ 2/26) — Auth 서비스 핵심 구현

| 상태 | 작업 | 설명 |
|------|------|------|
| ✅ | Keycloak Docker 환경 구성 | 로컬/배포용 Keycloak 컨테이너 설정 |
| ✅ | User Entity & Repository 생성 | email/keycloakId 기반 조회 |
| ✅ | 요청/응답 DTO 설계 | Bean Validation 적용 |
| ✅ | 에러코드 정의 | Auth 도메인 전용 에러코드 |
| ✅ | Outbox 패턴 이벤트 클래스 | 비동기 이벤트 발행 기반 마련 |
| ✅ | ProfileServiceClient 연동 | Auth → Profile 서비스 간 WebClient 통신 |
| ✅ | 회원가입/로그인/비밀번호 변경/이메일 변경/탈퇴 구현 | Keycloak Admin API 활용 |
| ✅ | 내부 API (사용자 정보, 존재 여부, KC→Snowflake 매핑) | 서비스 간 통신용 |
| ✅ | Gateway 외부 API 연동 | 회원가입, 비밀번호/이메일 변경, 탈퇴, 로그인 기록 |
| ✅ | auth-client 라이브러리 리팩토링 | product-client 패턴에 맞게 facade 구현체 분리 |
| ✅ | Auth 서버 설정 파일 및 .env.example | 배포 환경변수 문서화 |
| ✅ | 닉네임/이메일 중복 확인 | Repository + Service + Controller |
| ✅ | 이메일 인증번호 발송/검증 | 인증 코드 엔티티, SMTP 설정 |
| ✅ | Keycloak 검증 기능 위임 리팩토링 | 자체 검증 코드 → Keycloak 위임 |
| ✅ | CORS 설정 | 프론트엔드 연동 대비 |
| ✅ | GitHub Issue → Jira 담당자 자동 동기화 | GitHub Actions 연동 |

### Sprint 2 (2/27 ~ 3/3) — Auth 서비스 안정화

| 상태 | 작업 | 설명 |
|------|------|------|
| ✅ | Auth 서비스 로컬 개발 환경 완성 | 회원가입 플로우 End-to-End 검증 |
| ✅ | 로컬 프론트엔드 CORS 설정 추가 | 개발 환경 호환 |
| ✅ | 이메일 인증번호 발송 이벤트 생성 | Outbox 기반 비동기 처리 |
| ✅ | Gateway CORS 헤더 중복 제거 | 다운스트림 서비스 응답 헤더 정리 |
| ✅ | CORS 핸들링 중앙 집중화 | Gateway에서 일괄 관리, 개별 서비스 CORS 제거 |
| ✅ | AuthServiceTest 수정 | ProfileServicePort 리팩토링 반영 |

---

## Phase 2: Order 서비스 — 주문 도메인 구현 (3/4 ~ 3/14)

### Sprint 3 (3/4 ~ 3/10) — Order 서비스 스캐폴딩 및 핵심 API

| 상태 | 작업 | 설명 |
|------|------|------|
| ✅ | Order 서비스 스캐폴딩 | Entity(Order, OrderItem), DB 설정, SecurityConfig |
| ✅ | Order 도메인 모델 설계 | 상태 머신(CREATED→PAID→COMPLETED/CANCELLED/REFUND) |
| 🔨 | 주문 생성 API (POST /api/orders) | Stock 예약 후 orderId 기반 주문 저장 |
| 🔨 | 주문 정보 저장 API | orderId + 배송지/수령인/메모 등 부가 정보 |
| 🔨 | 주문 조회 API (GET /api/orders/{id}) | 단건 조회 |
| 🔨 | 내 주문 목록 API (GET /api/orders) | userId 기반 목록 조회 (페이징) |
| 🔨 | Order → Stock 연동 (validate) | orderId 기반 금액/상태 확인 내부 API |
| 🔨 | Flyway 마이그레이션 스크립트 | orders, order_items 테이블 DDL |

### Sprint 4-A (3/11 ~ 3/12) — Order 서비스 이벤트 및 상태 관리

| 상태 | 작업 | 설명 |
|------|------|------|
| 📋 | Payment 이벤트 소비자 구현 | PAYMENT_COMPLETED / CANCELLED / TIMED_OUT 처리 |
| 📋 | 주문 상태 확정 로직 | 결제 결과에 따른 confirm/cancel 처리 |
| 📋 | 주문 확인/취소 API | 상태 전이 및 연관 도메인 알림 |
| 📋 | Outbox 이벤트 발행 | ORDER_CONFIRMED / ORDER_CANCELLED 이벤트 |
| 📋 | Order 서비스 단위 테스트 | Service/Domain 레이어 테스트 |

---

## Phase 3: Payment 서비스 — Toss Payments 연동 (3/11 ~ 3/16)

### Sprint 4-B (3/11 ~ 3/14) — Payment 서비스 핵심 구현

> 시퀀스 다이어그램 기준: `pay(orderId)` → `validate(orderId)` → `결제 승인 요청(Toss)` → 결과 이벤트 발행

| 상태 | 작업 | 설명 |
|------|------|------|
| 📋 | Payment 서비스 스캐폴딩 | Entity, Config, Application 초기 구조 |
| 📋 | Payment 도메인 모델 | Payment 엔티티 (paymentId, orderId, amount, status, pgTransactionId) |
| 📋 | 결제 요청 API (POST /api/payments) | orderId 기반 결제 시작 |
| 📋 | Order 서비스 validate 호출 | orderId로 금액/상태 확인 (WebClient) |
| 📋 | Toss Payments 승인 API 연동 | 결제 승인 요청 → 승인 결과 수신 |
| 📋 | 결제 결과 이벤트 발행 (Outbox) | PAYMENT_COMPLETED / PAYMENT_CANCELLED / PAYMENT_TIMED_OUT |
| 📋 | Flyway 마이그레이션 | payments 테이블 DDL |
| 📋 | Payment 에러 핸들링 | Toss API 에러, 타임아웃, 중복 결제 방지 |

### Sprint 5-A (3/15 ~ 3/16) — Payment 서비스 고도화

| 상태 | 작업 | 설명 |
|------|------|------|
| 📋 | 결제 조회 API | 결제 상태 확인 |
| 📋 | 결제 취소/환불 API | Toss 취소 API 연동 |
| 📋 | 멱등성 보장 | 동일 orderId 중복 결제 방지 (분산락/Unique 제약) |
| 📋 | Payment 서비스 단위 테스트 | Service 레이어 테스트 |

---

## Phase 4: 통합 및 마무리 (3/15 ~ 3/18)

### Sprint 5-B (3/15 ~ 3/18) — End-to-End 플로우 검증

| 상태 | 작업 | 설명 |
|------|------|------|
| 📋 | 전체 플로우 통합 테스트 | Stock 예약 → 주문 생성 → 결제 → 상태 반영 |
| 📋 | Hot Deal / Sales / Funding → Order 연동 | 각 도메인에서 orderId 기반 주문 연동 확인 |
| 📋 | Kafka 이벤트 플로우 검증 | Payment → Order/Stock/Sales 이벤트 전파 확인 |
| 📋 | Gateway 라우팅 설정 | Order/Payment 서비스 경로 추가 |
| 📋 | 배포 환경 설정 | Docker Compose, 환경변수, buildspec 업데이트 |
| 📋 | 버그 수정 및 안정화 | QA 피드백 반영 |

---

## 핵심 플로우 (시퀀스 다이어그램 기반)

```
User → Hot Deal/Sales/Funding : 구매 시작
Hot Deal/Sales/Funding → Stock : reserveAndIssueOrderId()
Stock → Stock : reserve + orderId 생성
Stock → Hot Deal/Sales/Funding : orderId 반환
User → Hot Deal/Sales/Funding : 부족한 주문 정보 입력
Hot Deal/Sales/Funding → Order : saveOrderInfo(orderId, 배송지/수령인/메모 ...)
Order → Hot Deal/Sales/Funding : 저장 성공

User → Payment : pay(orderId)
Payment → Order : validate(orderId) — 금액/상태 확인
Order → Payment : canonical amount / order status
Payment → Toss : 결제 승인 요청
Toss → Payment : 승인 결과
Payment → Order : PAYMENT_COMPLETED 또는 PAYMENT_CANCELLED/TIMED_OUT
Payment → Hot Deal/Sales/Funding : PAYMENT_COMPLETED 또는 PAYMENT_CANCELLED/TIMED_OUT

User ← Hot Deal/Sales/Funding : PAYMENT_* (orderId)
Order → Order : 주문 상태 확정
Order → Hot Deal/Sales/Funding : confirm/cancel by orderId
Hot Deal/Sales/Funding : 자기 도메인 상태 반영
```

> **참고**: orderId는 Stock이 첫 진입의 reserve 성공과 함께 생성한다.
> 즉 시작점은 order가 아니라 stock이다.
> order는 이후에 같은 orderId로 주문 정보를 저장한다.

---

## 상태 범례

| 아이콘 | 의미 |
|--------|------|
| ✅ | 완료 |
| 🔨 | 진행 중 |
| 📋 | 작업 예정 |

---

## 리스크 및 고려사항

1. **Toss Payments 연동**: 테스트 키 발급 및 샌드박스 환경 사전 준비 필요
2. **결제 멱등성**: 네트워크 장애 시 중복 결제 방지 로직 (orderId 기반 Unique 제약 + 분산락)
3. **이벤트 정합성**: Payment → Order/Stock/Sales 간 Kafka 이벤트 유실 대비 (Outbox + Inbox 패턴 활용)
4. **Stock ↔ Order 간 orderId 정합**: Stock에서 발급한 orderId를 Order에서 정확히 수신하는 흐름 검증 필요
