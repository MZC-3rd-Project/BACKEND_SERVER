# don-moa EKS 배포 및 Kubernetes 운영 설명서

작성일: 2026-03-18  
대상: 이사님 공유용  
기준: 이 문서는 현재 리포지토리에 체크인된 설정을 기준으로 작성했다. 실제 AWS 콘솔, Terraform state, EKS 클러스터 live 상태와 다를 수 있으므로 최종 운영 판단 전에는 실환경 확인이 필요하다.

## 1. 문서 목적

이 문서는 `don-moa` 프로젝트의 현재 EKS 배포 구조와 Kubernetes 설정, 운영 방식, 비용 절감 방법, 복구 방식, 그리고 이사님께 설명해야 할 핵심 포인트를 한 문서에 정리한 것이다.

설명 범위는 다음과 같다.

- EKS 클러스터와 노드 그룹 구성
- GitHub Actions, CodePipeline, CodeBuild를 포함한 배포 흐름
- Helm 차트와 Kubernetes 리소스 구조
- 공개/비공개 서비스 구조
- 시크릿, IAM, IRSA, External Secrets 사용 방식
- 운영 중 스케일 다운, 복구, 주의사항
- 현재 dev 환경 기준의 리스크와 남은 과제

## 2. 한눈에 보는 현재 구조

현재 리포지토리 기준 `don-moa`는 `dev` 환경 중심의 EKS 배포 구조를 갖고 있다.

- 서비스 런타임 네임스페이스: `donmoa-dev`
- 플랫폼 컨트롤러 네임스페이스: `donmoa-system`
- 공개 진입점: `client-gateway`, `keycloak`
- 내부 서비스: auth, profile, product, stock, funding, sales, hot-deal, order, store, store-query, notification, chat, media-api, media-worker, analytics-dashboard, cart
- EKS 제외 서비스: `search`, `test-server`
- 시크릿 원본 저장소: AWS Secrets Manager
- Kubernetes 시크릿 동기화: External Secrets
- Ingress 컨트롤러: AWS Load Balancer Controller
- 무중단/점진 배포 컨트롤러: Argo Rollouts
- 이미지 저장소: ECR
- 데이터 계층: Aurora PostgreSQL, Redis, Kafka, DynamoDB, S3, CloudFront

핵심 흐름은 아래와 같다.

1. GitHub Actions가 `develop` 브랜치 push 또는 수동 실행으로 시작된다.
2. GitHub Actions는 AWS OIDC로 권한을 위임받아 CodePipeline을 실행한다.
3. CodeBuild 빌드 단계가 Gradle `bootBuildImage`로 이미지를 빌드하고 ECR에 push한다.
4. CodeBuild 배포 단계가 EKS kubeconfig를 갱신하고 Helm으로 서비스들을 배포한다.
5. 서비스는 `rolloutWave` 순서에 따라 순차적으로 반영된다.
6. `client-gateway`는 blue-green 배포를 사용하고, 나머지는 기본 rolling update를 사용한다.

## 3. 소스 오브 트루스 파일

이 문서를 검증하거나 추가 설명할 때 기준으로 삼아야 하는 주요 파일은 아래와 같다.

- `infra/terraform/eks-foundation/`
- `deploy/catalog/runtime-services.yaml`
- `deploy/helm/charts/spring-service/`
- `deploy/helm/environments/dev/*.yaml`
- `scripts/ci/export-eks-deploy-plan.rb`
- `scripts/ci/validate-eks-runtime-inputs.sh`
- `scripts/ci/install-eks-platform-addons.sh`
- `.github/workflows/trigger-codepipeline.yml`
- `buildspecs/dev-eks-build.yml`
- `buildspecs/dev-eks-deploy.yml`
- `deploy/catalog/dev-secrets-inventory.yaml`

## 4. EKS 인프라 구조

### 4.1 분리된 두 개의 인프라 레이어

현재 리포 구조는 인프라를 두 층으로 나눠 본다.

