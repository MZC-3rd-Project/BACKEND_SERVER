# Terraform 인프라 발표 포인트 5가지

형식은 발표용으로 맞췄다.

- 위: 실제 Terraform 코드
- 아래: 그 코드를 보면서 말할 발표 멘트

## 1. 인프라를 Foundation과 EKS로 분리해서 관리

```hcl
# infra/terraform/foundation/network.tf
resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true
```

```hcl
# infra/terraform/eks-foundation/cluster.tf
resource "aws_eks_cluster" "this" {
  name     = local.cluster_name
  role_arn = aws_iam_role.cluster.arn
  version  = var.cluster_version
```

발표 멘트:

우리는 Terraform 인프라를 한 스택에 다 몰아넣지 않고
`foundation`과 `eks-foundation`으로 나눠서 관리했습니다.
`foundation`은 VPC, 데이터, 보안 그룹 같은 공통 기반을 만들고,
`eks-foundation`은 그 위에 Kubernetes control plane과 node, addon, IRSA를 올리는 구조입니다.
즉, 변경 주기가 다른 기반 인프라와 클러스터 계층을 분리해서
운영 중 영향 범위를 줄이고 재사용성을 높인 것이 첫 번째 포인트입니다.

## 2. 상태 저장소를 private subnet 기반 공통 계층으로 분리

```hcl
# infra/terraform/foundation/database.tf
resource "aws_rds_cluster" "aurora" {
  count = var.enable_aurora ? 1 : 0

  cluster_identifier              = "${var.name_prefix}-${var.environment}-aurora"
  engine                          = "aurora-postgresql"
  vpc_security_group_ids          = [aws_security_group.db.id]
  storage_encrypted               = true
  backup_retention_period         = var.aurora_backup_retention_days
  enabled_cloudwatch_logs_exports = ["postgresql"]
```

```hcl
# infra/terraform/foundation/database.tf
resource "aws_rds_cluster_instance" "writer" {
  count = var.enable_aurora ? 1 : 0

  identifier         = "${var.name_prefix}-${var.environment}-aurora-writer"
  cluster_identifier = aws_rds_cluster.aurora[0].id
  publicly_accessible = false
}
```

```hcl
# infra/terraform/foundation/database.tf
resource "aws_rds_cluster_instance" "reader" {
  count = var.enable_aurora ? var.aurora_reader_instance_count : 0

  identifier          = "${var.name_prefix}-${var.environment}-aurora-reader-${count.index + 1}"
  cluster_identifier  = aws_rds_cluster.aurora[0].id
  publicly_accessible = false
}
```

```hcl
# infra/terraform/foundation/redis.tf
resource "aws_elasticache_replication_group" "this" {
  count = var.enable_redis ? 1 : 0

  replication_group_id       = substr(replace("${var.name_prefix}-${var.environment}-redis", "_", "-"), 0, 40)
  automatic_failover_enabled = local.redis_automatic_failover_enabled
  multi_az_enabled           = local.redis_automatic_failover_enabled
  at_rest_encryption_enabled = var.redis_at_rest_encryption_enabled
  transit_encryption_enabled = var.redis_transit_encryption_enabled
```

발표 멘트:

두 번째 포인트는 상태 저장소를 애플리케이션과 분리된 공통 계층으로 둔 것입니다.
Aurora는 writer와 reader를 분리한 구조로 만들었고,
둘 다 public 접근이 아니라 private subnet 안에만 두었습니다.
Redis도 replication group과 failover 기준으로 구성해서
단순 캐시 하나가 아니라 장애 대응이 가능한 형태로 준비했습니다.
즉, 애플리케이션이 늘어나도 공통으로 사용할 수 있는 데이터 계층을
Terraform에서 먼저 분리해 둔 것이 특징입니다.

## 3. 네트워크를 private 기본값 + 다층 방어 구조로 설계

```hcl
# infra/terraform/foundation/network.tf
resource "aws_subnet" "private" {
  for_each = local.private_subnet_map

  vpc_id                  = aws_vpc.this.id
  cidr_block              = each.value
  availability_zone       = local.azs[tonumber(each.key)]
  map_public_ip_on_launch = false
```

```hcl
# infra/terraform/foundation/network.tf
resource "aws_network_acl" "private" {
  count = var.enable_private_subnet_network_acl ? 1 : 0

  vpc_id     = aws_vpc.this.id
  subnet_ids = [for s in values(aws_subnet.private) : s.id]
```

```hcl
# infra/terraform/foundation/security.tf
resource "aws_security_group" "db" {
  name        = "${var.name_prefix}-${var.environment}-db-sg"
  description = "Aurora access security group"
  vpc_id      = aws_vpc.this.id

  dynamic "ingress" {
    for_each = var.eks_node_security_group_id != null ? [var.eks_node_security_group_id] : []
```

발표 멘트:

세 번째 포인트는 네트워크를 처음부터 private 중심으로 잡았다는 점입니다.
private subnet은 public IP를 받지 않도록 했고,
외부로 나가야 할 때만 NAT를 타도록 구성했습니다.
여기에 더해서 security group만 두는 것이 아니라,
옵션으로 private subnet NACL도 켤 수 있게 해서 방어 계층을 하나 더 둘 수 있게 했습니다.
즉, 네트워크를 단순 연결 구조가 아니라
"private 기본값 + SG + optional NACL"의 다층 방어 구조로 설계한 것이 특징입니다.

