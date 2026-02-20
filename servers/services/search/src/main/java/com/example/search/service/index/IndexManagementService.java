package com.example.search.service.index;

import com.example.core.exception.BusinessException;
import com.example.core.util.JsonUtils;
import com.example.search.dto.index.response.IndexRecreateResponse;
import com.example.search.exception.SearchErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexManagementService {

    private static final String DEFAULT_INDEX_BASE = "items";

    private final RestClient restClient;
    private final SearchIndexTemplateResolver templateResolver;

    public IndexRecreateResponse recreateItemsIndex(String requestedIndexName) {
        String baseName = normalizeIndexBaseName(requestedIndexName);
        String readAlias = baseName + "-read";
        String writeAlias = baseName + "-write";

        String previousWriteIndex = resolveWriteAliasIndex(writeAlias).orElse(null);
        int nextVersion = nextVersion(previousWriteIndex);
        String targetIndex = baseName + "-v" + nextVersion;
        boolean existed = previousWriteIndex != null;

        try {
            createIndex(targetIndex);
            switchAliases(readAlias, writeAlias, previousWriteIndex, targetIndex);
            verifyWriteAlias(writeAlias, targetIndex);

            return IndexRecreateResponse.builder()
                    .indexName(targetIndex)
                    .existed(existed)
                    .recreated(true)
                    .requestedAt(Instant.now())
                    .build();
        } catch (Exception e) {
            rollbackAliases(readAlias, writeAlias, previousWriteIndex, targetIndex);
            deleteIndexQuietly(targetIndex);
            log.error("Failed to recreate search index with alias switch. base={}, target={}", baseName, targetIndex, e);
            throw new BusinessException(SearchErrorCode.INDEX_MANAGEMENT_FAILED,
                    "검색 인덱스 재생성(alias switch)에 실패했습니다: " + targetIndex, e);
        }
    }

    private void createIndex(String indexName) throws IOException {
        Request request = new Request("PUT", "/" + indexName);
        request.setJsonEntity(templateResolver.resolveItemsIndexTemplate());
        restClient.performRequest(request);
    }

    private void switchAliases(String readAlias,
                               String writeAlias,
                               String previousWriteIndex,
                               String targetIndex) throws IOException {
        List<Map<String, Object>> actions = new ArrayList<>();
        if (StringUtils.hasText(previousWriteIndex)) {
            actions.add(removeAliasAction(previousWriteIndex, readAlias));
            actions.add(removeAliasAction(previousWriteIndex, writeAlias));
        }
        actions.add(addAliasAction(targetIndex, readAlias, false));
        actions.add(addAliasAction(targetIndex, writeAlias, true));
        applyAliasActions(actions);
    }

    private void verifyWriteAlias(String writeAlias, String expectedIndex) {
        String actual = resolveWriteAliasIndex(writeAlias)
                .orElseThrow(() -> new BusinessException(SearchErrorCode.INDEX_MANAGEMENT_FAILED,
                        "write alias가 존재하지 않습니다. alias=" + writeAlias));
        if (!expectedIndex.equals(actual)) {
            throw new BusinessException(SearchErrorCode.INDEX_MANAGEMENT_FAILED,
                    "write alias 스위칭 검증 실패. expected=" + expectedIndex + ", actual=" + actual);
        }
    }

    private void rollbackAliases(String readAlias,
                                 String writeAlias,
                                 String previousWriteIndex,
                                 String failedTargetIndex) {
        try {
            List<Map<String, Object>> rollbackActions = new ArrayList<>();
            rollbackActions.add(removeAliasAction(failedTargetIndex, readAlias));
            rollbackActions.add(removeAliasAction(failedTargetIndex, writeAlias));

            if (StringUtils.hasText(previousWriteIndex)) {
                rollbackActions.add(addAliasAction(previousWriteIndex, readAlias, false));
                rollbackActions.add(addAliasAction(previousWriteIndex, writeAlias, true));
            }
            applyAliasActions(rollbackActions);
            log.warn("Search alias rollback executed. previous={}, failedTarget={}", previousWriteIndex, failedTargetIndex);
        } catch (Exception rollbackEx) {
            log.error("Search alias rollback failed. previous={}, failedTarget={}",
                    previousWriteIndex, failedTargetIndex, rollbackEx);
        }
    }

    private void deleteIndexQuietly(String indexName) {
        try {
            Request request = new Request("DELETE", "/" + indexName);
            restClient.performRequest(request);
        } catch (Exception deleteEx) {
            log.warn("Failed to delete index after alias switch failure. index={}", indexName, deleteEx);
        }
    }

    private void applyAliasActions(List<Map<String, Object>> actions) throws IOException {
        Map<String, Object> body = Map.of("actions", actions);
        Request request = new Request("POST", "/_aliases");
        request.setJsonEntity(JsonUtils.toJson(body));
        restClient.performRequest(request);
    }

    private Map<String, Object> addAliasAction(String index, String alias, boolean write) {
        Map<String, Object> add = new LinkedHashMap<>();
        add.put("index", index);
        add.put("alias", alias);
        if (write) {
            add.put("is_write_index", true);
        }
        return Map.of("add", add);
    }

    private Map<String, Object> removeAliasAction(String index, String alias) {
        return Map.of("remove", Map.of("index", index, "alias", alias));
    }

    private Optional<String> resolveWriteAliasIndex(String writeAlias) {
        try {
            Request request = new Request("GET", "/_alias/" + writeAlias);
            Response response = restClient.performRequest(request);
            Map<String, Object> root = JsonUtils.fromJson(
                    org.apache.http.util.EntityUtils.toString(response.getEntity()),
                    new TypeReference<>() {
                    });

            for (Map.Entry<String, Object> entry : root.entrySet()) {
                String indexName = entry.getKey();
                Map<String, Object> aliasesMap = toMap(toMap(entry.getValue()).get("aliases"));
                Map<String, Object> writeAliasMeta = toMap(aliasesMap.get(writeAlias));
                Object isWriteIndex = writeAliasMeta.get("is_write_index");
                if (Boolean.TRUE.equals(isWriteIndex) || aliasesMap.size() == 1) {
                    return Optional.of(indexName);
                }
            }
            return Optional.empty();
        } catch (ResponseException e) {
            if (e.getResponse() != null && e.getResponse().getStatusLine().getStatusCode() == 404) {
                return Optional.empty();
            }
            throw new BusinessException(SearchErrorCode.INDEX_MANAGEMENT_FAILED,
                    "write alias 조회에 실패했습니다. alias=" + writeAlias, e);
        } catch (Exception e) {
            throw new BusinessException(SearchErrorCode.INDEX_MANAGEMENT_FAILED,
                    "write alias 조회에 실패했습니다. alias=" + writeAlias, e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object source) {
        if (source instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private int nextVersion(String previousWriteIndex) {
        if (!StringUtils.hasText(previousWriteIndex)) {
            return 1;
        }
        int marker = previousWriteIndex.lastIndexOf("-v");
        if (marker < 0) {
            return 1;
        }
        String versionPart = previousWriteIndex.substring(marker + 2);
        try {
            return Integer.parseInt(versionPart) + 1;
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private String normalizeIndexBaseName(String requestedIndexName) {
        if (!StringUtils.hasText(requestedIndexName)) {
            return DEFAULT_INDEX_BASE;
        }

        String normalized = requestedIndexName.trim().toLowerCase(Locale.ROOT);
        if (normalized.matches("^[a-z0-9._-]+-v\\d+$")) {
            normalized = normalized.substring(0, normalized.lastIndexOf("-v"));
        }

        if (!normalized.matches("^[a-z0-9._-]{1,255}$")) {
            throw new BusinessException(SearchErrorCode.INVALID_SEARCH_PARAMETER,
                    "유효하지 않은 인덱스 이름입니다: " + requestedIndexName);
        }
        return normalized;
    }
}
