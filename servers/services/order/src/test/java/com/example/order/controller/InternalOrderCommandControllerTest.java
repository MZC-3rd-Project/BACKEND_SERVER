package com.example.order.controller;

import com.example.core.exception.BusinessException;
import com.example.core.util.JsonUtils;
import com.example.order.domain.ChannelType;
import com.example.order.dto.request.InternalCreateOrderRequest;
import com.example.order.dto.response.InternalCreateOrderResponse;
import com.example.order.exception.OrderErrorCode;
import com.example.order.service.command.OrderCommandService;
import com.example.order.service.query.OrderQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = com.example.order.controller.command.InternalOrderCommandController.class)
@WithMockUser
class InternalOrderCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderCommandService orderCommandService;

    @MockBean
    private OrderQueryService orderQueryService;

    @Test
    @DisplayName("POST /internal/v1/orders - 정상 생성 → 200, status: PAYMENT_PENDING")
    void createOrderSuccess() throws Exception {
        // given
        InternalCreateOrderResponse response = InternalCreateOrderResponse.builder()
                .orderId(1L)
                .status("PAYMENT_PENDING")
                .createdAt(LocalDateTime.now())
                .build();
        given(orderCommandService.createOrder(any())).willReturn(response);

        String requestBody = createRequestJson(1L);

        // when & then
        mockMvc.perform(post("/internal/v1/orders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAYMENT_PENDING"));
    }

    @Test
    @DisplayName("POST /internal/v1/orders - 중복 생성 → 409")
    void createOrderDuplicate() throws Exception {
        // given
        given(orderCommandService.createOrder(any()))
                .willThrow(new BusinessException(OrderErrorCode.ORDER_ALREADY_EXISTS));

        String requestBody = createRequestJson(1L);

        // when & then
        mockMvc.perform(post("/internal/v1/orders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /internal/v1/orders - 필수 필드 누락 → 400")
    void createOrderMissingFields() throws Exception {
        // given - orderId 없는 요청
        String requestBody = """
                {
                    "userId": 100,
                    "totalAmount": 50000,
                    "lineItems": []
                }
                """;

        // when & then
        mockMvc.perform(post("/internal/v1/orders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    private String createRequestJson(Long orderId) {
        return """
                {
                    "orderId": %d,
                    "userId": 100,
                    "totalAmount": 50000,
                    "expiresAt": "2026-03-11T12:00:00",
                    "recipientName": "홍길동",
                    "recipientPhone": "010-1234-5678",
                    "deliveryAddressId": 1,
                    "deliveryMemo": "문 앞에 놓아주세요",
                    "lineItems": [
                        {
                            "channelType": "NORMAL",
                            "itemId": 10,
                            "storeId": 1,
                            "quantity": 2,
                            "finalUnitPrice": 25000,
                            "lineAmount": 50000
                        }
                    ]
                }
                """.formatted(orderId);
    }
}
