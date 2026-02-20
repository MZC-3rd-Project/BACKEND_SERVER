package com.example.chat.controller.ws;

import com.example.contracts.http.HttpHeaderNames;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ChatHandshakeInterceptorTest {

    private final ChatHandshakeInterceptor interceptor = new ChatHandshakeInterceptor();
    private final WebSocketHandler wsHandler = mock(WebSocketHandler.class);

    @Test
    void beforeHandshake_storesUserIdAndRoles() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/ws/chat");
        servletRequest.addHeader(HttpHeaderNames.USER_ID, "101");
        servletRequest.addHeader(HttpHeaderNames.USER_ROLES, "ROLE_USER, ROLE_PLATFORM_ADMIN");

        Map<String, Object> attributes = new HashMap<>();
        boolean accepted = interceptor.beforeHandshake(
                new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(new MockHttpServletResponse()),
                wsHandler,
                attributes
        );

        assertThat(accepted).isTrue();
        assertThat(attributes.get(ChatHandshakeInterceptor.ATTR_USER_ID)).isEqualTo(101L);
        assertThat(attributes.get(ChatHandshakeInterceptor.ATTR_ROLES)).isEqualTo(
                List.of("ROLE_USER", "ROLE_PLATFORM_ADMIN")
        );
    }

    @Test
    void beforeHandshake_rejectsWhenUserIdHeaderMissing() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/ws/chat");
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(
                new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(new MockHttpServletResponse()),
                wsHandler,
                attributes
        );

        assertThat(accepted).isFalse();
        assertThat(attributes).isEmpty();
    }
}
