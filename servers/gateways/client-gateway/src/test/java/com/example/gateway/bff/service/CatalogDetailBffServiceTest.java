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

import java.time.Duration;
import java.time.Instant;
import java.util.List;

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

    @Test
    void getCatalogDetail_enrichesFundingResponse() {
        when(downstreamClient.fetchFundingDetail(eq(77L), any(HttpHeaders.class)))
                .thenReturn(Mono.just(successResponse("""
                        {
                          "id": 77,
                          "itemId": 22,
                          "goalAmount": 1000,
                          "currentAmount": 250
                        }
                        """)));
        when(downstreamClient.fetchItemSummary(eq(22L), any(HttpHeaders.class)))
                .thenReturn(Mono.just(successResponse("""
                        {
                          "id": 22,
                          "itemType": "PRODUCT"
                        }
                        """)));
        when(downstreamClient.fetchNormalDetail(eq(BffItemType.PRODUCT), eq(22L), any(HttpHeaders.class)))
                .thenReturn(Mono.just(successResponse("""
                        {
                          "title": "일반 상품",
                          "price": 9900,
                          "categoryId": 12,
                          "images": {
                            "thumbnail": {
                              "mediaId": 3001
                            }
                          }
                        }
                        """)));
        when(downstreamClient.fetchFundingParticipations(eq(77L), any(HttpHeaders.class)))
                .thenReturn(Mono.just(successArrayResponse("[{\"id\":1},{\"id\":2}]")));
        when(downstreamClient.fetchStockSummary(eq(22L), any(HttpHeaders.class)))
                .thenReturn(Mono.just(successResponse("""
                        {
                          "itemId": 22,
                          "stocks": [
                            {
                              "stockItemType": "ITEM_OPTION",
                              "referenceId": 501,
                              "availableQuantity": 12
                            }
                          ]
                        }
                        """)));
        when(downstreamClient.fetchMediaUrls(eq(List.of(3001L)), any(HttpHeaders.class)))
                .thenReturn(Mono.just(successArrayResponse("""
                        [
                          {
                            "mediaId": 3001,
                            "mediaUrl": "https://cdn.example.com/3001.webp"
                          }
                        ]
                        """)));

        ResponseEntity<JsonNode> response = service
                .getCatalogDetail(22L, "PRODUCT", "FUNDING", null, 77L)
                .block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("data").path("progressRate").asDouble()).isEqualTo(25.0);
        assertThat(response.getBody().path("data").path("supporterCount").asInt()).isEqualTo(2);
        assertThat(response.getBody().path("data").path("title").asText()).isEqualTo("일반 상품");
        assertThat(response.getBody().path("data").path("campaignId").asLong()).isEqualTo(77L);
        assertThat(response.getBody().path("data").path("salesChannel").asText()).isEqualTo("FUNDING");
        assertThat(response.getBody().path("data").path("stock").path("availableQuantity").asInt()).isEqualTo(12);
        assertThat(response.getBody().path("data").path("stock").path("optionStocks").get(0).path("itemOptionId").asLong())
                .isEqualTo(501L);
        assertThat(response.getBody().path("data").path("thumbnailUrl").asText())
                .isEqualTo("https://cdn.example.com/3001.webp");
    }

    @Test
    void getCatalogDetail_enrichesHotDealResponseWithLeftLabel() {
        String endAt = Instant.now().plus(Duration.ofHours(2)).toString();
        when(downstreamClient.fetchHotDealDetail(eq(901L), any(HttpHeaders.class)))
                .thenReturn(Mono.just(successResponse("""
                        {
                          "id": 901,
                          "itemId": 11,
                          "endAt": "%s"
                        }
                        """.formatted(endAt))));
        when(downstreamClient.fetchItemSummary(eq(11L), any(HttpHeaders.class)))
                .thenReturn(Mono.just(successResponse("""
                        {
                          "id": 11,
                          "itemType": "PRODUCT"
                        }
                        """)));
        when(downstreamClient.fetchNormalDetail(eq(BffItemType.PRODUCT), eq(11L), any(HttpHeaders.class)))
                .thenReturn(Mono.just(successResponse("""
                        {
                          "title": "핫딜 상품",
                          "images": {
                            "thumbnail": {
                              "mediaId": 1010
                            }
                          }
                        }
                        """)));
        when(downstreamClient.fetchStockSummary(eq(11L), any(HttpHeaders.class)))
                .thenReturn(Mono.just(successResponse("""
                        {
                          "itemId": 11,
                          "stocks": []
                        }
                        """)));
        when(downstreamClient.fetchMediaUrls(eq(List.of(1010L)), any(HttpHeaders.class)))
                .thenReturn(Mono.just(successArrayResponse("""
                        [
                          {
                            "mediaId": 1010,
                            "mediaUrl": "https://cdn.example.com/1010.webp"
                          }
                        ]
                        """)));

        ResponseEntity<JsonNode> response = service
                .getCatalogDetail(11L, "PRODUCT", "HOT_DEAL", 901L, null)
                .block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("data").path("leftLabel").asText()).isNotBlank();
        assertThat(response.getBody().path("data").path("title").asText()).isEqualTo("핫딜 상품");
        assertThat(response.getBody().path("data").path("salesChannel").asText()).isEqualTo("HOT_DEAL");
        assertThat(response.getBody().path("data").path("thumbnailUrl").asText())
                .isEqualTo("https://cdn.example.com/1010.webp");
    }

    @Test
    void getFundingCampaignDetail_enrichesFundingReadModel() {
        when(downstreamClient.fetchFundingDetail(eq(1001L), any(HttpHeaders.class)))
                .thenReturn(Mono.just(successResponse("""
                        {
                          "id": 1001,
                          "itemId": 2001,
                          "summary": "펀딩 요약",
                          "goalAmount": 1000000,
                          "currentAmount": 500000,
                          "status": "ACTIVE",
                          "rewardOptions": [
                            {
                              "id": 91,
                              "title": "펀딩 얼리버드",
                              "price": 39000,
                              "shippingText": "4월 말 순차배송",
                              "itemOptionId": 501
                            }
                          ]
                        }
                        """)));
        when(downstreamClient.fetchItemSummary(eq(2001L), any(HttpHeaders.class)))
                .thenReturn(Mono.just(successResponse("""
                        {
                          "id": 2001,
                          "itemType": "PRODUCT",
                          "storeId": 31
                        }
                        """)));
        when(downstreamClient.fetchNormalDetail(eq(BffItemType.PRODUCT), eq(2001L), any(HttpHeaders.class)))
                .thenReturn(Mono.just(successResponse("""
                        {
                          "itemId": 2001,
                          "title": "무선 포터블 스피커",
                          "price": 49000,
                          "storeId": 31,
                          "options": [
                            {
                              "id": 501,
                              "optionName": "얼리버드",
                              "additionalPrice": 0
                            }
                          ],
                          "shippingInfo": {
                            "shippingNotice": "4월 말 순차배송"
                          },
                          "images": {
                            "thumbnail": {
                              "mediaId": 3001
                            }
                          }
                        }
                        """)));
        when(downstreamClient.fetchFundingParticipations(eq(1001L), any(HttpHeaders.class)))
                .thenReturn(Mono.just(successArrayResponse("[{\"id\":1},{\"id\":2},{\"id\":3}]")));
        when(downstreamClient.fetchStockSummary(eq(2001L), any(HttpHeaders.class)))
                .thenReturn(Mono.just(successResponse("""
                        {
                          "itemId": 2001,
                          "availableQuantity": 12,
                          "soldQuantity": 18,
                          "soldOut": false,
                          "optionStocks": [
                            {
                              "itemOptionId": 501,
                              "availableQuantity": 12,
                              "soldOut": false
                            }
                          ],
                          "stocks": [
                            {
                              "stockItemType": "ITEM_OPTION",
                              "referenceId": 501,
                              "totalQuantity": 30,
                              "availableQuantity": 12
                            }
                          ]
                        }
                        """)));
        when(downstreamClient.fetchStoreDetail(eq(31L), any(HttpHeaders.class)))
                .thenReturn(Mono.just(successResponse("""
                        {
                          "storeId": 31,
                          "storeName": "도모아랩",
                          "description": "프로젝트를 운영하는 파트너 스토어"
                        }
                        """)));
        when(downstreamClient.fetchMediaUrls(eq(List.of(3001L)), any(HttpHeaders.class)))
                .thenReturn(Mono.just(successArrayResponse("""
                        [
                          {
                            "mediaId": 3001,
                            "mediaUrl": "https://cdn.example.com/items/2001-thumb.jpg"
                          }
                        ]
                        """)));

        ResponseEntity<JsonNode> response = service.getFundingCampaignDetail(1001L).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("data").path("campaignId").isTextual()).isTrue();
        assertThat(response.getBody().path("data").path("campaignId").asText()).isEqualTo("1001");
        assertThat(response.getBody().path("data").path("itemId").isTextual()).isTrue();
        assertThat(response.getBody().path("data").path("itemId").asText()).isEqualTo("2001");
        assertThat(response.getBody().path("data").path("salesChannel").asText()).isEqualTo("FUNDING");
        assertThat(response.getBody().path("data").path("checkout").path("campaignId").isTextual()).isTrue();
        assertThat(response.getBody().path("data").path("checkout").path("campaignId").asText()).isEqualTo("1001");
        assertThat(response.getBody().path("data").path("rewardOptions").get(0).path("title").asText())
                .isEqualTo("펀딩 얼리버드");
        assertThat(response.getBody().path("data").path("rewardOptions").get(0).path("id").isTextual()).isTrue();
        assertThat(response.getBody().path("data").path("rewardOptions").get(0).path("id").asText())
                .isEqualTo("91");
        assertThat(response.getBody().path("data").path("rewardOptions").get(0).path("itemOptionId").isTextual()).isTrue();
        assertThat(response.getBody().path("data").path("rewardOptions").get(0).path("itemOptionId").asText())
                .isEqualTo("501");
        assertThat(response.getBody().path("data").path("stock").path("soldQuantity").asInt()).isEqualTo(18);
        assertThat(response.getBody().path("data").path("stock").path("optionStocks").get(0).path("itemOptionId").isTextual()).isTrue();
        assertThat(response.getBody().path("data").path("stock").path("optionStocks").get(0).path("itemOptionId").asText())
                .isEqualTo("501");
        assertThat(response.getBody().path("data").path("store").path("id").isTextual()).isTrue();
        assertThat(response.getBody().path("data").path("store").path("id").asText()).isEqualTo("31");
        assertThat(response.getBody().path("data").path("thumbnailMediaId").isTextual()).isTrue();
        assertThat(response.getBody().path("data").path("thumbnailMediaId").asText()).isEqualTo("3001");
        assertThat(response.getBody().path("data").path("store").path("name").asText()).isEqualTo("도모아랩");
    }

    private ResponseEntity<JsonNode> response(HttpStatus status, String source) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("source", source);
        return ResponseEntity.status(status).body(body);
    }

    private ResponseEntity<JsonNode> successResponse(String dataJson) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("success", true);
        try {
            body.set("data", objectMapper.readTree(dataJson));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return ResponseEntity.ok(body);
    }

    private ResponseEntity<JsonNode> successArrayResponse(String dataJson) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("success", true);
        try {
            body.set("data", objectMapper.readTree(dataJson));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return ResponseEntity.ok(body);
    }
}
