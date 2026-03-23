package com.example.product.service.command;

import com.example.event.EventPublisher;
import com.example.product.dto.item.request.ReviewMetricsUpdateRequest;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemType;
import com.example.product.repository.ItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemReviewMetricCommandServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private ItemReviewMetricCommandService itemReviewMetricCommandService;

    @Test
    void updateReviewMetrics_updatesItemAndPublishesItemUpdatedEvent() {
        Item item = Item.create("item", "desc", 1000L, ItemType.PRODUCT, null, 10L, 20L, null);
        ReflectionTestUtils.setField(item, "id", 1L);
        when(itemRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(item));

        ReviewMetricsUpdateRequest request = new ReviewMetricsUpdateRequest();
        request.setAverageRating(new BigDecimal("4.67"));
        request.setReviewCount(3L);

        itemReviewMetricCommandService.updateReviewMetrics(1L, request);

        assertThat(item.getAverageRating()).isEqualByComparingTo("4.67");
        assertThat(item.getReviewCount()).isEqualTo(3L);
        verify(eventPublisher).publish(any(), any());
    }
}
