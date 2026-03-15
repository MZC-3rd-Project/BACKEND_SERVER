package com.example.sales.domain.checkout;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public record ReserveIntent(List<LineItem> lineItems) {

    private static final Comparator<LineItem> LINE_ITEM_COMPARATOR = Comparator
            .comparing((LineItem item) -> item.key().itemId(), Comparator.nullsFirst(Long::compareTo))
            .thenComparing(LineItem::channelType, Comparator.nullsFirst(String::compareTo))
            .thenComparing(LineItem::channelRefId, Comparator.nullsFirst(Long::compareTo))
            .thenComparing(LineItem::stockItemType, Comparator.nullsFirst(String::compareTo))
            .thenComparing(item -> item.key().referenceId(), Comparator.nullsFirst(Long::compareTo))
            .thenComparing(LineItem::quantity, Comparator.nullsFirst(Integer::compareTo));

    public ReserveIntent {
        List<LineItem> source = lineItems == null ? List.of() : lineItems;
        lineItems = source.stream()
                .sorted(LINE_ITEM_COMPARATOR)
                .toList();
    }

    public boolean matches(ReserveIntent other) {
        return equals(other);
    }

    public record LineItem(
            CheckoutLineItemKey key,
            String channelType,
            Long channelRefId,
            String stockItemType,
            Integer quantity
    ) {

        public LineItem {
            channelType = normalize(channelType);
            stockItemType = normalize(stockItemType);
        }

        private static String normalize(String value) {
            if (value == null) {
                return null;
            }
            return value.trim().toUpperCase(Locale.ROOT);
        }
    }
}
