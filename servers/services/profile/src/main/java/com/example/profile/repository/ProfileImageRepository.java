package com.example.profile.repository;

import com.example.profile.entity.Profiles;
import com.example.profile.entity.ProfilesImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProfileImageRepository extends JpaRepository<ProfilesImage, Long> {

    ProfilesImage findByProfileId(Long profileId);
}
