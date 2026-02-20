package com.example.search.controller.query;

import com.example.search.dto.search.response.SearchResponse;
import com.example.search.service.query.SearchQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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
        SearchResponse response = SearchResponse.builder()
                .items(List.of())
                .nextCursor(null)
                .total(0L)
                .build();
        given(searchQueryService.search(any())).willReturn(response);

        mockMvc.perform(get("/api/v1/search")
                        .param("q", "아이폰")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(0));
    }
}
