package com.example.product.service.query;

import com.example.core.exception.BusinessException;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemStatus;
import com.example.product.entity.item.ItemType;
import com.example.product.exception.ProductErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemAccessPolicyTest {

    private final ItemAccessPolicy itemAccessPolicy = new ItemAccessPolicy();

    @Test
    void validatePublicAccess_allowsVisibleItemWithMatchingType() {
        Item item = item(ItemType.GOODS, ItemStatus.ON_SALE, 1L);

        itemAccessPolicy.validatePublicAccess(item, ItemType.GOODS);

        assertThat(item.isPubliclyVisible()).isTrue();
    }

    @Test
    void validatePublicAccess_rejectsHiddenItem() {
        Item item = item(ItemType.GOODS, ItemStatus.HIDDEN, 1L);

        assertThatThrownBy(() -> itemAccessPolicy.validatePublicAccess(item, ItemType.GOODS))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.ITEM_NOT_FOUND);
    }

    @Test
    void validateSellerAccess_rejectsWrongOwner() {
        Item item = item(ItemType.PRODUCT, ItemStatus.HIDDEN, 10L);

        assertThatThrownBy(() -> itemAccessPolicy.validateSellerAccess(item, ItemType.PRODUCT, 11L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.UNAUTHORIZED_ACCESS);
    }

    @Test
    void validateSaleable_rejectsWrongStatus() {
        Item item = item(ItemType.PERFORMANCE, ItemStatus.FUNDING, 10L);

        assertThatThrownBy(() -> itemAccessPolicy.validateSaleable(item, ItemStatus.ON_SALE))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.ITEM_NOT_SALEABLE);
    }

    @Test
    void visibleStatuses_returnsPublicStatuses() {
        assertThat(itemAccessPolicy.visibleStatuses())
                .containsExactly(ItemStatus.FUNDING, ItemStatus.FUNDED, ItemStatus.ON_SALE, ItemStatus.HOT_DEAL);
    }

    private Item item(ItemType itemType, ItemStatus status, Long sellerId) {
        Item item = Item.create("item", "desc", 1000L, itemType, null, sellerId, 1L, null);
        ReflectionTestUtils.setField(item, "status", status);
        return item;
    }
}
