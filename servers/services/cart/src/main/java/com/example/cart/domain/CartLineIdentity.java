package com.example.cart.domain;

import java.util.Locale;
import org.springframework.util.StringUtils;

public record CartLineIdentity(
        Long itemId,
        Long referenceId,
        String channelType,
        Long channelRefId
) {

    public CartLineIdentity {
        if (itemId == null || itemId <= 0L) {
            throw new IllegalArgumentException("itemId는 양수여야 합니다");
        }
        if (referenceId == null || referenceId <= 0L) {
            throw new IllegalArgumentException("referenceId는 양수여야 합니다");
        }
        if (!StringUtils.hasText(channelType)) {
            throw new IllegalArgumentException("channelType은 비어 있을 수 없습니다");
        }
        channelType = normalize(channelType);
    }

    public static CartLineIdentity of(Long itemId, Long referenceId, String channelType, Long channelRefId) {
        return new CartLineIdentity(itemId, referenceId, channelType, channelRefId);
    }

    private static String normalize(String channelType) {
        return channelType.trim().toUpperCase(Locale.ROOT);
    }
}
