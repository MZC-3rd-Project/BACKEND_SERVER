package com.example.profile.controller.command;

import com.example.api.response.ApiResponse;
import com.example.profile.dto.request.InternalProfileCreateRequest;
import com.example.profile.service.command.ProfileProjectionSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping({"/internal/v1/profiles", "/internal/v1/profile"})
@RequiredArgsConstructor
public class ProfileInternalCommandController {

    private final ProfileProjectionSyncService profileProjectionSyncService;

    @PostMapping
    public ApiResponse<Void> createProfile(@RequestBody InternalProfileCreateRequest request) {
        profileProjectionSyncService.upsertFromUserCreated(
                request.userId(),
                request.email(),
                request.nickname()
        );
        log.info("[ProfileInternalCommand] profile projection upsert requested. userId={}", request.userId());
        return ApiResponse.success();
    }
}
