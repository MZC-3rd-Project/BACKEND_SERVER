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
import com.example.cart.repository.CartRepository;
import com.example.cart.service.CartSnapshotData;
import com.example.cart.service.CartSnapshotEnricher;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
        return toResponse(cart);
    }

    public CartCheckoutReservationResponse startCheckout(Long userId, StartCartCheckoutRequest request) {
        Cart cart = loadCart(userId);
        return cartSalesClient.reserve(userId, request.getIdempotencyKey(), cart.selectedLines());
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
