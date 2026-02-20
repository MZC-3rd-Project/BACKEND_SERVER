package com.example.search.controller.query;

import com.example.api.response.ApiResponse;
import com.example.search.controller.api.query.SearchAutocompleteApi;
import com.example.search.dto.autocomplete.request.AutocompleteRequest;
import com.example.search.dto.autocomplete.response.AutocompleteResponse;
import com.example.search.service.query.autocomplete.AutocompleteService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchAutocompleteController implements SearchAutocompleteApi {

    private final AutocompleteService autocompleteService;

    @Override
    public ApiResponse<AutocompleteResponse> autocomplete(String q, int size) {
        AutocompleteRequest request = new AutocompleteRequest();
        request.setQ(q);
        request.setSize(size);
        return ApiResponse.success(autocompleteService.suggest(request));
    }
}
