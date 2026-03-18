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
    private boolean allowClientIdentityHeaders = false;
    private boolean allowClientSignedContextHeader = false;
    private List<String> relayPathPrefixes = List.of(
            "/bff/v1",
            "/api/store",
            "/api/v1/cart",
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
            "/api/v1/notifications",
            "/api/v1/orders"
    );
    private List<String> requireAuthPathPrefixes = List.of("/api/v1/chat", "/ws/chat", "/api/v1/cart");
    private List<String> requireAuthWritePathPrefixes = List.of(
            "/bff/v1",
            "/api/store",
            "/api/v1/cart",
            "/api/v1/media",
            "/api/products",
            "/api/goods",
            "/api/performances",
            "/api/items",
            "/api/categories",
            "/api/campaigns",
            "/api/v1/sales",
            "/api/v1/hot-deals",
            "/api/v1/notifications",
            "/api/v1/orders"
    );
}