- `foundation`
  - VPC
  - private subnet
  - Aurora
  - Redis
  - Kafka
  - Secrets Manager
  - ECR
- `eks-foundation`
  - EKS cluster
  - managed node group
  - cluster/node IAM role
  - OIDC provider
  - IRSA bootstrap role
  - EKS managed addon

즉, EKS는 애플리케이션을 올리는 컴퓨트 계층이고, 데이터 저장소와 공용 AWS 리소스는 별도 foundation 스택에서 재사용하는 구조다.

### 4.2 현재 체크인된 EKS 기본값

체크인된 예시 기준 설정은 아래와 같다.

- 리전: `ap-northeast-2`
- 환경명: `dev`
- 프로젝트 prefix: `donmoa`
- 기본 클러스터명 규칙: `donmoa-dev-eks`
- 기본 노드 그룹명 규칙: `dev-apps-x86`
- 쿠버네티스 버전: `1.31`
- 노드 타입 기본값: `t3.large`
- 노드 desired size 기본값: `3`
- 노드 min size 기본값: `1`
- 노드 max size 기본값: `4`
- 루트 볼륨: `80GiB`, `gp3`

이 값은 예시 파일 기준이다. 실제 운영 중인 dev 클러스터도 이와 동일할 가능성이 높지만, 확정은 Terraform state 또는 AWS 콘솔에서 확인해야 한다.

### 4.3 클러스터 엔드포인트 접근

리포 기준 변수는 아래처럼 되어 있다.

- private endpoint 접근: 기본 `true`
- public endpoint 접근: 기본 `true`
- public endpoint 허용 CIDR: 변수 기본값은 `0.0.0.0/0`
- 예시 tfvars는 `<your-ip>/32`로 제한하도록 작성됨

즉, 설계상은 “사설/공인 둘 다 가능하지만, 공인 접근 대역은 최소화해야 한다”가 맞다. 이 부분은 이사님께도 “공개 제어 포인트”로 설명해야 한다.

### 4.4 노드 그룹 보안 설정

노드 그룹 launch template에는 기본적으로 아래 보안 설정이 있다.

- EBS 암호화
- IMDSv2 강제
- worker node 전용 security group 사용

security group은 대략 아래 포트 흐름을 허용한다.

- cluster -> node: `443`
- cluster -> node kubelet: `10250`
- cluster -> node webhook: `9443`
- node -> cluster: `443`
- node끼리 상호 통신 허용

## 5. EKS 플랫폼 애드온 구성

EKS managed addon과 Helm 설치형 플랫폼 애드온을 구분해서 이해해야 한다.

### 5.1 EKS managed addon

Terraform으로 직접 붙는 managed addon은 아래와 같다.

- `coredns`
- `kube-proxy`
- `vpc-cni`
- `aws-ebs-csi-driver`

이 중 `aws-ebs-csi-driver`는 IRSA role을 사용한다.

### 5.2 Helm 설치형 플랫폼 애드온

애플리케이션이 정상 동작하려면 아래 컨트롤러가 필요하다.

- `aws-load-balancer-controller`
- `external-secrets`
- `metrics-server`
- `argo-rollouts`

설치 대상 네임스페이스는 `donmoa-system`이다.

각자의 역할은 아래와 같다.

- `aws-load-balancer-controller`
  - ALB Ingress 생성과 관리
- `external-secrets`
  - AWS Secrets Manager 값을 Kubernetes Secret으로 동기화
- `metrics-server`
  - CPU, 메모리 메트릭 제공
  - HPA 사용 시 필수
- `argo-rollouts`
  - blue-green 및 progressive delivery 지원

현재 배포 buildspec에서 `INSTALL_EKS_PLATFORM_ADDONS` 기본값은 `false`다. 즉 클러스터를 처음 올리거나 애드온이 없는 상태라면 별도로 설치 스크립트를 실행해야 한다.

## 6. 서비스 카탈로그

