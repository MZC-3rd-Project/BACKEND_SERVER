package com.example.cart.service.query;

import com.example.cart.domain.CartLine;
import com.example.cart.domain.Cart;
import com.example.cart.dto.response.CartItemResponse;
import com.example.cart.dto.response.CartResponse;
import com.example.cart.repository.CartRepository;
import com.example.cart.service.CartSnapshotData;
import com.example.cart.service.CartSnapshotEnricher;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CartQueryService {

    private final CartRepository cartRepository;
    private final CartSnapshotEnricher cartSnapshotEnricher;

    public CartResponse getCart(Long userId) {
        Cart cart = Cart.of(userId, cartRepository.findAllByUserId(userId));
        backfillMissingSnapshots(cart.lines(), userId);
        return CartResponse.of(cart.lines().stream().map(CartItemResponse::from).toList());
    }

    private void backfillMissingSnapshots(List<CartLine> lines, Long userId) {
        List<CartLine> updatedLines = new ArrayList<>();
        for (CartLine line : lines) {
            if (!needsRefresh(line)) {
                continue;
            }

            CartSnapshotData snapshot = cartSnapshotEnricher.enrich(line.getIdentity().itemId());
            if (!hasBackfillData(snapshot)) {
                continue;
            }

            line.refreshSnapshot(
                    snapshot.storeId() != null ? snapshot.storeId() : line.getStoreId(),
                    StringUtils.hasText(snapshot.itemTitle()) ? snapshot.itemTitle() : line.getItemTitle(),
                    StringUtils.hasText(snapshot.thumbnailUrl()) ? snapshot.thumbnailUrl() : line.getThumbnailUrl(),
                    StringUtils.hasText(snapshot.storeName()) ? snapshot.storeName() : line.getStoreName(),
                    snapshot.displayPrice() != null ? snapshot.displayPrice() : line.getDisplayPrice(),
                    StringUtils.hasText(snapshot.salesStatus()) ? snapshot.salesStatus() : line.getSalesStatus()
            );
            updatedLines.add(line);
        }

        if (!updatedLines.isEmpty()) {
            cartRepository.saveAll(updatedLines, userId);
        }
    }

    private boolean needsRefresh(CartLine line) {
        return !StringUtils.hasText(line.getThumbnailUrl()) || !StringUtils.hasText(line.getStoreName());
    }

    private boolean hasBackfillData(CartSnapshotData snapshot) {
        return snapshot != null && (
                snapshot.storeId() != null
                        || StringUtils.hasText(snapshot.itemTitle())
                        || StringUtils.hasText(snapshot.thumbnailUrl())
                        || StringUtils.hasText(snapshot.storeName())
                        || snapshot.displayPrice() != null
                        || StringUtils.hasText(snapshot.salesStatus())
        );
    }
}
