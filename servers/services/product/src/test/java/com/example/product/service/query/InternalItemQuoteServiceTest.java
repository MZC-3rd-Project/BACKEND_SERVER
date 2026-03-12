package com.example.product.service.query;

import com.example.product.dto.item.request.ItemQuoteRequest;
import com.example.product.dto.item.response.ItemQuoteResponse;
import com.example.product.entity.goods.ItemOption;
import com.example.product.entity.item.Item;
import com.example.product.entity.item.ItemStatus;
import com.example.product.entity.item.ItemType;
import com.example.product.entity.performance.Performance;
import com.example.product.entity.performance.SeatGrade;
import com.example.product.repository.ItemOptionRepository;
import com.example.product.repository.ItemRepository;
import com.example.product.repository.PerformanceRepository;
import com.example.product.repository.SeatGradeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalItemQuoteServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private ItemOptionRepository itemOptionRepository;

    @Mock
    private PerformanceRepository performanceRepository;

    @Mock
    private SeatGradeRepository seatGradeRepository;

    @InjectMocks
    private InternalItemQuoteService internalItemQuoteService;

    @Test
    void quote_goodsItem_returnsOptionAdjustedPrice() {
        Item item = createItem(10L, "MZC 티셔츠", 10_000L, ItemType.GOODS, ItemStatus.ON_SALE, 1L, 2L);
        ItemOption option = ItemOption.create(10L, "BLACK / L", 2_000L, 50);
        ReflectionTestUtils.setField(option, "id", 101L);

        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));
        when(itemOptionRepository.findById(101L)).thenReturn(Optional.of(option));

        ItemQuoteResponse response = internalItemQuoteService.quote(request("NORMAL", 10L, 101L, 2));

        assertThat(response.getTotalAmount()).isEqualTo(24_000L);
        assertThat(response.getLineItems()).hasSize(1);
        ItemQuoteResponse.QuotedLineItem lineItem = response.getLineItems().getFirst();
        assertThat(lineItem.getStockItemType()).isEqualTo("ITEM_OPTION");
        assertThat(lineItem.getBaseUnitPrice()).isEqualTo(10_000L);
        assertThat(lineItem.getFinalUnitPrice()).isEqualTo(12_000L);
        assertThat(lineItem.getLineAmount()).isEqualTo(24_000L);
    }

    @Test
    void quote_performanceItem_returnsSeatGradePrice() {
        Item item = createItem(20L, "MZC 콘서트", 5_000L, ItemType.PERFORMANCE, ItemStatus.FUNDING, 3L, 4L);
        Performance performance = Performance.create(20L, "KSPO", LocalDate.of(2026, 3, 20), LocalTime.of(19, 0), 100);
        ReflectionTestUtils.setField(performance, "id", 200L);
        SeatGrade seatGrade = SeatGrade.create(200L, "R석", 15_000L, 50, 20);
        ReflectionTestUtils.setField(seatGrade, "id", 201L);

        when(itemRepository.findById(20L)).thenReturn(Optional.of(item));
        when(seatGradeRepository.findById(201L)).thenReturn(Optional.of(seatGrade));
        when(performanceRepository.findById(200L)).thenReturn(Optional.of(performance));

        ItemQuoteResponse response = internalItemQuoteService.quote(request("FUNDING", 20L, 201L, 1));

        assertThat(response.getTotalAmount()).isEqualTo(15_000L);
        assertThat(response.getLineItems()).hasSize(1);
        ItemQuoteResponse.QuotedLineItem lineItem = response.getLineItems().getFirst();
        assertThat(lineItem.getStockItemType()).isEqualTo("SEAT_GRADE");
        assertThat(lineItem.getBaseUnitPrice()).isEqualTo(5_000L);
        assertThat(lineItem.getFinalUnitPrice()).isEqualTo(15_000L);
        assertThat(lineItem.getReferenceName()).isEqualTo("R석");
    }

    @Test
    void quote_allowsMixedChannelTypesPerLineItem() {
        Item goodsItem = createItem(10L, "MZC 티셔츠", 10_000L, ItemType.GOODS, ItemStatus.ON_SALE, 1L, 2L);
        ItemOption option = ItemOption.create(10L, "BLACK / L", 2_000L, 50);
        ReflectionTestUtils.setField(option, "id", 101L);
        Item fundingItem = createItem(20L, "MZC 콘서트", 5_000L, ItemType.PERFORMANCE, ItemStatus.FUNDING, 3L, 4L);
        Performance performance = Performance.create(20L, "KSPO", LocalDate.of(2026, 3, 20), LocalTime.of(19, 0), 100);
        ReflectionTestUtils.setField(performance, "id", 200L);
        SeatGrade seatGrade = SeatGrade.create(200L, "R석", 15_000L, 50, 20);
        ReflectionTestUtils.setField(seatGrade, "id", 201L);

        when(itemRepository.findById(10L)).thenReturn(Optional.of(goodsItem));
        when(itemRepository.findById(20L)).thenReturn(Optional.of(fundingItem));
        when(itemOptionRepository.findById(101L)).thenReturn(Optional.of(option));
        when(seatGradeRepository.findById(201L)).thenReturn(Optional.of(seatGrade));
        when(performanceRepository.findById(200L)).thenReturn(Optional.of(performance));

        ItemQuoteRequest.LineItem goodsLineItem = new ItemQuoteRequest.LineItem();
        ReflectionTestUtils.setField(goodsLineItem, "itemId", 10L);
        ReflectionTestUtils.setField(goodsLineItem, "channelType", "NORMAL");
        ReflectionTestUtils.setField(goodsLineItem, "channelRefId", null);
        ReflectionTestUtils.setField(goodsLineItem, "referenceId", 101L);
        ReflectionTestUtils.setField(goodsLineItem, "quantity", 1);

        ItemQuoteRequest.LineItem fundingLineItem = new ItemQuoteRequest.LineItem();
        ReflectionTestUtils.setField(fundingLineItem, "itemId", 20L);
        ReflectionTestUtils.setField(fundingLineItem, "channelType", "FUNDING");
        ReflectionTestUtils.setField(fundingLineItem, "channelRefId", 4401L);
        ReflectionTestUtils.setField(fundingLineItem, "referenceId", 201L);
        ReflectionTestUtils.setField(fundingLineItem, "quantity", 1);

        ItemQuoteRequest request = new ItemQuoteRequest();
        ReflectionTestUtils.setField(request, "lineItems", List.of(goodsLineItem, fundingLineItem));

        ItemQuoteResponse response = internalItemQuoteService.quote(request);

        assertThat(response.getTotalAmount()).isEqualTo(27_000L);
        assertThat(response.getLineItems()).hasSize(2);
        assertThat(response.getLineItems().get(0).getItemId()).isEqualTo(10L);
        assertThat(response.getLineItems().get(1).getItemId()).isEqualTo(20L);
    }

    private Item createItem(Long id, String title, Long price, ItemType itemType, ItemStatus status, Long sellerId, Long storeId) {
        Item item = Item.create(title, "desc", price, itemType, null, sellerId, storeId, null);
        ReflectionTestUtils.setField(item, "id", id);
        ReflectionTestUtils.setField(item, "status", status);
        return item;
    }

    private ItemQuoteRequest request(String channelType, Long itemId, Long referenceId, int quantity) {
        ItemQuoteRequest.LineItem lineItem = new ItemQuoteRequest.LineItem();
        ReflectionTestUtils.setField(lineItem, "itemId", itemId);
        ReflectionTestUtils.setField(lineItem, "channelType", channelType);
        ReflectionTestUtils.setField(lineItem, "channelRefId", null);
        ReflectionTestUtils.setField(lineItem, "referenceId", referenceId);
        ReflectionTestUtils.setField(lineItem, "quantity", quantity);

        ItemQuoteRequest request = new ItemQuoteRequest();
        ReflectionTestUtils.setField(request, "lineItems", List.of(lineItem));
        return request;
    }
}