현재 dev EKS에 정의된 서비스는 아래와 같다.

| 서비스 | Wave | 공개 여부 | 포트 | 주요 저장소/의존성 | 비고 |
| --- | ---: | --- | ---: | --- | --- |
| client-gateway | 3 | Public | 8080 | Redis, Keycloak | ALB Ingress, blue-green |
| keycloak | 2 | Public | 8080 | Aurora `keycloak_db`, Secrets Manager | ALB Ingress |
| auth | 2 | Internal | 8081 | Aurora `auth_db`, Kafka | 인증 서비스 |
| profile | 2 | Internal | 8071 | Aurora `profile_db`, Redis, Kafka | 사용자 프로필 |
| product | 4 | Internal | 8084 | Aurora `product_db`, read replica, Redis, Kafka | read/write split |
| stock | 4 | Internal | 8085 | Aurora `stock_db`, read replica, Redis, Kafka | read/write split |
| funding | 4 | Internal | 8086 | Aurora `funding_db`, read replica, Redis, Kafka | read/write split |
| sales | 4 | Internal | 8087 | Aurora `sales_db`, Redis, Kafka | 주문/결제 연계 |
| hot-deal | 4 | Internal | 8089 | Aurora `hotdeal_db`, read replica, Redis, Kafka | read/write split |
| order | 5 | Internal | 8090 | Aurora `order_db`, Redis, Kafka | 주문 서비스 |
| payment | 5 | Internal | 8095 | Aurora `payment_db`, Redis, Kafka, TossPayments | 결제 승인/조회 |
| review | 6 | Internal | 8097 | Aurora `review_db`, Kafka, Order, Media | 구매 리뷰 생성/조회 |
| store | 5 | Internal | 8072 | Aurora `store_db`, Redis, Kafka | 판매점 도메인 |
| store-query | 5 | Internal | 8091 | Aurora `store_query_db`, Redis, Kafka | 조회 모델 |
| notification | 6 | Internal | 8092 | Aurora `notification_db`, Redis, Kafka, SMTP | 메일 전송 포함 |
| chat | 6 | Internal | 8093 | Aurora `chat_db`, Redis, Kafka | WebSocket 연계 |
| media-api | 7 | Internal | 8094 | Aurora `media_db`, Redis, Kafka, S3, CloudFront | S3 IRSA 사용 |
| media-worker | 7 | Internal | 8095 | Aurora `media_db`, Kafka, S3, CloudFront | S3 IRSA 사용 |
| analytics-dashboard | 8 | Internal | 8095 | Aurora `analytics_db`, read replica, Redis, Kafka | read/write split |
| cart | 9 | Internal | 8096 | DynamoDB `cart_items` | DynamoDB IRSA 사용 |

제외 대상은 아래와 같다.

- `search`
- `test-server`

즉, 현재 EKS 구성은 “검색 서비스는 제외한 dev 런타임 전체”라고 보면 된다.

## 7. 배포 흐름

### 7.1 배포 트리거 방식

현재 체크인된 GitHub Actions 기준 배포 트리거는 아래와 같다.

- `develop` 브랜치 push
- `workflow_dispatch` 수동 실행

수동 실행 시 아래를 고를 수 있다.

- 대상: `dev` 또는 `prod`
- 모드: `build-and-deploy` 또는 `deploy-only`
- 대상 서비스: comma-separated key 지정 가능

중요한 점은, 이 워크플로는 “직접 배포”가 아니라 “CodePipeline 실행 요청”만 한다는 것이다.

### 7.2 GitHub Actions의 역할

GitHub Actions는 다음만 수행한다.

- 변경 파일 감지
- 어떤 파이프라인을 실행할지 결정
- 어떤 서비스만 선택 배포할지 계산
- AWS OIDC로 deploy role assume
- CodePipeline 실행 시작

즉, GitHub는 컨트롤 타워 역할이고, 실제 빌드/배포는 AWS 쪽이 한다.

