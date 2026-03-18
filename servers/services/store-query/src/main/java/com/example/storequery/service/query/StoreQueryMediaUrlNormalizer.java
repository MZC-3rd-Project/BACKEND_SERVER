package com.example.storequery.service.query;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;

@Component
public class StoreQueryMediaUrlNormalizer {

    private final String cloudFrontDomain;

    public StoreQueryMediaUrlNormalizer(@Value("${MEDIA_CLOUDFRONT_DOMAIN:}") String cloudFrontDomain) {
        this.cloudFrontDomain = trimTrailingSlash(cloudFrontDomain);
    }

    public String normalize(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            return rawUrl;
        }
        if (!StringUtils.hasText(cloudFrontDomain)) {
            return rawUrl;
        }

        String trimmed = rawUrl.trim();
        if (trimmed.startsWith(cloudFrontDomain + "/")) {
            return trimmed;
        }

        try {
            URI uri = URI.create(trimmed);
            String host = uri.getHost();
            String path = uri.getRawPath();
            if (!StringUtils.hasText(host) || !StringUtils.hasText(path)) {
                return trimmed;
            }
            if (!host.contains(".s3.") || !host.endsWith(".amazonaws.com")) {
                return trimmed;
            }

            StringBuilder normalized = new StringBuilder(cloudFrontDomain).append(path);
            if (StringUtils.hasText(uri.getRawQuery())) {
                normalized.append('?').append(uri.getRawQuery());
            }
            if (StringUtils.hasText(uri.getRawFragment())) {
                normalized.append('#').append(uri.getRawFragment());
            }
            return normalized.toString();
        } catch (IllegalArgumentException ignored) {
            return trimmed;
        }
    }

    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
