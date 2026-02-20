package com.example.chat.controller.ws;

import com.example.contracts.http.HttpHeaderNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Slf4j
@Component
public class ChatHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER_ID = "chatUserId";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String userIdHeader = request.getHeaders().getFirst(HttpHeaderNames.USER_ID);
        if (!StringUtils.hasText(userIdHeader)) {
            log.warn("WebSocket handshake rejected. Missing X-User-Id header");
            return false;
        }

        try {
            long userId = Long.parseLong(userIdHeader);
            if (userId <= 0) {
                return false;
            }
            attributes.put(ATTR_USER_ID, userId);
            return true;
        } catch (NumberFormatException e) {
            log.warn("WebSocket handshake rejected. Invalid X-User-Id={}", userIdHeader);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // no-op
    }
}
