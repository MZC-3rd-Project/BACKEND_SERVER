package com.example.search.dto.autocomplete.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AutocompleteResponse {

    private final List<String> suggestions;
}
