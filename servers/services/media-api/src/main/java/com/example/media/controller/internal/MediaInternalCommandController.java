package com.example.media.controller.internal;

import com.example.api.response.ApiResponse;
import com.example.media.dto.command.request.MediaLinksSyncRequest;
import com.example.media.dto.command.response.MediaLinksSyncResponse;
import com.example.media.service.command.MediaCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/media")
@RequiredArgsConstructor
public class MediaInternalCommandController {

    private final MediaCommandService mediaCommandService;

    @PutMapping("/links/sync")
    public ApiResponse<MediaLinksSyncResponse> syncLinks(@Valid @RequestBody MediaLinksSyncRequest request) {
        return ApiResponse.success(mediaCommandService.syncLinks(request, null));
    }
}
