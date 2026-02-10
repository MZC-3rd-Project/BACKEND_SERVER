package com.example.stock.scheduler;

import com.example.stock.entity.ReservationStatus;
import com.example.stock.entity.StockReservation;
import com.example.stock.repository.StockReservationRepository;
import com.example.stock.service.command.StockCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockReservationScheduler {

    private final StockReservationRepository stockReservationRepository;
    private final StockCommandService stockCommandService;

    @Scheduled(fixedDelay = 60000) // 1분마다
    @Transactional
    public void expireReservations() {
        List<StockReservation> expired = stockReservationRepository
                .findExpiredReservations(ReservationStatus.RESERVED, LocalDateTime.now());

        if (expired.isEmpty()) return;

        log.info("만료 예약 처리 시작: {}건", expired.size());

        for (StockReservation reservation : expired) {
            try {
                stockCommandService.expireReservation(reservation);
                log.info("예약 만료 처리 완료: reservationId={}", reservation.getId());
            } catch (Exception e) {
                log.error("예약 만료 처리 실패: reservationId={}", reservation.getId(), e);
            }
        }

        log.info("만료 예약 처리 완료: {}건", expired.size());
    }
}
