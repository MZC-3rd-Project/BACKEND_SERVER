package com.example.search.controller.internal;

import com.example.search.dto.reconciliation.response.StockReconciliationResponse;
import com.example.search.service.reconciliation.StockReconciliationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchStockReconciliationController.class)
class SearchStockReconciliationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StockReconciliationService stockReconciliationService;

    @Test
    void reconcileStock_returnsSuccessResponse() throws Exception {
        StockReconciliationResponse response = StockReconciliationResponse.builder()
                .itemId(101L)
                .sourceAvailableStock(12)
                .indexedStock(10)
                .matched(false)
                .result("mismatch")
                .build();
        given(stockReconciliationService.reconcileItem(101L)).willReturn(response);

        mockMvc.perform(get("/internal/v1/search/reconciliation/stock/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.itemId").value(101))
                .andExpect(jsonPath("$.data.result").value("mismatch"));
    }
}
