package com.example.hotdeal.controller;

import com.example.api.handler.GlobalExceptionHandler;
import com.example.hotdeal.dto.QueueEnterResponse;
import com.example.hotdeal.service.HotDealCommandService;
import com.example.hotdeal.service.HotDealPurchaseService;
import com.example.hotdeal.service.QueueSseEventPublisher;
import com.example.hotdeal.service.QueueSseService;
import com.example.hotdeal.service.QueueService;
import com.example.hotdeal.service.checkout.HotDealCheckoutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HotDealCommandControllerTest {

    private QueueService queueService;
    private QueueSseEventPublisher queueSseEventPublisher;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        HotDealCommandService hotDealCommandService = mock(HotDealCommandService.class);
        HotDealPurchaseService hotDealPurchaseService = mock(HotDealPurchaseService.class);
        HotDealCheckoutService hotDealCheckoutService = mock(HotDealCheckoutService.class);
        queueService = mock(QueueService.class);
        QueueSseService queueSseService = mock(QueueSseService.class);
        queueSseEventPublisher = mock(QueueSseEventPublisher.class);

        HotDealCommandController controller = new HotDealCommandController(
                hotDealCommandService,
                hotDealPurchaseService,
                hotDealCheckoutService,
                queueService,
                queueSseService,
                queueSseEventPublisher
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void enterQueue_bindsPathVariableAndUserHeader() throws Exception {
        when(queueService.enter(eq(55L), eq(777L)))
                .thenReturn(QueueEnterResponse.builder().token("queue-token").position(1L).estimatedWaitSeconds(15L).build());

        mockMvc.perform(post("/api/v1/hot-deals/55/queue/enter")
                        .header("X-User-Id", "777"))
                .andExpect(status().isOk());

        verify(queueService).enter(55L, 777L);
        verify(queueSseEventPublisher).publishQueueStatus(55L, 777L, 1L, false);
    }
}