### 7.3 변경 파일에 따른 배포 전략

현재 워크플로의 판단 로직은 대략 아래와 같다.

- `buildspecs`, `scripts`, `infra/terraform`, GitHub workflow 변경
  - 자동 배포 안 함
  - 수동 실행만 허용
- 공용 모듈, Gradle 설정 변경
  - 전체 build-and-deploy
- 특정 서비스 소스 변경
  - 해당 서비스 중심 selective build-and-deploy
- Helm values만 변경
  - deploy-only

즉, “코드 바뀌면 이미지 다시 빌드”, “values만 바뀌면 재배포만”이라는 구조다.

### 7.4 빌드 단계

CodeBuild 빌드 단계는 아래 순서로 동작한다.

1. AWS account ID와 region 확인
2. 현재 커밋 SHA 앞 8자리를 이미지 태그로 사용
3. 입력값 검증 스크립트 실행
4. 서비스 카탈로그와 values를 조합해 배포 계획 JSON 생성
5. 선택된 Gradle 서비스에 대해 `bootBuildImage` 수행
6. ECR push
7. 같은 이미지에 `latest` 태그도 갱신

빌드 모드는 두 가지다.

- `build-and-deploy`
  - 이미지 빌드 + 배포
- `deploy-only`
  - 이미지 빌드는 생략하고 values에 적힌 태그만 사용

### 7.5 배포 계획 생성

배포 전에 Ruby 스크립트가 `eks-deploy-plan.json`을 만든다.

이 계획 파일에는 아래가 들어간다.

- 환경명
- namespace
- 전체 배포인지 selective deploy인지
- 대상 서비스 목록
- release name
- workload kind
- rollout wave
- values file 경로
- 최종 image repository
- 최종 image tag

이 계획은 `rolloutWave` 순서로 정렬된다.

### 7.6 배포 단계

배포 단계는 아래 순서로 동작한다.

1. `aws eks update-kubeconfig`
2. 필요 시 플랫폼 애드온 설치
3. 서비스 수 확인
4. 서비스별로 `helm upgrade --install`
5. blue-green인 경우 Argo Rollout 상태를 `Healthy`까지 대기
6. rolling update인 경우 Deployment rollout status 대기

즉, Helm이 최종 반영 도구이고, 배포 검증도 CodeBuild 안에서 수행한다.

## 8. Helm 차트 및 Kubernetes 리소스 구조

현재 런타임 서비스는 공통 Helm 차트 `spring-service`를 사용한다.

### 8.1 공통 차트가 만드는 리소스

차트는 values에 따라 아래 리소스를 생성한다.

- `ServiceAccount`
- `ConfigMap`
- `ExternalSecret`
- `Service`
- `Ingress`
- `Deployment` 또는 `Rollout`
- `HorizontalPodAutoscaler`
- 추가 file ConfigMap

### 8.2 Deployment vs Rollout

배포 전략은 values의 `deploymentStrategy.type`에 따라 갈린다.

- `rolling`
  - 일반 `Deployment`
- `blueGreen`
  - Argo `Rollout`

현재 dev values 기준 blue-green을 사용하는 서비스는 `client-gateway` 하나다.

`client-gateway`는 active service와 preview service를 두고 자동 승격을 사용한다.

### 8.3 Ingress

공개 서비스는 Ingress를 사용한다.

- Ingress class: `alb`
- ALB target type: `ip`
- healthcheck path: 보통 `/actuator/health`

현재 공개 Ingress는 아래 두 개다.

- `client-gateway`
- `keycloak`

주의할 점은, 카탈로그에는 `api.dev.<domain>`, `id.dev.<domain>` 같은 host 정보가 들어 있으나 현재 dev values 파일에는 explicit host 값이 비어 있다. 즉, 실제 운영 중 host 기반 라우팅을 사용 중인지, 혹은 values가 로컬 예시 수준인지 실환경 확인이 필요하다.

### 8.4 ConfigMap와 ExternalSecret

