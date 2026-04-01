# Architecture Diagrams

발표용 산출물:

- `deploy/diagrams/donmoa-presentation-architecture.drawio`
- `deploy/diagrams/donmoa-presentation-style-variants.drawio`
- `deploy/diagrams/donmoa-portfolio-deep-dive.drawio`
- `deploy/diagrams/donmoa-branch-strategy.drawio`
- `deploy/diagrams/donmoa-logging-standard.drawio`
- `deploy/diagrams/donmoa-observability-implementation.drawio`
- `deploy/diagrams/donmoa-observability-code-proof.drawio`
- `deploy/diagrams/donmoa-helm-aligned-architecture.drawio`
- `deploy/diagrams/제목 없는 다이어그램.drawio (4).xml`
- `deploy/diagrams/donmoa-sync-facade-communication.drawio`
- `deploy/diagrams/donmoa-sync-facade-communication-mermaid.md`
- `deploy/diagrams/donmoa-sync-facade-class.puml`
- `deploy/diagrams/donmoa-sync-facade-usage.puml`
- `deploy/diagrams/donmoa-async-event-class.puml`
- `deploy/diagrams/donmoa-async-event-usage.puml`
- `deploy/diagrams/donmoa-async-event-communication.md`
- `deploy/diagrams/donmoa-async-event-producer.puml`
- `deploy/diagrams/donmoa-async-event-consumer.puml`
- `deploy/diagrams/donmoa-async-event-consumer-simple.puml`
- `deploy/diagrams/donmoa-service-communication-design.md`
- `deploy/diagrams/donmoa-async-event-presentation.md`
- `deploy/diagrams/donmoa-presentation-outline.md`

설명용 산출물:

- `deploy/diagrams/donmoa-explainer-architecture.drawio`
- `deploy/diagrams/donmoa-security-minimum-privilege.drawio`
- `deploy/diagrams/donmoa-observability-architecture.drawio`
- `deploy/diagrams/donmoa-cicd-eks-pipeline.drawio`

각 파일 구성:

1. `클라우드 아키텍처`
   EKS 기준 AWS 배포 구조, 네트워크 계층, Public/Private Subnet, 2개 AZ 분산, backing service를 표현합니다.
2. `서비스 아키텍처`
   `client-gateway`, `Keycloak`, 도메인 서비스 묶음, backing service와의 연결 관계를 표현합니다.

보안 설명 자료:

- `donmoa-security-minimum-privilege.drawio`
  - `최소 권한 보안 아키텍처`: ALB 공개 범위, SG 체이닝, private subnet NACL, Bastion /32, EKS API /32, Prometheus/Loki 내부 NLB까지 한 장으로 설명
  - `보안 체크리스트`: 발표 멘트용 보안 요약과 남은 과제 정리

옵저빌리티 설명 자료:

- `donmoa-observability-architecture.drawio`
  - `Observability Pipeline`: Metrics / Logs / Trace 수집과 Grafana 탐색 흐름을 한 장으로 정리
  - `운영 흐름 + 보안 포인트`: 설치 순서, internal NLB, Grafana EC2, SG 제한 포인트를 발표형으로 정리

옵저빌리티 구현 / 활용 설명 자료:

- `donmoa-observability-implementation.drawio`
  - `옵저빌리티 구현과 활용`: 실제로 붙인 구성(JSON 로그, requestId/traceId, Alloy, Loki, Grafana, Prometheus, alert rules)과 그래서 가능한 운영 액션을 한 장으로 설명
  - `발표 멘트`: 설계 다음 장에서 바로 말할 수 있는 40초 멘트와 질의응답 포인트 정리

옵저빌리티 코드 증거 자료:

- `donmoa-observability-code-proof.drawio`
  - `실제 구현 코드`: `logback-spring.xml`, `edge-nginx.yaml`, `alloy-values.yaml`, `grafana-donmoa-alert-rules.yaml`에서 바로 가져온 핵심 스니펫 정리
  - `실제 로그 포맷`: 앱 로그 JSON, nginx 로그 JSON, LogQL 검색 예시를 발표용으로 정리

