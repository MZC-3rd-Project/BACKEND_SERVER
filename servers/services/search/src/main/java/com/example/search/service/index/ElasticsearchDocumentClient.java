package com.example.search.service.index;

import com.example.core.exception.CommonErrorCode;
import com.example.core.exception.TechnicalException;
import com.example.search.config.SearchElasticsearchProperties;
import com.example.search.document.ItemDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestBodyUriSpec;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class ElasticsearchDocumentClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final SearchElasticsearchProperties properties;
    private final AtomicBoolean indexReady = new AtomicBoolean(false);
    private final Object indexInitMonitor = new Object();

    public ElasticsearchDocumentClient(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            SearchElasticsearchProperties properties
    ) {
        this.webClient = webClientBuilder.baseUrl(properties.primaryUri()).build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public JsonNode search(ObjectNode requestBody) {
        ensureIndexExists();
        RawResponse response = exchange(
                "search query",
                HttpMethod.POST,
                "/" + properties.getIndexName() + "/_search",
                requestBody
        );
        if (!response.status().is2xxSuccessful()) {
            throw backendFailure("search query failed", response);
        }
        return parseJson(response.body());
    }

    public void upsert(ItemDocument document) {
        if (document == null || document.itemId() == null || document.itemId() <= 0L) {
            return;
        }

        ensureIndexExists();
        RawResponse response = exchange(
                "document upsert",
                HttpMethod.PUT,
                "/" + properties.getIndexName() + "/_doc/" + document.itemId(),
                objectMapper.valueToTree(document)
        );
        if (!response.status().is2xxSuccessful()) {
            throw backendFailure("document upsert failed", response);
        }
    }

    public void updateAvailableStock(Long itemId, Integer availableStock) {
        if (itemId == null || itemId <= 0L) {
            return;
        }

        ensureIndexExists();

        ObjectNode requestBody = objectMapper.createObjectNode();
        ObjectNode docNode = requestBody.putObject("doc");
        if (availableStock == null) {
            docNode.putNull("availableStock");
        } else {
            docNode.put("availableStock", availableStock);
        }

        RawResponse response;
        try {
            response = exchange(
                    "available stock update",
                    HttpMethod.POST,
                    "/" + properties.getIndexName() + "/_update/" + itemId,
                    requestBody
            );
        } catch (WebClientResponseException.NotFound exception) {
            return;
        }
        if (response.status().value() == 404) {
            return;
        }
        if (!response.status().is2xxSuccessful()) {
            throw backendFailure("available stock update failed", response);
        }
    }

    public void delete(Long itemId) {
        if (itemId == null || itemId <= 0L) {
            return;
        }

        RawResponse response;
        try {
            response = exchange(
                    "document delete",
                    HttpMethod.DELETE,
                    "/" + properties.getIndexName() + "/_doc/" + itemId,
                    null
            );
        } catch (WebClientResponseException.NotFound exception) {
            return;
        }
        if (response.status().value() == 404) {
            return;
        }
        if (!response.status().is2xxSuccessful()) {
            throw backendFailure("document delete failed", response);
        }
    }

    public void recreateIndex() {
        synchronized (indexInitMonitor) {
            RawResponse deleteResponse;
            try {
                deleteResponse = exchange(
                        "index delete",
                        HttpMethod.DELETE,
                        "/" + properties.getIndexName(),
                        null
                );
            } catch (WebClientResponseException.NotFound exception) {
                deleteResponse = new RawResponse(HttpStatusCode.valueOf(404), "");
            }
            if (deleteResponse.status().value() != 404 && !deleteResponse.status().is2xxSuccessful()) {
                throw backendFailure("index delete failed", deleteResponse);
            }

            indexReady.set(false);
            createIndex();
            indexReady.set(true);
            log.info("Search index recreated. indexName={}, uri={}", properties.getIndexName(), properties.primaryUri());
        }
    }

    private void ensureIndexExists() {
        if (indexReady.get()) {
            return;
        }

        synchronized (indexInitMonitor) {
            if (indexReady.get()) {
                return;
            }

            RawResponse existsResponse;
            try {
                existsResponse = exchange(
                        "index exists check",
                        HttpMethod.HEAD,
                        "/" + properties.getIndexName(),
                        null
                );
            } catch (WebClientResponseException.NotFound exception) {
                existsResponse = new RawResponse(HttpStatusCode.valueOf(404), "");
            }
            if (existsResponse.status().is2xxSuccessful()) {
                indexReady.set(true);
                return;
            }
            if (existsResponse.status().value() != 404) {
                throw backendFailure("index existence check failed", existsResponse);
            }

            createIndex();
            indexReady.set(true);
            log.info("Search index ready. indexName={}, uri={}", properties.getIndexName(), properties.primaryUri());
        }
    }

    private RawResponse exchange(String operation, HttpMethod method, String path, JsonNode requestBody) {
        try {
            RequestBodyUriSpec requestSpec = webClient.method(method);
            RequestHeadersSpec<?> requestHeadersSpec = requestBody == null
                    ? requestSpec.uri(path).contentType(MediaType.APPLICATION_JSON)
                    : requestSpec.uri(path).contentType(MediaType.APPLICATION_JSON).bodyValue(requestBody);

            RawResponse response = requestHeadersSpec
                    .exchangeToMono(clientResponse -> clientResponse.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .map(body -> new RawResponse(clientResponse.statusCode(), body)))
                    .block();
            if (response == null) {
                throw new TechnicalException(
                        CommonErrorCode.EXTERNAL_API_ERROR,
                        "Elasticsearch 응답이 비어 있습니다: " + operation
                );
            }
            return response;
        } catch (WebClientResponseException.NotFound exception) {
            throw exception;
        } catch (Exception exception) {
            throw new TechnicalException(
                    CommonErrorCode.EXTERNAL_API_ERROR,
                    "Elasticsearch 요청에 실패했습니다: " + operation,
                    exception
            );
        }
    }

    private ObjectNode createIndexBody() {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode settingsNode = root.putObject("settings");
        settingsNode.put("number_of_shards", 1);
        settingsNode.put("number_of_replicas", 0);

        ObjectNode analysisNode = settingsNode.putObject("analysis");
        analysisNode.putObject("tokenizer")
                .putObject("korean_nori_tokenizer")
                .put("type", "nori_tokenizer")
                .put("decompound_mode", "mixed");
        analysisNode.putObject("analyzer")
                .putObject("korean_nori")
                .put("type", "custom")
                .put("tokenizer", "korean_nori_tokenizer")
                .putArray("filter")
                .add("lowercase");

        ObjectNode propertiesNode = root.putObject("mappings").putObject("properties");

        addLongField(propertiesNode, "itemId");
        addTextWithKeywordField(propertiesNode, "title");
        addTextField(propertiesNode, "description");
        addLongField(propertiesNode, "categoryId");
        addTextWithKeywordField(propertiesNode, "category");
        addTextWithKeywordField(propertiesNode, "categoryPath");
        addKeywordField(propertiesNode, "domainType");
        addKeywordField(propertiesNode, "status");
        addKeywordField(propertiesNode, "salesChannel");
        addLongField(propertiesNode, "price");
        addLongField(propertiesNode, "basePrice");
        addLongField(propertiesNode, "effectivePrice");
        addLongField(propertiesNode, "sellerId");
        addLongField(propertiesNode, "storeId");
        addTextWithKeywordField(propertiesNode, "storeName");
        addLongField(propertiesNode, "thumbnailMediaId");
        addKeywordField(propertiesNode, "thumbnailUrl");
        addTextWithKeywordField(propertiesNode, "tags");
        addTextField(propertiesNode, "features");
        addTextField(propertiesNode, "detailTitles");
        addTextField(propertiesNode, "detailDescriptions");
        addTextField(propertiesNode, "detailHighlights");
        addIntegerField(propertiesNode, "stock");
        addIntegerField(propertiesNode, "availableStock");
        addLongField(propertiesNode, "activeHotDealId");
        addLongField(propertiesNode, "activeCampaignId");
        addDateField(propertiesNode, "sourceCreatedAt");
        addDateField(propertiesNode, "sourceUpdatedAt");

        return root;
    }

    private void addKeywordField(ObjectNode propertiesNode, String fieldName) {
        propertiesNode.putObject(fieldName).put("type", "keyword");
    }

    private void addTextField(ObjectNode propertiesNode, String fieldName) {
        propertiesNode.putObject(fieldName)
                .put("type", "text")
                .put("analyzer", "korean_nori")
                .put("search_analyzer", "korean_nori");
    }

    private void addTextWithKeywordField(ObjectNode propertiesNode, String fieldName) {
        ObjectNode field = propertiesNode.putObject(fieldName);
        field.put("type", "text");
        field.put("analyzer", "korean_nori");
        field.put("search_analyzer", "korean_nori");
        field.putObject("fields")
                .putObject("keyword")
                .put("type", "keyword")
                .put("ignore_above", 256);
    }

    private void createIndex() {
        RawResponse createResponse = exchange(
                "index create",
                HttpMethod.PUT,
                "/" + properties.getIndexName(),
                createIndexBody()
        );
        if (!createResponse.status().is2xxSuccessful()) {
            throw backendFailure("index create failed", createResponse);
        }
    }

    private void addLongField(ObjectNode propertiesNode, String fieldName) {
        propertiesNode.putObject(fieldName).put("type", "long");
    }

    private void addIntegerField(ObjectNode propertiesNode, String fieldName) {
        propertiesNode.putObject(fieldName).put("type", "integer");
    }

    private void addDateField(ObjectNode propertiesNode, String fieldName) {
        propertiesNode.putObject(fieldName).put("type", "date");
    }

    private JsonNode parseJson(String rawBody) {
        if (!StringUtils.hasText(rawBody)) {
            return objectMapper.createObjectNode();
        }

        try {
            return objectMapper.readTree(rawBody);
        } catch (Exception exception) {
            throw new TechnicalException(
                    CommonErrorCode.EXTERNAL_API_ERROR,
                    "Elasticsearch 응답 파싱에 실패했습니다",
                    exception
            );
        }
    }

    private TechnicalException backendFailure(String message, RawResponse response) {
        String detail = StringUtils.hasText(response.body()) ? response.body() : response.status().toString();
        return new TechnicalException(
                CommonErrorCode.EXTERNAL_API_ERROR,
                message + ": " + detail
        );
    }

    private record RawResponse(HttpStatusCode status, String body) {
    }
}
