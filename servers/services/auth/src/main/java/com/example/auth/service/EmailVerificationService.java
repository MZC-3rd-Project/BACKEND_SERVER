package com.example.auth.service;

import com.example.auth.entity.EmailVerification;
import com.example.auth.exception.AuthErrorCode;
import com.example.auth.repository.EmailVerificationRepository;
import com.example.core.exception.BusinessException;
import com.example.core.exception.TechnicalException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final EmailVerificationRepository emailVerificationRepository;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.from:noreply@don-moa.com}")
    private String fromEmail;

    private static final int OTP_TTL_MINUTES = 5;

    @Transactional
    public void sendVerificationCode(String email) {
        emailVerificationRepository.deleteByEmail(email);

        String code = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        emailVerificationRepository.save(EmailVerification.create(email, code, OTP_TTL_MINUTES));

        sendEmail(email, code);
        log.info("Verification code sent: email={}", email);
    }

    @Transactional
    public void verifyCode(String email, String code) {
        EmailVerification verification = emailVerificationRepository
                .findTopByEmailOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.VERIFICATION_CODE_INVALID));

        if (verification.isExpired()) {
            throw new BusinessException(AuthErrorCode.VERIFICATION_CODE_EXPIRED);
        }
        if (!verification.getCode().equals(code)) {
            throw new BusinessException(AuthErrorCode.VERIFICATION_CODE_INVALID);
        }

        verification.verify();
        log.info("Email verified: email={}", email);
    }

    private void sendEmail(String email, String code) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject("[돈모아] 이메일 인증 코드");
            message.setText("인증 코드: " + code + "\n\n" + OTP_TTL_MINUTES + "분 내에 입력해주세요.");
            mailSender.send(message);
        } catch (Exception e) {
            throw new TechnicalException(AuthErrorCode.EMAIL_SEND_FAILED, "이메일 발송 실패", e);
        }
    }
}