## 4. EKS를 클러스터 생성 수준이 아니라 플랫폼 하드닝까지 포함해 구성

```hcl
# infra/terraform/eks-foundation/cluster.tf
resource "aws_eks_cluster" "this" {
  name     = local.cluster_name
  role_arn = aws_iam_role.cluster.arn
  version  = var.cluster_version

  vpc_config {
    endpoint_private_access = var.cluster_endpoint_private_access
    endpoint_public_access  = var.cluster_endpoint_public_access
    public_access_cidrs     = var.cluster_endpoint_public_access_cidrs
    security_group_ids      = [aws_security_group.cluster.id]
    subnet_ids              = var.private_subnet_ids
  }
```

```hcl
# infra/terraform/eks-foundation/cluster.tf
resource "aws_launch_template" "node_group" {
  metadata_options {
    http_endpoint               = "enabled"
    http_put_response_hop_limit = 2
    http_tokens                 = "required"
  }
```

```hcl
# infra/terraform/eks-foundation/iam.tf
resource "aws_iam_openid_connect_provider" "this" {
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.oidc.certificates[0].sha1_fingerprint]
  url             = aws_eks_cluster.this.identity[0].oidc[0].issuer
}
```

```hcl
# infra/terraform/eks-foundation/addons.tf
resource "aws_eks_addon" "managed" {
  for_each = local.managed_addons

  cluster_name                = aws_eks_cluster.this.name
  addon_name                  = each.key
  addon_version               = each.value.version
  resolve_conflicts_on_create = "OVERWRITE"
  resolve_conflicts_on_update = "OVERWRITE"
```

발표 멘트:

네 번째 포인트는 EKS를 그냥 띄운 것이 아니라,
운영 기준의 하드닝 요소까지 같이 넣었다는 점입니다.
클러스터는 private endpoint 접근을 지원하고,
노드 launch template에서는 IMDSv2를 required로 강제했습니다.
또 OIDC provider를 생성해서 IRSA 기반 IAM 분리를 가능하게 했고,
EBS CSI 같은 managed addon도 Terraform 스택 안에서 같이 관리하게 했습니다.
즉, EKS를 단순 compute cluster가 아니라
"접근 제어, 메타데이터 보호, 서비스 권한 분리, addon 일관성"까지 포함한 플랫폼 계층으로 만든 것이 특징입니다.

## 5. 플랫폼 리소스를 옵션형으로 설계해서 확장 가능하게 만듦

```hcl
# infra/terraform/foundation/platform_optional.tf
resource "aws_msk_cluster" "this" {
  count = var.enable_msk ? 1 : 0

  cluster_name           = "${var.name_prefix}-${var.environment}-msk"
  kafka_version          = var.msk_kafka_version
  number_of_broker_nodes = var.msk_broker_node_count
```

```hcl
# infra/terraform/foundation/platform_optional.tf
resource "aws_opensearch_domain" "this" {
  count = var.enable_opensearch ? 1 : 0

  domain_name    = coalesce(var.opensearch_domain_name, replace("${var.name_prefix}-${var.environment}-search", "_", "-"))
  engine_version = var.opensearch_engine_version
```

```hcl
# infra/terraform/foundation/search_ai_enrichment.tf
resource "aws_sqs_queue" "search_ai_enrichment" {
  count = var.enable_search_ai_enrichment ? 1 : 0

  name                       = local.search_ai_enrichment_queue_name
  visibility_timeout_seconds = var.search_ai_enrichment_queue_visibility_timeout
```

```hcl
# infra/terraform/foundation/search_ai_enrichment.tf
resource "aws_lambda_function" "search_ai_enricher" {
  count = var.enable_search_ai_enrichment ? 1 : 0

  function_name    = local.search_ai_enrichment_lambda_name
  role             = aws_iam_role.search_ai_enricher_lambda[0].arn
```

발표 멘트:

다섯 번째 포인트는 플랫폼 리소스를 고정 구조로 박아 넣지 않고,
옵션형으로 확장할 수 있게 설계했다는 점입니다.
예를 들어 Kafka는 필요하면 MSK로 갈 수 있고,
검색은 OpenSearch로 확장할 수 있고,
Search AI enrichment도 SQS와 Lambda 조합을 옵션처럼 켤 수 있게 만들었습니다.
즉, 현재 필요한 인프라만 먼저 쓰고,
서비스가 커지면 같은 Terraform 구조 안에서 managed 서비스나 추가 플랫폼으로 확장할 수 있도록 열어둔 것이 특징입니다.

## 발표 마무리 멘트

정리하면,
우리 Terraform 인프라는 단순히 AWS 리소스를 생성하는 수준이 아니라,
Foundation과 EKS의 계층 분리,
stateful 계층 분리,
private 중심 네트워크,
EKS 하드닝과 IRSA,
그리고 옵션형 플랫폼 확장까지 함께 고려해서 설계했습니다.
즉, 처음부터 "배포만 되는 인프라"가 아니라
"운영과 확장을 감당할 수 있는 플랫폼"을 만드는 방향으로 구현했다고 설명하시면 됩니다.
