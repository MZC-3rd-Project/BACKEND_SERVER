package com.example.cart.service.command;

import com.example.cart.client.CartSalesClient;
import com.example.cart.config.CartPolicyProperties;
import com.example.cart.domain.Cart;
import com.example.cart.domain.CartLine;
import com.example.cart.domain.CartLineIdentity;
import com.example.cart.dto.request.AddCartItemRequest;
import com.example.cart.dto.request.ChangeCartSelectionRequest;
import com.example.cart.dto.request.RemoveCartItemRequest;
import com.example.cart.dto.request.StartCartCheckoutRequest;
import com.example.cart.dto.request.UpdateCartItemQuantityRequest;
import com.example.cart.dto.response.CartCheckoutReservationResponse;
import com.example.cart.dto.response.CartItemResponse;
import com.example.cart.dto.response.CartResponse;
import com.example.cart.exception.CartErrorCode;
import com.example.cart.repository.CartRepository;
import com.example.cart.service.CartSnapshotData;
import com.example.cart.service.CartSnapshotEnricher;
import com.example.core.exception.BusinessException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartCommandService {

    private final CartRepository cartRepository;
    private final CartSnapshotEnricher cartSnapshotEnricher;
    private final CartSalesClient cartSalesClient;
    private final CartPolicyProperties cartPolicyProperties;

    public CartResponse addItem(Long userId, AddCartItemRequest request) {
        Instant now = Instant.now();
        long expiresAtEpoch = expiresAtEpoch(now);
        Cart cart = loadCart(userId);
        CartSnapshotData snapshot = cartSnapshotEnricher.enrich(request.getItemId());

        CartLine line = CartLine.create(
                CartLineIdentity.of(request.getItemId(), request.getReferenceId(), request.getChannelType(), request.getChannelRefId()),
                request.getStockItemType(),
                request.getQuantity(),
                request.getSelected() == null || request.getSelected(),
                snapshot.storeId(),
                snapshot.itemTitle(),
                snapshot.thumbnailUrl(),
                snapshot.storeName(),
                snapshot.displayPrice(),
                snapshot.salesStatus(),
                now,
                now,
                expiresAtEpoch
        );

        CartLine updated = cart.addOrMerge(line, now, expiresAtEpoch);
        cartRepository.save(updated, userId);
        log.info("Cart item added or merged. userId={}, itemId={}, quantity={}",
                userId, request.getItemId(), updated.getQuantity());
        return toResponse(cart);
    }

    public CartResponse updateQuantity(Long userId, UpdateCartItemQuantityRequest request) {
        Instant now = Instant.now();
        long expiresAtEpoch = expiresAtEpoch(now);
        Cart cart = loadCart(userId);
        CartLine updated = cart.changeQuantity(
                CartLineIdentity.of(request.getItemId(), request.getReferenceId(), request.getChannelType(), request.getChannelRefId()),
                request.getQuantity(),
                now,
                expiresAtEpoch
        );
        cartRepository.save(updated, userId);
        log.info("Cart item quantity updated. userId={}, itemId={}, quantity={}",
                userId, request.getItemId(), updated.getQuantity());
        return toResponse(cart);
    }

    public CartResponse changeSelection(Long userId, ChangeCartSelectionRequest request) {
        Instant now = Instant.now();
        long expiresAtEpoch = expiresAtEpoch(now);
        Cart cart = loadCart(userId);
        Map<CartLineIdentity, Boolean> changes = new LinkedHashMap<>();
        for (ChangeCartSelectionRequest.LineItemSelection lineItem : request.getLineItems()) {
            changes.put(
                    CartLineIdentity.of(lineItem.getItemId(), lineItem.getReferenceId(), lineItem.getChannelType(), lineItem.getChannelRefId()),
                    lineItem.getSelected()
            );
        }
        cartRepository.saveAll(cart.changeSelections(changes, now, expiresAtEpoch), userId);
        log.info("Cart selection updated. userId={}, changedItems={}", userId, changes.size());
        return toResponse(cart);
    }

    public CartResponse removeItem(Long userId, RemoveCartItemRequest request) {
        Cart cart = loadCart(userId);
        CartLineIdentity identity = CartLineIdentity.of(
                request.getItemId(),
                request.getReferenceId(),
                request.getChannelType(),
                request.getChannelRefId()
        );
        cart.remove(identity);
        cartRepository.delete(userId, identity);
        log.info("Cart item removed. userId={}, itemId={}", userId, request.getItemId());
        return toResponse(cart);
    }

    public CartCheckoutReservationResponse startCheckout(Long userId, StartCartCheckoutRequest request) {
        Cart cart = loadCart(userId);
        java.util.List<CartLine> linesToReserve = resolveRequestedCheckoutLines(cart, request);
        CartCheckoutReservationResponse response = cartSalesClient.reserve(userId, request.getIdempotencyKey(), linesToReserve);
        log.info("Cart checkout started. userId={}, orderId={}, selectedItems={}",
                userId, response.getOrderId(), linesToReserve.size());
        return response;
    }

    private java.util.List<CartLine> resolveRequestedCheckoutLines(Cart cart, StartCartCheckoutRequest request) {
        if (request.getLineItems() == null || request.getLineItems().isEmpty()) {
            return cart.selectedLines();
        }

        java.util.List<CartLine> selectedLines = cart.selectedLines();
        Map<CartLineIdentity, CartLine> selectedLineMap = new LinkedHashMap<>();
        for (CartLine selectedLine : selectedLines) {
            selectedLineMap.put(selectedLine.getIdentity(), selectedLine);
        }

        java.util.List<CartLine> resolved = new ArrayList<>();
        for (StartCartCheckoutRequest.LineItem lineItem : request.getLineItems()) {
            CartLineIdentity identity = CartLineIdentity.of(
                    lineItem.getItemId(),
                    lineItem.getReferenceId(),
                    lineItem.getChannelType(),
                    lineItem.getChannelRefId()
            );

            CartLine cartLine = selectedLineMap.get(identity);
            if (cartLine == null) {
                throw new BusinessException(CartErrorCode.INVALID_CART_LINE);
            }
            if (!cartLine.getStockItemType().equalsIgnoreCase(lineItem.getStockItemType())) {
                throw new BusinessException(CartErrorCode.INVALID_CART_LINE);
            }
            if (cartLine.getQuantity() != lineItem.getQuantity()) {
                throw new BusinessException(CartErrorCode.INVALID_CART_LINE);
            }
            resolved.add(cartLine);
        }

        if (resolved.isEmpty()) {
            throw new BusinessException(CartErrorCode.EMPTY_SELECTED_ITEMS);
        }
        return resolved;
    }

    private Cart loadCart(Long userId) {
        return Cart.of(userId, cartRepository.findAllByUserId(userId));
    }

    private CartResponse toResponse(Cart cart) {
        return CartResponse.of(cart.lines().stream().map(CartItemResponse::from).toList());
    }

    private long expiresAtEpoch(Instant now) {
        return now.plusSeconds(cartPolicyProperties.getTtlDays() * 24 * 60 * 60).getEpochSecond();
    }
}
