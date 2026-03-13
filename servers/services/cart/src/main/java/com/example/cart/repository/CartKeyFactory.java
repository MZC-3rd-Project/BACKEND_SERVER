package com.example.cart.repository;

import com.example.cart.domain.CartLineIdentity;

final class CartKeyFactory {

    private CartKeyFactory() {
    }

    static String partitionKey(Long userId) {
        return "USER#" + userId;
    }

    static String sortKey(CartLineIdentity identity) {
        String channelRef = identity.channelRefId() == null ? "NONE" : String.valueOf(identity.channelRefId());
        return "LINE#ITEM#" + identity.itemId()
                + "#REF#" + identity.referenceId()
                + "#CH#" + identity.channelType()
                + "#CR#" + channelRef;
    }
}
