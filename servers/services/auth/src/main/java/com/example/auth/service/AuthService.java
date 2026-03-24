package com.example.auth.service;

import com.example.auth.client.ProfileServicePort;
import com.example.auth.dto.request.ChangeEmailRequest;
import com.example.auth.dto.request.ChangePasswordRequest;
import com.example.auth.dto.request.SignupRequest;
import com.example.auth.dto.request.WithdrawRequest;
import com.example.auth.dto.response.SignupResponse;
import com.example.auth.dto.response.VerifyEmailResponse;
import com.example.auth.entity.EmailVerification;
import com.example.auth.entity.User;
import com.example.auth.entity.UserStatus;
import com.example.auth.entity.UserStatusHistory;
import com.example.auth.event.EmailConfirmEvent;
import com.example.auth.event.UserCreatedEvent;
import com.example.auth.event.UserEmailChangedEvent;
import com.example.auth.event.UserWithdrawnEvent;
import com.example.auth.exception.AuthErrorCode;
import com.example.auth.repository.EmailVerificationRepository;
import com.example.auth.repository.UserRepository;
import com.example.auth.repository.UserStatusHistoryRepository;
import com.example.auth.util.VerificationCodeGenerator;
import com.example.core.exception.BusinessException;
import com.example.core.exception.TechnicalException;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AuthService {

    private static final int VERIFICATION_CODE_EXPIRY_MINUTES = 10;

    private final UserRepository userRepository;
    private final UserStatusHistoryRepository statusHistoryRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final Keycloak keycloakAdminClient;
    private final ProfileServicePort profileServiceClient;
    private final EventPublisher eventPublisher;
    private final String realm;
    private final String keycloakServerUrl;
    private final String directGrantClientId;
    private final boolean syncProfileCreateOnSignup;

    public AuthService(UserRepository userRepository,
                       UserStatusHistoryRepository statusHistoryRepository,
                       EmailVerificationRepository emailVerificationRepository,
                       Keycloak keycloakAdminClient,
                       ProfileServicePort profileServiceClient,
                       EventPublisher eventPublisher,
                       @Value("${keycloak.admin.realm}") String realm,
                       @Value("${keycloak.admin.server-url}") String keycloakServerUrl,
                       @Value("${keycloak.admin.direct-grant-client-id}") String directGrantClientId,
                       @Value("${feature.sync-profile-create-on-signup:true}") boolean syncProfileCreateOnSignup) {
        this.userRepository = userRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.emailVerificationRepository = emailVerificationRepository;
        this.keycloakAdminClient = keycloakAdminClient;
        this.profileServiceClient = profileServiceClient;
        this.eventPublisher = eventPublisher;
        this.realm = realm;
        this.keycloakServerUrl = keycloakServerUrl;
        this.directGrantClientId = directGrantClientId;
        this.syncProfileCreateOnSignup = syncProfileCreateOnSignup;
    }

    // ─── 회원가입 ──────────────────────────────────────────────

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        // 1. 이메일 중복 확인
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }

        // 2. Keycloak 사용자 생성
        String keycloakUserId = createKeycloakUser(request.email(), request.password());

        try {
            // 3. DB 저장 (Snowflake ID 자동 생성)
            User user = User.create(keycloakUserId, request.email(), request.nickname());
            userRepository.save(user);

            // 4. Keycloak 사용자에 snowflakeId 속성 설정 (JWT 토큰에 포함시키기 위함)
            updateKeycloakUserSnowflakeId(keycloakUserId, user.getId());

            // 5. 상태 이력 기록
            UserStatusHistory history = UserStatusHistory.create(
                    user.getId(), null, UserStatus.ACTIVE, "회원가입", user.getId());
            statusHistoryRepository.save(history);

            // 6. Profile Service 동기 호출 (feature flag)
            if (syncProfileCreateOnSignup) {
                profileServiceClient.createProfile(user.getId(), user.getEmail(), request.nickname());
            } else {
                log.info("Sync profile create disabled by feature flag. userId={}", user.getId());
            }

            // 7. Outbox 이벤트 발행
            eventPublisher.publish(
                    new UserCreatedEvent(user.getId(), user.getEmail(), user.getNickname()),
                    EventMetadata.of("USER", String.valueOf(user.getId()))
            );

            // 8. 이메일 인증 코드 생성 및 발행
            String verificationCode = VerificationCodeGenerator.generate();
            EmailVerification emailVerification = EmailVerification.create(
                    request.email(), verificationCode,
                    LocalDateTime.now().plusMinutes(VERIFICATION_CODE_EXPIRY_MINUTES));
            emailVerificationRepository.save(emailVerification);

            eventPublisher.publish(
                    new EmailConfirmEvent(request.email(), verificationCode),
                    EventMetadata.of("USER", String.valueOf(user.getId()))
            );

            log.info("Signup completed: userId={}, email={}", user.getId(), user.getEmail());
            return SignupResponse.of(user.getId(), user.getEmail());

        } catch (Exception e) {
            // 보상 트랜잭션: Keycloak 사용자 삭제
            deleteKeycloakUserSafely(keycloakUserId);
            throw e;
        }
    }

    // ─── 비밀번호 변경 ────────────────────────────────────────

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = findActiveUser(userId);

        // 현재 비밀번호 검증
        verifyCurrentPassword(user.getKeycloakId(), request.currentPassword());

        // Keycloak 비밀번호 변경
        updateKeycloakPassword(user.getKeycloakId(), request.newPassword());

        log.info("Password changed: userId={}", userId);
    }

    // ─── 이메일 변경 ──────────────────────────────────────────

    @Transactional
    public void changeEmail(Long userId, ChangeEmailRequest request) {
        User user = findActiveUser(userId);

        // 새 이메일 중복 확인
        if (userRepository.existsByEmail(request.newEmail())) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }

        String oldEmail = user.getEmail();

        // Keycloak 이메일 업데이트
        updateKeycloakEmail(user.getKeycloakId(), request.newEmail());

        // DB 이메일 업데이트
        user.changeEmail(request.newEmail());

        // Outbox 이벤트 발행
        eventPublisher.publish(
                new UserEmailChangedEvent(userId, oldEmail, request.newEmail()),
                EventMetadata.of("USER", String.valueOf(userId))
        );

        log.info("Email changed: userId={}, {} -> {}", userId, oldEmail, request.newEmail());
    }

    // ─── 계정 탈퇴 ────────────────────────────────────────────

    @Transactional
    public void withdraw(Long userId, WithdrawRequest request) {
        User user = findActiveUser(userId);

        // 비밀번호 검증
        verifyCurrentPassword(user.getKeycloakId(), request.password());

        // Keycloak 사용자 비활성화
        disableKeycloakUser(user.getKeycloakId());

        // 상태 변경 이력
        UserStatus previousStatus = user.getStatus();
        user.withdraw();

        UserStatusHistory history = UserStatusHistory.create(
                userId, previousStatus, UserStatus.WITHDRAWN, request.reason(), userId);
        statusHistoryRepository.save(history);

        // Outbox 이벤트 발행
        eventPublisher.publish(
                new UserWithdrawnEvent(userId),
                EventMetadata.of("USER", String.valueOf(userId))
        );

        log.info("User withdrawn: userId={}", userId);
    }

    // ─── 이메일 인증 검증 ────────────────────────────────────

    @Transactional
    public VerifyEmailResponse verifyEmail(String email, String code) {
        EmailVerification verification = emailVerificationRepository
                .findTopByEmailAndVerifiedFalseOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.VERIFICATION_NOT_FOUND));

        if (verification.isExpired()) {
            throw new BusinessException(AuthErrorCode.VERIFICATION_CODE_EXPIRED);
        }

        if (!verification.getCode().equals(code)) {
            throw new BusinessException(AuthErrorCode.VERIFICATION_CODE_INVALID);
        }

        verification.verify();

        // Keycloak emailVerified=true 업데이트
        updateKeycloakEmailVerified(email);

        log.info("Email verified: email={}", email);
        return VerifyEmailResponse.of(true, email);
    }

    // ─── 이메일 인증 재발송 ────────────────────────────────────

    @Transactional
    public void resendVerificationEmail(String email) {

        // 이미 인증 완료 여부 확인
        if (emailVerificationRepository.existsByEmailAndVerifiedTrue(email)) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_VERIFIED);
        }

        // 새 인증 코드 생성 및 저장
        String verificationCode = VerificationCodeGenerator.generate();
        EmailVerification emailVerification = EmailVerification.create(
                email, verificationCode,
                LocalDateTime.now().plusMinutes(VERIFICATION_CODE_EXPIRY_MINUTES));
        emailVerificationRepository.save(emailVerification);

        // EmailConfirmEvent 발행
        eventPublisher.publish(
                new EmailConfirmEvent(email, verificationCode),
                EventMetadata.of("USER", String.valueOf(email))
        );

        log.info("Verification email resent: email={}", email);
    }

    // ─── 중복 확인 ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public boolean isEmailAvailable(String email) {
        return !userRepository.existsByEmail(email);
    }

    @Transactional(readOnly = true)
    public boolean isNicknameAvailable(String nickname) {
        return !userRepository.existsByNickname(nickname);
    }

    // ─── 로그인 기록 갱신 ──────────────────────────────────────

    @Transactional
    public void updateLastLogin(Long userId) {
        User user = findActiveUser(userId);
        user.updateLastLoginAt();
        log.debug("Last login updated: userId={}", userId);
    }

    // ─── Keycloak 헬퍼 메서드 ─────────────────────────────────

    private String createKeycloakUser(String email, String password) {
        try {
            UserRepresentation kcUser = new UserRepresentation();
            kcUser.setUsername(email);
            kcUser.setEmail(email);
            kcUser.setEnabled(true);
            kcUser.setEmailVerified(false);
            kcUser.setRequiredActions(Collections.emptyList());

            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(password);
            credential.setTemporary(false);
            kcUser.setCredentials(List.of(credential));

            UsersResource usersResource = keycloakAdminClient.realm(realm).users();
            try (Response response = usersResource.create(kcUser)) {
                if (response.getStatus() == 201) {
                    String locationHeader = response.getHeaderString("Location");
                    String keycloakUserId = locationHeader.substring(
                            locationHeader.lastIndexOf("/") + 1);
                    log.info("Keycloak user created: keycloakId={}", keycloakUserId);
                    return keycloakUserId;
                } else if (response.getStatus() == 409) {
                    throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS,
                            "Keycloak에 이미 등록된 이메일입니다");
                } else {
                    throw new TechnicalException(AuthErrorCode.SIGNUP_KEYCLOAK_FAILED,
                            "Keycloak 응답 코드: " + response.getStatus());
                }
            }
        } catch (BusinessException | TechnicalException e) {
            throw e;
        } catch (Exception e) {
            throw new TechnicalException(AuthErrorCode.KEYCLOAK_COMMUNICATION_ERROR,
                    "Keycloak 사용자 생성 중 오류", e);
        }
    }

    private void verifyCurrentPassword(String keycloakId, String password) {
        try {
            UserRepresentation kcUser = keycloakAdminClient.realm(realm)
                    .users().get(keycloakId).toRepresentation();

            try (Keycloak tempKc = KeycloakBuilder.builder()
                    .serverUrl(keycloakServerUrl)
                    .realm(realm)
                    .grantType("password")
                    .clientId(directGrantClientId)
                    .username(kcUser.getUsername())
                    .password(password)
                    .build()) {
                tempKc.tokenManager().getAccessToken();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(AuthErrorCode.INVALID_PASSWORD);
        }
    }

    private void updateKeycloakPassword(String keycloakId, String newPassword) {
        try {
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(newPassword);
            credential.setTemporary(false);

            keycloakAdminClient.realm(realm).users().get(keycloakId).resetPassword(credential);
            log.info("Keycloak password updated: keycloakId={}", keycloakId);
        } catch (Exception e) {
            throw new TechnicalException(AuthErrorCode.PASSWORD_CHANGE_FAILED,
                    "Keycloak 비밀번호 변경 실패", e);
        }
    }

    private void updateKeycloakEmail(String keycloakId, String newEmail) {
        try {
            UserRepresentation kcUser = keycloakAdminClient.realm(realm)
                    .users().get(keycloakId).toRepresentation();
            kcUser.setEmail(newEmail);
            kcUser.setUsername(newEmail);
            keycloakAdminClient.realm(realm).users().get(keycloakId).update(kcUser);
            log.info("Keycloak email updated: keycloakId={}", keycloakId);
        } catch (Exception e) {
            throw new TechnicalException(AuthErrorCode.EMAIL_CHANGE_FAILED,
                    "Keycloak 이메일 변경 실패", e);
        }
    }

    private void updateKeycloakUserSnowflakeId(String keycloakId, Long snowflakeId) {
        try {
            UserRepresentation kcUser = keycloakAdminClient.realm(realm)
                    .users().get(keycloakId).toRepresentation();
            kcUser.setAttributes(Map.of("snowflakeId", List.of(String.valueOf(snowflakeId))));
            keycloakAdminClient.realm(realm).users().get(keycloakId).update(kcUser);
            log.info("Keycloak user snowflakeId set: keycloakId={}, snowflakeId={}", keycloakId, snowflakeId);
        } catch (Exception e) {
            log.error("Failed to set snowflakeId on Keycloak user: keycloakId={}, snowflakeId={}",
                    keycloakId, snowflakeId, e);
            throw new TechnicalException(AuthErrorCode.KEYCLOAK_COMMUNICATION_ERROR,
                    "Keycloak 사용자 snowflakeId 설정 실패", e);
        }
    }

    private void disableKeycloakUser(String keycloakId) {
        try {
            UserRepresentation kcUser = keycloakAdminClient.realm(realm)
                    .users().get(keycloakId).toRepresentation();
            kcUser.setEnabled(false);
            keycloakAdminClient.realm(realm).users().get(keycloakId).update(kcUser);
            log.info("Keycloak user disabled: keycloakId={}", keycloakId);
        } catch (Exception e) {
            throw new TechnicalException(AuthErrorCode.WITHDRAW_KEYCLOAK_FAILED,
                    "Keycloak 사용자 비활성화 실패", e);
        }
    }

    private void deleteKeycloakUserSafely(String keycloakUserId) {
        try {
            keycloakAdminClient.realm(realm).users().delete(keycloakUserId);
            log.info("Keycloak user rolled back (deleted): keycloakId={}", keycloakUserId);
        } catch (Exception e) {
            log.error("Failed to rollback Keycloak user: keycloakId={}", keycloakUserId, e);
        }
    }

    // ─── Keycloak 이메일 인증 ─────────────────────────────────

    private void updateKeycloakEmailVerified(String email) {
        try {
            List<UserRepresentation> users =
                    keycloakAdminClient.realm(realm).users().searchByEmail(email, true);
            if (!users.isEmpty()) {
                UserRepresentation kcUser = users.get(0);
                kcUser.setEmailVerified(true);
                kcUser.setRequiredActions(Collections.emptyList());
                keycloakAdminClient.realm(realm).users().get(kcUser.getId()).update(kcUser);
                log.info("Keycloak emailVerified updated: email={}", email);
            }
        } catch (Exception e) {
            log.warn("Keycloak emailVerified 업데이트 실패: email={}, error={}", email, e.getMessage());
        }
    }

    // ─── 공통 헬퍼 ────────────────────────────────────────────

    private User findActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.USER_NOT_FOUND));
        if (!user.isActive()) {
            throw new BusinessException(AuthErrorCode.ACCOUNT_NOT_ACTIVE);
        }
        return user;
    }
}
