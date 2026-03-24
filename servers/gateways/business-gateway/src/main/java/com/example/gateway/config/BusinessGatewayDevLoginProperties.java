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
public class BusinessGatewayDevLoginProperties {

    private boolean enabled = false;
    private String username = "seller";
    private String password = "seller123";
    private Long userId = 9000002L;
    private List<String> roles = List.of("USER", "SELLER");
    private String sessionIdPrefix = "dev-login-biz";
}
