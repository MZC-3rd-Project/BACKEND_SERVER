package com.example.hotdeal.service.checkout;

import com.example.core.exception.BusinessException;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.hotdeal.dto.HotDealPurchaseResponse;
import com.example.hotdeal.event.HotDealPurchasedEvent;
import com.example.hotdeal.exception.HotDealErrorCode;
import com.example.hotdeal.repository.HotDealRepository;
import com.example.hotdeal.service.QueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LegacyRedisHotDealCheckoutProcessor implements HotDealCheckoutProcessor {

    private static final String STOCK_KEY_PREFIX = "hotdeal:stock:";
    private static final String RESERVATION_KEY_PREFIX = "hotdeal:reservation:";
    private static final String PURCHASED_KEY_PREFIX = "hotdeal:purchased:";
    private static final String DETAIL_CACHE_KEY_PREFIX = "hotdeal:detail:";
    private static final long RESERVATION_TTL_MINUTES = 5;

    private static final String LUA_SCRIPT = """
            local stockKey = KEYS[1]
            local reservationKey = KEYS[2]
            local purchasedKey = KEYS[3]
            local quantity = tonumber(ARGV[1])
            local ttl = tonumber(ARGV[2])
            local reservationId = ARGV[3]
            local maxPerUser = tonumber(ARGV[4])

            local purchased = tonumber(redis.call('GET', purchasedKey) or '0')
            if purchased == nil then purchased = 0 end
            if purchased + quantity > maxPerUser then
                return -2
            end

            local current = tonumber(redis.call('GET', stockKey))
            if current == nil then
                return -1
            end
            if current < quantity then
                return 0
            end

            redis.call('DECRBY', stockKey, quantity)
            redis.call('SET', reservationKey, reservationId, 'EX', ttl)
            redis.call('INCRBY', purchasedKey, quantity)
            return 1
            """;

    private static final String COMPENSATE_LUA_SCRIPT = """
            local stockKey = KEYS[1]
            local reservationKey = KEYS[2]
            local purchasedKey = KEYS[3]
            local reservationId = ARGV[1]
            local quantity = tonumber(ARGV[2])

            local currentReservation = redis.call('GET', reservationKey)
            if currentReservation == reservationId then
                redis.call('INCRBY', stockKey, quantity)

                local purchased = tonumber(redis.call('GET', purchasedKey) or '0')
                if purchased <= quantity then
                    redis.call('DEL', purchasedKey)
                else
                    redis.call('DECRBY', purchasedKey, quantity)
                end

                redis.call('DEL', reservationKey)
                return 1
            end

            return 0
            """;

    private final HotDealRepository hotDealRepository;
    private final EventPublisher eventPublisher;
    private final StringRedisTemplate stringRedisTemplate;
    private final QueueService queueService;

    @Override
    public HotDealPurchaseResponse checkout(HotDealCheckoutCommand command) {
        String stockKey = STOCK_KEY_PREFIX + command.hotDealId();
        String reservationKey = RESERVATION_KEY_PREFIX + command.hotDealId() + ":" + command.userId();
        String purchasedKey = PURCHASED_KEY_PREFIX + command.hotDealId() + ":" + command.userId();
        String reservationId = UUID.randomUUID().toString();

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);
        Long result = stringRedisTemplate.execute(
                script,
                List.of(stockKey, reservationKey, purchasedKey),
                String.valueOf(command.quantity()),
                String.valueOf(RESERVATION_TTL_MINUTES * 60),
                reservationId,
                String.valueOf(command.maxPerUser())
        );

        if (result == null || result == -1) {
            throw new BusinessException(HotDealErrorCode.HOT_DEAL_NOT_FOUND);
        }
        if (result == -2) {
            throw new BusinessException(HotDealErrorCode.PURCHASE_QUANTITY_EXCEEDED);
        }
        if (result == 0) {
            throw new BusinessException(HotDealErrorCode.HOT_DEAL_SOLD_OUT);
        }

        try {
            int updated = hotDealRepository.incrementSoldQuantity(command.hotDealId(), command.quantity());
            if (updated == 0) {
                throw new BusinessException(HotDealErrorCode.HOT_DEAL_SOLD_OUT);
            }

            Long totalAmount = command.discountedPrice() * command.quantity();
            eventPublisher.publish(
                    new HotDealPurchasedEvent(
                            command.hotDealId(),
                            command.orderId(),
                            command.userId(),
                            command.itemId(),
                            command.quantity(),
                            totalAmount
                    ),
                    EventMetadata.of("HotDeal", String.valueOf(command.hotDealId()))
            );

            stringRedisTemplate.delete(DETAIL_CACHE_KEY_PREFIX + command.hotDealId());
            releaseAdmissionSlotAfterCommit(command, purchasedKey);

            log.info("Hot deal purchased: hotDealId={}, orderId={}, userId={}, quantity={}",
                    command.hotDealId(), command.orderId(), command.userId(), command.quantity());

            return HotDealPurchaseResponse.success(
                    command.orderId(),
                    LocalDateTime.now().plusMinutes(RESERVATION_TTL_MINUTES)
            );
        } catch (RuntimeException e) {
            compensateReservation(stockKey, reservationKey, purchasedKey, reservationId, command.quantity());
            throw e;
        }
    }

    private void compensateReservation(String stockKey, String reservationKey, String purchasedKey,
                                       String reservationId, int quantity) {
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(COMPENSATE_LUA_SCRIPT, Long.class);
            stringRedisTemplate.execute(
                    script,
                    List.of(stockKey, reservationKey, purchasedKey),
                    reservationId,
                    String.valueOf(quantity)
            );
        } catch (Exception e) {
            log.error("Failed to compensate hot-deal reservation: reservationKey={}", reservationKey, e);
        }
    }

    private void releaseAdmissionSlotAfterCommit(HotDealCheckoutCommand command, String purchasedKey) {
        Runnable action = () -> {
            String purchasedValue = stringRedisTemplate.opsForValue().get(purchasedKey);
            if (!hasReachedMaxPerUser(purchasedValue, command.maxPerUser())) {
                return;
            }

            try {
                queueService.releaseAdmissionSlot(command.hotDealId(), command.userId());
            } catch (Exception e) {
                log.warn("Failed to release queue admission slot: hotDealId={}, userId={}",
                        command.hotDealId(), command.userId(), e);
            }
        };

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }

        action.run();
    }

    private boolean hasReachedMaxPerUser(String purchasedValue, int maxPerUser) {
        if (purchasedValue == null) {
            return false;
        }

        try {
            return Integer.parseInt(purchasedValue) >= maxPerUser;
        } catch (NumberFormatException e) {
            log.warn("Failed to parse purchased quantity for slot release. purchasedValue={}", purchasedValue);
            return false;
        }
    }
}
