package com.example.gateway.config;

import com.example.contracts.http.HttpHeaderNames;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway.security")
public class GatewaySecurityProperties {

    private String internalAuthHeader = HttpHeaderNames.GATEWAY_AUTH;
    private String internalAuthToken = "";
    private List<String> relayPathPrefixes = List.of(
            "/bff/v1",
            "/api/v1/search",
            "/api/v1/media",
            "/api/v1/chat",
            "/ws/chat",
            "/api/products",
            "/api/goods",
            "/api/performances",
            "/api/items",
            "/api/categories",
            "/api/campaigns",
            "/api/v1/sales",
            "/api/v1/hot-deals",
            "/api/v1/notifications"
    );
    private List<String> requireAuthPathPrefixes = List.of("/api/v1/chat", "/ws/chat");
    private List<String> requireAuthWritePathPrefixes = List.of(
            "/bff/v1",
            "/api/v1/media",
            "/api/products",
            "/api/goods",
            "/api/performances",
            "/api/items",
            "/api/categories",
            "/api/campaigns",
            "/api/v1/sales",
            "/api/v1/hot-deals",
            "/api/v1/notifications"
    );
}
