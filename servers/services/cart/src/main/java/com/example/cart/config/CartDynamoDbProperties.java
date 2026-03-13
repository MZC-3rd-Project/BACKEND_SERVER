package com.example.cart.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "cart.dynamodb")
public class CartDynamoDbProperties {

    private String region = "ap-northeast-2";
    private String endpoint;
    private String tableName = "cart_items";
    private String credentialProfile;
    private String accessKey;
    private String secretKey;
}
