package com.example.product.service.query;

import com.example.core.exception.BusinessException;
import com.example.product.entity.item.ItemStatus;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.ItemImageRepository;
import com.example.product.repository.ItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalItemQueryServiceTest {

    @Mock
    private ItemRepository itemRepository;
    @Mock
    private ItemImageRepository itemImageRepository;

    @InjectMocks
    private InternalItemQueryService internalItemQueryService;

    @Test
    void findById_whenItemMissing_throwsItemNotFound() {
        Long itemId = 1L;
        when(itemRepository.findById(itemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> internalItemQueryService.findById(itemId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.ITEM_NOT_FOUND);
    }

    @Test
    void findItemsEndingSoon_queriesOnSaleCandidates() {
        when(itemRepository.findByStatusIn(eq(List.of(ItemStatus.ON_SALE)), any(Pageable.class)))
                .thenReturn(List.of());
        when(itemImageRepository.findByItemIdInOrderByItemIdAscSortOrderAsc(List.of()))
                .thenReturn(List.of());

        internalItemQueryService.findItemsEndingSoon();

        verify(itemRepository).findByStatusIn(eq(List.of(ItemStatus.ON_SALE)), any(Pageable.class));
    }
}
