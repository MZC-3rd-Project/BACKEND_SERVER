package com.example.clients.order.impl;

import com.example.clients.order.dto.OrderCreateLineItem;
import com.example.clients.order.dto.OrderCreateRequest;
import com.example.clients.order.dto.OrderCreateResponse;
import com.example.clients.order.exception.OrderClientConflictException;
import com.example.clients.order.exception.OrderClientRetriableException;
import com.example.clients.order.exception.OrderClientValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultOrderClientFacadeTest {

    private MockWebServer mockWebServer;
    private DefaultOrderClientFacade facade;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        facade = new DefaultOrderClientFacade(WebClient.builder(), objectMapper, mockWebServer.url("/").toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void createOrder_returnsParsedResponse_whenSuccessEnvelopeReturned() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(201)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "success": true,
                          "data": {
                            "orderId": 289581624952246272,
                            "status": "CREATED"
                          }
                        }
                        """));

        OrderCreateResponse response = facade.createOrder(validRequest());

        RecordedRequest request = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/internal/v1/orders");
        assertThat(request.getBody().readUtf8()).contains("\"orderId\":289581624952246272");
        assertThat(response.orderId()).isEqualTo(289581624952246272L);
        assertThat(response.status()).isEqualTo("CREATED");
    }

    @Test
    void createOrder_throwsValidationException_whenOrderServiceReturns400() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(400)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "success": false,
                          "error": {
                            "code": "ORDER-006",
                            "message": "주문 항목이 비어있습니다"
                          }
                        }
                        """));

        assertThatThrownBy(() -> facade.createOrder(validRequest()))
                .isInstanceOf(OrderClientValidationException.class)
                .hasMessageContaining("주문 항목이 비어있습니다");
    }

    @Test
    void createOrder_throwsConflictException_whenOrderServiceReturns409() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(409)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "success": false,
                          "error": {
                            "code": "ORDER-003",
                            "message": "이미 존재하는 주문입니다"
                          }
                        }
                        """));

        assertThatThrownBy(() -> facade.createOrder(validRequest()))
                .isInstanceOf(OrderClientConflictException.class)
                .hasMessageContaining("이미 존재하는 주문입니다");
    }

    @Test
    void createOrder_throwsRetriableException_whenOrderServiceReturns5xx() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(503)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "success": false,
                          "error": {
                            "code": "ORDER-999",
                            "message": "temporary outage"
                          }
                        }
                        """));

        assertThatThrownBy(() -> facade.createOrder(validRequest()))
                .isInstanceOf(OrderClientRetriableException.class)
                .hasMessageContaining("temporary outage");
    }

    @Test
    void createOrder_throwsValidationException_whenRequestIsInvalid() {
        OrderCreateRequest invalidRequest = new OrderCreateRequest(
                null,
                1001L,
                LocalDateTime.of(2026, 3, 11, 12, 30),
                62000L,
                "홍길동",
                "01012345678",
                501L,
                "문 앞에 놓아주세요",
                validRequest().lineItems()
        );

        assertThatThrownBy(() -> facade.createOrder(invalidRequest))
                .isInstanceOf(OrderClientValidationException.class)
                .hasMessageContaining("orderId");
    }

    @Test
    void createOrder_allowsHotDealLineItemWithoutStockMetadata() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(201)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "success": true,
                          "data": {
                            "orderId": 295788441134858240,
                            "status": "CREATED"
                          }
                        }
                        """));

        OrderCreateRequest hotDealRequest = new OrderCreateRequest(
                295788441134858240L,
                1001L,
                LocalDateTime.of(2026, 3, 27, 14, 0),
                15000L,
                "홍길동",
                "01012345678",
                501L,
                "문 앞에 놓아주세요",
                List.of(
                        new OrderCreateLineItem(
                                "HOT_DEAL",
                                295770602109829120L,
                                920001L,
                                "PRODUCT",
                                "핫딜 상품",
                                77L,
                                10L,
                                null,
                                null,
                                null,
                                1,
                                15000L,
                                15000L,
                                15000L
                        )
                )
        );

        OrderCreateResponse response = facade.createOrder(hotDealRequest);

        RecordedRequest request = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getBody().readUtf8()).contains("\"channelType\":\"HOT_DEAL\"");
        assertThat(response.orderId()).isEqualTo(295788441134858240L);
        assertThat(response.status()).isEqualTo("CREATED");
    }

    private OrderCreateRequest validRequest() {
        return new OrderCreateRequest(
                289581624952246272L,
                1001L,
                LocalDateTime.of(2026, 3, 11, 12, 30),
                62000L,
                "홍길동",
                "01012345678",
                501L,
                "문 앞에 놓아주세요",
                List.of(
                        new OrderCreateLineItem(
                                "FUNDING",
                                7001L,
                                920001L,
                                "PERFORMANCE",
                                "MZC 콘서트 펀딩",
                                77L,
                                10L,
                                "SEAT_GRADE",
                                920101L,
                                "R석",
                                2,
                                20000L,
                                25000L,
                                50000L
                        )
                )
        );
    }
}
