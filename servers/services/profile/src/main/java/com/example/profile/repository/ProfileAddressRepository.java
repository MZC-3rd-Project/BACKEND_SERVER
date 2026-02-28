package com.example.profile.repository;

import com.example.profile.entity.ProfileAddress;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileAddressRepository extends JpaRepository<ProfileAddress, Long> {

    ProfileAddress findProfileAddressByProfileId(Long profileId); // userId = profileId
}
