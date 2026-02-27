package com.example.sales.service.query;

import com.example.clients.product.facade.ProductItemQueryClientFacade;
import com.example.clients.stock.facade.StockAvailabilityQueryFacade;
import com.example.core.exception.BusinessException;
import com.example.core.pagination.CursorResponse;
import com.example.sales.dto.response.SalesProductListItemResponse;
import com.example.sales.exception.SalesErrorCode;
import com.example.sales.repository.PurchaseRepository;
import com.example.sales.repository.SalesItemCursorProjection;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesProductQueryServiceTest {

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private ProductItemQueryClientFacade productItemQueryClientFacade;

    @Mock
    private StockAvailabilityQueryFacade stockAvailabilityQueryFacade;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private SalesProductQueryService service;

    @BeforeEach
    void setUp() {
        service = new SalesProductQueryService(
                purchaseRepository,
                productItemQueryClientFacade,
                stockAvailabilityQueryFacade
        );
    }

    @Test
    void findProducts_returnsCursorPage() throws Exception {
        SalesItemCursorProjection row = new SalesItemCursorProjection() {
            @Override
            public Long getItemId() {
                return 101L;
            }

            @Override
            public Long getCursorId() {
                return 9001L;
            }
        };

        when(purchaseRepository.findSalesItemCursorPage(anyList(), eq(null), any(Pageable.class)))
                .thenReturn(List.of(row));
        when(purchaseRepository.countDistinctItemIdByStatusIn(anyList())).thenReturn(1L);
        when(purchaseRepository.countByItemIdAndStatusIn(eq(101L), anyList())).thenReturn(7L);
        when(productItemQueryClientFacade.findItem(101L)).thenReturn(objectMapper.readTree("""
                {
                  "id": 101,
                  "title": "테스트 상품",
                  "price": 12000,
                  "images": {"thumbnail": {"mediaId": 333}}
                }
                """));
        when(stockAvailabilityQueryFacade.fetchAvailableStockTotal(101L)).thenReturn(25);

        CursorResponse<SalesProductListItemResponse> response = service.findProducts(null, 20);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getId()).isEqualTo(101L);
        assertThat(response.getItems().get(0).getTitle()).isEqualTo("테스트 상품");
        assertThat(response.getItems().get(0).getStatus()).isEqualTo("ON_SALE");
        assertThat(response.getTotalCount()).isEqualTo(1L);
    }

    @Test
    void findProducts_throwsWhenCursorInvalid() {
        assertThatThrownBy(() -> service.findProducts("%%%invalid%%%", 20))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(SalesErrorCode.INVALID_REQUEST));
    }

    @Test
    void findProductDetail_throwsWhenProductMissing() {
        when(productItemQueryClientFacade.findItem(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.findProductDetail(404L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(SalesErrorCode.PURCHASE_NOT_FOUND));
    }

    @Test
    void findProducts_usesSoldStatuses() {
        SalesItemCursorProjection row = new SalesItemCursorProjection() {
            @Override
            public Long getItemId() {
                return 1L;
            }

            @Override
            public Long getCursorId() {
                return 10L;
            }
        };

        when(purchaseRepository.findSalesItemCursorPage(anyList(), eq(null), any(Pageable.class)))
                .thenReturn(List.of(row));
        when(purchaseRepository.countDistinctItemIdByStatusIn(anyList())).thenReturn(1L);
        when(purchaseRepository.countByItemIdAndStatusIn(eq(1L), anyList())).thenReturn(0L);
        when(productItemQueryClientFacade.findItem(1L)).thenReturn(objectMapper.createObjectNode().put("title", "A"));
        when(stockAvailabilityQueryFacade.fetchAvailableStockTotal(1L)).thenReturn(0);

        CursorResponse<SalesProductListItemResponse> response = service.findProducts(null, 20);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getStatus()).isEqualTo("SOLD_OUT");
    }
}
