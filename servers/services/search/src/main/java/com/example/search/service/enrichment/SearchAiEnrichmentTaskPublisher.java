package com.example.search.service.enrichment;

import com.example.search.client.dto.ProductSearchDocument;
import com.example.search.config.SearchAiEnrichmentProperties;
import com.example.search.dto.message.SearchAiEnrichmentTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchAiEnrichmentTaskPublisher {

    private final ObjectMapper objectMapper;
    private final SearchAiEnrichmentProperties properties;
    private final ObjectProvider<SqsClient> sqsClientProvider;

    public void publish(ProductSearchDocument document, String triggerType) {
        if (!properties.isEnabled() || document == null || document.itemId() == null || document.itemId() <= 0L) {
            return;
        }
        if (!StringUtils.hasText(properties.getQueueUrl())) {
            log.debug("[SearchAiEnrichmentTaskPublisher] queueUrl missing. itemId={}", document.itemId());
            return;
        }

        SqsClient sqsClient = sqsClientProvider.getIfAvailable();
        if (sqsClient == null) {
            log.debug("[SearchAiEnrichmentTaskPublisher] sqsClient unavailable. itemId={}", document.itemId());
            return;
        }

        String sourceHash = createSourceHash(document);
        SearchAiEnrichmentTask task = SearchAiEnrichmentTask.of(document, normalizeTriggerType(triggerType), sourceHash);

        try {
            String payload = objectMapper.writeValueAsString(task);
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(properties.getQueueUrl())
                    .messageBody(payload)
                    .build());
            log.info("[SearchAiEnrichmentTaskPublisher] published. itemId={}, triggerType={}", document.itemId(), task.triggerType());
        } catch (Exception exception) {
            log.warn("[SearchAiEnrichmentTaskPublisher] publish failed. itemId={}", document.itemId(), exception);
        }
    }

    private String createSourceHash(ProductSearchDocument document) {
        StringJoiner joiner = new StringJoiner("\n");
        append(joiner, document.title());
        append(joiner, document.description());
        append(joiner, document.category());
        append(joiner, document.categoryPath());
        append(joiner, document.tags());
        append(joiner, document.features());
        append(joiner, document.detailTitles());
        append(joiner, document.detailDescriptions());
        append(joiner, document.detailHighlights());
        return sha256(joiner.toString());
    }

    private void append(StringJoiner joiner, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        values.stream()
                .filter(Objects::nonNull)
                .map(this::normalizeText)
                .filter(StringUtils::hasText)
                .forEach(joiner::add);
    }

    private void append(StringJoiner joiner, String value) {
        String normalized = normalizeText(value);
        if (StringUtils.hasText(normalized)) {
            joiner.add(normalized);
        }
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private String sha256(String rawValue) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(rawValue.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                builder.append(String.format(Locale.ROOT, "%02x", b));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("search ai enrichment source hash generation failed", exception);
        }
    }

    private String normalizeTriggerType(String triggerType) {
        if (!StringUtils.hasText(triggerType)) {
            return "ITEM_UPDATED";
        }
        return triggerType.trim().toUpperCase(Locale.ROOT);
    }
}
