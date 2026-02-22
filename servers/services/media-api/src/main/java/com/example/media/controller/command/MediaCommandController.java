package com.example.media.controller.command;

import com.example.api.response.ApiResponse;
import com.example.media.controller.api.command.MediaCommandApi;
import com.example.media.dto.command.request.MediaLinksSyncRequest;
import com.example.media.dto.command.request.UploadConfirmRequest;
import com.example.media.dto.command.request.UploadIntentRequest;
import com.example.media.dto.command.response.MediaLinksSyncResponse;
import com.example.media.dto.command.response.UploadConfirmResponse;
import com.example.media.dto.command.response.UploadIntentResponse;
import com.example.media.service.command.MediaCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaCommandController implements MediaCommandApi {

    private final MediaCommandService mediaCommandService;

    @Override
    public ApiResponse<UploadIntentResponse> createUploadIntent(UploadIntentRequest request, Long userId) {
        return ApiResponse.success(mediaCommandService.createUploadIntent(request, userId));
    }

    @Override
    public ApiResponse<UploadConfirmResponse> confirmUpload(UploadConfirmRequest request, Long userId) {
        return ApiResponse.success(mediaCommandService.confirmUpload(request, userId));
    }

    @Override
    public ApiResponse<MediaLinksSyncResponse> syncLinks(MediaLinksSyncRequest request, Long userId) {
        return ApiResponse.success(mediaCommandService.syncLinks(request, userId));
    }
}
