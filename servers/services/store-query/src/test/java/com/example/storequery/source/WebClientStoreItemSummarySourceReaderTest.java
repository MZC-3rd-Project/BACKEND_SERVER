package com.example.storequery.source;

import com.example.clients.media.facade.MediaClientFacade;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class WebClientStoreItemSummarySourceReaderTest {

    private MockWebServer mockWebServer;
    private MediaClientFacade mediaClientFacade;

    @BeforeEach
    void setUp() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        mediaClientFacade = mock(MediaClientFacade.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        mockWebServer.shutdown();
    }

    @Test
    void readByStoreId_allowsNullThumbnailMediaId() {
        mockWebServer.enqueue(new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("""
                {
                  "success": true,
                  "data": [
                    {
                      "itemId": 101,
                      "storeId": 201,
                      "sellerId": 301,
                      "title": "sample item",
                      "price": 15000,
                      "itemType": "GOODS",
                      "status": "ON_SALE",
                      "thumbnailMediaId": null,
                      "sourceUpdatedAt": "2026-03-12T22:15:27.942202"
                    }
                  ]
                }
                """));

        WebClientStoreItemSummarySourceReader reader = new WebClientStoreItemSummarySourceReader(
            WebClient.builder(),
            mediaClientFacade,
            mockWebServer.url("/").toString()
        );

        List<StoreItemSummarySource> items = reader.readByStoreId(201L);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).thumbnailMediaId()).isNull();
        assertThat(items.get(0).thumbnailUrl()).isNull();
        verifyNoInteractions(mediaClientFacade);
    }
}
