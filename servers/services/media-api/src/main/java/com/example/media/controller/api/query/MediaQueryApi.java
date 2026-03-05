package com.example.media.controller.api.query;

import com.example.api.response.ApiResponse;
import com.example.media.dto.query.response.MediaUrlResponse;
import com.example.security.starter.servlet.annotation.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "Media Query", description = "미디어 URL 조회/재발급 API")
public interface MediaQueryApi {

    @Operation(summary = "미디어 URL 조회/재발급")
    @GetMapping("/{mediaId}/url")
    ApiResponse<MediaUrlResponse> getMediaUrl(
            @PathVariable Long mediaId,
            @CurrentUserId(required = false) Long userId
    );
}
