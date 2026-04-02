package com.example.notification.controller.query;

import com.example.api.handler.GlobalExceptionHandler;
import com.example.core.exception.BusinessException;
import com.example.notification.exception.NotificationErrorCode;
import com.example.notification.service.realtime.SseConnectionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("VALID-003")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("X-User-Id")));

        verify(sseConnectionManager, never()).connect(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void subscribe_returnsJsonWhenSseConnectionLimitIsExceeded() throws Exception {
        when(sseConnectionManager.connect(eq(123L)))
                .thenThrow(new BusinessException(NotificationErrorCode.SSE_CONNECTION_LIMIT_EXCEEDED));

        mockMvc.perform(get("/api/v1/notifications/subscribe")
                        .header("X-User-Id", "123"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("NOTIFICATION-203")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("SSE 연결 허용 개수를 초과했습니다.")));
    }
}
