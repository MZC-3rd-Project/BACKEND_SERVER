# Dev Values

Place one values file per runtime service in this directory.

Suggested naming:

- `client-gateway.yaml`
- `keycloak.yaml`
- `auth.yaml`
- `profile.yaml`
- `product.yaml`
- `stock.yaml`
- `funding.yaml`
- `sales.yaml`
- `hot-deal.yaml`
- `order.yaml`
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
