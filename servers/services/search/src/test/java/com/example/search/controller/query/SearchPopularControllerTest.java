package com.example.search.controller.query;

import com.example.search.dto.popular.response.PopularSearchResponse;
import com.example.search.service.query.popular.PopularSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchPopularController.class)
class SearchPopularControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PopularSearchService popularSearchService;

    @Test
    void popular_returnsTopKeywords() throws Exception {
        PopularSearchResponse response = PopularSearchResponse.builder()
                .keywords(List.of(
                        PopularSearchResponse.KeywordCount.builder().keyword("아이폰").count(12L).build(),
                        PopularSearchResponse.KeywordCount.builder().keyword("아이패드").count(8L).build()
                ))
                .build();
        given(popularSearchService.getTopKeywords()).willReturn(response);

        mockMvc.perform(get("/api/v1/search/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.keywords[0].keyword").value("아이폰"))
                .andExpect(jsonPath("$.data.keywords[0].count").value(12));
    }
}
