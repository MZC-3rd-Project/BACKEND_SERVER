package com.example.profile.repository;

import com.example.profile.entity.Profiles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProfileRepository extends JpaRepository<Profiles, Long> {

   Optional<Profiles>  findByUserId(Long userId);

   @Query("select p from Profiles p left join fetch p.profileImage where p.userId = :userId")
   Optional<Profiles> findByUserIdWithImage(@Param("userId") Long userId);

   boolean existsByNickname(String nickname);

    List<Profiles> findAllByUserIdIn(List<Long> userIds);
}
