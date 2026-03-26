# EKS Platform Addons

This directory contains the base Helm values for the platform controllers required by the EKS runtime:

- `aws-load-balancer-controller`
- `argo-rollouts`
- `external-secrets`
- `metrics-server`
- `loki`
- `alloy`

Dynamic values such as cluster name, VPC ID, AWS region, and IRSA role ARNs are injected by
`scripts/ci/install-eks-platform-addons.sh`.

## Rollout

```bash
bash ./scripts/ci/install-eks-platform-addons.sh
```

The script will:

1. Create the `donmoa-system` namespace when missing.
2. Install/upgrade the four controllers with pinned chart versions.
3. Apply `ClusterSecretStore/aws-secretsmanager`.

## Observability Rollout

Loki and Alloy are installed separately because Loki requires an S3 bucket and a dedicated IRSA role.

Base values:

- `loki-values.yaml`
- `alloy-values.yaml`

Rollout:

```bash
AWS_PROFILE=mzc \
AWS_DEFAULT_REGION=ap-northeast-2 \
LOKI_S3_BUCKET=<bucket-name> \
LOKI_S3_ROLE_ARN=<iam-role-arn> \
bash ./deploy/helm/addons/install-observability-stack.sh
```

The script will:

1. Ensure the `donmoa-system` namespace exists.
2. Install Loki in `SingleBinary` mode backed by S3.
3. Install Alloy as a lightweight Kubernetes log shipper.

## Rollback

```bash
helm uninstall aws-load-balancer-controller -n donmoa-system
helm uninstall argo-rollouts -n donmoa-system
helm uninstall external-secrets -n donmoa-system
helm uninstall metrics-server -n donmoa-system
kubectl delete clustersecretstore aws-secretsmanager
kubectl delete namespace donmoa-system
```
