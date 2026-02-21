package com.example.security.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.gateway-security")
public class GatewaySecurityModuleProperties extends GatewayHeaderValidationBaseProperties {

    private boolean enabled = false;
    private List<String> requiredPaths = new ArrayList<>();
    private List<String> optionalUserContextPaths = new ArrayList<>();
    private List<String> excludedPaths = List.of("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**");
    private String gatewayAuthErrorCode = "GW-AUTH-001";
    private String userContextErrorCode = "GW-AUTH-002";
    private int filterOrder = Ordered.HIGHEST_PRECEDENCE;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getRequiredPaths() {
        return requiredPaths;
    }

    public void setRequiredPaths(List<String> requiredPaths) {
        this.requiredPaths = requiredPaths == null ? new ArrayList<>() : requiredPaths;
    }

    public List<String> getOptionalUserContextPaths() {
        return optionalUserContextPaths;
    }

    public void setOptionalUserContextPaths(List<String> optionalUserContextPaths) {
        this.optionalUserContextPaths = optionalUserContextPaths == null ? new ArrayList<>() : optionalUserContextPaths;
    }

    public List<String> getExcludedPaths() {
        return excludedPaths;
    }

    public void setExcludedPaths(List<String> excludedPaths) {
        this.excludedPaths = excludedPaths == null ? List.of() : excludedPaths;
    }

    public String getGatewayAuthErrorCode() {
        return gatewayAuthErrorCode;
    }

    public void setGatewayAuthErrorCode(String gatewayAuthErrorCode) {
        this.gatewayAuthErrorCode = gatewayAuthErrorCode;
    }

    public String getUserContextErrorCode() {
        return userContextErrorCode;
    }

    public void setUserContextErrorCode(String userContextErrorCode) {
        this.userContextErrorCode = userContextErrorCode;
    }

    public int getFilterOrder() {
        return filterOrder;
    }

    public void setFilterOrder(int filterOrder) {
        this.filterOrder = filterOrder;
    }
}
