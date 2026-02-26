package com.example.auth.service;

import com.example.auth.client.ProfileServiceClient;
import com.example.auth.dto.request.ChangeEmailRequest;
import com.example.auth.dto.request.ChangePasswordRequest;
import com.example.auth.dto.request.SignupRequest;
import com.example.auth.dto.request.WithdrawRequest;
import com.example.auth.dto.response.SignupResponse;
import com.example.auth.entity.User;
import com.example.auth.entity.UserStatus;
import com.example.auth.entity.UserStatusHistory;
import com.example.auth.event.UserCreatedEvent;
import com.example.auth.event.UserEmailChangedEvent;
import com.example.auth.event.UserWithdrawnEvent;
import com.example.auth.exception.AuthErrorCode;
import com.example.auth.repository.UserRepository;
import com.example.auth.repository.UserStatusHistoryRepository;
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

import java.util.List;

@Slf4j
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserStatusHistoryRepository statusHistoryRepository;
    private final Keycloak keycloakAdminClient;
    private final ProfileServiceClient profileServiceClient;
    private final EventPublisher eventPublisher;
    private final String realm;
    private final String keycloakServerUrl;
    private final String directGrantClientId;

    public AuthService(UserRepository userRepository,
                       UserStatusHistoryRepository statusHistoryRepository,
                       Keycloak keycloakAdminClient,
                       ProfileServiceClient profileServiceClient,
                       EventPublisher eventPublisher,
                       @Value("${keycloak.admin.realm}") String realm,
                       @Value("${keycloak.admin.server-url}") String keycloakServerUrl,
                       @Value("${keycloak.admin.direct-grant-client-id}") String directGrantClientId) {
        this.userRepository = userRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.keycloakAdminClient = keycloakAdminClient;
        this.profileServiceClient = profileServiceClient;
        this.eventPublisher = eventPublisher;
        this.realm = realm;
        this.keycloakServerUrl = keycloakServerUrl;
        this.directGrantClientId = directGrantClientId;
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
            // 3. DB 저장
            User user = User.create(keycloakUserId, request.email(), request.nickname());
            userRepository.save(user);

            // 4. 상태 이력 기록
            UserStatusHistory history = UserStatusHistory.create(
                    user.getId(), null, UserStatus.ACTIVE, "회원가입", user.getId());
            statusHistoryRepository.save(history);

            // 5. Profile Service 동기 호출
            profileServiceClient.createProfile(user.getId(), user.getEmail(), request.nickname());

            // 6. Outbox 이벤트 발행
            eventPublisher.publish(
                    new UserCreatedEvent(user.getId(), user.getEmail()),
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
            kcUser.setEmailVerified(true);

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
