# AWS ECS Deployment Playbook (App Team)

This playbook covers two paths:

- external handoff: platform/security team provides foundation
- self-provision: foundation is created in this repository

## 1) Responsibility Split

### Platform/Security team (external handoff)

- Budget alerts / IAM baseline
- VPC, subnets, route tables, NAT
- ECS cluster
- Task execution role / task role
- Security groups
- RDS / Redis / Kafka / Elasticsearch / Keycloak endpoints
- Secrets Manager entries and ARNs

### App team (this repo)

- Build and push service images to ECR
- Define service env/secrets mapping
- Apply Terraform in `infra/terraform/ecs-app`
- Run smoke checks and integration verification

### Self-provision path (same repo)

- Apply Terraform in `infra/terraform/foundation`
- Use foundation outputs as `ecs-app` inputs

## 2) Inputs Required Before Terraform Apply

Collect these values first:

- `cluster_arn`
- `subnet_ids` (private)
- `security_group_ids`
- `task_execution_role_arn`
- `task_role_arn`
- target group ARN for internet-facing services (at least gateway)
- service endpoints:
  - RDS (writer endpoint)
  - RDS reader endpoint (for read-only routing)
  - Redis
  - Kafka bootstrap servers
  - Elasticsearch endpoint
  - Keycloak URL
- Secret ARNs for sensitive env vars

If one of these is missing, block deployment and request handoff completion.

If you run self-provision path:

- first apply `infra/terraform/foundation`
- then map foundation outputs to this stack inputs

Read/write routing rollout keys:

- `APP_DATASOURCE_READ_WRITE_ROUTING_ENABLED=true`
- `APP_DATASOURCE_READ_URL` (or fallback to write when omitted)
- `APP_DATASOURCE_READ_USERNAME` (optional, fallback to `DB_USERNAME`)
- `APP_DATASOURCE_READ_PASSWORD` (optional, fallback to `DB_PASSWORD`)

Activation strategy (recommended):

- local dev: do not set `APP_DATASOURCE_READ_WRITE_ROUTING_ENABLED` (single write DB as-is)
- ECS: set above keys only for rollout targets (e.g. `product`, `funding`, `search`, `hot-deal`, `analytics-dashboard`)
- non-target services remain unchanged and continue with single datasource

## 3) Terraform Package in This Repo

- Path: `infra/terraform/ecs-app`
- Sample backend: `infra/terraform/ecs-app/backend.hcl.example`
- Sample vars: `infra/terraform/ecs-app/terraform.tfvars.example`
- Foundation path: `infra/terraform/foundation`

Naming rule:

- All created resources use `donmoa-` prefix.
- Set `name_prefix` as `donmoa` (or `donmoa-*`), never `project03` style.

## 4) Deployment Procedure

```bash
# Option A: self-provision foundation first
cd infra/terraform/foundation
cp backend.hcl.example backend.hcl
cp terraform.tfvars.example terraform.tfvars
terraform init -backend-config=backend.hcl
terraform plan -var-file=terraform.tfvars
terraform apply -var-file=terraform.tfvars

# Option B: deploy app layer
cd infra/terraform/ecs-app
cp backend.hcl.example backend.hcl
cp terraform.tfvars.example terraform.tfvars

# Fill real values in backend.hcl and terraform.tfvars
# (or copy from foundation outputs)

terraform init -backend-config=backend.hcl
terraform plan -var-file=terraform.tfvars
terraform apply -var-file=terraform.tfvars
```

Unified runner (foundation -> app):

```bash
./scripts/terraform-bootstrap-all.sh plan
./scripts/terraform-bootstrap-all.sh apply
```

Foundation output mapping (minimum):

- `ecs_cluster_arn` -> `cluster_arn`
- `private_subnet_ids` -> `subnet_ids`
- `ecs_service_security_group_id` -> `security_group_ids`
- `ecs_task_execution_role_arn` -> `task_execution_role_arn`
- `ecs_task_role_arn` -> `task_role_arn`
- `gateway_target_group_arn` -> gateway `target_group_arn`
- `aurora_writer_endpoint`/`aurora_reader_endpoint` -> DB endpoints
- `redis_primary_endpoint` -> `REDIS_HOST`

Helper script:

```bash
./scripts/generate-ecs-app-foundation-inputs.sh
```

Aurora domain DB bootstrap:

```bash
export AURORA_HOST="<foundation aurora_writer_endpoint>"
export AURORA_USER="postgres"
export AURORA_PASSWORD="<aurora password>"
./scripts/bootstrap-aurora-domain-databases.sh
```

## 5) Recommended Rollout Order

1. `client-gateway`
2. `keycloak`
3. `product`
4. `stock`
5. `search`
6. `sales`
7. `funding`
8. `hot-deal`
9. support services (`notification`, `analytics-dashboard`, `chat`, `profile`, `auth`, `media-*`)

## 6) Env/Secrets Reference

- Full generated reference: `infra/terraform/ecs-app/ENV_VARS_REFERENCE.md`
- Regenerate after config changes:

```bash
./scripts/generate-ecs-env-reference.sh
```

## 7) CI/CD Setup

- GitHub Actions + CodePipeline setup checklist:
  - `infra/terraform/ecs-app/CICD_SETUP_CHECKLIST.md`
- Required GitHub Actions workflows:
  - `.github/workflows/backend-ci.yml`
  - `.github/workflows/trigger-codepipeline.yml`
- Selective deploy:
  - set `AWS_CODEPIPELINE_DEV_SERVICE_MAP` to trigger only changed domain/service pipelines on `develop` push.
  - when shared code changes (`libs/*`, `gradle/*`, root build files), workflow falls back to full dev pipeline.
- Safety gate:
  - keep `AWS_PROD_CD_ENABLED=false` until you intentionally allow prod manual dispatch.

## 8) Smoke Verification

Minimum checks after each rollout batch:

- `GET /actuator/health` on updated services
- Gateway through-path checks:
  - product read API
  - stock read API
  - search query API
- Event chain sanity:
  - create item -> Kafka publish -> search index update

## 9) Rollback Criteria

Rollback immediately if any persists over agreed threshold:

- error rate spike on gateway or core services
- sustained timeout increase
- Kafka lag keeps increasing without recovery trend

Rollback method:

- revert image tag in `terraform.tfvars`
- `terraform apply` again

## 10) Common Pitfalls

- Missing shared auth token env for internal gateway header validation
- Inconsistent Kafka bootstrap servers between services
- Secret value put directly in tfvars instead of secret ARN
- service port mismatch between `container_port` and app `SERVER_PORT`
