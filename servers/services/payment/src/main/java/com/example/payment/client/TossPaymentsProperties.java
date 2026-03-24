package com.example.payment.client;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.tosspayments")
public class TossPaymentsProperties {

    private String secretKey;
    private String baseUrl = "https://api.tosspayments.com";
    private int confirmTimeoutSeconds = 30;
}
