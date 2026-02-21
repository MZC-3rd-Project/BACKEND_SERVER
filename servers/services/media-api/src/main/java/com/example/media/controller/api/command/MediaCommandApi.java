package com.example.media.controller.api.command;

import com.example.api.response.ApiResponse;
import com.example.media.dto.command.request.UploadConfirmRequest;
import com.example.media.dto.command.request.UploadIntentRequest;
import com.example.media.dto.command.response.UploadConfirmResponse;
import com.example.media.dto.command.response.UploadIntentResponse;
import com.example.security.gateway.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Media Command", description = "미디어 업로드 의도 생성 및 업로드 확정 API")
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
}
