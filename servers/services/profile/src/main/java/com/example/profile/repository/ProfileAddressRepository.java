package com.example.profile.repository;

import com.example.profile.entity.ProfileAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProfileAddressRepository extends JpaRepository<ProfileAddress, Long> {

    List<ProfileAddress> findProfileAddressByProfileId(Long profileId); // userId = profileId

    Optional<ProfileAddress> findByProfileIdAndIsDefaultTrue(Long profileId);
}