일반 환경변수는 `env`로 들어가서 ConfigMap이 된다.  
민감한 값은 `secretEnv`로 들어가서 ExternalSecret이 생성되고, 이는 AWS Secrets Manager 값을 읽어 Kubernetes Secret으로 만든다.

즉, 애플리케이션은 아래 두 소스를 합쳐 환경변수를 받는다.

- public-safe 값: ConfigMap
- secret 값: Kubernetes Secret, 원본은 AWS Secrets Manager

### 8.5 Health Check

기본 차트는 아래 probe를 사용한다.

- liveness: `/actuator/health`
- readiness: `/actuator/health`

하지만 현재 dev values에서 `keycloak`은 liveness/readiness probe가 비활성화되어 있다. 즉 Keycloak은 예외 취급이다.

### 8.6 HPA

차트는 HPA를 지원한다.

- CPU target 기본값: 70%
- Memory target 기본값: 80%

다만 현재 dev values 파일에는 HPA 활성화가 보이지 않는다. 따라서 현재 체크인 상태 기준으로는 오토스케일링은 차트 지원만 있고 실제 활성화는 안 된 것으로 보는 것이 안전하다.

## 9. 네트워크 및 서비스 접근 구조

### 9.1 외부 노출

외부 사용자는 ALB를 통해 아래 서비스로 들어온다.

- `client-gateway`
- `keycloak`

이 두 서비스가 외부 진입점이다.

### 9.2 내부 서비스 통신

나머지 서비스는 Kubernetes service DNS 이름으로 통신한다.

예시:

- `http://auth-service:8081`
- `http://profile-service:8071`
- `http://product-service:8084`
- `http://store-query-service:8091`
- `ws://chat-service:8093`

즉, 마이크로서비스 간에는 클러스터 내부 DNS와 ClusterIP service를 쓴다.

### 9.3 외부 인프라 연결

현재 dev values 기준 주요 외부 인프라는 아래처럼 붙는다.

- Aurora PostgreSQL endpoint 직접 참조
- Redis endpoint 직접 참조
- Kafka bootstrap server 직접 참조
- S3 bucket 및 CloudFront 자원 연계
- DynamoDB table 직접 사용

특히 Kafka는 현재 dev values에서 고정 private IP `10.30.130.193:9092` 형태로 쓰이는 서비스가 많다. 운영 관점에서 보면 이 부분은 DNS 기반으로 전환하는 것이 더 안정적이다.

## 10. 인증, 보안, 시크릿 관리

### 10.1 GitHub Secrets에 넣는 것

GitHub에는 런타임 애플리케이션 시크릿을 넣지 않는다.

GitHub에서 필요한 것은 배포 권한 위임용 정보 정도다.

- `AWS_DEPLOY_ROLE_ARN`

그리고 repository variables는 대략 아래 수준이다.

- `AWS_REGION`
- `AWS_CODEPIPELINE_DEV_NAME`
- `AWS_CODEPIPELINE_PROD_NAME`
- `AWS_PROD_CD_ENABLED`

### 10.2 OIDC 기반 AWS 권한 위임

GitHub Actions는 AWS access key를 저장하지 않고 OIDC로 role을 assume한다.

즉, 배포용 AWS 권한 흐름은 아래와 같다.

1. GitHub Actions 실행
2. OIDC token 발급
3. AWS deploy role assume
4. CodePipeline 실행

이 방식은 정적 자격증명보다 안전하다.

### 10.3 External Secrets와 Secrets Manager

런타임 시크릿 원본은 AWS Secrets Manager다.

예시 secret path:

- `donmoa/dev/common`
- `donmoa/dev/auth-db`
- `donmoa/dev/product-db`
- `donmoa/dev/keycloak-db`
- `donmoa/dev/keycloak-admin`
- `donmoa/dev/notification-mail`
- `donmoa/dev/media-db`

공통 secret에는 아래 성격의 값이 들어간다.

