package com.example.security.gateway;

import com.example.security.context.AuthContext;
import com.example.security.context.AuthContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserIdArgumentResolverTest {

    private final HandlerMethodArgumentResolver resolver = new CurrentUserIdArgumentResolver();

    @AfterEach
    void clearContext() {
        AuthContextHolder.clear();
    }

    @Test
    void supportsParameter_returnsTrueForAnnotatedLong() throws Exception {
        Method method = Fixture.class.getDeclaredMethod("required", Long.class);
        MethodParameter parameter = new MethodParameter(method, 0);

        assertThat(resolver.supportsParameter(parameter)).isTrue();
    }

    @Test
    void resolveArgument_returnsParsedUserId() throws Exception {
        AuthContextHolder.setContext(AuthContext.builder().userId("101").build());
        Method method = Fixture.class.getDeclaredMethod("required", Long.class);
        MethodParameter parameter = new MethodParameter(method, 0);

        Object resolved = resolver.resolveArgument(parameter, null, null, null);

        assertThat(resolved).isEqualTo(101L);
    }

    @Test
    void resolveArgument_throwsWhenRequiredAndContextMissing() throws Exception {
        Method method = Fixture.class.getDeclaredMethod("required", Long.class);
        MethodParameter parameter = new MethodParameter(method, 0);

        assertThatThrownBy(() -> resolver.resolveArgument(parameter, null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("인증 사용자 컨텍스트");
    }

    @Test
    void resolveArgument_returnsNullWhenOptionalAndContextMissing() throws Exception {
        Method method = Fixture.class.getDeclaredMethod("optional", Long.class);
        MethodParameter parameter = new MethodParameter(method, 0);

        Object resolved = resolver.resolveArgument(parameter, null, null, null);

        assertThat(resolved).isNull();
    }

    private static class Fixture {
        void required(@CurrentUserId Long userId) {
        }

        void optional(@CurrentUserId(required = false) Long userId) {
        }
    }
}
