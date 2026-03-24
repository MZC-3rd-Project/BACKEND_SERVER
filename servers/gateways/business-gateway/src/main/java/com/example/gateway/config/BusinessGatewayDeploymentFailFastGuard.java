package com.example.gateway.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class BusinessGatewayDeploymentFailFastGuard implements ApplicationRunner {

    private static final String AUTH_ENV = "GATEWAY_AUTH_ENABLED";
    private static final String SESSION_ENV = "GATEWAY_SESSION_ENABLED";

    private final BusinessGatewayDeploymentProperties deploymentProperties;
    private final BusinessGatewayAuthProperties authProperties;
    private final BusinessGatewaySessionProperties sessionProperties;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        if (!deploymentProperties.isFailFastEnabled()) {
            return;
        }
        if (!isEnforcedProfileActive()) {
            return;
        }

        if (deploymentProperties.isRequireExplicitAuthSessionEnv()) {
            requireExplicitEnvFlag(AUTH_ENV);
            requireExplicitEnvFlag(SESSION_ENV);
        }

        if (deploymentProperties.isRequireAuthOrSessionEnabled()
                && !authProperties.isEnabled()
                && !sessionProperties.isEnabled()) {
            throw new IllegalStateException(
                    "Business gateway deployment guard blocked startup: both gateway.auth.enabled and gateway.session.enabled are false"
            );
        }

        log.info("Business gateway deployment guard passed. profiles={}, authEnabled={}, sessionEnabled={}",
                Arrays.toString(environment.getActiveProfiles()),
                authProperties.isEnabled(),
                sessionProperties.isEnabled());
    }

    private boolean isEnforcedProfileActive() {
        Set<String> activeProfiles = Arrays.stream(environment.getActiveProfiles())
                .filter(StringUtils::hasText)
                .map(this::normalize)
                .collect(Collectors.toSet());

        if (activeProfiles.isEmpty()) {
            return false;
        }

        Set<String> enforcedProfiles = deploymentProperties.getEnforcedProfiles().stream()
                .filter(StringUtils::hasText)
                .map(this::normalize)
                .collect(Collectors.toSet());

        return activeProfiles.stream().anyMatch(enforcedProfiles::contains);
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private void requireExplicitEnvFlag(String envName) {
        String envValue = System.getenv(envName);
        String systemPropertyValue = System.getProperty(envName);
        if (!StringUtils.hasText(envValue) && !StringUtils.hasText(systemPropertyValue)) {
            throw new IllegalStateException(
                    "Business gateway deployment guard blocked startup: required env/system property is missing: " + envName
            );
        }
    }
}
