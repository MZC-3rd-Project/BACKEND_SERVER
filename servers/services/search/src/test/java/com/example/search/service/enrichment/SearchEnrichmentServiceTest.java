package com.example.search.service.enrichment;

import com.example.search.document.ItemEnrichmentPatch;
import com.example.search.dto.request.SearchItemEnrichmentPatchRequest;
import com.example.search.dto.response.SearchItemEnrichmentPatchResponse;
import com.example.search.service.index.ElasticsearchDocumentClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SearchEnrichmentServiceTest {

    private ElasticsearchDocumentClient elasticsearchDocumentClient;
    private SearchEnrichmentService searchEnrichmentService;

    @BeforeEach
    void setUp() {
        elasticsearchDocumentClient = mock(ElasticsearchDocumentClient.class);
        searchEnrichmentService = new SearchEnrichmentService(elasticsearchDocumentClient);
    }

    @Test
    void applyItemEnrichment_normalizesAndUpdatesDocument() {
        SearchItemEnrichmentPatchRequest request = new SearchItemEnrichmentPatchRequest(
                " sha256-123 ",
                " anthropic.claude-haiku ",
                " ready ",
                java.util.List.of(" 데이트 ", "", "입문자용", "데이트"),
                java.util.List.of(" 봄 공연 ", "커플", "봄 공연"),
                "  봄 데이트용 공연 추천  "
        );

        SearchItemEnrichmentPatchResponse response = searchEnrichmentService.applyItemEnrichment(101L, request);

        ArgumentCaptor<ItemEnrichmentPatch> patchCaptor = ArgumentCaptor.forClass(ItemEnrichmentPatch.class);
        verify(elasticsearchDocumentClient).updateAiEnrichment(org.mockito.ArgumentMatchers.eq(101L), patchCaptor.capture());
        ItemEnrichmentPatch patch = patchCaptor.getValue();
        assertThat(patch.aiSourceHash()).isEqualTo("sha256-123");
        assertThat(patch.aiModel()).isEqualTo("anthropic.claude-haiku");
        assertThat(patch.aiStatus()).isEqualTo("READY");
        assertThat(patch.aiTags()).containsExactly("데이트", "입문자용");
        assertThat(patch.aiKeywords()).containsExactly("봄 공연", "커플");
        assertThat(patch.aiSummary()).isEqualTo("봄 데이트용 공연 추천");
        assertThat(patch.aiEnrichedAt()).isNotNull();

        assertThat(response.itemId()).isEqualTo(101L);
        assertThat(response.status()).isEqualTo("READY");
        assertThat(response.aiTagCount()).isEqualTo(2);
        assertThat(response.aiKeywordCount()).isEqualTo(2);
        assertThat(response.enrichedAt()).isNotNull();
    }
}
