package com.example.search.service.enrichment;

import com.example.search.client.dto.ProductSearchDocument;
import com.example.search.config.SearchAiEnrichmentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchAiEnrichmentTaskPublisherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SearchAiEnrichmentProperties properties;
    private SqsClient sqsClient;
    private ObjectProvider<SqsClient> sqsClientProvider;
    private SearchAiEnrichmentTaskPublisher publisher;

    @BeforeEach
    void setUp() {
        properties = new SearchAiEnrichmentProperties();
        properties.setEnabled(true);
        properties.setQueueUrl("https://sqs.ap-northeast-2.amazonaws.com/123/search-ai-enrichment");
        sqsClient = mock(SqsClient.class);
        sqsClientProvider = mock(ObjectProvider.class);
        when(sqsClientProvider.getIfAvailable()).thenReturn(sqsClient);
        publisher = new SearchAiEnrichmentTaskPublisher(objectMapper, properties, sqsClientProvider);
    }

    @Test
    void publish_sendsNormalizedPayloadToSqs() throws Exception {
        ProductSearchDocument document = new ProductSearchDocument(
                101L,
                "  구장 관련 상품 목록 ",
                " 응원용 굿즈와 캘린더 소개 ",
                10L,
                "굿즈",
                List.of("스포츠", "구장 굿즈"),
                "PRODUCT",
                "ON_SALE",
                12000L,
                1001L,
                2001L,
                3001L,
                List.of("구장", "응원"),
                List.of("데스크", "한정판"),
                List.of("상세 제목"),
                List.of("상세 설명"),
                List.of("상세 하이라이트"),
                30,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        publisher.publish(document, "item_created");

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(requestCaptor.capture());
        JsonNode payload = objectMapper.readTree(requestCaptor.getValue().messageBody());
        assertThat(payload.path("itemId").asLong()).isEqualTo(101L);
        assertThat(payload.path("triggerType").asText()).isEqualTo("ITEM_CREATED");
        assertThat(payload.path("sourceHash").asText()).isNotBlank();
        assertThat(payload.path("title").asText()).isEqualTo("  구장 관련 상품 목록 ");
        assertThat(payload.path("tags")).hasSize(2);
        assertThat(payload.path("requestedAt").asText()).isNotBlank();
    }

    @Test
    void publish_skipsWhenDisabled() {
        properties.setEnabled(false);

        publisher.publish(new ProductSearchDocument(
                101L, "title", "desc", 1L, "cat", List.of(), "PRODUCT", "ON_SALE",
                1000L, 1L, 1L, 1L, List.of(), List.of(), List.of(), List.of(), List.of(), 1,
                LocalDateTime.now(), LocalDateTime.now()
        ), "ITEM_UPDATED");

        verify(sqsClient, never()).sendMessage(any(SendMessageRequest.class));
    }
}
