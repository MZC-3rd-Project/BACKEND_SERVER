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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexManagementService {

    private static final String DEFAULT_INDEX_BASE = "items";

    private final RestClient restClient;
    private final SearchIndexTemplateResolver templateResolver;
    private final ConcurrentMap<String, ReentrantLock> recreateLocks = new ConcurrentHashMap<>();

    public IndexRecreateResponse recreateItemsIndex(String requestedIndexName) {
        String baseName = normalizeIndexBaseName(requestedIndexName);
        ReentrantLock lock = recreateLocks.computeIfAbsent(baseName, key -> new ReentrantLock());
        lock.lock();
        try {
            return recreateItemsIndexSafely(baseName);
        } finally {
            lock.unlock();
            if (!lock.hasQueuedThreads()) {
                recreateLocks.remove(baseName, lock);
            }
        }
    }

    private IndexRecreateResponse recreateItemsIndexSafely(String baseName) {
        String readAlias = baseName + "-read";
        String writeAlias = baseName + "-write";

        String previousWriteIndex = resolveWriteAliasIndex(writeAlias).orElse(null);
        int nextVersion = nextVersion(previousWriteIndex);
        String targetIndex = baseName + "-v" + nextVersion;
        boolean existed = previousWriteIndex != null;
        boolean createdByRequest = false;

        try {
            createdByRequest = createIndex(targetIndex);
            switchAliases(readAlias, writeAlias, previousWriteIndex, targetIndex);
            verifyWriteAlias(writeAlias, targetIndex);

            return successResponse(targetIndex, existed);
        } catch (Exception e) {
            if (isWriteAliasPointingTo(writeAlias, targetIndex)) {
                log.warn("Alias already switched by concurrent request. base={}, target={}", baseName, targetIndex);
                return successResponse(targetIndex, existed);
            }

            cleanupFailedTargetIndex(createdByRequest, readAlias, writeAlias, targetIndex);
            log.error("Failed to recreate search index with alias switch. base={}, target={}", baseName, targetIndex, e);
            throw new BusinessException(SearchErrorCode.INDEX_MANAGEMENT_FAILED,
                    "검색 인덱스 재생성(alias switch)에 실패했습니다: " + targetIndex, e);
        }
    }

    private IndexRecreateResponse successResponse(String targetIndex, boolean existed) {
        return IndexRecreateResponse.builder()
                .indexName(targetIndex)
                .existed(existed)
                .recreated(true)
                .requestedAt(Instant.now())
                .build();
    }

    private boolean createIndex(String indexName) throws IOException {
        Request request = new Request("PUT", "/" + indexName);
        request.setJsonEntity(templateResolver.resolveItemsIndexTemplate());
        try {
            restClient.performRequest(request);
            return true;
        } catch (ResponseException e) {
            if (isIndexAlreadyExists(e)) {
                log.warn("Target index already exists. concurrentRecreate=true, index={}", indexName);
                return false;
            }
            throw e;
        }
    }

    private boolean isIndexAlreadyExists(ResponseException e) {
        if (e.getResponse() == null || e.getResponse().getStatusLine() == null) {
            return false;
        }
        int statusCode = e.getResponse().getStatusLine().getStatusCode();
        if (statusCode != 400 && statusCode != 409) {
            return false;
        }

        String message = e.getMessage();
        if (StringUtils.hasText(message) && message.contains("resource_already_exists_exception")) {
            return true;
        }
        try {
            String body = org.apache.http.util.EntityUtils.toString(
                    e.getResponse().getEntity(),
                    StandardCharsets.UTF_8
            );
            return StringUtils.hasText(body) && body.contains("resource_already_exists_exception");
        } catch (Exception ignored) {
            return false;
        }
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

    private void cleanupFailedTargetIndex(boolean createdByRequest,
                                          String readAlias,
                                          String writeAlias,
                                          String targetIndex) {
        if (!createdByRequest) {
            return;
        }

        if (isAliasBoundToIndexSafely(readAlias, targetIndex) || isAliasBoundToIndexSafely(writeAlias, targetIndex)) {
            log.warn("Skip deleting failed target index because alias is still attached. target={}", targetIndex);
            return;
        }

        log.warn(
                "Alias switch failed and orphan index might remain. autoDeleteSkipped=true, target={}",
                targetIndex
        );
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

    private boolean isWriteAliasPointingTo(String writeAlias, String expectedIndex) {
        try {
            return resolveWriteAliasIndex(writeAlias)
                    .map(expectedIndex::equals)
                    .orElse(false);
        } catch (Exception ex) {
            log.warn("Failed to verify write alias during recreate failure handling. alias={}", writeAlias, ex);
            return false;
        }
    }

    private boolean isAliasBoundToIndexSafely(String alias, String indexName) {
        try {
            Set<String> indices = resolveAliasIndices(alias);
            return indices.contains(indexName);
        } catch (Exception e) {
            log.warn("Failed to resolve alias mapping. skipDeleteForSafety=true, alias={}, index={}",
                    alias, indexName, e);
            return true;
        }
    }

    private Set<String> resolveAliasIndices(String alias) {
        Map<String, Object> root = resolveAliasRoot(alias);
        return root.keySet();
    }

    private Optional<String> resolveWriteAliasIndex(String writeAlias) {
        Map<String, Object> root = resolveAliasRoot(writeAlias);
        if (root.isEmpty()) {
            return Optional.empty();
        }
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
    }

    private Map<String, Object> resolveAliasRoot(String alias) {
        try {
            Request request = new Request("GET", "/_alias/" + alias);
            Response response = restClient.performRequest(request);
            return JsonUtils.fromJson(
                    org.apache.http.util.EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8),
                    new TypeReference<>() {
                    });
        } catch (ResponseException e) {
            if (e.getResponse() != null && e.getResponse().getStatusLine().getStatusCode() == 404) {
                return Map.of();
            }
            throw new BusinessException(SearchErrorCode.INDEX_MANAGEMENT_FAILED,
                    "alias 조회에 실패했습니다. alias=" + alias, e);
        } catch (Exception e) {
            throw new BusinessException(SearchErrorCode.INDEX_MANAGEMENT_FAILED,
                    "alias 조회에 실패했습니다. alias=" + alias, e);
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
