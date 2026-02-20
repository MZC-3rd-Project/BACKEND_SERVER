package com.example.search.service.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import com.example.core.exception.BusinessException;
import com.example.search.dto.index.response.IndexRecreateResponse;
import com.example.search.exception.SearchErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.Instant;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexManagementService {

    private static final String DEFAULT_INDEX_NAME = "items";

    private final ElasticsearchClient elasticsearchClient;
    private final RestClient restClient;
    private final SearchIndexTemplateResolver templateResolver;

    public IndexRecreateResponse recreateItemsIndex(String requestedIndexName) {
        String indexName = normalizeIndexName(requestedIndexName);

        try {
            boolean existed = exists(indexName);
            if (existed) {
                delete(indexName);
            }
            create(indexName);

            return IndexRecreateResponse.builder()
                    .indexName(indexName)
                    .existed(existed)
                    .recreated(true)
                    .requestedAt(Instant.now())
                    .build();
        } catch (IOException e) {
            log.error("Failed to recreate search index: {}", indexName, e);
            throw new BusinessException(SearchErrorCode.INDEX_MANAGEMENT_FAILED,
                    "검색 인덱스 재생성에 실패했습니다: " + indexName, e);
        }
    }

    private boolean exists(String indexName) throws IOException {
        BooleanResponse response = elasticsearchClient.indices().exists(request -> request.index(indexName));
        return response.value();
    }

    private void delete(String indexName) throws IOException {
        elasticsearchClient.indices().delete(request -> request.index(indexName));
    }

    private void create(String indexName) throws IOException {
        Request request = new Request("PUT", "/" + indexName);
        request.setJsonEntity(templateResolver.resolveItemsIndexTemplate());
        restClient.performRequest(request);
    }

    private String normalizeIndexName(String requestedIndexName) {
        if (!StringUtils.hasText(requestedIndexName)) {
            return DEFAULT_INDEX_NAME;
        }

        String normalized = requestedIndexName.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("^[a-z0-9._-]{1,255}$")) {
            throw new BusinessException(SearchErrorCode.INVALID_SEARCH_PARAMETER,
                    "유효하지 않은 인덱스 이름입니다: " + requestedIndexName);
        }

        return normalized;
    }
}
