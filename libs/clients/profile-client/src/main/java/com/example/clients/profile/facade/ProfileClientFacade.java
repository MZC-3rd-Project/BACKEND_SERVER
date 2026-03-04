package com.example.clients.profile.facade;

import com.example.clients.profile.dto.ProfileCreateCommand;

public interface ProfileClientFacade {

    void createProfile(ProfileCreateCommand command);
}
