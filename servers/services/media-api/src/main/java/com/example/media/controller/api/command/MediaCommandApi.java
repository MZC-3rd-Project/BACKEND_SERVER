package com.example.media.controller.api.command;

import com.example.api.response.ApiResponse;
import com.example.media.dto.command.request.MediaLinksSyncRequest;
import com.example.media.dto.command.request.UploadConfirmRequest;
import com.example.media.dto.command.request.UploadIntentRequest;
import com.example.media.dto.command.response.MediaLinksSyncResponse;
import com.example.media.dto.command.response.UploadConfirmResponse;
import com.example.media.dto.command.response.UploadIntentResponse;
import com.example.security.gateway.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Media Command", description = "미디어 업로드/확정 및 링크 동기화 API")
public interface MediaCommandApi {

    @Operation(summary = "업로드 의도 생성(Presigned PUT + uploadToken 발급)")
    @PostMapping("/upload-intents")
    ApiResponse<UploadIntentResponse> createUploadIntent(
            @Valid @RequestBody UploadIntentRequest request,
            @CurrentUserId(required = false) Long userId
    );

    @Operation(summary = "업로드 확정(HeadObject 검증 + 멱등 처리)")
    @PostMapping("/confirm")
    ApiResponse<UploadConfirmResponse> confirmUpload(
            @Valid @RequestBody UploadConfirmRequest request,
            @CurrentUserId(required = false) Long userId
    );

    @Operation(summary = "미디어 링크 최종 상태 동기화(추가/삭제/순서/썸네일 원자 반영)")
    @PutMapping("/links/sync")
    ApiResponse<MediaLinksSyncResponse> syncLinks(
            @Valid @RequestBody MediaLinksSyncRequest request,
            @CurrentUserId(required = false) Long userId
    );
}
