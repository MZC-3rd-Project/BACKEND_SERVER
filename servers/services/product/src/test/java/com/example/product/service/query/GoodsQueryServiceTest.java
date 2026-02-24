package com.example.product.service.query;

import com.example.core.exception.BusinessException;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemStatus;
import com.example.product.entity.item.ItemType;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.ItemGoodsLinkRepository;
import com.example.product.repository.ItemImageRepository;
import com.example.product.repository.ItemOptionRepository;
import com.example.product.repository.ItemRepository;
import com.example.product.repository.ShippingInfoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoodsQueryServiceTest {

    @Mock
    private ItemRepository itemRepository;
    @Mock
    private ItemOptionRepository itemOptionRepository;
    @Mock
    private ShippingInfoRepository shippingInfoRepository;
    @Mock
    private ItemGoodsLinkRepository itemGoodsLinkRepository;
    @Mock
    private ItemImageRepository itemImageRepository;

    @InjectMocks
    private GoodsQueryService goodsQueryService;

    @Test
    void findGoodsById_whenItemTypeMismatch_throwsBusinessError() {
        Long itemId = 100L;
        Item productItem = Item.create("product", "desc", 1000L, ItemType.PRODUCT, null, 1L, 1L, null);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(productItem));

        assertThatThrownBy(() -> goodsQueryService.findGoodsById(itemId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.ITEM_TYPE_MISMATCH);

        verifyNoInteractions(itemOptionRepository, shippingInfoRepository, itemGoodsLinkRepository, itemImageRepository);
    }

    @Test
    void findGoodsById_whenItemNotVisible_throwsItemNotFound() {
        Long itemId = 101L;
        Item hiddenGoods = Item.create("goods", "desc", 1000L, ItemType.GOODS, null, 1L, 1L, null);
        ReflectionTestUtils.setField(hiddenGoods, "status", ItemStatus.HIDDEN);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(hiddenGoods));

        assertThatThrownBy(() -> goodsQueryService.findGoodsById(itemId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.ITEM_NOT_FOUND);

        verifyNoInteractions(itemOptionRepository, shippingInfoRepository, itemGoodsLinkRepository, itemImageRepository);
    }

    @Test
    void findSellerGoodsById_whenOwnerCanAccessHiddenItem_returnsDetail() {
        Long itemId = 102L;
        Long sellerId = 9L;
        Item hiddenGoods = Item.create("goods", "desc", 1000L, ItemType.GOODS, null, sellerId, 1L, null);
        ReflectionTestUtils.setField(hiddenGoods, "status", ItemStatus.HIDDEN);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(hiddenGoods));
        when(itemOptionRepository.findByItemId(itemId)).thenReturn(List.of());
        when(shippingInfoRepository.findByItemId(itemId)).thenReturn(Optional.empty());
        when(itemGoodsLinkRepository.findByGoodsItemId(itemId)).thenReturn(List.of());
        when(itemImageRepository.findByItemIdOrderBySortOrder(itemId)).thenReturn(List.of());

        assertThat(goodsQueryService.findSellerGoodsById(itemId, sellerId).getStatus())
                .isEqualTo(ItemStatus.HIDDEN.name());
    }
}
