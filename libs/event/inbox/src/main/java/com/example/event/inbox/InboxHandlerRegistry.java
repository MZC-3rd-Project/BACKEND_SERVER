package com.example.event.inbox;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class InboxHandlerRegistry {

    private final List<InboxEventHandler> handlers;

    public InboxHandlerRegistry(List<InboxEventHandler> handlers) {
        this.handlers = handlers == null ? List.of() : List.copyOf(handlers);
    }

    public Set<String> consumerNames() {
        if (handlers.isEmpty()) {
            return Collections.emptySet();
        }
        return handlers.stream()
                .map(InboxEventHandler::consumerName)
                .collect(Collectors.toSet());
    }

    public Optional<InboxEventHandler> findHandler(String consumerName, String eventType) {
        return handlers.stream()
                .filter(handler -> handler.consumerName().equals(consumerName))
                .filter(handler -> handler.supports(eventType))
                .findFirst();
    }
}
