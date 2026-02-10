package com.example.product.service.query;

import com.example.product.dto.item.response.ItemSummaryResponse;
import com.example.product.entity.item.Item;
import com.example.product.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InternalItemQueryService {

    private final ItemRepository itemRepository;

    public ItemSummaryResponse findById(Long itemId) {
        Item item = itemRepository.findById(itemId).orElse(null);
        if (item == null) return null;
        return ItemSummaryResponse.from(item);
    }

    public List<ItemSummaryResponse> findByIds(List<Long> itemIds) {
        List<Item> items = itemRepository.findAllById(itemIds);
        return items.stream().map(ItemSummaryResponse::from).toList();
    }
}
