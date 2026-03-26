package com.example.hotdeal.scheduler;

import com.example.hotdeal.service.checkout.HotDealCheckoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HotDealCheckoutExpirationScheduler {

    private final HotDealCheckoutService hotDealCheckoutService;

    @Scheduled(fixedRate = 60000)
    public void expireReservedSessions() {
        hotDealCheckoutService.expireReservedCheckoutSessions();
    }
}
