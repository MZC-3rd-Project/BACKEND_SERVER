package com.example.cart.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "cart.policy")
public class CartPolicyProperties {

    private long ttlDays = 30;
}
