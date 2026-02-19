package com.example.notification.service.realtime;

import com.example.core.exception.BusinessException;
import com.example.notification.config.NotificationSseProperties;
import com.example.notification.exception.NotificationErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SseConnectionManagerTest {

    @Test
    void connect_rejectsWhenPerUserConnectionLimitExceeded() {
        NotificationSseProperties properties = new NotificationSseProperties();
        properties.setMaxConnectionsPerUser(1);
        properties.setMaxTotalConnections(10);

        SseConnectionManager manager = new SseConnectionManager(properties);
        SseEmitter first = manager.connect(1L);
        assertThat(first).isNotNull();

        BusinessException exception = assertThrows(BusinessException.class, () -> manager.connect(1L));
        assertThat(exception.getErrorCode()).isEqualTo(NotificationErrorCode.SSE_CONNECTION_LIMIT_EXCEEDED);
        assertThat(manager.connectionCount(1L)).isEqualTo(1);
    }

    @Test
    void connect_rejectsWhenTotalConnectionLimitExceeded() {
        NotificationSseProperties properties = new NotificationSseProperties();
        properties.setMaxConnectionsPerUser(3);
        properties.setMaxTotalConnections(1);

        SseConnectionManager manager = new SseConnectionManager(properties);
        SseEmitter first = manager.connect(1L);
        assertThat(first).isNotNull();

        BusinessException exception = assertThrows(BusinessException.class, () -> manager.connect(2L));
        assertThat(exception.getErrorCode()).isEqualTo(NotificationErrorCode.SSE_CONNECTION_LIMIT_EXCEEDED);
        assertThat(manager.totalConnectionCount()).isEqualTo(1);
    }
}
