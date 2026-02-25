package com.example.gateway.bff.service;

import com.example.gateway.bff.dto.BffItemType;
import com.example.gateway.config.GatewaySecurityProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogDetailBffServiceTest {

    @Mock
    private CatalogDetailDownstreamClient downstreamClient;

    private CatalogDetailBffService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        GatewaySecurityProperties securityProperties = new GatewaySecurityProperties();
        securityProperties.setInternalAuthHeader("X-Gateway-Auth");
        securityProperties.setInternalAuthToken("internal-secret");

        objectMapper = new ObjectMapper();
        CatalogMetricsService catalogMetricsService = new CatalogMetricsService(new SimpleMeterRegistry());
        service = new CatalogDetailBffService(downstreamClient, securityProperties, catalogMetricsService, objectMapper);
    }

    @Test
    void getCatalogDetail_returnsHotDealDetailWhenPrimarySuccess() {
        when(downstreamClient.fetchHotDealDetail(eq(901L), any(HttpHeaders.class)))
                .thenReturn(Mono.just(response(HttpStatus.OK, "hot-deal")));

        ResponseEntity<JsonNode> response = service
                .getCatalogDetail(11L, "PRODUCT", "HOT_DEAL", 901L, null)
                .block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("source").asText()).isEqualTo("hot-deal");
        verify(downstreamClient, never()).fetchNormalDetail(any(), any(), any());
    }

    @Test
    void getCatalogDetail_fallbacksToNormalWhenHotDeal404() {
        when(downstreamClient.fetchHotDealDetail(eq(901L), any(HttpHeaders.class)))
                .thenReturn(Mono.just(response(HttpStatus.NOT_FOUND, "hot-deal-404")));
        when(downstreamClient.fetchNormalDetail(eq(BffItemType.PRODUCT), eq(11L), any(HttpHeaders.class)))
                .thenReturn(Mono.just(response(HttpStatus.OK, "normal")));

        ResponseEntity<JsonNode> response = service
                .getCatalogDetail(11L, "PRODUCT", "HOT_DEAL", 901L, null)
                .block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("source").asText()).isEqualTo("normal");
        verify(downstreamClient).fetchNormalDetail(eq(BffItemType.PRODUCT), eq(11L), any(HttpHeaders.class));
    }

    @Test
    void getCatalogDetail_fallbacksToNormalWhenFundingItemLookup404() {
        when(downstreamClient.fetchFundingDetailByItem(eq(22L), any(HttpHeaders.class)))
                .thenReturn(Mono.just(response(HttpStatus.NOT_FOUND, "funding-404")));
        when(downstreamClient.fetchNormalDetail(eq(BffItemType.GOODS), eq(22L), any(HttpHeaders.class)))
                .thenReturn(Mono.just(response(HttpStatus.OK, "normal")));

        ResponseEntity<JsonNode> response = service
                .getCatalogDetail(22L, "GOODS", "FUNDING", null, null)
                .block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("source").asText()).isEqualTo("normal");
        verify(downstreamClient).fetchFundingDetailByItem(eq(22L), any(HttpHeaders.class));
        verify(downstreamClient, never()).fetchFundingDetail(any(), any(HttpHeaders.class));
    }

    @Test
    void getCatalogDetail_returnsNormalDirectlyWhenChannelNormal() {
        when(downstreamClient.fetchNormalDetail(eq(BffItemType.PERFORMANCE), eq(33L), any(HttpHeaders.class)))
                .thenReturn(Mono.just(response(HttpStatus.OK, "normal")));

        ResponseEntity<JsonNode> response = service
                .getCatalogDetail(33L, "PERFORMANCE", "NORMAL", null, null)
                .block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("source").asText()).isEqualTo("normal");
        verify(downstreamClient, never()).fetchHotDealDetail(any(), any(HttpHeaders.class));
        verify(downstreamClient, never()).fetchFundingDetail(any(), any(HttpHeaders.class));
        verify(downstreamClient, never()).fetchFundingDetailByItem(any(), any(HttpHeaders.class));
    }

    @Test
    void getCatalogDetail_returns400WhenRequestInvalid() {
        ResponseEntity<JsonNode> response = service
                .getCatalogDetail(0L, "PRODUCT", "HOT_DEAL", 1L, null)
                .block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().path("success").asBoolean()).isFalse();
        verifyNoInteractions(downstreamClient);
    }

    private ResponseEntity<JsonNode> response(HttpStatus status, String source) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("source", source);
        return ResponseEntity.status(status).body(body);
    }
}
