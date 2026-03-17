# Keycloak Realm Example

`don-moa-realm.eks.example.json` is a sanitized EKS-oriented example generated from:

- `docker/keycloak/don-moa-realm.json`

It intentionally keeps the realm, role, and client structure, but replaces local-only values.

Sanitized fields:

- `don-moa-admin.secret`
  - `__KC_ADMIN_CLIENT_SECRET__`
- `don-moa-gateway.secret`
  - `__GATEWAY_OAUTH2_CLIENT_SECRET__`
- `don-moa-gateway.redirectUris`
  - `__KEYCLOAK_GATEWAY_REDIRECT_URI__`
- `don-moa-gateway.webOrigins`
  - `__KEYCLOAK_GATEWAY_WEB_ORIGIN__`
- `don-moa-gateway.attributes.post.logout.redirect.uris`
  - `__KEYCLOAK_GATEWAY_POST_LOGOUT_REDIRECT_URI__`

Notes:

- This file is an example input, not a live secret templating mechanism.
- Current dev Helm values inject the placeholder values during container startup and launch Keycloak with `--import-realm`.
- If you change the example file, also sync `deploy/helm/charts/spring-service/files/keycloak/don-moa-realm.eks.example.json`.
