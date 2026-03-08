package com.example.event.consumer;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

public record EventSpec<T extends EventEnvelope>(
        Class<T> eventClass,
        Predicate<T> validator,
        Consumer<T> action
) {

    public EventSpec {
        Objects.requireNonNull(eventClass, "eventClass must not be null");
        Objects.requireNonNull(validator, "validator must not be null");
        Objects.requireNonNull(action, "action must not be null");
    }

    public static <T extends EventEnvelope> EventSpec<T> of(
            Class<T> eventClass,
            Predicate<T> validator,
            Consumer<T> action
    ) {
        return new EventSpec<>(eventClass, validator, action);
    }

    public static <T extends EventEnvelope> EventSpec<T> of(
            Class<T> eventClass,
            Consumer<T> action
    ) {
        return new EventSpec<>(eventClass, event -> true, action);
    }
}
