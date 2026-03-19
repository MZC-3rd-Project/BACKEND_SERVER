# CI/CD Setup Checklist (GitHub Actions + CodePipeline)

This document lists exactly what must be prepared in AWS and GitHub to run this repository with:

- CI: GitHub Actions (`backend-ci.yml`)
- CD: GitHub Actions trigger -> AWS CodePipeline (`trigger-codepipeline.yml`)

Reference files:

- GitHub Actions settings example: `deploy/catalog/github-actions-settings.example.yaml`
- Selective deploy service map example: `deploy/catalog/github-actions-dev-service-map.example.json`
- Runtime secret inventory: `deploy/catalog/dev-secrets-inventory.yaml`
- EKS build stage buildspec: `buildspecs/dev-eks-build.yml`
- EKS deploy stage buildspec: `buildspecs/dev-eks-deploy.yml`
- EKS static validation script: `scripts/ci/validate-eks-runtime-inputs.sh`

## 1) GitHub Repository Settings (required)

### Repository Variables

Set these in `Settings -> Secrets and variables -> Actions -> Variables`:

- `AWS_REGION`: e.g. `ap-northeast-2`
- `AWS_CODEPIPELINE_DEV_NAME`: e.g. `donmoa-dev-eks-deploy`
- `AWS_CODEPIPELINE_PROD_NAME`: e.g. `donmoa-prod-eks-deploy`
- `AWS_PROD_CD_ENABLED`: default `false`, switch to `true` when prod manual dispatch should be allowed
- `AWS_CODEPIPELINE_DEV_SERVICE_MAP` (optional JSON):
  - if provided, changed-service selective deployment is enabled on the branch watched by `trigger-codepipeline.yml`
  - if missing, the default dev pipeline falls back to `AWS_CODEPIPELINE_DEV_NAME`
  - for current EKS migration draft, use `deploy/catalog/github-actions-dev-service-map.example.json` as the base template

### Repository Secrets

Set these in `Settings -> Secrets and variables -> Actions -> Secrets`:

- `AWS_DEPLOY_ROLE_ARN`: IAM role ARN assumed by GitHub OIDC
  - Example: `arn:aws:iam::123456789012:role/donmoa-github-actions-deploy-role`

Do not put runtime application secrets here. Database passwords, OAuth client secrets,
internal auth tokens, and similar values should stay in AWS Secrets Manager and be synced
to pods through `ExternalSecret`.

## 2) AWS Team Handoff (must provide to app team)

App team cannot complete CD bootstrap without these:

- AWS account ID used for deployment
- OIDC-assumable role ARN for GitHub Actions
- CodePipeline names (dev/prod)
- Confirmation that branch mapping is finalized:
  - current workflow file: `prod` push -> branch-triggered pipeline
  - recommended after final activation: `develop` push -> dev pipeline (auto trigger)
  - prod pipeline -> manual dispatch only
- Confirmation that all naming uses `donmoa-` prefix

## 3) AWS IAM/OIDC Requirements

### 3.1 GitHub OIDC provider

In AWS account, ensure OIDC provider exists:

- Provider URL: `https://token.actions.githubusercontent.com`
- Audience: `sts.amazonaws.com`

### 3.2 IAM role trust policy

`AWS_DEPLOY_ROLE_ARN` role trust policy must allow this repository to assume role.
Example condition values to adjust:

- Repository: `ddingjoo/3rdProject`
- Allowed refs must match the workflow branch filter exactly
  - current checked-in workflow: `refs/heads/prod`
  - recommended after dev auto-deploy activation: `refs/heads/develop`
  - `refs/tags/*` (optional, release workflow 용도)

Minimal trust policy example:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::<ACCOUNT_ID>:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
        },
        "StringLike": {
          "token.actions.githubusercontent.com:sub": [
            "repo:ddingjoo/3rdProject:ref:refs/heads/prod"
          ]
        }
      }
    }
  ]
}
```

### 3.3 IAM role permissions

Role needs at least:

- `codepipeline:StartPipelineExecution`
- `codepipeline:GetPipeline`
- `codepipeline:GetPipelineExecution`
- `codepipeline:GetPipelineState`

Scoped to dev/prod pipeline ARNs only (least privilege).

## 4) CodePipeline Side Requirements

Each pipeline (dev/prod) should already include:

- Source stage (GitHub connection or artifact handoff)
- Build stage (CodeBuild or equivalent)
  - EKS target: `buildspecs/dev-eks-build.yml`
- Deploy stage
  - current baseline: ECS deployment
  - EKS migration target: `helm upgrade --install` + rollout checks
  - EKS target: `buildspecs/dev-eks-deploy.yml`

If your pipeline also builds/pushes images, ensure:

- ECR repositories exist with `donmoa-` naming convention
- Build role has ECR push permission
- ECS/EKS runtime role can pull ECR images
- EKS deploy image has `aws`, `helm`, `kubectl`, `ruby` installed

## 5) Workflow Behavior in This Repo

### `backend-ci.yml`

- Trigger: PR/push (`develop`, `main`) and manual dispatch
- Action: runs `./gradlew test --no-daemon`

### `trigger-codepipeline.yml`

- Trigger:
  - current checked-in workflow: push to `prod`
  - manual dispatch with `dev|prod` target
- Action:
  - assumes `AWS_DEPLOY_ROLE_ARN` via OIDC
  - auto-detects changed services on branch push and triggers mapped pipelines
  - falls back to full dev pipeline when shared modules/build config changed
  - starts matching CodePipeline execution(s)
  - if target is prod and `AWS_PROD_CD_ENABLED != true`, workflow exits safely without trigger

If dev auto-deploy should run on `develop`, update `.github/workflows/trigger-codepipeline.yml`
branch filters before activation.

## 6) What You Need To Tell Me Before Final Activation

Share these values to finish end-to-end activation:

- `AWS_REGION`
- `AWS_DEPLOY_ROLE_ARN`
- `AWS_CODEPIPELINE_DEV_NAME`
- `AWS_CODEPIPELINE_PROD_NAME`
- final default auto-deploy branch (`prod` currently, `develop` recommended)
- whether prod manual dispatch should stay blocked (`AWS_PROD_CD_ENABLED=false`) until 발표 주간

## 7) Recommended Safety Controls

- GitHub branch protection on `develop` and `main`
- Require CI pass before merge
- AWS Budget alerts (monthly 500,000 KRW guardrail as discussed)
- CloudWatch log retention policy per service
