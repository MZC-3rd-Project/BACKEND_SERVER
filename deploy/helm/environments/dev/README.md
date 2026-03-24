# Dev Values

Place one values file per runtime service in this directory.

Suggested naming:

- `client-gateway.yaml`
- `edge-nginx.yaml`
- `keycloak.yaml`
- `auth.yaml`
- `profile.yaml`
- `search.yaml`
- `product.yaml`
- `stock.yaml`
- `funding.yaml`
- `sales.yaml`
- `hot-deal.yaml`
- `order.yaml`
- `payment.yaml`
- `review.yaml`
- `store.yaml`
- `store-query.yaml`
- `notification.yaml`
- `chat.yaml`
- `media-api.yaml`
- `media-worker.yaml`
- `analytics-dashboard.yaml`
- `cart.yaml`

Each values file should map to one entry in `deploy/catalog/runtime-services.yaml`.

These files are intended for local/private environment management. If GitHub-based deploy
automation is enabled later, keep runtime secrets in AWS Secrets Manager and only commit
public-safe examples or sanitized values files.

`edge-nginx.yaml` is reserved for the EKS connection-management demo edge that fronts
`client-gateway` behind a dedicated ALB host.

When gateway and Keycloak share one public hostname through CloudFront or another edge,
route `/realms/*` to Keycloak and `/login/oauth2/*` plus application/API paths to
`client-gateway`. OAuth issuer, redirect URI, and Keycloak `KC_HOSTNAME` must all point to
the same externally visible host in that topology.

To generate a focused deploy plan for the demo, run:

```bash
DEPLOY_TARGETS=client-gateway,edge-nginx \
AWS_DEFAULT_REGION=ap-northeast-2 \
ACCOUNT_ID=<aws-account-id> \
ruby scripts/ci/export-eks-deploy-plan.rb
```
