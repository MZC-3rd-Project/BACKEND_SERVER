# EKS Platform Addons

This directory contains the base Helm values for the platform controllers required by the EKS runtime:

- `aws-load-balancer-controller`
- `argo-rollouts`
- `external-secrets`
- `metrics-server`

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

## Rollback

```bash
helm uninstall aws-load-balancer-controller -n donmoa-system
helm uninstall argo-rollouts -n donmoa-system
helm uninstall external-secrets -n donmoa-system
helm uninstall metrics-server -n donmoa-system
kubectl delete clustersecretstore aws-secretsmanager
kubectl delete namespace donmoa-system
```
