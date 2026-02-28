package com.example.profile.controller.command;


import com.example.api.response.ApiResponse;
import com.example.clients.auth.dto.profile.AuthSyncQuery;
import com.example.profile.controller.api.query.ProfileInternalAPI;
import com.example.profile.dto.request.ProfileCreateRequest;
import com.example.profile.entity.ProfileAddress;
import com.example.profile.entity.Profiles;
import com.example.profile.service.command.ProfileCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController("/internal/v1/profile")
@RequiredArgsConstructor
public class ProfileInternalCommandController {
    private final ProfileCommandService profileCommandService;

    @PostMapping
    public ApiResponse<Void> createProfile(AuthSyncQuery req)
    {
        profileCommandService.createProfile(req);
        return ApiResponse.success();
    }

}