Helm / 설정 기준 아키텍처 자료:

- `donmoa-helm-aligned-architecture.drawio`
  - 참고 이미지처럼 `AWS Cloud -> Region -> VPC -> AZ / Subnet -> Runtime / Managed Services` 구조로 정리
  - `deploy/catalog/runtime-services.yaml`, `deploy/helm/environments/dev/*.yaml`, `deploy/helm/addons/*.yaml` 기준으로 public ingress, EKS runtime, backing service, observability stack을 반영

- `제목 없는 다이어그램.drawio (4).xml`
  - 위 구조를 바탕으로 정렬/구분/화살표 가독성을 다듬은 발표형 아키텍처 버전

Facade 동기 통신 설명 자료:

- `donmoa-sync-facade-communication.drawio`
  - `Facade Class Diagram`: `Application Service -> small facade interfaces -> DefaultFacade -> AutoConfiguration` 구조 설명
  - `Usage Example`: `HotDealCheckoutService`에서 `ProductItemQueryClientFacade`, `OrderCreateClientFacade`를 사용하는 실제 흐름 설명

- `donmoa-sync-facade-communication-mermaid.md`
  - `Mermaid` 기반 클래스 다이어그램과 시퀀스 다이어그램 버전
  - `draw.io` 없이도 문서 뷰어에서 바로 렌더링 가능한 설명 자료

- `donmoa-sync-facade-class.puml`
  - PlantUML 클래스 다이어그램 버전
  - `Service -> Facade -> DefaultFacade -> Downstream` 구조만 남겨 DX 메시지에 집중한 버전

- `donmoa-sync-facade-usage.puml`
  - PlantUML 시퀀스 다이어그램 버전
  - 서비스 로직에서는 facade 를 함수 호출처럼 사용한다는 점을 보여주는 단순 예시 버전

비동기 이벤트 설명 자료:

- `donmoa-async-event-class.puml`
  - `Producer -> EventPublisher -> Outbox -> Kafka -> Consumer -> Inbox -> Processor` 구조를 단순화한 PlantUML 클래스 다이어그램
  - 서비스 코드에서는 `publish()` 와 `process()` 중심으로 보이도록 DX 메시지에 집중

- `donmoa-async-event-usage.puml`
  - `PaymentCommandService`가 `PaymentCompletedEvent`를 발행하고 `NotificationPaymentEventConsumer/Processor`가 처리하는 사용 예시
  - outbox 저장, immediate publish, inbox enqueue, worker 처리 흐름을 발표용으로 단순화

- `donmoa-async-event-communication.md`
  - 비동기 통신 파트만 따로 읽히도록 설명, 코드 캡처 포인트, PlantUML 원문을 한 파일에 합친 마크다운 버전

- `donmoa-async-event-producer.puml`
  - Producer 전용 PlantUML 시퀀스 다이어그램
  - `publish() -> outbox -> immediate publish -> relay retry` 흐름만 분리해서 설명

- `donmoa-async-event-consumer.puml`
  - Consumer 전용 PlantUML 시퀀스 다이어그램
  - `KafkaListener -> inbox enqueue -> worker -> processor` 흐름만 분리해서 설명

- `donmoa-async-event-consumer-simple.puml`
  - 코드 배경지식이 없어도 이해할 수 있게 표현한 발표용 비동기 소비 구조
  - `이벤트 수신 -> 안전 보관 -> 중복 확인/재시도 -> 알림 발송` 메시지에 집중

- `donmoa-async-event-presentation.md`
  - `클래스 다이어그램 -> 시퀀스 다이어그램 -> 코드 예시` 순서로 바로 발표에 쓸 수 있게 정리한 비동기 파트 전용 마크다운
  - 각 슬라이드별 제목, 핵심 메시지, 발표 멘트, PlantUML 코드, 코드 캡처 포인트 포함

