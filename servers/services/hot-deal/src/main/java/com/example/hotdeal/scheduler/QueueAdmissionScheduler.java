package com.example.hotdeal.scheduler;

import com.example.hotdeal.entity.HotDealStatus;
import com.example.hotdeal.repository.HotDealRepository;
import com.example.hotdeal.service.QueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueAdmissionScheduler {

    private final QueueService queueService;
    private final HotDealRepository hotDealRepository;

    private static final int ADMIT_COUNT = 10;

    /**
     * 매 1초마다 실행: ACTIVE 핫딜별 대기열에서 상위 N명 입장 허용
     */
    @Scheduled(fixedRate = 1000)
    public void admitUsers() {
        hotDealRepository.findByStatus(HotDealStatus.ACTIVE)
                .forEach(hotDeal -> {
                    try {
                        queueService.admitUsers(hotDeal.getId(), ADMIT_COUNT);
                    } catch (Exception e) {
                        log.error("Queue admission failed: hotDealId={}", hotDeal.getId(), e);
                    }
                });
    }
}
