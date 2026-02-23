package com.example.media.controller.internal;

import com.example.api.response.ApiResponse;
import com.example.media.dto.query.request.MediaUrlBatchRequest;
import com.example.media.dto.query.response.MediaUrlResponse;
import com.example.media.service.query.MediaQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/v1/media")
@RequiredArgsConstructor
public class MediaInternalQueryController {

    private final MediaQueryService mediaQueryService;

    @GetMapping("/{mediaId}/url")
    public ApiResponse<MediaUrlResponse> getMediaUrl(@PathVariable Long mediaId) {
        return ApiResponse.success(mediaQueryService.getMediaUrl(mediaId, null));
    }

    @PostMapping("/urls/batch")
    public ApiResponse<List<MediaUrlResponse>> getMediaUrls(@Valid @RequestBody MediaUrlBatchRequest request) {
        return ApiResponse.success(mediaQueryService.getMediaUrls(request.getMediaIds(), null));
    }
}
