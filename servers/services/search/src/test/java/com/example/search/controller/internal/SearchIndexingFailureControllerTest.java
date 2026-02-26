package com.example.search.controller.internal;

import com.example.search.dto.index.response.IndexingFailureRetryResponse;
import com.example.search.entity.SearchIndexingFailureStatus;
import com.example.search.service.index.SearchIndexingFailureService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchIndexingFailureController.class)
class SearchIndexingFailureControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchIndexingFailureService searchIndexingFailureService;

    @Test
    void retryFailure_returnsSuccessResponse() throws Exception {
        IndexingFailureRetryResponse response = IndexingFailureRetryResponse.builder()
                .failureId(1L)
                .eventId("evt-1")
                .eventType("ITEM_UPDATED")
                .status(SearchIndexingFailureStatus.RESOLVED)
                .retryCount(1)
                .lastRetriedAt(LocalDateTime.parse("2026-02-20T10:00:00"))
                .build();
        given(searchIndexingFailureService.retryFailure(1L)).willReturn(response);

        mockMvc.perform(post("/internal/v1/search/indexing-failures/1/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.failureId").value(1))
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));
    }
}
