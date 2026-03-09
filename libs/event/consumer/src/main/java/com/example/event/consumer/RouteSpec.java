package com.example.event.consumer;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

public record RouteSpec<T>(
        Predicate<T> validator,
        Consumer<T> action
) {

    public RouteSpec {
        Objects.requireNonNull(validator, "validator must not be null");
        Objects.requireNonNull(action, "action must not be null");
    }

    public static <T> RouteSpec<T> of(Predicate<T> validator, Consumer<T> action) {
        return new RouteSpec<>(validator, action);
    }

    public static <T> RouteSpec<T> of(Consumer<T> action) {
        return new RouteSpec<>(event -> true, action);
    }
}
