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
public class BusinessGatewaySecurityProperties {

    private String internalAuthHeader = HttpHeaderNames.GATEWAY_AUTH;
    private String internalAuthToken = "";
    private boolean allowClientIdentityHeaders = false;
    private boolean allowClientSignedContextHeader = false;
    private List<String> relayPathPrefixes = List.of(
            "/api/products",
            "/api/goods",
            "/api/performances",
            "/api/items",
            "/api/categories",
            "/api/store",
            "/api/campaigns",
            "/api/v1/hot-deals"
    );
    private List<String> requireAuthPathPrefixes = List.of();
    private List<String> requireAuthWritePathPrefixes = List.of(
            "/api/products",
            "/api/goods",
            "/api/performances",
            "/api/items",
            "/api/categories",
            "/api/store",
            "/api/campaigns",
            "/api/v1/hot-deals"
    );
}
