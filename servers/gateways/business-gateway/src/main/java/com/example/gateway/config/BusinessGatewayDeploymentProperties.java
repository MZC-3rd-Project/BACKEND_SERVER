package com.example.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway.deployment")
public class BusinessGatewayDeploymentProperties {

    private boolean failFastEnabled = true;
    private List<String> enforcedProfiles = List.of("prod", "production", "stg", "stage");
    private boolean requireExplicitAuthSessionEnv = true;
    private boolean requireAuthOrSessionEnabled = true;
}
