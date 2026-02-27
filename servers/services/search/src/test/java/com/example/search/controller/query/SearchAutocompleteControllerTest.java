package com.example.search.controller.query;

import com.example.search.dto.autocomplete.response.AutocompleteResponse;
import com.example.search.service.query.autocomplete.AutocompleteService;
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

@WebMvcTest(SearchAutocompleteController.class)
class SearchAutocompleteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AutocompleteService autocompleteService;

    @Test
    void autocomplete_returnsSuggestions() throws Exception {
        given(autocompleteService.suggest(any()))
                .willReturn(AutocompleteResponse.builder()
                        .suggestions(List.of("아이폰", "아이폰 케이스"))
                        .build());

        mockMvc.perform(get("/api/v1/search/autocomplete")
                        .param("q", "아이")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.suggestions[0]").value("아이폰"));
    }
}
