package com.example.sales.entity;

import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "checkout_session_line_items",
        indexes = {
                @Index(name = "idx_checkout_line_item_session", columnList = "checkout_session_id"),
                @Index(name = "idx_checkout_line_item_session_line_no", columnList = "checkout_session_id, line_no", unique = true),
                @Index(name = "idx_checkout_line_item_item_reference", columnList = "item_id, reference_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class CheckoutSessionLineItem extends BaseEntity {

    private static final int MAX_CHANNEL_TYPE_LENGTH = 20;
    private static final int MAX_ITEM_TYPE_LENGTH = 20;
    private static final int MAX_STOCK_ITEM_TYPE_LENGTH = 30;
    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_REFERENCE_NAME_LENGTH = 255;

    @Id
    @SnowflakeGenerated
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checkout_session_id", nullable = false)
    private CheckoutSession checkoutSession;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "channel_type", nullable = false, length = MAX_CHANNEL_TYPE_LENGTH)
    private String channelType;

    @Column(name = "channel_ref_id")
    private Long channelRefId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "item_type", length = MAX_ITEM_TYPE_LENGTH)
    private String itemType;

    @Column(name = "title", length = MAX_TITLE_LENGTH)
    private String title;

    @Column(name = "seller_id")
    private Long sellerId;

    @Column(name = "store_id")
    private Long storeId;

    @Column(name = "stock_item_type", nullable = false, length = MAX_STOCK_ITEM_TYPE_LENGTH)
    private String stockItemType;

    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    @Column(name = "reference_name", length = MAX_REFERENCE_NAME_LENGTH)
    private String referenceName;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "base_unit_price")
    private Long baseUnitPrice;

    @Column(name = "final_unit_price")
    private Long finalUnitPrice;

    @Column(name = "line_amount")
    private Long lineAmount;

    public static CheckoutSessionLineItem createReserved(
            Integer lineNo,
            String channelType,
            Long channelRefId,
            Long itemId,
            String stockItemType,
            Long referenceId,
            Integer quantity
    ) {
        CheckoutSessionLineItem lineItem = new CheckoutSessionLineItem();
        lineItem.lineNo = lineNo;
        lineItem.channelType = normalize(channelType, MAX_CHANNEL_TYPE_LENGTH);
        lineItem.channelRefId = channelRefId;
        lineItem.itemId = itemId;
        lineItem.stockItemType = normalize(stockItemType, MAX_STOCK_ITEM_TYPE_LENGTH);
        lineItem.referenceId = referenceId;
        lineItem.quantity = quantity;
        return lineItem;
    }

    public void applyQuoteSnapshot(
            String itemType,
            String title,
            Long sellerId,
            Long storeId,
            String referenceName,
            Long baseUnitPrice,
            Long finalUnitPrice,
            Long lineAmount
    ) {
        this.itemType = normalize(itemType, MAX_ITEM_TYPE_LENGTH);
        this.title = normalize(title, MAX_TITLE_LENGTH);
        this.sellerId = sellerId;
        this.storeId = storeId;
        this.referenceName = normalize(referenceName, MAX_REFERENCE_NAME_LENGTH);
        this.baseUnitPrice = baseUnitPrice;
        this.finalUnitPrice = finalUnitPrice;
        this.lineAmount = lineAmount;
    }

    public boolean matches(Long itemId, Long referenceId) {
        return this.itemId != null
                && this.referenceId != null
                && this.itemId.equals(itemId)
                && this.referenceId.equals(referenceId);
    }

    public boolean hasSameReserveIntent(CheckoutSessionLineItem other) {
        if (other == null) {
            return false;
        }
        return lineNo.equals(other.lineNo)
                && equalsIgnoreCase(channelType, other.channelType)
                && java.util.Objects.equals(channelRefId, other.channelRefId)
                && java.util.Objects.equals(itemId, other.itemId)
                && equalsIgnoreCase(stockItemType, other.stockItemType)
                && java.util.Objects.equals(referenceId, other.referenceId)
                && java.util.Objects.equals(quantity, other.quantity);
    }

    void attachTo(CheckoutSession checkoutSession) {
        this.checkoutSession = checkoutSession;
    }

    private boolean equalsIgnoreCase(String left, String right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.equalsIgnoreCase(right);
    }

    private static String normalize(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
