package com.example.search.service.index;

import com.example.core.exception.BusinessException;
import com.example.search.exception.SearchErrorCode;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class SearchIndexTemplateResolver {

    private static final String ITEMS_TEMPLATE_RESOURCE = "classpath:elasticsearch/items-index-template.json";
    private static final String SYNONYM_RESOURCE = "classpath:elasticsearch/analysis/synonym.txt";
    private static final String STOPWORDS_RESOURCE = "classpath:elasticsearch/analysis/stopwords_ko.txt";
    private static final String USERDICT_RESOURCE = "classpath:elasticsearch/analysis/userdict_ko.txt";
    private static final String SYNONYMS_PLACEHOLDER = "__SYNONYMS__";
    private static final String STOPWORDS_PLACEHOLDER = "__STOPWORDS__";
    private static final String USERDICT_PLACEHOLDER = "__USERDICT__";

    private final ResourceLoader resourceLoader;

    public SearchIndexTemplateResolver(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public String resolveItemsIndexTemplate() {
        String template = readResourceAsString(ITEMS_TEMPLATE_RESOURCE);
        String synonyms = toJsonArray(readDictionaryLines(SYNONYM_RESOURCE));
        String stopwords = toJsonArray(readDictionaryLines(STOPWORDS_RESOURCE));
        String userdict = toJsonArray(readDictionaryLines(USERDICT_RESOURCE));

        return template
                .replace(SYNONYMS_PLACEHOLDER, synonyms)
                .replace(STOPWORDS_PLACEHOLDER, stopwords)
                .replace(USERDICT_PLACEHOLDER, userdict);
    }

    private List<String> readDictionaryLines(String resourcePath) {
        Resource resource = resourceLoader.getResource(resourcePath);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .filter(line -> !line.startsWith("#"))
                    .toList();
        } catch (IOException e) {
            throw new BusinessException(SearchErrorCode.SEARCH_INDEX_TEMPLATE_ERROR,
                    "검색 인덱스 사전 리소스를 읽지 못했습니다: " + resourcePath, e);
        }
    }

    private String readResourceAsString(String resourcePath) {
        Resource resource = resourceLoader.getResource(resourcePath);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new BusinessException(SearchErrorCode.SEARCH_INDEX_TEMPLATE_ERROR,
                    "검색 인덱스 템플릿을 읽지 못했습니다: " + resourcePath, e);
        }
    }

    private String toJsonArray(List<String> values) {
        return values.stream()
                .map(value -> "\"" + escapeJson(value) + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