- `donmoa-service-communication-design.md`
  - 동기/비동기를 왜 나눴는지, 선택 기준이 무엇인지, Kafka 기반 이벤트 드리븐 비동기 처리와 outbox/inbox를 어떻게 설명할지까지 한 번에 정리한 문서
  - 동기/비동기 발표 대본과 PlantUML 코드 포함

로깅 표준 설명 자료:

- `donmoa-logging-standard.drawio`
  - `로깅 표준 설계`: JSON stdout, 공통 필드명, requestId / traceId / spanId, 레벨 규칙, PII 금지, Loki label 최소화까지 한 장으로 설명
  - `발표 포인트`: 좋은 로그 / 나쁜 로그 예시, 질의응답 포인트, 짧은 발표 키워드 정리
  - 발표 메인 장표보다는 심화 설명용 보조 자료에 가깝습니다.

CI/CD 설명 자료:

- `donmoa-cicd-eks-pipeline.drawio`
  - `CI-CD Pipeline`: GitHub Actions OIDC, 영향 서비스 탐색, 선택 빌드, ECR, Helm/Argo Rollouts 배포 흐름 정리
  - `발표 포인트`: “의존성 그래프 기반 영향 서비스 선택 배포” 스토리를 말하기 쉽게 정리

브랜치 전략 설명 자료:

- `donmoa-branch-strategy.drawio`
  - `브랜치 전략`: Issue -> 작업 브랜치 -> Conventional Commit -> PR -> CI -> `develop` 통합 -> `main` 승격 흐름 정리
  - `발표 포인트`: 30초 발표 멘트, 질의응답 포인트, 장표 배치 순서 정리

발표 스크립트 자료:

- `donmoa-presentation-outline.md`
  - 5분 / 10분 발표 순서
  - 각 슬라이드별 추천 다이어그램
  - 바로 읽을 수 있는 발표 멘트 초안

TCC 발표 자료:

- `donmoa-tcc-slide-notes.md`
  - `재고 보호를 위한 TCC` 1장 설명용 발표자 노트
  - 왜 TCC가 필요한지, 어디에만 적용했는지, 무엇을 얻고 감수했는지 한 장 기준으로 정리

- `donmoa-tcc-deck-outline.md`
  - `왜 설계했는가 -> 어떻게 적용했는가 -> 무엇을 얻었는가` 흐름의 3장 + 백업 1장 PPT 구조
  - 슬라이드 제목, 본문 문장, 발표 멘트, 강조 키워드까지 바로 옮길 수 있게 정리

- `tcc-why-one-slide.svg`
  - `왜 재고에 TCC가 필요했는가`를 한 장에 정리한 발표용 장표
  - 왼쪽은 오버세일이 생기는 시간차, 오른쪽은 `available -> reserved -> sold / restore`, 아래는 얻은 효과를 보여줌

- `tcc-why-one-slide.png`
  - 위 장표의 PNG 렌더링 버전
  - PPT에서 SVG 렌더링이 깨질 때 바로 넣기 좋은 안전한 버전

PK 전략 발표 자료:

- `pk-strategy-one-slide-no-title.drawio`
  - 전역 PK 생성 전략을 한 장에 정리한 제목 없는 draw.io 장표
  - 기본 전략은 Snowflake Long, 생성 경로는 `설정 -> Bean -> JPA/직접 발급`, 예외 규칙은 `IDENTITY / 자연키`로 구분

심화 발표 자료:

- `donmoa-portfolio-deep-dive.drawio`
  - `TCC + Saga`: 재고 내부 TCC와 서비스 간 choreography saga를 한 장으로 정리
  - `Read Write Split`: 공통 라우팅 모듈, Writer/Reader 분기, KPI를 발표형 레이아웃으로 정리
  - `Media Progressive Delivery`: presign, confirm, worker, DLQ, CloudFront 전환 흐름을 단계별로 정리
  - `Observability Pipeline`: Metrics / Logs / Trace, Loki S3 backend, Alloy, Prometheus, Grafana 연결 구조를 정리

