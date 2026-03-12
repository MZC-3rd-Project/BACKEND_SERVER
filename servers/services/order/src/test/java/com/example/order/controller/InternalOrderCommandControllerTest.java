package com.example.order.controller;

import com.example.order.domain.Order;
import com.example.order.domain.OrderRepository;
import com.example.order.domain.OrderStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@EmbeddedKafka(partitions = 1, topics = {"payment-events", "order-events"})
class InternalOrderCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
    }

    @Test
    @DisplayName("정상 주문 생성 → 200, status: PAYMENT_PENDING")
    void createOrder_success() throws Exception {
        String requestBody = """
                {
                    "orderId": 12345,
                    "userId": 100,
                    "totalAmount": 50000,
                    "recipientName": "홍길동",
                    "recipientPhone": "010-1234-5678",
                    "deliveryAddressId": 1,
                    "deliveryMemo": "문 앞에 놓아주세요",
                    "lineItems": [
                        {
                            "channelType": "NORMAL",
                            "itemId": 1001,
                            "storeId": 10,
                            "quantity": 2,
                            "finalUnitPrice": 25000,
                            "lineAmount": 50000
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/internal/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PAYMENT_PENDING"));

        Order saved = orderRepository.findById(12345L).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(saved.getOrderItems()).hasSize(1);
    }

    @Test
    @DisplayName("중복 orderId로 주문 생성 → 409")
    void createOrder_duplicate_returns409() throws Exception {
        // given: 미리 주문 생성
        Order existing = Order.create(12345L, 100L, 50000L, null, null, null, null, null);
        orderRepository.save(existing);

        String requestBody = """
                {
                    "orderId": 12345,
                    "userId": 100,
                    "totalAmount": 30000,
                    "lineItems": [
                        {
                            "channelType": "NORMAL",
                            "itemId": 1001,
                            "storeId": 10,
                            "quantity": 1,
                            "finalUnitPrice": 30000,
                            "lineAmount": 30000
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/internal/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("필수 필드 누락 → 400")
    void createOrder_missingRequiredField_returns400() throws Exception {
        String requestBody = """
                {
                    "orderId": 12345,
                    "userId": 100,
                    "lineItems": []
                }
                """;

        mockMvc.perform(post("/internal/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("내부 주문 조회 → 200")
    void getOrder_success() throws Exception {
        Order order = Order.create(99999L, 100L, 50000L, "홍길동", "010-1234-5678", 1L, "메모", null);
        orderRepository.save(order);

        mockMvc.perform(get("/internal/v1/orders/99999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.data.userId").value(100));
    }

    @Test
    @DisplayName("존재하지 않는 주문 조회 → 404")
    void getOrder_notFound() throws Exception {
        mockMvc.perform(get("/internal/v1/orders/99999"))
                .andExpect(status().isNotFound());
    }
}
