# Architecture Diagrams

발표용 산출물:

- `deploy/diagrams/donmoa-presentation-architecture.drawio`
- `deploy/diagrams/donmoa-presentation-style-variants.drawio`
- `deploy/diagrams/donmoa-portfolio-deep-dive.drawio`

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

CI/CD 설명 자료:

- `donmoa-cicd-eks-pipeline.drawio`
  - `CI-CD Pipeline`: GitHub Actions OIDC, 영향 서비스 탐색, 선택 빌드, ECR, Helm/Argo Rollouts 배포 흐름 정리
  - `발표 포인트`: “의존성 그래프 기반 영향 서비스 선택 배포” 스토리를 말하기 쉽게 정리

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
- CI/CD 설명용: `donmoa-cicd-eks-pipeline.drawio`
- 디자인 톤 비교: `donmoa-presentation-style-variants.drawio`

모델링 전제:

- ECS는 제외하고 EKS 기준으로만 정리했습니다.
- 외부 진입은 `CloudFront -> ALB -> client-gateway` 흐름을 기본으로 봅니다.
- `edge-nginx`는 선택 계층으로만 표시했습니다.
- 서비스 간 통신은 Kubernetes `Service` DNS 기반으로 가정했습니다.
- 시크릿 연동은 `External Secrets + AWS Secrets Manager`, AWS 권한 연동은 `IRSA`를 전제로 했습니다.
- `cart-service`는 `DynamoDB`, 미디어는 `S3`, 검색은 별도 검색 엔진을 쓰는 구조를 반영했습니다.
- `public_subnet_cidrs`, `private_subnet_cidrs`가 각각 2개이고, EKS는 `private_subnet_ids >= 2`를 요구하는 현재 Terraform 설정을 기준으로 `2 AZ` 구성을 설명합니다.