스타일 비교 파일:

- `donmoa-presentation-style-variants.drawio`
  `Style A_Executive`: 가장 무난한 임원 보고 / 정식 발표 톤
  `Style B_Dark`: 어두운 배경 기반의 보드룸 / 데모 데이 톤
  `Style C_Glass`: 밝고 현대적인 제품 발표 톤
  세 스타일 모두 동일한 상세 구조를 유지합니다:
  네트워크 계층, Public/Private Subnet, 2개 AZ, EKS 런타임, backing service, 서비스 아키텍처를 포함합니다.

권장 사용:

- 기본 발표 자료: `donmoa-presentation-architecture.drawio`
- 포트폴리오 / 발표 심화 슬라이드: `donmoa-portfolio-deep-dive.drawio`
- 발표자 설명용: `donmoa-explainer-architecture.drawio`
- 보안 설정 설명용: `donmoa-security-minimum-privilege.drawio`
- 옵저빌리티 설명용: `donmoa-observability-architecture.drawio`
- 옵저빌리티 구현 / 활용 설명용: `donmoa-observability-implementation.drawio`
- 옵저빌리티 코드 증거 설명용: `donmoa-observability-code-proof.drawio`
- Helm / 설정 기준 아키텍처 설명용: `donmoa-helm-aligned-architecture.drawio`
- 사용자 수정본 발표형 정리 버전: `제목 없는 다이어그램.drawio (4).xml`
- Facade 동기 통신 설명용: `donmoa-sync-facade-communication.drawio`
- Facade 동기 통신 설명용(Mermaid): `donmoa-sync-facade-communication-mermaid.md`
- Facade 동기 통신 설명용(PlantUML class): `donmoa-sync-facade-class.puml`
- Facade 동기 통신 설명용(PlantUML sequence): `donmoa-sync-facade-usage.puml`
- 비동기 이벤트 설명용(PlantUML class): `donmoa-async-event-class.puml`
- 비동기 이벤트 설명용(PlantUML sequence): `donmoa-async-event-usage.puml`
- 비동기 이벤트 설명용(Markdown + PlantUML): `donmoa-async-event-communication.md`
- 비동기 이벤트 설명용(Producer only): `donmoa-async-event-producer.puml`
- 비동기 이벤트 설명용(Consumer only): `donmoa-async-event-consumer.puml`
- 비동기 이벤트 설명용(Consumer simple): `donmoa-async-event-consumer-simple.puml`
- 비동기 이벤트 발표 정리본(Markdown): `donmoa-async-event-presentation.md`
- 서비스 통신 설계 정리본(Markdown): `donmoa-service-communication-design.md`
- 로깅 표준 설명용: `donmoa-logging-standard.drawio`
- CI/CD 설명용: `donmoa-cicd-eks-pipeline.drawio`
- 브랜치 전략 설명용: `donmoa-branch-strategy.drawio`
- 발표 대본 / 장표 순서 정리용: `donmoa-presentation-outline.md`
- 디자인 톤 비교: `donmoa-presentation-style-variants.drawio`

모델링 전제:

- ECS는 제외하고 EKS 기준으로만 정리했습니다.
- 외부 진입은 `CloudFront -> ALB -> client-gateway` 흐름을 기본으로 봅니다.
- `edge-nginx`는 선택 계층으로만 표시했습니다.
- 서비스 간 통신은 Kubernetes `Service` DNS 기반으로 가정했습니다.
- 시크릿 연동은 `External Secrets + AWS Secrets Manager`, AWS 권한 연동은 `IRSA`를 전제로 했습니다.
- `cart-service`는 `DynamoDB`, 미디어는 `S3`, 검색은 별도 검색 엔진을 쓰는 구조를 반영했습니다.
- `public_subnet_cidrs`, `private_subnet_cidrs`가 각각 2개이고, EKS는 `private_subnet_ids >= 2`를 요구하는 현재 Terraform 설정을 기준으로 `2 AZ` 구성을 설명합니다.
