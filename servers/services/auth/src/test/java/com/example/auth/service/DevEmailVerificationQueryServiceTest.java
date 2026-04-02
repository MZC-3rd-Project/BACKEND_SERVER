package com.example.auth.service;

import com.example.auth.entity.EmailVerification;
import com.example.auth.exception.AuthErrorCode;
import com.example.auth.repository.EmailVerificationRepository;
import com.example.core.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevEmailVerificationQueryServiceTest {

    @Mock
    private EmailVerificationRepository emailVerificationRepository;

    private DevEmailVerificationQueryService devEmailVerificationQueryService;

    @BeforeEach
    void setUp() {
        devEmailVerificationQueryService = new DevEmailVerificationQueryService(emailVerificationRepository);
    }

    @Test
    void getLatestVerificationCode_returnsLatestPendingVerification() {
        EmailVerification verification = EmailVerification.create(
                "user@example.com",
                "ABC123",
                LocalDateTime.now().plusMinutes(5)
        );
        when(emailVerificationRepository.findTopByEmailAndVerifiedFalseOrderByCreatedAtDesc("user@example.com"))
                .thenReturn(Optional.of(verification));

        var response = devEmailVerificationQueryService.getLatestVerificationCode("user@example.com");

        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.code()).isEqualTo("ABC123");
        assertThat(response.expired()).isFalse();
        assertThat(response.expiresAt()).isEqualTo(verification.getExpiresAt());
    }

    @Test
    void getLatestVerificationCode_throwsWhenPendingVerificationMissing() {
        when(emailVerificationRepository.findTopByEmailAndVerifiedFalseOrderByCreatedAtDesc("missing@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> devEmailVerificationQueryService.getLatestVerificationCode("missing@example.com"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(AuthErrorCode.VERIFICATION_NOT_FOUND));
    }
}
