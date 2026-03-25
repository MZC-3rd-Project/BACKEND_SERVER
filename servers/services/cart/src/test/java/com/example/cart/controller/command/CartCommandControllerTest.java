package com.example.cart.controller.command;

import com.example.api.handler.GlobalExceptionHandler;
import com.example.cart.dto.response.CartCheckoutReservationResponse;
import com.example.cart.service.command.CartCommandService;
import com.example.security.context.AuthContext;
import com.example.security.context.AuthContextHolder;
import com.example.security.gateway.CurrentUserIdArgumentResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CartCommandControllerTest {

    private CartCommandService cartCommandService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        cartCommandService = mock(CartCommandService.class);
        CartCommandController controller = new CartCommandController(cartCommandService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        AuthContextHolder.setContext(AuthContext.builder().userId("123").build());
    }

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    @Test
    void startCheckout_bindsCurrentUserIdAndRequestBody() throws Exception {
        when(cartCommandService.startCheckout(eq(123L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(CartCheckoutReservationResponse.builder().orderId(9001L).build());

        mockMvc.perform(post("/api/v1/cart/checkout/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idempotencyKey": "idem-1",
                                  "lineItems": [
                                    {
                                      "itemId": 101,
                                      "referenceId": 1001,
                                      "channelType": "NORMAL",
                                      "channelRefId": null,
                                      "stockItemType": "ITEM_OPTION",
                                      "quantity": 2
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<com.example.cart.dto.request.StartCartCheckoutRequest> requestCaptor =
                ArgumentCaptor.forClass(com.example.cart.dto.request.StartCartCheckoutRequest.class);
        verify(cartCommandService).startCheckout(eq(123L), requestCaptor.capture());
        assertThat(requestCaptor.getValue().getIdempotencyKey()).isEqualTo("idem-1");
        assertThat(requestCaptor.getValue().getLineItems()).hasSize(1);
        assertThat(requestCaptor.getValue().getLineItems().get(0).getItemId()).isEqualTo(101L);
    }
}
