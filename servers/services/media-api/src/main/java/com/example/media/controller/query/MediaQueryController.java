package com.example.media.controller.query;

import com.example.api.response.ApiResponse;
import com.example.media.controller.api.query.MediaQueryApi;
import com.example.media.dto.query.response.MediaUrlResponse;
import com.example.media.service.query.MediaQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaQueryController implements MediaQueryApi {

    private final MediaQueryService mediaQueryService;

    @Override
    public ApiResponse<MediaUrlResponse> getMediaUrl(Long mediaId, Long userId) {
        return ApiResponse.success(mediaQueryService.getMediaUrl(mediaId, userId));
    }
}
