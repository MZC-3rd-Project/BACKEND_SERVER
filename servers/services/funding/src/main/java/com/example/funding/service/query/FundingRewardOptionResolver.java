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
            JsonNode summaryResponse = webClient.get()
                    .uri("/internal/v1/items/{itemId}", itemId)
                    .headers(this::applyInternalHeaders)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (summaryResponse == null || !summaryResponse.path("success").asBoolean(false)) {
                return List.of();
            }

            JsonNode summaryData = summaryResponse.path("data");
            String itemType = textOrNull(summaryData.path("itemType"));
            if (!StringUtils.hasText(itemType)) {
                return List.of();
            }

            String detailPath = switch (itemType.trim().toUpperCase()) {
                case "PERFORMANCE" -> "/api/performances/{itemId}";
                case "GOODS" -> "/api/goods/{itemId}";
                default -> "/api/products/{itemId}";
            };

            JsonNode detailResponse = webClient.get()
                    .uri(detailPath, itemId)
                    .headers(this::applyInternalHeaders)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (detailResponse == null || !detailResponse.path("success").asBoolean(false)) {
                return List.of();
            }

            JsonNode data = detailResponse.path("data");
            if ("PERFORMANCE".equalsIgnoreCase(itemType)) {
                return resolvePerformanceRewardOptions(data);
            }
            return resolveGoodsRewardOptions(data);
        } catch (Exception e) {
            log.warn("[FundingRewardOptionResolver] product option lookup failed. itemId={}", itemId, e);
            return List.of();
        }
    }

    private List<FundingRewardOptionResponse> resolveGoodsRewardOptions(JsonNode data) {
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
            Long additionalPrice = positiveLong(option.path("additionalPrice"), 0L);
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
    }

    private List<FundingRewardOptionResponse> resolvePerformanceRewardOptions(JsonNode data) {
        JsonNode seatGrades = data.path("seatGrades");
        if (!seatGrades.isArray() || seatGrades.isEmpty()) {
            return List.of();
        }

        String venue = textOrNull(data.path("venue"));
        String performanceDate = textOrNull(data.path("performanceDate"));
        String performanceTime = textOrNull(data.path("performanceTime"));
        String descriptor = String.join(" · ",
                java.util.stream.Stream.of(venue, performanceDate, performanceTime)
                        .filter(StringUtils::hasText)
                        .toList());

        List<FundingRewardOptionResponse> rewardOptions = new ArrayList<>();
        for (JsonNode grade : seatGrades) {
            Long gradeId = positiveLong(grade.path("id"));
            String title = textOrNull(grade.path("gradeName"));
            Long price = positiveLong(grade.path("price"));
            if (gradeId == null && !StringUtils.hasText(title)) {
                continue;
            }

            rewardOptions.add(FundingRewardOptionResponse.builder()
                    .id(gradeId)
                    .itemOptionId(gradeId)
                    .title(title)
                    .price(price)
                    .shippingText(StringUtils.hasText(descriptor) ? descriptor : textOrNull(data.path("bookingNotice")))
                    .build());
        }
        return List.copyOf(rewardOptions);
    }

    private void applyInternalHeaders(HttpHeaders headers) {
        if (StringUtils.hasText(securityProperties.getInternalAuthToken())) {
            headers.set(securityProperties.getInternalAuthHeader(), securityProperties.getInternalAuthToken());
        }
    }

    private Long positiveLong(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.canConvertToLong()) {
            long value = node.asLong();
            return value > 0L ? value : null;
        }
        String text = textOrNull(node);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            long value = Long.parseLong(text);
            return value > 0L ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Long positiveLong(JsonNode node, Long fallback) {
        Long value = positiveLong(node);
        return value != null ? value : fallback;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return StringUtils.hasText(value) ? value : null;
    }
}
