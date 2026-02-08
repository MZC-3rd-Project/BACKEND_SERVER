package com.example.security.core;

public class AuthContextHolder {

    private static final ThreadLocal<AuthContext> contextHolder = new ThreadLocal<>();

    public static void setContext(AuthContext context) {
        contextHolder.set(context);
    }

    public static AuthContext getContext() {
        return contextHolder.get();
    }

    public static AuthContext currentContext() {
        AuthContext context = contextHolder.get();
        if (context == null) {
            throw new IllegalStateException("AuthContext가 설정되지 않았습니다");
        }
        return context;
    }

    public static void clear() {
        contextHolder.remove();
    }
}
