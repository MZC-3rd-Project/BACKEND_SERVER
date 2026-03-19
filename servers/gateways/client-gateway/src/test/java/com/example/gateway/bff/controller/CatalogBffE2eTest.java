package com.example.gateway.bff.controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class CatalogBffE2eTest {

    private static final int DOWNSTREAM_PORT = findAvailablePort();
    private static final String DOWNSTREAM_BASE_URL = "http://127.0.0.1:" + DOWNSTREAM_PORT;

    private static final AtomicReference<DownstreamDispatcher> DISPATCHER = new AtomicReference<>();
    private static final List<RequestRecord> REQUESTS = new CopyOnWriteArrayList<>();

    private static HttpServer downstreamServer;

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("app.service.search-url", () -> DOWNSTREAM_BASE_URL);
        registry.add("app.service.media-url", () -> DOWNSTREAM_BASE_URL);
        registry.add("app.service.product-url", () -> DOWNSTREAM_BASE_URL);
        registry.add("app.service.funding-url", () -> DOWNSTREAM_BASE_URL);
        registry.add("app.service.hot-deal-url", () -> DOWNSTREAM_BASE_URL);
        registry.add("app.service.stock-url", () -> DOWNSTREAM_BASE_URL);
        registry.add("app.service.store-query-url", () -> DOWNSTREAM_BASE_URL);
        registry.add("gateway.auth.enabled", () -> "false");
        registry.add("gateway.session.enabled", () -> "false");
    }

    @BeforeAll
    static void startDownstreamServer() throws IOException {
        downstreamServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), DOWNSTREAM_PORT), 0);
        downstreamServer.createContext("/", new DispatchingHandler());
        downstreamServer.setExecutor(Executors.newCachedThreadPool());
        downstreamServer.start();
    }

    @AfterAll
    static void stopDownstreamServer() {
        if (downstreamServer != null) {
            downstreamServer.stop(0);
        }
    }

    @BeforeEach
    void setUp() {
        REQUESTS.clear();
        DISPATCHER.set(request -> StubResponse.json(404, "{\"success\":false,\"error\":{\"message\":\"not found\"}}"));
    }

    @Test
    void listCatalogItems_returnsChannelPriorityOrderAndEventReflectedTargets() {
        DISPATCHER.set(request -> {
            if ("GET".equals(request.method()) && "/api/v1/search".equals(request.path())) {
                String body = """
                        {
                          "success": true,
                          "data": {
                            "items": [
                              {
                                "itemId": 101,
                                "title": "hot",
                                "domainType": "PRODUCT",
                                "status": "HOT_DEAL",
                                "salesChannel": "HOT_DEAL",
                                "activeHotDealId": 9001,
                                "basePrice": 20000,
                                "effectivePrice": 15000,
                                "stock": 11,
                                "thumbnailMediaId": 501,
                                "thumbnailUrl": "https://cdn.example/hot.webp"
                              },
                              {
                                "itemId": 102,
                                "title": "funding",
                                "domainType": "PRODUCT",
                                "status": "FUNDING",
                                "salesChannel": "FUNDING",
                                "activeCampaignId": 7001,
                                "basePrice": 30000,
                                "effectivePrice": 30000,
                                "stock": 8,
                                "thumbnailMediaId": 502,
                                "thumbnailUrl": "https://cdn.example/funding.webp"
                              },
                              {
                                "itemId": 103,
                                "title": "normal",
                                "domainType": "PRODUCT",
                                "status": "ON_SALE",
                                "salesChannel": "NORMAL",
                                "basePrice": 10000,
                                "effectivePrice": 10000,
                                "stock": 20,
                                "thumbnailMediaId": 503,
                                "thumbnailUrl": "https://cdn.example/normal.webp"
                              }
                            ],
                            "nextCursor": null,
                            "totalCount": 3
                          }
                        }
                        """;
                return StubResponse.json(200, body);
            }
            return StubResponse.json(404, "{\"success\":false}");
        });

        webTestClient.get()
                .uri("/bff/v1/catalog/items?q=running&size=3")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.items.length()").isEqualTo(3)
                .jsonPath("$.data.items[0].itemId").isEqualTo(101)
                .jsonPath("$.data.items[0].salesChannel").isEqualTo("HOT_DEAL")
                .jsonPath("$.data.items[0].detailTarget.path")
                .isEqualTo("/bff/v1/catalog/items/101/detail?itemType=PRODUCT&salesChannel=HOT_DEAL&hotDealId=9001")
                .jsonPath("$.data.items[1].itemId").isEqualTo(102)
                .jsonPath("$.data.items[1].salesChannel").isEqualTo("FUNDING")
                .jsonPath("$.data.items[1].detailTarget.path")
                .isEqualTo("/bff/v1/catalog/items/102/detail?itemType=PRODUCT&salesChannel=FUNDING&campaignId=7001")
                .jsonPath("$.data.items[2].itemId").isEqualTo(103)
                .jsonPath("$.data.items[2].salesChannel").isEqualTo("NORMAL");

        List<RequestRecord> searchRequests = REQUESTS.stream()
                .filter(request -> "/api/v1/search".equals(request.path()))
                .toList();
        assertThat(searchRequests).hasSize(1);
        assertThat(searchRequests.get(0).query()).contains("sort=LATEST");
    }

    @Test
    void getCatalogDetail_fallbacksToNormalWhenHotDealReturns404() {
        DISPATCHER.set(request -> {
            if ("GET".equals(request.method()) && "/api/v1/hot-deals/9001".equals(request.path())) {
                return StubResponse.json(404, "{\"success\":false,\"error\":{\"message\":\"hot-deal not found\"}}");
            }
            if ("GET".equals(request.method()) && "/api/products/101".equals(request.path())) {
                return StubResponse.json(200, """
                        {
                          "success": true,
                          "data": {
                            "itemId": 101,
                            "title": "fallback-normal"
                          }
                        }
                        """);
            }
            return StubResponse.json(404, "{\"success\":false}");
        });

        webTestClient.get()
                .uri("/bff/v1/catalog/items/101/detail?itemType=PRODUCT&salesChannel=HOT_DEAL&hotDealId=9001")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.itemId").isEqualTo(101)
                .jsonPath("$.data.title").isEqualTo("fallback-normal");

        List<String> paths = REQUESTS.stream()
                .map(RequestRecord::path)
                .toList();
        assertThat(paths).contains("/api/v1/hot-deals/9001", "/api/products/101");
    }

    @Test
    void fundingBff_allowsCorsForConfiguredOrigin() {
        DISPATCHER.set(request -> {
            if ("GET".equals(request.method()) && "/api/campaigns".equals(request.path())) {
                return StubResponse.json(200, """
                        {
                          "success": true,
                          "data": {
                            "items": [
                              {
                                "id": 101,
                                "itemId": 501,
                                "title": "cors-check",
                                "goalAmount": 100000,
                                "currentAmount": 25000,
                                "currentQuantity": 2,
                                "goalQuantity": 10,
                                "status": "ACTIVE",
                                "endAt": "2099-12-31T23:59:59"
                              }
                            ],
                            "size": 1,
                            "hasNext": false
                          }
                        }
                        """);
            }
            if ("GET".equals(request.method()) && "/api/v1/items/batch".equals(request.path())) {
                return StubResponse.json(200, """
                        {
                          "success": true,
                          "data": [
                            {
                              "id": 501,
                              "images": {
                                "thumbnail": {
                                  "mediaId": 9001
                                }
                              }
                            }
                          ]
                        }
                        """);
            }
            if ("GET".equals(request.method()) && "/api/campaigns/101/participations".equals(request.path())) {
                return StubResponse.json(200, """
                        {
                          "success": true,
                          "data": [
                            {"id": 1}
                          ]
                        }
                        """);
            }
            if ("GET".equals(request.method()) && "/api/v1/media/urls".equals(request.path())) {
                return StubResponse.json(200, """
                        {
                          "success": true,
                          "data": [
                            {
                              "mediaId": 9001,
                              "mediaUrl": "https://cdn.example.com/funding/9001.png"
                            }
                          ]
                        }
                        """);
            }
            return StubResponse.json(404, "{\"success\":false}");
        });

        webTestClient.get()
                .uri("/bff/v1/funding/campaigns?size=1")
                .header("Origin", "http://localhost:3001")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueMatches("Access-Control-Allow-Origin", "http://localhost:3001|\\*")
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.items.length()").isEqualTo(1);
    }

    private static int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("사용 가능한 포트를 찾지 못했습니다", e);
        }
    }

    private static byte[] readBody(InputStream inputStream) throws IOException {
        return inputStream.readAllBytes();
    }

    private static final class DispatchingHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] requestBody = readBody(exchange.getRequestBody());
            URI requestUri = exchange.getRequestURI();
            REQUESTS.add(new RequestRecord(
                    exchange.getRequestMethod(),
                    requestUri.getPath(),
                    requestUri.getRawQuery(),
                    new String(requestBody, StandardCharsets.UTF_8)
            ));

            DownstreamDispatcher dispatcher = DISPATCHER.get();
            StubResponse response = dispatcher == null
                    ? StubResponse.json(500, "{\"success\":false}")
                    : dispatcher.dispatch(new RequestRecord(
                    exchange.getRequestMethod(),
                    requestUri.getPath(),
                    requestUri.getRawQuery(),
                    new String(requestBody, StandardCharsets.UTF_8)
            ));

            byte[] payload = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.status(), payload.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(payload);
            } finally {
                exchange.close();
            }
        }
    }

    @FunctionalInterface
    private interface DownstreamDispatcher {
        StubResponse dispatch(RequestRecord request);
    }

    private record StubResponse(int status, String body) {
        static StubResponse json(int status, String body) {
            return new StubResponse(status, body);
        }
    }

    private record RequestRecord(String method, String path, String query, String body) {
    }
}
