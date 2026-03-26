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
            "/api/v1/auth",
            "/api/profile",
            "/api/v1/users",
            "/api/users",
            "/api/store",
            "/api/v1/store-query",
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
            "/api/v1/orders",
            "/api/v1/payments",
            "/api/v1/reviews"
    );
    private List<String> requireAuthPathPrefixes = List.of(
            "/api/profile",
            "/api/v1/users",
            "/api/users",
            "/api/v1/chat",
            "/ws/chat",
            "/api/v1/cart"
    );
    private List<String> requireAuthWritePathPrefixes = List.of(
            "/api/profile",
            "/api/v1/users",
            "/api/users",
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
            "/api/v1/orders",
            "/api/v1/payments",
            "/api/v1/reviews"
    );
}
