package com.example.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway.oidc")
public class BusinessGatewayOidcProperties {

    private String issuerUri = "http://keycloak.internal:8080/realms/don-moa";
    private String clientId = "don-moa-business-gateway";
    private String clientSecret = "";
    private String clientAuthenticationMethod = "client_secret_basic";
    private String scopes = "openid,profile,email";
    private String redirectUri = "http://localhost:18081/login/oauth2/code/keycloak";
    private String loginSuccessUrl = "/";
    private String loginFailureUrl = "/auth/login?error=login_failed";

    public String authorizationEndpoint() {
        return appendPath("/protocol/openid-connect/auth");
    }

    public String tokenEndpoint() {
        return appendPath("/protocol/openid-connect/token");
    }

    private String appendPath(String suffix) {
        return issuerUri.endsWith("/")
                ? issuerUri.substring(0, issuerUri.length() - 1) + suffix
                : issuerUri + suffix;
    }
}
