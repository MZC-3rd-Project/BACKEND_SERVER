package com.example.auth.service;

import com.example.auth.dto.response.DevEmailVerificationCodeResponse;
import com.example.auth.exception.AuthErrorCode;
import com.example.auth.repository.EmailVerificationRepository;
import com.example.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DevEmailVerificationQueryService {

    private final EmailVerificationRepository emailVerificationRepository;

    @Transactional(readOnly = true)
    public DevEmailVerificationCodeResponse getLatestVerificationCode(String email) {
        return emailVerificationRepository.findTopByEmailAndVerifiedFalseOrderByCreatedAtDesc(email)
                .map(DevEmailVerificationCodeResponse::from)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.VERIFICATION_NOT_FOUND));
    }
}
