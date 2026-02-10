package com.example.product.service.query;

import com.example.product.dto.item.response.StatusHistoryResponse;
import com.example.product.repository.ItemStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemStatusQueryService {

    private final ItemStatusHistoryRepository statusHistoryRepository;

    public List<StatusHistoryResponse> getHistory(Long itemId) {
        return statusHistoryRepository.findByItemIdOrderByCreatedAtDesc(itemId).stream()
                .map(StatusHistoryResponse::from).toList();
    }
}
