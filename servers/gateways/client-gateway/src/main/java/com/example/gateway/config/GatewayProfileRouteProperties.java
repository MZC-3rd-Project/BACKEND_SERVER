package com.example.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway.profile-route")
public class GatewayProfileRouteProperties {

    private boolean enabled = false;
    private Long dummyUserId;
}
