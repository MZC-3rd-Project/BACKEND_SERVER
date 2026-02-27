package com.example.notification.controller.query;

import com.example.notification.controller.api.query.NotificationRealtimeApi;
import com.example.notification.service.realtime.SseConnectionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationRealtimeController implements NotificationRealtimeApi {

    private final SseConnectionManager sseConnectionManager;

    @Override
    public SseEmitter subscribe(Long userId) {
        return sseConnectionManager.connect(userId);
    }
}
