package com.example.gateway.bff.service;

import com.example.gateway.bff.dto.BffItemType;
import com.example.gateway.bff.dto.catalog.CatalogItemCardResponse;
import com.example.gateway.bff.dto.catalog.CatalogItemsResponse;
import com.example.gateway.bff.dto.catalog.CatalogQueryParams;
import com.example.gateway.bff.dto.catalog.CatalogSalesChannel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogResponseMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CatalogResponseMapper mapper = new CatalogResponseMapper();

    @Test
    void toCatalogResponse_mapsSalesChannelAndDetailTarget() throws Exception {
        JsonNode searchBody = objectMapper.readTree("""
                {
                  "success": true,
                  "data": {
                    "items": [
                      {
                        "itemId": 11,
                        "title": "hot",
                        "domainType": "PRODUCT",
                        "status": "HOT_DEAL",
                        "activeHotDealId": 901,
                        "price": 10000
                      },
                      {
                        "itemId": 22,
                        "title": "fund",
                        "domainType": "GOODS",
                        "status": "FUNDING",
                        "activeCampaignId": 301,
                        "price": 20000
                      },
                      {
                        "itemId": 33,
                        "title": "normal",
                        "domainType": "PERFORMANCE",
                        "status": "ON_SALE",
                        "price": 30000
                      }
                    ],
                    "nextCursor": "n1",
                    "totalCount": 3
                  }
                }
                """);

        CatalogItemsResponse response = mapper.toCatalogResponse(searchBody, defaultParams());
        String searchQueryHash = hashQuery("run");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isNotNull();
        assertThat(response.data().items()).hasSize(3);

        CatalogItemCardResponse hotDeal = response.data().items().get(0);
        assertThat(hotDeal.salesChannel()).isEqualTo(CatalogSalesChannel.HOT_DEAL);
        assertThat(hotDeal.detailTarget().type()).isEqualTo("HOT_DEAL");
        assertThat(hotDeal.detailTarget().path())
                .isEqualTo("/bff/v1/catalog/items/11/detail?itemType=PRODUCT&salesChannel=HOT_DEAL&hotDealId=901&searchQueryHash=" + searchQueryHash);

        CatalogItemCardResponse funding = response.data().items().get(1);
        assertThat(funding.salesChannel()).isEqualTo(CatalogSalesChannel.FUNDING);
        assertThat(funding.detailTarget().type()).isEqualTo("FUNDING");
        assertThat(funding.detailTarget().path())
                .isEqualTo("/bff/v1/catalog/items/22/detail?itemType=GOODS&salesChannel=FUNDING&campaignId=301&searchQueryHash=" + searchQueryHash);

        CatalogItemCardResponse normal = response.data().items().get(2);
        assertThat(normal.salesChannel()).isEqualTo(CatalogSalesChannel.NORMAL);
        assertThat(normal.detailTarget().type()).isEqualTo("NORMAL");
        assertThat(normal.detailTarget().path()).isEqualTo("/bff/v1/items/33?type=PERFORMANCE&searchQueryHash=" + searchQueryHash);
    }

    @Test
    void toCatalogResponse_usesRequestItemTypeWhenDomainTypeMissing() throws Exception {
        JsonNode searchBody = objectMapper.readTree("""
                {
                  "success": true,
                  "data": {
                    "items": [
                      {"itemId": 101, "title": "item", "status": "ON_SALE", "price": 12000}
                    ],
                    "nextCursor": null,
                    "totalCount": 1
                  }
                }
                """);

        CatalogQueryParams params = new CatalogQueryParams(
                "run", null, BffItemType.PRODUCT, CatalogSalesChannel.ALL,
                List.of(), null, null, "LATEST", null, 20
        );

        CatalogItemsResponse response = mapper.toCatalogResponse(searchBody, params);
        CatalogItemCardResponse item = response.data().items().get(0);

        assertThat(item.itemType()).isEqualTo(BffItemType.PRODUCT);
        assertThat(item.detailTarget().path()).isEqualTo("/bff/v1/items/101?type=PRODUCT&searchQueryHash=" + hashQuery("run"));
    }

    @Test
    void toCatalogResponse_filtersByChannel() throws Exception {
        JsonNode searchBody = objectMapper.readTree("""
                {
                  "success": true,
                  "data": {
                    "items": [
                      {"itemId": 1, "domainType": "PRODUCT", "status": "ON_SALE", "price": 12000},
                      {"itemId": 2, "domainType": "PRODUCT", "status": "FUNDING", "price": 14000}
                    ],
                    "nextCursor": null,
                    "totalCount": 2
                  }
                }
                """);

        CatalogQueryParams params = new CatalogQueryParams(
                "run", null, null, CatalogSalesChannel.FUNDING,
                List.of(), null, null, "LATEST", null, 20
        );

        CatalogItemsResponse response = mapper.toCatalogResponse(searchBody, params);

        assertThat(response.data().items()).hasSize(1);
        assertThat(response.data().items().get(0).salesChannel()).isEqualTo(CatalogSalesChannel.FUNDING);
    }

    @Test
    void toCatalogResponse_mapsFundingRouteWithoutCampaignId() throws Exception {
        JsonNode searchBody = objectMapper.readTree("""
                {
                  "success": true,
                  "data": {
                    "items": [
                      {"itemId": 2, "domainType": "PRODUCT", "status": "FUNDING", "price": 14000}
                    ],
                    "nextCursor": null,
                    "totalCount": 1
                  }
                }
                """);

        CatalogItemsResponse response = mapper.toCatalogResponse(searchBody, defaultParams());
        String searchQueryHash = hashQuery("run");

        assertThat(response.data().items()).hasSize(1);
        CatalogItemCardResponse funding = response.data().items().get(0);
        assertThat(funding.detailTarget().type()).isEqualTo("FUNDING");
        assertThat(funding.detailTarget().path())
                .isEqualTo("/bff/v1/catalog/items/2/detail?itemType=PRODUCT&salesChannel=FUNDING&searchQueryHash=" + searchQueryHash);
    }

    @Test
    void toCatalogResponse_throwsWhenSearchBodyIsInvalid() throws Exception {
        JsonNode invalid = objectMapper.readTree("""
                {
                  "success": false,
                  "error": {"code": "S-400", "message": "invalid"}
                }
                """);

        assertThatThrownBy(() -> mapper.toCatalogResponse(invalid, defaultParams()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("검색 응답 형식이 올바르지 않습니다");
    }

    private CatalogQueryParams defaultParams() {
        return new CatalogQueryParams(
                "run",
                null,
                null,
                CatalogSalesChannel.ALL,
                List.of(),
                null,
                null,
                "LATEST",
                null,
                20
        );
    }

    private String hashQuery(String rawQuery) throws Exception {
        MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
        byte[] digest = messageDigest.digest(rawQuery.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
