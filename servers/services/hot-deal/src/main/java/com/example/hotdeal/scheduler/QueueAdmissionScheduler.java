package com.example.hotdeal.scheduler;

import com.example.hotdeal.entity.HotDealStatus;
import com.example.hotdeal.repository.HotDealRepository;
import com.example.hotdeal.service.QueueSseEventPublisher;
import com.example.hotdeal.service.QueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueAdmissionScheduler {

    private final QueueService queueService;
    private final QueueSseEventPublisher queueSseEventPublisher;
    private final HotDealRepository hotDealRepository;

    @Value("${hotdeal.queue.max-concurrent-admissions:10}")
    private int maxConcurrentAdmissions;

    /**
     * 매 1초마다 실행: ACTIVE 핫딜별 남은 재고와 동시 처리 한도에 맞춰 대기열 입장 인원을 채운다.
     */
    @Scheduled(fixedRate = 1000)
    @SchedulerLock(name = "queueAdmission", lockAtLeastFor = "PT0.5S", lockAtMostFor = "PT5S")
    public void admitUsers() {
        hotDealRepository.findByStatus(HotDealStatus.ACTIVE)
                .forEach(hotDeal -> {
                    try {
                        Set<Long> admittedUsers = queueService.admitUsersByAvailableStock(
                                hotDeal.getId(),
                                maxConcurrentAdmissions
                        );
                        queueSseEventPublisher.publishAdmittedUsers(hotDeal.getId(), admittedUsers);
                    } catch (Exception e) {
                        log.error("Queue admission failed: hotDealId={}", hotDeal.getId(), e);
                    }
                });
    }
}
