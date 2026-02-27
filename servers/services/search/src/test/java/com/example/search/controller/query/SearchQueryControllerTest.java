package com.example.search.controller.query;

import com.example.core.pagination.CursorResponse;
import com.example.search.dto.search.response.SearchItemResponse;
import com.example.search.service.query.SearchQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchQueryController.class)
class SearchQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchQueryService searchQueryService;

    @Test
    void search_returnsSuccessResponse() throws Exception {
        CursorResponse<SearchItemResponse> response = CursorResponse.of(List.of(), null, 0L);
        given(searchQueryService.search(any())).willReturn(response);

        mockMvc.perform(get("/api/v1/search")
                        .param("q", "아이폰")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalCount").value(0));
    }

    @Test
    void search_acceptsRequestWhenQueryIsBlank() throws Exception {
        CursorResponse<SearchItemResponse> response = CursorResponse.of(List.of(), null, 0L);
        given(searchQueryService.search(any())).willReturn(response);

        mockMvc.perform(get("/api/v1/search")
                        .param("q", "  ")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void search_returnsBadRequestWhenMinPriceIsNegative() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .param("q", "아이폰")
                        .param("minPrice", "-1")
                        .param("size", "20"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(searchQueryService);
    }
}
