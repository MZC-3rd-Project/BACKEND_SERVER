package com.example.config.logging;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReactiveRequestIdWebFilter implements WebFilter {

    private final LoggingProperties properties;

    public ReactiveRequestIdWebFilter(LoggingProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String requestId = RequestIdSupport.resolveRequestId(request.getHeaders().getFirst(properties.getRequestIdHeader()));
        exchange.getAttributes().put(RequestIdSupport.REQUEST_ID_KEY, requestId);

        if (properties.isWriteRequestIdResponseHeader()) {
            exchange.getResponse().getHeaders().set(properties.getRequestIdHeader(), requestId);
        }

        return Mono.defer(() -> {
            RequestIdSupport.putRequestId(requestId);
            return chain.filter(exchange)
                    .doFinally(signalType -> RequestIdSupport.clearRequestId());
        }).contextWrite(context -> context.put(RequestIdSupport.REQUEST_ID_KEY, requestId));
    }
}
