package com.example.search.controller.api.internal;

import com.example.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Tag(name = "Search Internal Popular Admin", description = "인기검색어 차단어 관리 API")
public interface SearchPopularKeywordAdminApi {

    @Operation(summary = "인기검색어 차단어 조회")
    @GetMapping("/popular/blocked-keywords")
    ApiResponse<List<String>> blockedKeywords();

    @Operation(summary = "인기검색어 차단어 추가")
    @PostMapping("/popular/blocked-keywords")
    ApiResponse<List<String>> addBlockedKeyword(@RequestParam @NotBlank String keyword);

    @Operation(summary = "인기검색어 차단어 삭제")
    @DeleteMapping("/popular/blocked-keywords")
    ApiResponse<List<String>> removeBlockedKeyword(@RequestParam @NotBlank String keyword);
}
