package com.example.notification.controller.api.query;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Notification Realtime", description = "실시간 SSE 알림 API")
public interface NotificationRealtimeApi {

    @Operation(summary = "SSE 연결 구독")
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter subscribe(
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id") Long userId
    );
}
