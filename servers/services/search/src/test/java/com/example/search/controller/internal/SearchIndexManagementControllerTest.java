package com.example.search.controller.internal;

import com.example.search.dto.index.response.IndexRecreateResponse;
import com.example.search.service.index.IndexManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchIndexManagementController.class)
class SearchIndexManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IndexManagementService indexManagementService;

    @Test
    void recreateIndex_returnsSuccessResponse() throws Exception {
        IndexRecreateResponse response = IndexRecreateResponse.builder()
                .indexName("items")
                .existed(true)
                .recreated(true)
                .requestedAt(Instant.parse("2026-02-20T10:00:00Z"))
                .build();
        given(indexManagementService.recreateItemsIndex("items")).willReturn(response);

        mockMvc.perform(post("/internal/v1/search/indexes/recreate")
                        .param("indexName", "items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.indexName").value("items"))
                .andExpect(jsonPath("$.data.existed").value(true))
                .andExpect(jsonPath("$.data.recreated").value(true));
    }
}
