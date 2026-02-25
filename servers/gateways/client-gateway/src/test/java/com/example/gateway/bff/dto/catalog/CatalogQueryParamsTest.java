package com.example.gateway.bff.dto.catalog;

import com.example.gateway.bff.dto.BffItemType;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogQueryParamsTest {

    @Test
    void from_parsesAndNormalizesValidRequest() {
        ServerHttpRequest request = MockServerHttpRequest.get(
                        "/bff/v1/catalog/items?q=shoe&channel=funding&itemType=product&status=funding"
                                + "&sort=price_desc&minPrice=1000&maxPrice=5000&cursor=c1&size=30")
                .build();

        CatalogQueryParams params = CatalogQueryParams.from(request);
        MultiValueMap<String, String> searchParams = params.toSearchQueryParams();

        assertThat(params.query()).isEqualTo("shoe");
        assertThat(params.channel()).isEqualTo(CatalogSalesChannel.FUNDING);
        assertThat(params.itemType()).isEqualTo(BffItemType.PRODUCT);
        assertThat(params.statuses()).containsExactly("FUNDING");
        assertThat(params.sort()).isEqualTo("PRICE_DESC");
        assertThat(params.minPrice()).isEqualTo(1000L);
        assertThat(params.maxPrice()).isEqualTo(5000L);
        assertThat(params.cursor()).isEqualTo("c1");
        assertThat(params.size()).isEqualTo(30);

        assertThat(searchParams.getFirst("q")).isEqualTo("shoe");
        assertThat(searchParams.getFirst("domainType")).isEqualTo("PRODUCT");
        assertThat(searchParams.get("status")).containsExactly("FUNDING");
        assertThat(searchParams.getFirst("sort")).isEqualTo("PRICE_DESC");
    }

    @Test
    void from_appliesDefaultsWhenOptionalFieldsMissing() {
        ServerHttpRequest request = MockServerHttpRequest.get("/bff/v1/catalog/items?q=shoe").build();

        CatalogQueryParams params = CatalogQueryParams.from(request);
        MultiValueMap<String, String> searchParams = params.toSearchQueryParams();

        assertThat(params.channel()).isEqualTo(CatalogSalesChannel.ALL);
        assertThat(params.sort()).isEqualTo("LATEST");
        assertThat(params.size()).isEqualTo(20);
        assertThat(params.resolvedStatuses()).isEmpty();

        assertThat(searchParams.getFirst("q")).isEqualTo("shoe");
        assertThat(searchParams.getFirst("size")).isEqualTo("20");
        assertThat(searchParams.containsKey("status")).isFalse();
    }

    @Test
    void from_throwsWhenChannelValueIsInvalid() {
        ServerHttpRequest request = MockServerHttpRequest.get("/bff/v1/catalog/items?q=shoe&channel=foo").build();

        assertThatThrownBy(() -> CatalogQueryParams.from(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("channel은 ALL, HOT_DEAL, FUNDING, NORMAL 중 하나여야 합니다");
    }

    @Test
    void from_throwsWhenChannelAndStatusCombinationIsInvalid() {
        ServerHttpRequest request = MockServerHttpRequest.get(
                        "/bff/v1/catalog/items?q=shoe&channel=HOT_DEAL&status=FUNDING")
                .build();

        assertThatThrownBy(() -> CatalogQueryParams.from(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("channel과 status 조합이 유효하지 않습니다");
    }

    @Test
    void from_throwsWhenPriceRangeIsInvalid() {
        ServerHttpRequest request = MockServerHttpRequest.get(
                        "/bff/v1/catalog/items?q=shoe&minPrice=10000&maxPrice=1000")
                .build();

        assertThatThrownBy(() -> CatalogQueryParams.from(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minPrice는 maxPrice보다 클 수 없습니다");
    }

    @Test
    void from_throwsWhenSizeIsOutOfRange() {
        ServerHttpRequest request = MockServerHttpRequest.get("/bff/v1/catalog/items?q=shoe&size=0").build();

        assertThatThrownBy(() -> CatalogQueryParams.from(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size는 1 이상 100 이하여야 합니다");
    }
}
