package com.example.notification.controller.query;

import com.example.api.handler.GlobalExceptionHandler;
import com.example.notification.service.realtime.SseConnectionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationRealtimeControllerTest {

    private SseConnectionManager sseConnectionManager;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        sseConnectionManager = mock(SseConnectionManager.class);
        NotificationRealtimeController controller = new NotificationRealtimeController(sseConnectionManager);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void subscribe_bindsUserIdFromHeader() throws Exception {
        when(sseConnectionManager.connect(eq(123L))).thenReturn(new SseEmitter());

        mockMvc.perform(get("/api/v1/notifications/subscribe")
                        .header("X-User-Id", "123"))
                .andExpect(status().isOk());

        verify(sseConnectionManager).connect(123L);
    }

    @Test
    void subscribe_requiresUserIdHeader() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/subscribe"))
                .andExpect(status().isBadRequest());

        verify(sseConnectionManager, never()).connect(org.mockito.ArgumentMatchers.any());
    }
}
