package com.example.search.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "search.ai-enrichment")
public class SearchAiEnrichmentProperties {

    private boolean enabled = false;
    private String queueUrl = "";
    private String awsRegion = "ap-northeast-2";
}
