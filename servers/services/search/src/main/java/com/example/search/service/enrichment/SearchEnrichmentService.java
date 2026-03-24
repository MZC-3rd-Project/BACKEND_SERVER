package com.example.search.service.enrichment;

import com.example.search.document.ItemEnrichmentPatch;
import com.example.search.dto.request.SearchItemEnrichmentPatchRequest;
import com.example.search.dto.response.SearchItemEnrichmentPatchResponse;
import com.example.search.service.index.ElasticsearchDocumentClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SearchEnrichmentService {

    private final ElasticsearchDocumentClient elasticsearchDocumentClient;

    @Transactional
    public SearchItemEnrichmentPatchResponse applyItemEnrichment(Long itemId, SearchItemEnrichmentPatchRequest request) {
        OffsetDateTime enrichedAt = OffsetDateTime.now(ZoneOffset.UTC);
        ItemEnrichmentPatch patch = new ItemEnrichmentPatch(
                normalizeValues(request.aiTags()),
                normalizeValues(request.aiKeywords()),
                trimToNull(request.aiSummary()),
                trimToNull(request.sourceHash()),
                trimToNull(request.model()),
                normalizeStatus(request.status()),
                enrichedAt
        );
        elasticsearchDocumentClient.updateAiEnrichment(itemId, patch);

        return SearchItemEnrichmentPatchResponse.builder()
                .itemId(itemId)
                .status(patch.aiStatus())
                .aiTagCount(patch.aiTags().size())
                .aiKeywordCount(patch.aiKeywords().size())
                .enrichedAt(enrichedAt)
                .build();
    }

    private List<String> normalizeValues(List<String> rawValues) {
        if (rawValues == null || rawValues.isEmpty()) {
            return List.of();
        }
        return rawValues.stream()
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private String normalizeStatus(String rawStatus) {
        String normalized = trimToNull(rawStatus);
        return normalized == null ? null : normalized.toUpperCase();
    }

    private String trimToNull(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        return rawValue.trim();
    }
}