- signing key
- gateway internal auth token
- OAuth2 client secret
- refresh token pepper
- Keycloak admin client secret

즉, 보안 원칙은 아래와 같다.

- GitHub에는 배포 권한만
- 애플리케이션 시크릿은 AWS Secrets Manager
- Kubernetes에는 External Secrets를 통해 필요한 값만 내려보냄

### 10.4 IRSA

AWS 접근 권한이 필요한 일부 서비스는 service account annotation으로 IRSA role을 쓴다.

현재 dev values 기준 예시는 아래와 같다.

- `media-api`
  - S3/CloudFront 관련 AWS 권한
- `media-worker`
  - S3/CloudFront 관련 AWS 권한
- `cart`
  - DynamoDB 권한

즉, 모든 서비스에 광범위한 node role 권한을 주는 것이 아니라, 필요한 pod에만 필요한 AWS 권한을 분리하는 구조다.

## 11. 현재 dev 값 기준 운영상 특징

이 부분은 “현재는 production hardening이 끝난 상태가 아니다”를 설명할 때 중요하다.

### 11.1 gateway는 현재 개발 편의 설정이 많이 들어가 있다

현재 `client-gateway` values에는 아래 값이 보인다.

- `SPRING_PROFILES_ACTIVE=local`
- `GATEWAY_AUTH_ENABLED=false`
- `GATEWAY_DEV_LOGIN_ENABLED=true`
- 개발용 아이디/비밀번호 고정
- `CORS_ALLOWED_ORIGIN_PATTERNS=*`
- session 비활성화

즉, 현재 EKS dev는 “개발/통합 테스트용” 성격이 강하다.

### 11.2 Keycloak도 dev 성격이 강하다

현재 Keycloak은 아래 특징이 있다.

- HTTP enabled
- hostname strict false
- realm import를 startup 시 자동 수행
- readiness/liveness off

즉, 인증 서버도 prod 보안 정책보다는 dev 운영 편의에 가깝다.

## 12. 서비스별 주요 운영 포인트

### 12.1 gateway

- 유일한 API 진입점
- blue-green 배포 적용
- Keycloak과 연계
- 현재 dev 기준으로는 인증 완화 상태

### 12.2 keycloak

- 외부 로그인/인증 담당
- realm 템플릿을 ConfigMap으로 마운트하고 startup 시 값 치환 후 import
- DB는 Aurora `keycloak_db`

### 12.3 product, stock, funding, hot-deal, analytics-dashboard

- read replica를 이용한 read/write split 활성화
- 조회 부하가 높은 서비스군으로 해석 가능

### 12.4 notification

- SMTP 기반 메일 발송
- Gmail SMTP secret 사용
- 향후 SES 전환 가능성을 열어둔 설정

### 12.5 media-api, media-worker

- S3 권한 필요
- IRSA 사용
- media DB 공유

### 12.6 cart

- 유일하게 Aurora가 아니라 DynamoDB 사용
- IRSA 사용

## 13. 운영자가 알아야 할 스케일 다운과 복구 방식

### 13.1 EKS는 stop 개념이 없다

중요한 점은 EKS는 EC2처럼 `중지(stop)` 버튼이 있는 구조가 아니라는 것이다.

운영상 가능한 선택지는 아래 둘이다.

- 노드 그룹을 줄여서 실질적으로 서비스만 멈추기
- 클러스터 자체를 삭제하기

### 13.2 가장 현실적인 비용 절감 방식: 노드 수 줄이기

비용을 줄이면서도 클러스터 정의를 유지하려면 노드 그룹을 스케일 다운하면 된다.

예시 전략:

- 평시 최소 운영: `desired=1`, `min=1`
- 사실상 서비스 중지: `desired=0`, `min=0`

Terraform으로는 아래처럼 조정한다.

```hcl
node_group_desired_size = 0
node_group_min_size     = 0
node_group_max_size     = 1
```

이후 아래처럼 적용한다.

