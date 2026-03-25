package com.example.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway.oidc")
public class GatewayOidcProperties {

    private String issuerUri = "http://keycloak.internal:8080/realms/don-moa";
    private String clientId = "don-moa-gateway";
    private String clientSecret = "";
    private String clientAuthenticationMethod = "client_secret_basic";
}
