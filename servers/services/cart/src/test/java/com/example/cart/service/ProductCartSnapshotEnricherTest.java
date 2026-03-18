package com.example.cart.service;

import com.example.clients.media.facade.MediaClientFacade;
import com.example.clients.product.facade.ProductItemQueryClientFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCartSnapshotEnricherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ProductItemQueryClientFacade productItemQueryClientFacade;

    @Mock
    private StoreQueryStoreNameClient storeQueryStoreNameClient;

    @Mock
    private MediaClientFacade mediaClientFacade;

    @Test
    @DisplayName("thumbnailUrl이 없으면 thumbnail mediaId로 media url을 조회한다")
    void enrich_resolvesThumbnailUrlFromMediaId() throws Exception {
        ProductCartSnapshotEnricher enricher = new ProductCartSnapshotEnricher(
            productItemQueryClientFacade,
            storeQueryStoreNameClient,
            mediaClientFacade
        );

        when(productItemQueryClientFacade.findItem(100L)).thenReturn(objectMapper.readTree("""
            {
              "storeId": "200",
              "title": "item-title",
              "price": 12000,
              "status": "ON_SALE",
              "images": {
                "thumbnail": {
                  "mediaId": "300"
                }
              }
            }
            """));
        when(storeQueryStoreNameClient.findStoreName(200L)).thenReturn("store-name");
        when(mediaClientFacade.getMediaUrl(300L)).thenReturn("https://cdn.example.com/item-300.png");

        CartSnapshotData snapshot = enricher.enrich(100L);

        assertThat(snapshot.storeId()).isEqualTo(200L);
        assertThat(snapshot.itemTitle()).isEqualTo("item-title");
        assertThat(snapshot.thumbnailUrl()).isEqualTo("https://cdn.example.com/item-300.png");
        assertThat(snapshot.storeName()).isEqualTo("store-name");
        assertThat(snapshot.displayPrice()).isEqualTo(12000L);
        assertThat(snapshot.salesStatus()).isEqualTo("ON_SALE");
    }

    @Test
    @DisplayName("top-level thumbnailUrl이 있으면 media 조회 없이 그대로 사용한다")
    void enrich_keepsExistingThumbnailUrl() throws Exception {
        ProductCartSnapshotEnricher enricher = new ProductCartSnapshotEnricher(
            productItemQueryClientFacade,
            storeQueryStoreNameClient,
            mediaClientFacade
        );

        when(productItemQueryClientFacade.findItem(101L)).thenReturn(objectMapper.readTree("""
            {
              "storeId": "201",
              "title": "item-title",
              "price": 15000,
              "status": "ON_SALE",
              "thumbnailUrl": "https://cdn.example.com/existing.png",
              "images": {
                "thumbnail": {
                  "mediaId": "301"
                }
              }
            }
            """));

        CartSnapshotData snapshot = enricher.enrich(101L);

        assertThat(snapshot.thumbnailUrl()).isEqualTo("https://cdn.example.com/existing.png");
    }
}