```bash
cd infra/terraform/eks-foundation
terraform plan
terraform apply
```

AWS CLI로도 가능하다.

```bash
aws eks update-nodegroup-config \
  --cluster-name <cluster-name> \
  --nodegroup-name <node-group-name> \
  --scaling-config minSize=0,maxSize=1,desiredSize=0 \
  --region ap-northeast-2
```

### 13.3 노드를 0으로 내리면 생기는 일

- 애플리케이션 pod 전부 중단
- ALB는 남을 수 있지만 backend target이 비정상 상태가 됨
- platform addon pod도 노드가 없으면 뜰 수 없음
- EKS control plane은 살아 있음
- Kubernetes 리소스 정의는 남아 있음
- Aurora, Redis, Kafka, DynamoDB, S3는 삭제되지 않음

즉, “서비스는 사실상 중지”지만 “클러스터 자체가 삭제된 것은 아님”이다.

### 13.4 다시 올리면 예전 상태로 돌아가나

다시 노드를 올리면 Kubernetes가 현재 저장하고 있는 desired state를 기준으로 pod를 다시 스케줄한다.  
즉, 대부분 서비스는 자동으로 다시 뜬다.

하지만 이건 “시점 복구”가 아니라 “마지막 적용된 리소스 정의로 재기동”이다.

자동 복구되는 것:

- Deployment/Argo Rollout pod 재생성
- Service, Ingress, Secret, ConfigMap 재사용
- External Secrets, ALB Controller 등 컨트롤러 재기동

복구되지 않는 것:

- pod 메모리 상태
- 로컬 임시 파일
- 인메모리 캐시
- 끊어진 웹소켓 연결
- 완전한 시점 롤백

즉, “노드 0 후 복구”는 재기동이지 스냅샷 복구가 아니다.

## 14. 롤백과 장애 대응

### 14.1 현재 자동 롤백 구조

파이프라인 자체에 별도 자동 롤백 스크립트가 보이진 않는다.  
현재는 아래 수준으로 이해하면 된다.

- `client-gateway`
  - Argo Rollouts blue-green
  - 비정상 상태면 Rollout `Degraded` 감지 가능
- 일반 서비스
  - Deployment rollout status로 배포 성공 여부 확인

즉, “배포 감지와 실패 판별”은 있지만, “실패 시 자동 되돌리기”는 별도 운영 정책이 더 필요하다.

### 14.2 수동 대응 기본 방향

운영상 대응은 일반적으로 아래 순서가 맞다.

1. 어떤 서비스가 실패했는지 확인
2. values 변경 문제인지, 이미지 문제인지, secret 문제인지 분리
3. 필요 시 이전 이미지 태그로 Helm 재배포
4. gateway인 경우 Rollout 상태 확인
5. DB 마이그레이션이 있다면 애플리케이션만 되돌려도 되는지 별도 검토

## 15. 현재 구성의 리스크와 남은 과제

이 부분은 이사님께 반드시 같이 설명하는 것이 좋다.

### 15.1 dev 기준 구성이라 prod hardening이 아직 아니다

현재 체크인 상태는 아래 이유로 production-ready라고 말하기 어렵다.

- gateway auth off
- dev login on
- CORS 전면 허용
- Keycloak HTTP 기반
- Keycloak probe off
- Ingress TLS 설정 없음
- HPA 실사용 미확인

### 15.2 Kafka bootstrap가 고정 IP 기반이다

현재 여러 서비스 values에 Kafka bootstrap이 `10.30.130.193:9092` 형태로 들어간다.  
운영상 IP 고정 방식은 장애 전환이나 유지보수에서 취약할 수 있다.

### 15.3 공개 host 설정은 실환경 확인이 필요하다

서비스 카탈로그에는 공개 host 개념이 정의돼 있으나, 현재 dev values에는 explicit host가 생략되어 있다.  
따라서 실제 ALB host-based routing이 어떤 식으로 적용되는지 확인이 필요하다.

