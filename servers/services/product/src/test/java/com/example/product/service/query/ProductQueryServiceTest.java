package com.example.product.service.query;

import com.example.core.exception.BusinessException;
import com.example.product.entity.category.Category;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemStatus;
import com.example.product.entity.item.ItemType;
import com.example.product.entity.goods.ShippingInfo;
import com.example.product.exception.ProductErrorCode;
import com.example.product.service.query.assembler.ProductDetailAssembler;
import com.example.product.service.query.detail.ProductItemDetailReader;
import com.example.product.service.query.detail.ItemCategoryDetailResolver;
import com.example.product.repository.CategoryRepository;
import com.example.product.repository.ItemImageRepository;
import com.example.product.repository.ItemOptionRepository;
import com.example.product.repository.ItemRepository;
import com.example.product.repository.ShippingInfoRepository;
import com.example.product.service.content.ItemContentService;
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
class ProductQueryServiceTest {

    @Mock
    private ItemRepository itemRepository;
    @Mock
    private ItemOptionRepository itemOptionRepository;
    @Mock
    private ShippingInfoRepository shippingInfoRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ItemImageRepository itemImageRepository;
    @Mock
    private ItemContentService itemContentService;
    @Spy
    private ItemAccessPolicy itemAccessPolicy = new ItemAccessPolicy();
    @Spy
    private ProductDetailAssembler productDetailAssembler = new ProductDetailAssembler();
    private ItemCategoryDetailResolver itemCategoryDetailResolver;
    private ProductItemDetailReader productItemDetailReader;
    private ProductQueryService productQueryService;

    @BeforeEach
    void setUp() {
        itemCategoryDetailResolver = new ItemCategoryDetailResolver(categoryRepository);
        productItemDetailReader = new ProductItemDetailReader(
                itemRepository,
                itemOptionRepository,
                shippingInfoRepository,
                itemImageRepository,
                itemContentService,
                itemCategoryDetailResolver
        );
        productQueryService = new ProductQueryService(
                itemRepository,
                itemAccessPolicy,
                productItemDetailReader,
                productDetailAssembler
        );
    }

    @Test
    void findProductById_whenItemTypeMismatch_throwsBusinessError() {
        Long itemId = 100L;
        Item goodsItem = Item.create("goods", "desc", 1000L, ItemType.GOODS, null, 1L, 1L, null);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(goodsItem));

        assertThatThrownBy(() -> productQueryService.findProductById(itemId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.ITEM_TYPE_MISMATCH);

        verifyNoInteractions(itemOptionRepository, shippingInfoRepository, itemImageRepository);
    }

    @Test
    void findProductById_whenItemNotVisible_throwsItemNotFound() {
        Long itemId = 101L;
        Item hiddenProduct = Item.create("product", "desc", 1000L, ItemType.PRODUCT, null, 1L, 1L, null);
        ReflectionTestUtils.setField(hiddenProduct, "status", ItemStatus.HIDDEN);

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(hiddenProduct));

        assertThatThrownBy(() -> productQueryService.findProductById(itemId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.ITEM_NOT_FOUND);

        verifyNoInteractions(itemOptionRepository, shippingInfoRepository, itemImageRepository);
    }

    @Test
    void findSellerProductById_whenOwnerCanAccessHiddenItem_returnsDetail() {
        Long itemId = 102L;
        Long sellerId = 7L;
        Item hiddenProduct = Item.create("product", "desc", 1000L, ItemType.PRODUCT, 210L, sellerId, 1L, null);
        ReflectionTestUtils.setField(hiddenProduct, "id", itemId);
        ReflectionTestUtils.setField(hiddenProduct, "status", ItemStatus.HIDDEN);
        Category rootCategory = Category.createRoot("Electronics", 1);
        ReflectionTestUtils.setField(rootCategory, "id", 110L);
        Category childCategory = Category.createChild("Audio", 110L, 0, 1);
        ReflectionTestUtils.setField(childCategory, "id", 210L);
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

        when(itemRepository.findById(itemId)).thenReturn(Optional.of(hiddenProduct));
        when(itemOptionRepository.findByItemIdIn(List.of(itemId))).thenReturn(List.of());
        when(shippingInfoRepository.findByItemIdIn(List.of(itemId))).thenReturn(List.of(shippingInfo));
        when(itemImageRepository.findByItemIdInOrderByItemIdAscSortOrderAsc(List.of(itemId))).thenReturn(List.of());
        when(itemContentService.findByItemIds(List.of(itemId))).thenReturn(Map.of());
        when(categoryRepository.findAllByOrderByDepthAscSortOrderAsc()).thenReturn(List.of(rootCategory, childCategory));

        var response = productQueryService.findSellerProductById(itemId, sellerId);

        assertThat(response.getStatus()).isEqualTo(ItemStatus.HIDDEN.name());
        assertThat(response.getCategoryName()).isEqualTo("Audio");
        assertThat(response.getCategoryPath()).containsExactly("Electronics", "Audio");
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
    void findProductList_returnsAssembledResponses() {
        Item visibleProduct = Item.create("product", "desc", 1000L, ItemType.PRODUCT, null, 1L, 1L, null);
        ReflectionTestUtils.setField(visibleProduct, "id", 300L);
        ReflectionTestUtils.setField(visibleProduct, "status", ItemStatus.ON_SALE);

        when(itemRepository.findByItemTypeAndStatusIn(ItemType.PRODUCT, itemAccessPolicy.visibleStatuses(), org.springframework.data.domain.PageRequest.of(0, 2)))
                .thenReturn(List.of(visibleProduct));
        when(itemOptionRepository.findByItemIdIn(List.of(300L))).thenReturn(List.of());
        when(shippingInfoRepository.findByItemIdIn(List.of(300L))).thenReturn(List.of());
        when(itemImageRepository.findByItemIdInOrderByItemIdAscSortOrderAsc(List.of(300L))).thenReturn(List.of());
        when(itemContentService.findByItemIds(List.of(300L))).thenReturn(Map.of());

        var response = productQueryService.findProductList(null, 1);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().getFirst().getId()).isEqualTo(300L);
        assertThat(response.getItems().getFirst().getLinkedPerformanceItemIds()).isEmpty();
        assertThat(response.getItems().getFirst().getLinkedPerformanceItems()).isEmpty();
        assertThat(response.getItems().getFirst().getPriceMeta().getStartingPrice()).isEqualTo(1000L);
        assertThat(response.getNextCursor()).isNull();
    }
}
