package com.example.profile.repository;

import com.example.profile.entity.Profiles;
import io.lettuce.core.dynamic.annotation.Param;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProfileRepository extends JpaRepository<Profiles, Long> {

   Optional<Profiles>  findByUserId(Long userId);

   boolean existsByNickname(String nickname);

    List<Profiles> findAllByUserIdIn(List<Long> userIds);
}