### 15.4 플랫폼 애드온 설치는 기본 자동이 아니다

배포 buildspec에서 `INSTALL_EKS_PLATFORM_ADDONS=false`가 기본이다.  
즉 새 클러스터에 바로 앱 배포한다고 끝나는 구조가 아니라, 애드온 초기화가 선행돼야 할 수 있다.

### 15.5 오래된 문서와 실제 workflow가 다를 수 있다

일부 체크리스트 문서는 branch trigger가 `prod`라고 적혀 있지만, 실제 체크인된 워크플로는 `develop` push 기준이다.  
이사님께 설명할 때는 “현재 코드 기준은 develop auto trigger”로 정리해서 전달해야 혼선이 없다.

### 15.6 리포 기준 문서와 실환경 drift 가능성

이 문서는 리포 기준이다.  
실환경에서 아래가 바뀌었을 수 있다.

- 실제 node size
- 실제 ALB host/domain
- 실제 secret ARN 범위
- 실제 CodePipeline 이름
- 실제 prod 사용 여부
- dev 외 추가 namespace 존재 여부

## 16. 이사님 설명용 핵심 문장

짧게 설명하면 아래처럼 정리할 수 있다.

`don-moa`는 현재 dev 환경 기준으로 EKS에 마이크로서비스들을 개별 Helm release로 배포하는 구조입니다. 외부 공개는 게이트웨이와 Keycloak만 ALB로 받고, 나머지는 클러스터 내부 통신입니다. GitHub Actions는 AWS OIDC를 통해 CodePipeline만 실행하고, 실제 빌드와 배포는 CodeBuild가 ECR 이미지 빌드 후 Helm으로 `donmoa-dev` 네임스페이스에 순차 반영합니다. 시크릿은 GitHub가 아니라 AWS Secrets Manager를 원본으로 두고 External Secrets로 pod에 주입합니다. S3나 DynamoDB가 필요한 서비스는 IRSA로 권한을 분리했습니다. 다만 현재 체크인된 값은 dev 중심이라 prod 보안 하드닝은 아직 별도 단계로 봐야 합니다.

## 17. 운영 체크리스트

### 17.1 배포 전 확인

- 대상 브랜치가 맞는지 확인
- build-and-deploy인지 deploy-only인지 확인
- 변경 파일이 selective deploy 대상인지 확인
- 필요한 secret이 Secrets Manager에 존재하는지 확인
- platform addon이 이미 설치되어 있는지 확인
- Kafka, Redis, Aurora endpoint가 유효한지 확인

### 17.2 배포 중 확인

- CodePipeline 실행 여부
- CodeBuild build 단계 성공 여부
- ECR push 성공 여부
- Helm upgrade 성공 여부
- Rollout/Deployment status 성공 여부

### 17.3 배포 후 확인

- `client-gateway` ingress/ALB health
- `keycloak` ingress/ALB health
- 주요 내부 서비스 pod Ready 여부
- ExternalSecret 동기화 정상 여부
- 최근 로그 에러 급증 여부

### 17.4 비용 절감 운영

- 야간/비사용 시간대 노드 0 또는 1대로 축소 가능
- 단, 노드 0이면 서비스는 전부 멈춤
- 다시 올리면 현재 desired state 기준으로 자동 재기동
- 완전한 시점 복구는 아님

## 18. 마지막 정리

현재 `don-moa` EKS 구조는 다음 네 가지 키워드로 설명할 수 있다.

- `EKS + Helm`
- `GitHub Actions -> OIDC -> CodePipeline`
- `Secrets Manager + External Secrets`
- `ALB + Argo Rollouts + IRSA`

즉, 아키텍처 자체는 실무형 클라우드 마이크로서비스 배포 구조에 가깝다.  
다만 현재 리포에 체크인된 값은 명확히 dev 성격이 강하므로, 운영/보안/자동복구/HPA/TLS 측면의 hardening은 별도 단계로 관리해야 한다.
