package com.example.auth.repository;

import com.example.auth.entity.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    Optional<EmailVerification> findTopByEmailAndVerifiedFalseOrderByCreatedAtDesc(String email);

    boolean existsByEmailAndVerifiedTrue(String email);
}
