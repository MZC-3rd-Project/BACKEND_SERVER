package com.example.search.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "search.elasticsearch")
public class SearchElasticsearchProperties {

    private String uris = "http://localhost:9200";
    private String indexName = "items";

    public String primaryUri() {
        if (!StringUtils.hasText(uris)) {
            return "http://localhost:9200";
        }

        String[] candidates = StringUtils.commaDelimitedListToStringArray(uris);
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate.trim();
            }
        }
        return "http://localhost:9200";
    }
}
