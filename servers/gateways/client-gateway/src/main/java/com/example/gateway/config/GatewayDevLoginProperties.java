package com.example.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway.dev-login")
public class GatewayDevLoginProperties {

    private boolean enabled = false;
    private String username = "test";
    private String password = "test123";
    private Long userId = 9000001L;
    private List<String> roles = List.of("USER", "BUYER", "SELLER");
    private String sessionIdPrefix = "dev-login";
}
