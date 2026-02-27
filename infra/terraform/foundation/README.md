# Foundation Terraform (VPC + ECS + Data + Platform)

This package provisions the AWS foundation required before deploying application services.

## What This Stack Creates

- Network
  - VPC, public/private subnets, route tables
  - Internet Gateway
  - NAT gateway (single NAT by default for cost)
- Security
  - ALB / ECS service / DB / Redis security groups
- Compute base
  - ECS cluster
  - ECS task execution role and task role
- Ingress
  - Application Load Balancer
  - Gateway target group + HTTP listener
- Registry
  - ECR repositories (+ lifecycle policy)
- Data
  - Aurora PostgreSQL cluster (writer + reader)
  - ElastiCache Redis replication group
- Optional managed platform
  - EC2 single-node Kafka (`enable_ec2_kafka=true`)
  - EC2 single-node Elasticsearch (`enable_ec2_elasticsearch=true`)
  - Amazon MSK (`enable_msk=true`)
  - Amazon OpenSearch (`enable_opensearch=true`)

All resource names are prefixed with `donmoa-` by validation.

## Files

- `network.tf`: VPC/subnets/routes/NAT
- `security.tf`: SGs for ALB/ECS/DB/Redis
- `alb.tf`: ALB, listener, gateway target group
- `ecs.tf`: ECS cluster + IAM roles
- `ecr.tf`: ECR repositories and lifecycle policies
- `database.tf`: Aurora + Secrets Manager master credential
- `redis.tf`: ElastiCache Redis
- `kafka_ec2.tf`: optional EC2 single-node Kafka
- `elasticsearch_ec2.tf`: optional EC2 single-node Elasticsearch
- `platform_optional.tf`: optional MSK/OpenSearch
- `outputs.tf`: values consumed by `infra/terraform/ecs-app`

## Quick Start

```bash
cd infra/terraform/foundation
cp backend.hcl.example backend.hcl
cp terraform.tfvars.example terraform.tfvars

terraform init -backend-config=backend.hcl
terraform plan -var-file=terraform.tfvars
terraform apply -var-file=terraform.tfvars
```

Orchestrated run (foundation -> ecs-app):

```bash
./scripts/terraform-bootstrap-all.sh plan
./scripts/terraform-bootstrap-all.sh apply
```

## Apply Order

1. `foundation` apply
2. Create domain databases in Aurora
3. Fill `ecs-app/terraform.tfvars` with foundation outputs
4. `ecs-app` apply

## Domain Database Bootstrap (Aurora)

This stack creates one Aurora cluster. Domain-specific DBs should be created once after provisioning.

Script (run from bastion/SSM-connected host with PostgreSQL client):

```bash
export AURORA_HOST="<writer-endpoint>"
export AURORA_USER="postgres"
export AURORA_PASSWORD="<password>"
./scripts/bootstrap-aurora-domain-databases.sh
```

Manual SQL example:

```sql
CREATE DATABASE product_db;
CREATE DATABASE stock_db;
CREATE DATABASE sales_db;
CREATE DATABASE funding_db;
CREATE DATABASE hotdeal_db;
CREATE DATABASE search_db;
CREATE DATABASE analytics_db;
CREATE DATABASE auth_db;
CREATE DATABASE user_db;
CREATE DATABASE keycloak_db;
```

## Core Output Mapping To `ecs-app`

Use these outputs to fill `infra/terraform/ecs-app/terraform.tfvars`:

- `ecs_cluster_arn` -> `cluster_arn`
- `private_subnet_ids` -> `subnet_ids`
- `ecs_service_security_group_id` -> `security_group_ids[0]`
- `ecs_task_execution_role_arn` -> `task_execution_role_arn`
- `ecs_task_role_arn` -> `task_role_arn`
- `gateway_target_group_arn` -> `services["client-gateway"].target_group_arn`
- `aurora_writer_endpoint` -> `DB_URL` host
- `aurora_reader_endpoint` -> `APP_DATASOURCE_READ_URL` host
- `redis_primary_endpoint` -> `REDIS_HOST`
- `ec2_kafka_bootstrap_server` -> `KAFKA_BOOTSTRAP_SERVERS` (when `enable_ec2_kafka=true`)
- `ec2_elasticsearch_endpoint` -> `ELASTICSEARCH_URIS` (when `enable_ec2_elasticsearch=true`)
- `msk_bootstrap_brokers(_tls)` -> `KAFKA_BOOTSTRAP_SERVERS` (when `enable_msk=true`)
- `opensearch_endpoint` -> `ELASTICSEARCH_URIS` (when `enable_opensearch=true`)
- `service_discovery_namespace_id` -> `service_discovery_namespace_id` (for ECS Cloud Map registration)

You can generate a mapping template automatically:

```bash
./scripts/generate-ecs-app-foundation-inputs.sh
```

## Notes

- This stack intentionally does not create GitHub OIDC / CodePipeline itself.
  - Keep CI/CD setup in `infra/terraform/ecs-app/CICD_SETUP_CHECKLIST.md`.
- For cost control in dev, start with:
  - `single_nat_gateway=true`
  - `aurora_reader_instance_count=1`
  - `redis_num_cache_clusters=1`
  - `enable_msk=false`, `enable_opensearch=false`
