package com.example.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway.auth")
public class GatewayAuthProperties {

    private boolean enabled = false;
    private String authServiceUrl = "http://localhost:8081";
    private String userServiceUrl = "http://localhost:8082";
}
