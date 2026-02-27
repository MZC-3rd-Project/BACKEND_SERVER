# ECS App Terraform (Application Layer)

This Terraform package deploys application ECS services on top of pre-provisioned foundation resources.

## Scope

This code manages:

- ECS task definitions
- ECS services
- CloudWatch log groups

This code does **not** create:

- VPC/Subnet/NAT/ALB base network
- ECS cluster
- IAM foundation roles
- RDS/Redis/Kafka/Elasticsearch/Keycloak infrastructure

Those resources can be provisioned by either:

- Platform/security owners
- This repository's `infra/terraform/foundation` stack

## Prerequisites from platform/security team

- `cluster_arn`
- `subnet_ids` (private subnets preferred)
- `security_group_ids`
- `service_discovery_namespace_id` (optional, Cloud Map private DNS namespace id)
- `task_execution_role_arn`
- `task_role_arn`
- `target_group_arn` per internet-facing service (e.g. gateway)
- Secrets Manager ARNs for sensitive values

If you provisioned foundation via this repository, map outputs from:

- `infra/terraform/foundation/outputs.tf`

Main mapping:

- `ecs_cluster_arn` -> `cluster_arn`
- `private_subnet_ids` -> `subnet_ids`
- `ecs_service_security_group_id` -> `security_group_ids`
- `ecs_task_execution_role_arn` -> `task_execution_role_arn`
- `ecs_task_role_arn` -> `task_role_arn`
- `gateway_target_group_arn` -> gateway `target_group_arn`

## Files

- `versions.tf` / `providers.tf`: Terraform/AWS provider version and region
- `variables.tf`: input contract
- `main.tf`: log groups + task definitions + services
- `outputs.tf`: service/task/log outputs
- `terraform.tfvars.example`: example for core commerce services
- `backend.hcl.example`: remote state backend sample

## Quick start

```bash
cd infra/terraform/ecs-app
terraform init -backend-config=backend.hcl
terraform plan -var-file=terraform.tfvars
terraform apply -var-file=terraform.tfvars
```

### Full Self-Provision Path (in this repo)

```bash
# 1) foundation
cd infra/terraform/foundation
terraform init -backend-config=backend.hcl
terraform apply -var-file=terraform.tfvars

# 2) app
cd ../ecs-app
terraform init -backend-config=backend.hcl
terraform apply -var-file=terraform.tfvars
```

## Naming convention

- All resource names must use `donmoa-` prefix.
- This package enforces `name_prefix` to start with `donmoa`.
- Recommended: `name_prefix = "donmoa"` and include environment in `environment` variable.

## Secret mapping rule

`services.<service>.secrets` expects ECS `valueFrom` string.

Examples:

- full secret ARN: `arn:aws:secretsmanager:...:secret:donmoa/common-AbCdE`
- JSON key in one secret: `arn:aws:secretsmanager:...:secret:donmoa/common-AbCdE:APP_SECURITY_CONTEXT_SIGNING_KEY::`

## Suggested rollout order

1. `client-gateway`
2. `keycloak`
3. `product`
4. `stock`
5. `search`
6. `sales`
7. `funding`
8. `hot-deal`
9. supporting services (`notification`, `analytics-dashboard`, `chat`, `profile`, `auth`, media)

## Notes

- `launch_type` supports `FARGATE` and `EC2`.
- `target_group_arn` is optional for internal-only services.
- Set `service_discovery_namespace_id` to auto-register each ECS service in Cloud Map.
- Keep secrets out of `.tfvars`; use ARNs only.
