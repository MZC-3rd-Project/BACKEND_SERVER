package com.example.cart.domain;

import com.example.cart.exception.CartErrorCode;
import com.example.core.exception.BusinessException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Cart {

    private final Long userId;
    private final Map<CartLineIdentity, CartLine> lines;

    private Cart(Long userId, Collection<CartLine> lines) {
        this.userId = userId;
        this.lines = new LinkedHashMap<>();
        for (CartLine line : lines) {
            this.lines.put(line.getIdentity(), line);
        }
    }

    public static Cart of(Long userId, Collection<CartLine> lines) {
        return new Cart(userId, lines);
    }

    public Long getUserId() {
        return userId;
    }

    public List<CartLine> lines() {
        return lines.values().stream()
                .sorted(Comparator.comparing(CartLine::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public CartLine addOrMerge(CartLine incoming, Instant now, long expiresAtEpoch) {
        CartLine existing = lines.get(incoming.getIdentity());
        if (existing == null) {
            incoming.touch(now, expiresAtEpoch);
            lines.put(incoming.getIdentity(), incoming);
            return incoming;
        }
        return existing.merge(incoming, now, expiresAtEpoch);
    }

    public CartLine changeQuantity(CartLineIdentity identity, int quantity, Instant now, long expiresAtEpoch) {
        CartLine line = getRequired(identity);
        line.changeQuantity(quantity, now, expiresAtEpoch);
        return line;
    }

    public List<CartLine> changeSelections(Map<CartLineIdentity, Boolean> changes, Instant now, long expiresAtEpoch) {
        List<CartLine> updated = new ArrayList<>();
        for (Map.Entry<CartLineIdentity, Boolean> entry : changes.entrySet()) {
            CartLine line = getRequired(entry.getKey());
            line.changeSelection(entry.getValue(), now, expiresAtEpoch);
            updated.add(line);
        }
        return updated;
    }

    public void remove(CartLineIdentity identity) {
        if (lines.remove(identity) == null) {
            throw new BusinessException(CartErrorCode.CART_LINE_NOT_FOUND);
        }
    }

    public Optional<CartLine> findLine(CartLineIdentity identity) {
        return Optional.ofNullable(lines.get(identity));
    }

    public List<CartLine> selectedLines() {
        List<CartLine> selected = lines.values().stream()
                .filter(CartLine::isSelected)
                .toList();
        if (selected.isEmpty()) {
            throw new BusinessException(CartErrorCode.EMPTY_SELECTED_ITEMS);
        }
        return selected;
    }

    private CartLine getRequired(CartLineIdentity identity) {
        return findLine(identity)
                .orElseThrow(() -> new BusinessException(CartErrorCode.CART_LINE_NOT_FOUND));
    }
}
