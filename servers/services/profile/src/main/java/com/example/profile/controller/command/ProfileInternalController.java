package com.example.profile.controller.command;


import com.example.api.response.ApiResponse;
import com.example.profile.dto.request.ProfileCreateRequest;
import com.example.profile.service.command.ProfileCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("/internal/v1/profiles")
@RequiredArgsConstructor
public class ProfileInternalController {
    private final ProfileCommandService profileCommandService;

    @PostMapping()
    public ApiResponse<Void> createProfile(ProfileCreateRequest req)
    {
        profileCommandService.createProfile(req);
        return ApiResponse.success();
    }
}
