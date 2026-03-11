package com.example.sales.service.command;

import com.example.sales.event.CheckoutReservedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class CheckoutQuoteWarmupListener {

    private final CheckoutCommandService checkoutCommandService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void warmQuoteCache(CheckoutReservedEvent event) {
        try {
            checkoutCommandService.warmQuoteCache(event.orderId());
        } catch (Exception e) {
            log.warn("Checkout quote warm-up failed: orderId={}", event.orderId(), e);
        }
    }
}
