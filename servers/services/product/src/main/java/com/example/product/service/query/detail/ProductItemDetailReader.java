package com.example.product.service.query.detail;

import com.example.product.repository.ItemImageRepository;
import com.example.product.repository.ItemOptionRepository;
import com.example.product.repository.ItemRepository;
import com.example.product.repository.ShippingInfoRepository;
import com.example.product.service.content.ItemContentService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ProductItemDetailReader extends AbstractGoodsItemDetailReader {

    public ProductItemDetailReader(ItemRepository itemRepository,
                                   ItemOptionRepository itemOptionRepository,
                                   ShippingInfoRepository shippingInfoRepository,
                                   ItemImageRepository itemImageRepository,
                                   ItemContentService itemContentService,
                                   ItemCategoryDetailResolver itemCategoryDetailResolver) {
        super(itemRepository, itemOptionRepository, shippingInfoRepository, itemImageRepository, itemContentService,
                itemCategoryDetailResolver);
    }

    @Override
    protected Map<Long, List<Long>> findLinkedPerformanceIds(List<Long> itemIds) {
        return Map.of();
    }
}
