package com.example.product.service.command;

import com.example.core.exception.BusinessException;
import com.example.event.EventPublisher;
import com.example.product.dto.item.request.StatusChangeRequest;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemStatus;
import com.example.product.entity.item.ItemType;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.ItemRepository;
import com.example.product.repository.ItemStatusHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemStatusCommandServiceTest {

    @Mock
    private ItemRepository itemRepository;
    @Mock
    private ItemStatusHistoryRepository statusHistoryRepository;
    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private ItemStatusCommandService itemStatusCommandService;

    @Test
    void changeStatus_whenStatusIsLowerCase_parsesAndChanges() {
        Long itemId = 1L;
        Long ownerId = 10L;
        Item item = createItem(itemId, ownerId);
        StatusChangeRequest request = new StatusChangeRequest();
        ReflectionTestUtils.setField(request, "status", "on_sale");
        ReflectionTestUtils.setField(request, "reason", "manual");

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));

        itemStatusCommandService.changeStatus(itemId, request, ownerId);

        assertThat(item.getStatus()).isEqualTo(ItemStatus.ON_SALE);
        verify(statusHistoryRepository).save(any());
        verify(eventPublisher).publish(any(), any());
    }

    @Test
    void changeStatus_whenStatusIsInvalid_throwsDomainError() {
        Long itemId = 1L;
        Long ownerId = 10L;
        Item item = createItem(itemId, ownerId);
        StatusChangeRequest request = new StatusChangeRequest();
        ReflectionTestUtils.setField(request, "status", "on-sale");

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> itemStatusCommandService.changeStatus(itemId, request, ownerId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.INVALID_ITEM_STATUS_TRANSITION);

        verifyNoInteractions(statusHistoryRepository);
        verifyNoInteractions(eventPublisher);
    }

    private Item createItem(Long itemId, Long sellerId) {
        Item item = Item.create(
                "item",
                "desc",
                1000L,
                ItemType.PRODUCT,
                null,
                sellerId,
                100L,
                null
        );
        ReflectionTestUtils.setField(item, "id", itemId);
        return item;
    }
}
