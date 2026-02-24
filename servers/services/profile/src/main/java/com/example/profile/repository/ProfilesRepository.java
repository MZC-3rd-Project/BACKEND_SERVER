package com.example.profile.repository;

import com.example.profile.entity.Profiles;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProfilesRepository extends JpaRepository<Profiles, Long> {
    Optional<Object> findByUserId(Long userId);
    @Transactional
    void deleteByUserId(Long userId);
}
