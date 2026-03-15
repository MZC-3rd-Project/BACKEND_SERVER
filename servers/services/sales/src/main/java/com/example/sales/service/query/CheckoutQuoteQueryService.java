package com.example.sales.service.query;

import com.example.clients.product.dto.ProductQuoteLineItemRequest;
import com.example.clients.product.dto.ProductQuoteRequest;
import com.example.clients.product.dto.ProductQuoteResponse;
import com.example.clients.product.exception.ProductClientException;
import com.example.clients.product.facade.ProductQuoteClientFacade;
import com.example.config.lock.DistributedLock;
import com.example.core.exception.BusinessException;
import com.example.sales.domain.checkout.QuoteSnapshot;
import com.example.sales.dto.checkout.CheckoutDraft;
import com.example.sales.dto.checkout.request.CheckoutQuoteRequest;
import com.example.sales.dto.checkout.response.CheckoutQuoteResponse;
import com.example.sales.entity.CheckoutSession;
import com.example.sales.entity.CheckoutSessionStatus;
import com.example.sales.exception.SalesErrorCode;
import com.example.sales.repository.CheckoutSessionRepository;
import com.example.sales.service.command.CheckoutDraftRedisService;
import com.example.sales.service.command.CheckoutQuoteCacheRedisService;
import com.example.sales.service.support.CheckoutSnapshotAssembler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CheckoutQuoteQueryService {

    private final ProductQuoteClientFacade productQuoteClientFacade;
    private final CheckoutDraftRedisService checkoutDraftRedisService;
    private final CheckoutQuoteCacheRedisService checkoutQuoteCacheRedisService;
    private final CheckoutSessionRepository checkoutSessionRepository;
    private final CheckoutSnapshotAssembler checkoutSnapshotAssembler;

    @Transactional
    @DistributedLock(key = "'checkout:quote:' + #request.orderId", waitTime = 3, leaseTime = 10)
    public CheckoutQuoteResponse quote(CheckoutQuoteRequest request, Long userId) {
        CheckoutSession session = checkoutSessionRepository.findByOrderId(request.getOrderId())
                .orElseThrow(() -> new BusinessException(SalesErrorCode.CHECKOUT_SESSION_NOT_FOUND));
        validateOwnedSession(session, userId);
        validateQuoteableSession(session);

        CheckoutDraft draft = findDraft(request.getOrderId())
                .orElseGet(() -> checkoutSnapshotAssembler.toDraft(session));

        return checkoutSnapshotAssembler.toResponse(loadQuoteSnapshot(draft));
    }

    @Transactional
    @DistributedLock(key = "'checkout:quote:' + #orderId", waitTime = 3, leaseTime = 10)
    public void warmQuoteCache(Long orderId) {
        findDraft(orderId)
                .filter(draft -> draft.getExpiresAt() == null || LocalDateTime.now().isBefore(draft.getExpiresAt()))
                .ifPresent(this::loadQuoteSnapshot);
    }

    @Transactional
    public void ensureSubmitQuoteSnapshot(CheckoutSession session) {
        QuoteSnapshot quoteSnapshot = loadQuoteSnapshot(checkoutSnapshotAssembler.toDraft(session));
        session.ensureQuoteSnapshot(quoteSnapshot);
    }

    private Optional<CheckoutDraft> findDraft(Long orderId) {
        Optional<CheckoutDraft> redisDraft = checkoutDraftRedisService.findDraft(orderId);
        if (redisDraft.isPresent()) {
            return redisDraft;
        }

        Optional<CheckoutSession> persistedSession = checkoutSessionRepository.findByOrderId(orderId);
        if (persistedSession.isEmpty()) {
            return Optional.empty();
        }

        CheckoutSession session = persistedSession.get();
        if (!isReplayableSession(session)) {
            return Optional.empty();
        }

        CheckoutDraft persistedDraft = checkoutSnapshotAssembler.toDraft(session);
        if (persistedDraft.getExpiresAt() != null && LocalDateTime.now().isBefore(persistedDraft.getExpiresAt())) {
            checkoutDraftRedisService.saveDraft(persistedDraft, ttlUntil(persistedDraft.getExpiresAt()));
        }
        return Optional.of(persistedDraft);
    }

    private QuoteSnapshot loadQuoteSnapshot(CheckoutDraft draft) {
        Optional<QuoteSnapshot> cachedQuote = checkoutQuoteCacheRedisService.findQuote(draft.getOrderId())
                .map(checkoutSnapshotAssembler::toQuoteSnapshot);
        if (cachedQuote.isPresent()) {
            return cachedQuote.get();
        }

        Optional<QuoteSnapshot> persistedQuote = checkoutSessionRepository.findByOrderId(draft.getOrderId())
                .filter(CheckoutSession::hasPersistedQuoteSnapshot)
                .map(session -> checkoutSnapshotAssembler.toQuoteSnapshot(session, draft.getExpiresAt()));
        if (persistedQuote.isPresent()) {
            cacheQuote(draft, persistedQuote.get());
            return persistedQuote.get();
        }

        ProductQuoteResponse quoteResponse = fetchLiveQuote(draft);
        QuoteSnapshot quoteSnapshot = checkoutSnapshotAssembler.toQuoteSnapshot(draft, quoteResponse);
        applyQuoteSnapshot(draft.getOrderId(), quoteSnapshot);

        cacheQuote(draft, quoteSnapshot);
        return quoteSnapshot;
    }

    private void validateOwnedSession(CheckoutSession session, Long userId) {
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(SalesErrorCode.CHECKOUT_SESSION_FORBIDDEN);
        }
    }

    private void validateQuoteableSession(CheckoutSession session) {
        if (session.getStatus() == CheckoutSessionStatus.CANCELLED) {
            throw new BusinessException(SalesErrorCode.CHECKOUT_SESSION_CANCELLED);
        }
        if (session.getStatus() == CheckoutSessionStatus.EXPIRED
                || (session.getExpiresAt() != null && LocalDateTime.now().isAfter(session.getExpiresAt()))) {
            clearCheckoutCaches(session);
            throw new BusinessException(SalesErrorCode.CHECKOUT_SESSION_EXPIRED);
        }
    }

    private void clearCheckoutCaches(CheckoutSession session) {
        checkoutDraftRedisService.deleteDraft(session.getOrderId(), session.getUserId(), session.getIdempotencyKey());
        checkoutQuoteCacheRedisService.deleteQuote(session.getOrderId());
    }

    private void applyQuoteSnapshot(Long orderId, QuoteSnapshot quoteSnapshot) {
        checkoutSessionRepository.findByOrderId(orderId).ifPresent(session -> {
            session.applyQuoteSnapshot(quoteSnapshot);
        });
    }

    private ProductQuoteResponse fetchLiveQuote(CheckoutDraft draft) {
        try {
            return productQuoteClientFacade.quoteItems(new ProductQuoteRequest(
                    draft.getLineItems().stream()
                            .map(item -> new ProductQuoteLineItemRequest(
                                    item.getItemId(),
                                    item.getChannelType(),
                                    item.getChannelRefId(),
                                    item.getReferenceId(),
                                    item.getQuantity()))
                            .toList()
            ));
        } catch (ProductClientException e) {
            throw new BusinessException(SalesErrorCode.PRODUCT_SERVICE_ERROR);
        }
    }

    private void cacheQuote(CheckoutDraft draft, QuoteSnapshot quoteSnapshot) {
        if (draft.getExpiresAt() == null) {
            log.debug("Skip quote cache because expiresAt is missing: orderId={}", draft.getOrderId());
            return;
        }

        checkoutQuoteCacheRedisService.saveQuote(
                checkoutSnapshotAssembler.toQuoteCache(quoteSnapshot),
                ttlUntil(draft.getExpiresAt())
        );
    }

    private boolean isReplayableSession(CheckoutSession session) {
        if (session.getExpiresAt() != null && LocalDateTime.now().isAfter(session.getExpiresAt())) {
            return false;
        }
        return session.getStatus() != CheckoutSessionStatus.EXPIRED
                && session.getStatus() != CheckoutSessionStatus.CANCELLED;
    }

    private Duration ttlUntil(LocalDateTime expiresAt) {
        Duration ttl = Duration.between(LocalDateTime.now(), expiresAt);
        return ttl.isNegative() || ttl.isZero() ? Duration.ofSeconds(1) : ttl;
    }
}
