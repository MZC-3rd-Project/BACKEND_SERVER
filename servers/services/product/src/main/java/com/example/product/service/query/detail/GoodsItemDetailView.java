package com.example.product.service.query.detail;

import com.example.product.dto.item.response.ItemContentSnapshot;
import com.example.product.dto.item.response.ItemSummaryResponse;
import com.example.product.entity.goods.ItemOption;
import com.example.product.entity.goods.ShippingInfo;
import com.example.product.entity.image.ItemImage;
import com.example.product.entity.item.Item;

import java.util.List;

public record GoodsItemDetailView(
        Item item,
        ItemCategoryDetailView categoryDetail,
        List<ItemOption> options,
        ShippingInfo shippingInfo,
        List<Long> linkedPerformanceItemIds,
        List<ItemSummaryResponse> linkedPerformanceItems,
        ItemContentSnapshot contentSnapshot,
        List<ItemImage> images
) {
    public GoodsItemDetailView {
        options = options == null ? List.of() : List.copyOf(options);
        linkedPerformanceItemIds = linkedPerformanceItemIds == null ? List.of() : List.copyOf(linkedPerformanceItemIds);
        linkedPerformanceItems = linkedPerformanceItems == null ? List.of() : List.copyOf(linkedPerformanceItems);
        contentSnapshot = contentSnapshot == null ? ItemContentSnapshot.empty() : contentSnapshot;
        images = images == null ? List.of() : List.copyOf(images);
    }
}
