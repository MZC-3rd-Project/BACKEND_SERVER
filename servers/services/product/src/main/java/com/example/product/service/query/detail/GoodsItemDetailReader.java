package com.example.product.service.query.detail;

import com.example.product.entity.goods.ItemGoodsLink;
import com.example.product.repository.ItemGoodsLinkRepository;
import com.example.product.repository.ItemImageRepository;
import com.example.product.repository.ItemOptionRepository;
import com.example.product.repository.ItemRepository;
import com.example.product.repository.ShippingInfoRepository;
import com.example.product.service.content.ItemContentService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GoodsItemDetailReader extends AbstractGoodsItemDetailReader {

    private final ItemGoodsLinkRepository itemGoodsLinkRepository;

    public GoodsItemDetailReader(ItemRepository itemRepository,
                                 ItemOptionRepository itemOptionRepository,
                                 ShippingInfoRepository shippingInfoRepository,
                                 ItemImageRepository itemImageRepository,
                                 ItemContentService itemContentService,
                                 ItemCategoryDetailResolver itemCategoryDetailResolver,
                                 ItemGoodsLinkRepository itemGoodsLinkRepository) {
        super(itemRepository, itemOptionRepository, shippingInfoRepository, itemImageRepository, itemContentService,
                itemCategoryDetailResolver);
        this.itemGoodsLinkRepository = itemGoodsLinkRepository;
    }

    @Override
    protected Map<Long, List<Long>> findLinkedPerformanceIds(List<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }

        return itemGoodsLinkRepository.findByGoodsItemIdIn(itemIds).stream()
                .collect(Collectors.groupingBy(
                        ItemGoodsLink::getGoodsItemId,
                        Collectors.mapping(ItemGoodsLink::getPerformanceItemId, Collectors.toList())
                ));
    }
}
