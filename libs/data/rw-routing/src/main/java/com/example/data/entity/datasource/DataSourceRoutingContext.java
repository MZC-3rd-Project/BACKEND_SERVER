package com.example.data.entity.datasource;

import java.util.ArrayDeque;
import java.util.Deque;

public final class DataSourceRoutingContext {

    private static final ThreadLocal<Deque<DataSourceRoute>> CONTEXT = ThreadLocal.withInitial(ArrayDeque::new);

    private DataSourceRoutingContext() {
    }

    public static void push(DataSourceRoute route) {
        CONTEXT.get().push(route);
    }

    public static void pop() {
        Deque<DataSourceRoute> stack = CONTEXT.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
        if (stack.isEmpty()) {
            CONTEXT.remove();
        }
    }

    public static DataSourceRoute current() {
        Deque<DataSourceRoute> stack = CONTEXT.get();
        return stack.isEmpty() ? null : stack.peek();
    }
}
