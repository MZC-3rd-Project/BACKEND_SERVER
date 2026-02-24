package com.example.product.service.query;

import com.example.core.exception.BusinessException;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemType;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.CastMemberRepository;
import com.example.product.repository.ItemImageRepository;
import com.example.product.repository.ItemRepository;
import com.example.product.repository.PerformanceRepository;
import com.example.product.repository.SeatGradeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformanceQueryServiceTest {

    @Mock
    private ItemRepository itemRepository;
    @Mock
    private PerformanceRepository performanceRepository;
    @Mock
    private SeatGradeRepository seatGradeRepository;
    @Mock
    private CastMemberRepository castMemberRepository;
    @Mock
    private ItemImageRepository itemImageRepository;

    @InjectMocks
    private PerformanceQueryService performanceQueryService;

    @Test
    void findById_whenItemTypeMismatch_throwsBusinessError() {
        Long itemId = 100L;
        Item goodsItem = Item.create("goods", "desc", 1000L, ItemType.GOODS, null, 1L, 1L, null);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(goodsItem));

        assertThatThrownBy(() -> performanceQueryService.findById(itemId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.ITEM_TYPE_MISMATCH);

        verifyNoInteractions(performanceRepository, seatGradeRepository, castMemberRepository, itemImageRepository);
    }
}

