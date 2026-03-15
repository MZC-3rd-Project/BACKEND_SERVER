package com.example.product.service.query;

import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemStatus;
import com.example.product.entity.item.ItemType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ItemAccessPolicy {

    public void validatePublicAccess(Item item, ItemType expectedType) {
        item.validatePublicAccess(expectedType);
    }

    public void validateSellerAccess(Item item, ItemType expectedType, Long sellerId) {
        item.validateSellerAccess(expectedType, sellerId);
    }

    public void validateSaleable(Item item, ItemStatus expectedStatus) {
        item.validateSaleable(expectedStatus);
    }

    public List<ItemStatus> visibleStatuses() {
        return ItemStatus.publiclyVisibleStatuses();
    }
}
