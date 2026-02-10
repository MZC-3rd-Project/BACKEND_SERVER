package com.example.product.service.query;

import com.example.product.dto.image.response.ItemImageResponse;
import com.example.product.repository.ItemImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemImageQueryService {

    private final ItemImageRepository itemImageRepository;

    public List<ItemImageResponse> findByItemId(Long itemId) {
        return itemImageRepository.findByItemIdOrderBySortOrder(itemId).stream()
                .map(ItemImageResponse::from).toList();
    }
}
