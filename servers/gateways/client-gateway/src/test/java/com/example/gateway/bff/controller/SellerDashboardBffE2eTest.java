package com.example.gateway.bff.controller;

import com.example.contracts.http.HttpHeaderNames;
import com.example.security.gateway.GatewayContextHeaderCodec;
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
class SellerDashboardBffE2eTest {

    private static final int DOWNSTREAM_PORT = findAvailablePort();
    private static final String DOWNSTREAM_BASE_URL = "http://127.0.0.1:" + DOWNSTREAM_PORT;

    private static final AtomicReference<DownstreamDispatcher> DISPATCHER = new AtomicReference<>();
    private static final List<RequestRecord> REQUESTS = new CopyOnWriteArrayList<>();

    private static HttpServer downstreamServer;

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("app.service.analytics-dashboard-url", () -> DOWNSTREAM_BASE_URL);
        registry.add("gateway.auth.enabled", () -> "false");
        registry.add("gateway.session.enabled", () -> "false");
        registry.add("gateway.security.internal-auth-token", () -> "test-internal-token");
        registry.add("app.security.context.signing-key", () -> "test-signing-key");
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
    void getOverview_forwardsSignedGatewayContextToAnalytics() {
        DISPATCHER.set(request -> {
            if ("GET".equals(request.method())
                    && "/internal/v1/analytics/stores/292483518599004160/dashboard/overview".equals(request.path())) {
                return StubResponse.json(200, """
                        {
                          "success": true,
                          "data": {
                            "mode": "RANGE"
                          }
                        }
                        """);
            }
            return StubResponse.json(404, "{\"success\":false}");
        });

        webTestClient.get()
                .uri("/bff/v1/seller/dashboard/overview?storeId=292483518599004160&mode=range&from=2026-03-01&to=2026-03-20")
                .header(HttpHeaderNames.USER_ID, "9000001")
                .header(HttpHeaderNames.USER_ROLES, "USER,SELLER")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.mode").isEqualTo("RANGE");

        assertThat(REQUESTS).hasSize(1);
        RequestRecord request = REQUESTS.getFirst();
        assertThat(request.path()).isEqualTo("/internal/v1/analytics/stores/292483518599004160/dashboard/overview");
        assertThat(request.query()).contains("mode=range");
        assertThat(request.header(HttpHeaderNames.USER_ID)).isEqualTo("9000001");
        assertThat(request.header(HttpHeaderNames.STORE_ID)).isEqualTo("292483518599004160");
        assertThat(request.header("X-Gateway-Auth")).isEqualTo("test-internal-token");

        String gatewayContext = request.header(HttpHeaderNames.GATEWAY_CONTEXT);
        assertThat(gatewayContext).isNotBlank();
        GatewayContextHeaderCodec.ParsedGatewayContext parsed = GatewayContextHeaderCodec.decode(gatewayContext);
        assertThat(parsed.userId()).isEqualTo("9000001");
        assertThat(parsed.roles()).isEqualTo("USER,SELLER");
    }

    private interface DownstreamDispatcher {
        StubResponse dispatch(RequestRecord request);
    }

    private record StubResponse(int status, String body) {
        static StubResponse json(int status, String body) {
            return new StubResponse(status, body);
        }
    }

    private record RequestRecord(String method, String path, String query, java.util.Map<String, List<String>> headers,
                                 String body) {
        String header(String name) {
            List<String> values = headers.get(name);
            return values == null || values.isEmpty() ? null : values.getFirst();
        }
    }

    private static final class DispatchingHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            RequestRecord request = new RequestRecord(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getRawQuery(),
                    exchange.getRequestHeaders(),
                    readBody(exchange)
            );
            REQUESTS.add(request);

            StubResponse response = DISPATCHER.get().dispatch(request);
            byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.status(), bytes.length);
            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(bytes);
            }
        }

        private String readBody(HttpExchange exchange) throws IOException {
            try (InputStream inputStream = exchange.getRequestBody()) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    private static int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("사용 가능한 포트를 찾지 못했습니다", e);
        }
    }
}
