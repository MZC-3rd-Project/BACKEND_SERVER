package com.example.profile.repository;

import com.example.profile.entity.Profiles;
import com.example.profile.entity.ProfilesImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ProfileImageRepository extends JpaRepository<ProfilesImage, Long> {

    Optional<ProfilesImage> findByUserId(Long userId);
}
