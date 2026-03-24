package com.example.search.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.WebIdentityTokenFileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

@Slf4j
@Configuration
public class SearchAwsClientConfig {

    @Bean
    @ConditionalOnProperty(prefix = "search.ai-enrichment", name = "enabled", havingValue = "true")
    public SqsClient sqsClient(SearchAiEnrichmentProperties properties) {
        return SqsClient.builder()
                .region(Region.of(properties.getAwsRegion()))
                .credentialsProvider(awsCredentialsProvider())
                .build();
    }

    private AwsCredentialsProvider awsCredentialsProvider() {
        String webIdentityTokenFile = System.getenv("AWS_WEB_IDENTITY_TOKEN_FILE");
        String roleArn = System.getenv("AWS_ROLE_ARN");
        if (StringUtils.hasText(webIdentityTokenFile) && StringUtils.hasText(roleArn)) {
            log.info("Using WebIdentityTokenFileCredentialsProvider for search SQS client. roleArn={}", roleArn);
            return WebIdentityTokenFileCredentialsProvider.create();
        }
        log.info("Using DefaultCredentialsProvider for search SQS client");
        return DefaultCredentialsProvider.create();
    }
}
