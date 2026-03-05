package com.example.security.starter.servlet.resolver;

import com.example.security.context.AuthContext;
import com.example.security.context.AuthContextHolder;
import com.example.security.starter.servlet.annotation.CurrentUserId;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        if (!parameter.hasParameterAnnotation(CurrentUserId.class)) {
            return false;
        }

        Class<?> parameterType = parameter.getParameterType();
        return parameterType.equals(Long.class) || parameterType.equals(long.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        CurrentUserId annotation = parameter.getParameterAnnotation(CurrentUserId.class);
        boolean required = annotation == null || annotation.required();

        AuthContext authContext = AuthContextHolder.getContext();
        if (authContext == null || authContext.getUserId() == null) {
            if (required) {
                throw new IllegalStateException("현재 요청에 인증 사용자 컨텍스트가 없습니다");
            }
            return parameter.getParameterType().equals(long.class) ? 0L : null;
        }

        try {
            return Long.parseLong(authContext.getUserId());
        } catch (NumberFormatException e) {
            if (required) {
                throw new IllegalStateException("사용자 식별자 형식이 올바르지 않습니다");
            }
            return parameter.getParameterType().equals(long.class) ? 0L : null;
        }
    }
}
