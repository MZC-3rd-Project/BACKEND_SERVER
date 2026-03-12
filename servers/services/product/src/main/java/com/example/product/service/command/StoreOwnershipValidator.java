package com.example.product.service.command;

import com.example.core.exception.BusinessException;
import com.example.product.exception.ProductErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@Service
public class StoreOwnershipValidator {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public StoreOwnershipValidator(
        WebClient.Builder webClientBuilder,
        ObjectMapper objectMapper,
        @Value("${app.service.store-url}") String storeServiceUrl
    ) {
        this.webClient = webClientBuilder.baseUrl(storeServiceUrl).build();
        this.objectMapper = objectMapper;
    }

    public void validateOwnership(Long sellerId, Long storeId) {
        if (sellerId == null || sellerId <= 0L || storeId == null || storeId <= 0L) {
            throw new BusinessException(ProductErrorCode.STORE_NOT_FOUND);
        }

        try {
            JsonNode response = webClient.get()
                .uri("/internal/v1/stores/{storeId}/snapshot", storeId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

            JsonNode data = response == null || !response.path("success").asBoolean(false)
                ? null
                : response.path("data");
            if (data == null || data.isMissingNode() || data.isNull()) {
                throw new BusinessException(ProductErrorCode.STORE_NOT_FOUND);
            }

            StoreSnapshotPayload payload = objectMapper.treeToValue(data, StoreSnapshotPayload.class);
            if (payload.userId() == null) {
                throw new BusinessException(ProductErrorCode.STORE_NOT_FOUND);
            }
            if (!payload.userId().equals(sellerId)) {
                throw new BusinessException(ProductErrorCode.STORE_OWNERSHIP_MISMATCH);
            }
        } catch (WebClientResponseException.NotFound exception) {
            throw new BusinessException(ProductErrorCode.STORE_NOT_FOUND);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("[StoreOwnershipValidator] store lookup failed. sellerId={}, storeId={}", sellerId, storeId, exception);
            throw new BusinessException(ProductErrorCode.STORE_SERVICE_ERROR);
        }
    }

    private record StoreSnapshotPayload(
        Long storeId,
        Long userId
    ) {
    }
}
