package com.example.hotdeal.scheduler;

import com.example.hotdeal.service.HotDealCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HotDealEndScheduler {

    private final HotDealCommandService hotDealCommandService;

    /**
     * 매 1분마다 실행: 종료 시간이 지난 ACTIVE 핫딜을 ENDED로 전환
     */
    @Scheduled(fixedRate = 60000)
    @SchedulerLock(name = "hotDealEnd", lockAtLeastFor = "PT30S", lockAtMostFor = "PT5M")
    public void endExpiredDeals() {
        try {
            hotDealCommandService.endExpiredDeals();
        } catch (Exception e) {
            log.error("Hot deal end scheduler failed", e);
        }
    }
}
