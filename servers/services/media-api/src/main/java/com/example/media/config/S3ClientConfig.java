package com.example.media.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.WebIdentityTokenFileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Slf4j
@Configuration
@EnableConfigurationProperties({MediaS3Properties.class, MediaCleanupProperties.class, MediaUrlProperties.class})
public class S3ClientConfig {

    @Bean
    public S3Client s3Client(MediaS3Properties properties) {
        return S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(awsCredentialsProvider())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(MediaS3Properties properties) {
        return S3Presigner.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(awsCredentialsProvider())
                .build();
    }

    private AwsCredentialsProvider awsCredentialsProvider() {
        String webIdentityTokenFile = System.getenv("AWS_WEB_IDENTITY_TOKEN_FILE");
        String roleArn = System.getenv("AWS_ROLE_ARN");
        if (StringUtils.hasText(webIdentityTokenFile) && StringUtils.hasText(roleArn)) {
            log.info("Using WebIdentityTokenFileCredentialsProvider for media S3 client. roleArn={}", roleArn);
            return WebIdentityTokenFileCredentialsProvider.create();
        }
        log.info("Using DefaultCredentialsProvider for media S3 client");
        return DefaultCredentialsProvider.create();
    }
}
