package com.example.cart.config;

import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;

@Configuration
@EnableConfigurationProperties({CartDynamoDbProperties.class, CartPolicyProperties.class})
public class CartDynamoDbConfig {

    @Bean
    public DynamoDbClient dynamoDbClient(CartDynamoDbProperties properties) {
        DynamoDbClientBuilder builder = DynamoDbClient.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(resolveCredentialsProvider(properties));

        if (StringUtils.hasText(properties.getEndpoint())) {
            builder.endpointOverride(URI.create(properties.getEndpoint()));
        }

        return builder.build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }

    private AwsCredentialsProvider resolveCredentialsProvider(CartDynamoDbProperties properties) {
        if (StringUtils.hasText(properties.getAccessKey()) && StringUtils.hasText(properties.getSecretKey())) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(
                    properties.getAccessKey(),
                    properties.getSecretKey()
            ));
        }

        if (StringUtils.hasText(properties.getCredentialProfile())) {
            return ProfileCredentialsProvider.builder()
                    .profileName(properties.getCredentialProfile())
                    .build();
        }

        if (StringUtils.hasText(properties.getEndpoint())) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local"));
        }

        return DefaultCredentialsProvider.create();
    }
}
