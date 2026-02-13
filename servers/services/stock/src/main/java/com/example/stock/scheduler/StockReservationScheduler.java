package com.example.stock.scheduler;

import com.example.config.redis.lock.DistributedLock;
import com.example.stock.entity.ReservationStatus;
import com.example.stock.repository.StockReservationRepository;
import com.example.stock.service.command.StockCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockReservationScheduler {

    private final StockReservationRepository stockReservationRepository;
    private final StockCommandService stockCommandService;
    private static final int BATCH_SIZE = 200;

    @Scheduled(fixedDelay = 60000) // 1분마다
    @DistributedLock(key = "'stock:expire-reservations'", waitTime = 1, leaseTime = 55)
    public void expireReservations() {
        LocalDateTime now = LocalDateTime.now();
        Long cursor = null;
        int totalProcessed = 0;

        while (true) {
            List<Long> expiredIds = stockReservationRepository.findExpiredReservationIdsWithCursor(
                    ReservationStatus.RESERVED, now, cursor, PageRequest.of(0, BATCH_SIZE));

            if (expiredIds.isEmpty()) {
                break;
            }

            for (Long reservationId : expiredIds) {
                try {
                    stockCommandService.expireReservationById(reservationId);
                    totalProcessed++;
                } catch (Exception e) {
                    log.error("예약 만료 처리 실패: reservationId={}", reservationId, e);
                }
            }

            cursor = expiredIds.get(expiredIds.size() - 1);
        }
        if (totalProcessed > 0) {
            log.info("만료 예약 처리 완료: {}건", totalProcessed);
        }
    }
}
