package com.example.profile.controller;

import com.example.profile.entity.Profile;
import com.example.profile.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileRepository profileRepository;

    @GetMapping
    public Profile getUser(){

        Profile profile = new Profile();
        Profile profileDto = profile.create("test", "testing");

        Profile result = profileRepository.save(profileDto);

        return result;
    }

}
