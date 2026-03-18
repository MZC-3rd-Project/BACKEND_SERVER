package com.example.funding.service.query;

import com.example.funding.dto.campaign.response.FundingRewardOptionResponse;
import com.example.security.gateway.GatewaySecurityModuleProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class FundingRewardOptionResolver {

    private final WebClient webClient;
    private final GatewaySecurityModuleProperties securityProperties;

    public FundingRewardOptionResolver(
            WebClient.Builder webClientBuilder,
            GatewaySecurityModuleProperties securityProperties,
            @Value("${app.service.product-url:http://localhost:8084}") String productServiceUrl
    ) {
        this.webClient = webClientBuilder.baseUrl(productServiceUrl).build();
        this.securityProperties = securityProperties;
    }

    public List<FundingRewardOptionResponse> resolveByItemId(Long itemId) {
        if (itemId == null || itemId <= 0L) {
            return List.of();
        }

        try {
            JsonNode response = webClient.get()
                    .uri("/api/products/{itemId}", itemId)
                    .headers(this::applyInternalHeaders)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.path("success").asBoolean(false)) {
                return List.of();
            }

            JsonNode data = response.path("data");
            JsonNode options = data.path("options");
            if (!options.isArray() || options.isEmpty()) {
                return List.of();
            }

            Long basePrice = positiveLong(data.path("price"));
            String shippingText = textOrNull(data.path("shippingInfo").path("shippingNotice"));
            List<FundingRewardOptionResponse> rewardOptions = new ArrayList<>();
            for (JsonNode option : options) {
                Long itemOptionId = positiveLong(option.path("id"));
                String title = textOrNull(option.path("optionName"));
                Long additionalPrice = positiveLong(option.path("additionalPrice"));
                if (itemOptionId == null && !StringUtils.hasText(title)) {
                    continue;
                }

                rewardOptions.add(FundingRewardOptionResponse.builder()
                        .id(itemOptionId)
                        .title(title)
                        .price(basePrice != null ? basePrice + (additionalPrice != null ? additionalPrice : 0L) : null)
                        .shippingText(shippingText)
                        .itemOptionId(itemOptionId)
                        .build());
            }
            return List.copyOf(rewardOptions);
        } catch (Exception e) {
            log.warn("[FundingRewardOptionResolver] product option lookup failed. itemId={}", itemId, e);
            return List.of();
        }
    }

    private void applyInternalHeaders(HttpHeaders headers) {
        if (StringUtils.hasText(securityProperties.getInternalAuthToken())) {
            headers.set(securityProperties.getInternalAuthHeader(), securityProperties.getInternalAuthToken());
        }
    }

    private Long positiveLong(JsonNode node) {
        if (node == null || node.isNull() || !node.canConvertToLong()) {
            return null;
        }
        long value = node.asLong();
        return value > 0L ? value : null;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return StringUtils.hasText(value) ? value : null;
    }
}
