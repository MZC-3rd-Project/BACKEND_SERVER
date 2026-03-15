package com.example.product.service.query;

import com.example.core.exception.BusinessException;
import com.example.product.entity.category.Category;
import com.example.product.entity.goods.ItemGoodsLink;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemStatus;
import com.example.product.entity.item.ItemType;
import com.example.product.entity.goods.ShippingInfo;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.CategoryRepository;
import com.example.product.repository.ItemGoodsLinkRepository;
import com.example.product.repository.ItemImageRepository;
import com.example.product.repository.ItemOptionRepository;
import com.example.product.repository.ItemRepository;
import com.example.product.repository.ShippingInfoRepository;
import com.example.product.service.content.ItemContentService;
import com.example.product.service.query.assembler.GoodsDetailAssembler;
import com.example.product.service.query.detail.ItemCategoryDetailResolver;
import com.example.product.service.query.detail.GoodsItemDetailReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
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
    private CategoryRepository categoryRepository;
    @Mock
    private ItemGoodsLinkRepository itemGoodsLinkRepository;
    @Mock
    private ItemImageRepository itemImageRepository;
    @Mock
    private ItemContentService itemContentService;
    @Spy
    private ItemAccessPolicy itemAccessPolicy = new ItemAccessPolicy();
    @Spy
    private GoodsDetailAssembler goodsDetailAssembler = new GoodsDetailAssembler();
    private ItemCategoryDetailResolver itemCategoryDetailResolver;
    private GoodsItemDetailReader goodsItemDetailReader;
    private GoodsQueryService goodsQueryService;

    @BeforeEach
    void setUp() {
        itemCategoryDetailResolver = new ItemCategoryDetailResolver(categoryRepository);
        goodsItemDetailReader = new GoodsItemDetailReader(
                itemRepository,
                itemOptionRepository,
                shippingInfoRepository,
                itemImageRepository,
                itemContentService,
                itemCategoryDetailResolver,
                itemGoodsLinkRepository
        );
        goodsQueryService = new GoodsQueryService(
                itemRepository,
                itemAccessPolicy,
                goodsItemDetailReader,
                goodsDetailAssembler
        );
    }

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
        Item hiddenGoods = Item.create("goods", "desc", 1000L, ItemType.GOODS, 200L, sellerId, 1L, null);
        ReflectionTestUtils.setField(hiddenGoods, "id", itemId);
        ReflectionTestUtils.setField(hiddenGoods, "status", ItemStatus.HIDDEN);
        Category rootCategory = Category.createRoot("Goods", 1);
        ReflectionTestUtils.setField(rootCategory, "id", 100L);
        Category childCategory = Category.createChild("Official MD", 100L, 0, 1);
        ReflectionTestUtils.setField(childCategory, "id", 200L);
        ShippingInfo shippingInfo = ShippingInfo.create(
                itemId,
                3000L,
                50000L,
                2,
                "return",
                "CJ",
                "Seoul",
                "Incheon",
                3500L,
                7000L,
                "remote area extra"
        );

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(hiddenGoods));
        when(itemOptionRepository.findByItemIdIn(List.of(itemId))).thenReturn(List.of());
        when(shippingInfoRepository.findByItemIdIn(List.of(itemId))).thenReturn(List.of(shippingInfo));
        when(itemGoodsLinkRepository.findByGoodsItemIdIn(List.of(itemId))).thenReturn(List.of());
        when(itemImageRepository.findByItemIdInOrderByItemIdAscSortOrderAsc(List.of(itemId))).thenReturn(List.of());
        when(itemContentService.findByItemIds(List.of(itemId))).thenReturn(Map.of());
        when(categoryRepository.findAllByOrderByDepthAscSortOrderAsc()).thenReturn(List.of(rootCategory, childCategory));

        var response = goodsQueryService.findSellerGoodsById(itemId, sellerId);

        assertThat(response.getStatus()).isEqualTo(ItemStatus.HIDDEN.name());
        assertThat(response.getCategoryName()).isEqualTo("Official MD");
        assertThat(response.getCategoryPath()).containsExactly("Goods", "Official MD");
        assertThat(response.getPriceMeta()).isNotNull();
        assertThat(response.getPriceMeta().getStartingPrice()).isEqualTo(1000L);
        assertThat(response.getShippingInfo()).isNotNull();
        assertThat(response.getShippingInfo().getCarrier()).isEqualTo("CJ");
        assertThat(response.getShippingInfo().getShipFrom()).isEqualTo("Seoul");
        assertThat(response.getShippingInfo().getReturnAddress()).isEqualTo("Incheon");
        assertThat(response.getShippingInfo().getReturnShippingFee()).isEqualTo(3500L);
        assertThat(response.getShippingInfo().getExchangeShippingFee()).isEqualTo(7000L);
        assertThat(response.getShippingInfo().getShippingNotice()).isEqualTo("remote area extra");
    }

    @Test
    void findGoodsList_includesLinkedPerformanceIds() {
        Item visibleGoods = Item.create("goods", "desc", 1000L, ItemType.GOODS, null, 1L, 1L, null);
        ReflectionTestUtils.setField(visibleGoods, "id", 301L);
        ReflectionTestUtils.setField(visibleGoods, "status", ItemStatus.ON_SALE);
        ItemGoodsLink link = ItemGoodsLink.create(901L, 301L);
        Item linkedPerformance = Item.create("concert", "desc", 2000L, ItemType.PERFORMANCE, null, 3L, 4L, null);
        ReflectionTestUtils.setField(linkedPerformance, "id", 901L);
        ReflectionTestUtils.setField(linkedPerformance, "status", ItemStatus.FUNDING);

        when(itemRepository.findByItemTypeAndStatusIn(ItemType.GOODS, itemAccessPolicy.visibleStatuses(), org.springframework.data.domain.PageRequest.of(0, 2)))
                .thenReturn(List.of(visibleGoods));
        when(itemRepository.findAllById(List.of(901L))).thenReturn(List.of(linkedPerformance));
        when(itemOptionRepository.findByItemIdIn(List.of(301L))).thenReturn(List.of());
        when(shippingInfoRepository.findByItemIdIn(List.of(301L))).thenReturn(List.of());
        when(itemGoodsLinkRepository.findByGoodsItemIdIn(List.of(301L))).thenReturn(List.of(link));
        when(itemImageRepository.findByItemIdInOrderByItemIdAscSortOrderAsc(List.of(301L))).thenReturn(List.of());
        when(itemImageRepository.findByItemIdInOrderByItemIdAscSortOrderAsc(List.of(901L))).thenReturn(List.of());
        when(itemContentService.findByItemIds(List.of(301L))).thenReturn(Map.of());

        var response = goodsQueryService.findGoodsList(null, 1);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().getFirst().getId()).isEqualTo(301L);
        assertThat(response.getItems().getFirst().getLinkedPerformanceItemIds()).containsExactly(901L);
        assertThat(response.getItems().getFirst().getLinkedPerformanceItems()).hasSize(1);
        assertThat(response.getItems().getFirst().getLinkedPerformanceItems().getFirst().getId()).isEqualTo(901L);
        assertThat(response.getItems().getFirst().getLinkedPerformanceItems().getFirst().getTitle()).isEqualTo("concert");
        assertThat(response.getItems().getFirst().getPriceMeta()).isNotNull();
        assertThat(response.getItems().getFirst().getPriceMeta().getStartingPrice()).isEqualTo(1000L);
        assertThat(response.getNextCursor()).isNull();
    }
}
