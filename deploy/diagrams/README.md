# Architecture Diagrams

발표용 산출물:

- `deploy/diagrams/donmoa-presentation-architecture.drawio`

설명용 산출물:

- `deploy/diagrams/donmoa-explainer-architecture.drawio`

각 파일 구성:

1. `클라우드 아키텍처`
   EKS 기준 AWS 배포 구조, 공개 진입 계층, 2개 AZ 분산, 공용 데이터 계층을 표현합니다.
2. `서비스 아키텍처`
   `client-gateway`, `Keycloak`, 도메인 서비스 묶음, 공용 저장소 / 플랫폼 의존 관계를 표현합니다.

모델링 전제:

- ECS는 제외하고 EKS 기준으로만 정리했습니다.
- 외부 진입은 `CloudFront -> ALB -> client-gateway` 흐름을 기본으로 봅니다.
- `edge-nginx`는 선택 계층으로만 표시했습니다.
- 서비스 간 통신은 Kubernetes `Service` DNS 기반으로 가정했습니다.
- 시크릿 연동은 `External Secrets + AWS Secrets Manager`, AWS 권한 연동은 `IRSA`를 전제로 했습니다.
- `cart-service`는 `DynamoDB`, 미디어는 `S3`, 검색은 별도 검색 엔진을 쓰는 구조를 반영했습니다.
- `public_subnet_cidrs`, `private_subnet_cidrs`가 각각 2개이고, EKS는 `private_subnet_ids >= 2`를 요구하는 현재 Terraform 설정을 기준으로 `2 AZ` 구성을 설명합니다.
