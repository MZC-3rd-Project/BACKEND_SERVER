package com.example.search.client;

import com.example.search.client.dto.FundingCampaignSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Optional;

@Slf4j
@Component
public class FundingCampaignClient {

    private final WebClient webClient;

    public FundingCampaignClient(
            WebClient.Builder webClientBuilder,
            @Value("${app.service.funding-url:http://localhost:8086}") String fundingServiceUrl
    ) {
        this.webClient = webClientBuilder.baseUrl(fundingServiceUrl).build();
    }

    public Optional<FundingCampaignSnapshot> findByItemId(Long itemId) {
        if (itemId == null || itemId <= 0L) {
            return Optional.empty();
        }

        try {
            JsonNode response = webClient.get()
                    .uri("/internal/v1/campaigns/item/{itemId}", itemId)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            JsonNode data = extractData(response);
            if (data == null || !data.isObject()) {
                return Optional.empty();
            }

            Long campaignId = nullableLong(data.path("id"));
            if (campaignId == null) {
                return Optional.empty();
            }

            return Optional.of(new FundingCampaignSnapshot(
                    campaignId,
                    nullableLong(data.path("itemId")),
                    nullableText(data.path("category"))
            ));
        } catch (WebClientResponseException.NotFound exception) {
            return Optional.empty();
        } catch (Exception exception) {
            log.warn("Funding campaign lookup failed. itemId={}", itemId, exception);
            return Optional.empty();
        }
    }

    private JsonNode extractData(JsonNode response) {
        if (response == null || !response.path("success").asBoolean(false)) {
            return null;
        }
        JsonNode data = response.path("data");
        return data.isMissingNode() || data.isNull() ? null : data;
    }

    private Long nullableLong(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        long value = node.asLong(Long.MIN_VALUE);
        return value == Long.MIN_VALUE ? null : value;
    }

    private String nullableText(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String value = node.asText(null);
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
