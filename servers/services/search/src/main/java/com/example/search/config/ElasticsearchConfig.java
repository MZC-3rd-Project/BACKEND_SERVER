package com.example.search.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchConfig {

    @Bean(destroyMethod = "close")
    public RestClient elasticsearchRestClient(@Value("${spring.elasticsearch.uris:http://localhost:9200}") String uris) {
        String endpoint = extractFirstEndpoint(uris);
        RestClientBuilder builder = RestClient.builder(org.apache.http.HttpHost.create(endpoint));
        return builder.build();
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(RestClient restClient) {
        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }

    private String extractFirstEndpoint(String uris) {
        if (uris == null || uris.isBlank()) {
            return "http://localhost:9200";
        }
        String[] split = uris.split(",");
        for (String candidate : split) {
            String trimmed = candidate.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return "http://localhost:9200";
    }
}
