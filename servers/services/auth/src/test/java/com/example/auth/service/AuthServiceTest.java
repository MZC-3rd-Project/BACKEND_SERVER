package com.example.auth.service;

import com.example.auth.client.ProfileServicePort;
import com.example.auth.dto.request.SignupRequest;
import com.example.auth.dto.request.WithdrawRequest;
import com.example.auth.dto.response.SignupResponse;
import com.example.auth.entity.EmailVerification;
import com.example.auth.entity.User;
import com.example.auth.entity.UserStatusHistory;
import com.example.auth.event.UserCreatedEvent;
import com.example.auth.exception.AuthErrorCode;
import com.example.auth.repository.EmailVerificationRepository;
import com.example.auth.repository.UserRepository;
import com.example.auth.repository.UserStatusHistoryRepository;
import com.example.core.exception.BusinessException;
import com.example.core.exception.TechnicalException;
import com.example.event.DomainEvent;
import com.example.event.EventPublisher;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserStatusHistoryRepository statusHistoryRepository;

    @Mock
    private Keycloak keycloakAdminClient;

    @Mock
    private EmailVerificationRepository emailVerificationRepository;

    @Mock
    private ProfileServicePort profileServiceClient;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private EntityManager entityManager;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                statusHistoryRepository,
                emailVerificationRepository,
                keycloakAdminClient,
                profileServiceClient,
                eventPublisher,
                entityManager,
                "don-moa",
                "http://localhost:43217",
                "don-moa-gateway",
                "gateway-secret",
                true,
                "test",
                ""
        );
    }

    // ─── 회원가입 테스트 ──────────────────────────────────

    @Nested
    @DisplayName("회원가입")
    class SignupTest {

        @Test
        @DisplayName("성공 - 정상적인 회원가입 흐름")
        void signup_success() {
            // given
            SignupRequest request = new SignupRequest("test@example.com", "password123", "테스터");

            when(userRepository.existsByEmail("test@example.com")).thenReturn(false);

            RealmResource realmResource = mock(RealmResource.class);
            UsersResource usersResource = mock(UsersResource.class);
            UserResource userResource = mock(UserResource.class);
            Response kcResponse = mock(Response.class);

            when(keycloakAdminClient.realm("don-moa")).thenReturn(realmResource);
            when(realmResource.users()).thenReturn(usersResource);
            when(usersResource.create(any(UserRepresentation.class))).thenReturn(kcResponse);
            when(kcResponse.getStatus()).thenReturn(201);
            when(kcResponse.getHeaderString("Location"))
                    .thenReturn("http://localhost/users/kc-user-id-123");
            when(usersResource.get(anyString())).thenReturn(userResource);
            when(userResource.toRepresentation()).thenReturn(new UserRepresentation());

            when(userRepository.save(any(User.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(statusHistoryRepository.save(any(UserStatusHistory.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            SignupResponse response = authService.signup(request);

            // then
            assertThat(response.email()).isEqualTo("test@example.com");
            verify(profileServiceClient).createProfile(any(), eq("test@example.com"), eq("테스터"));
            ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
            verify(eventPublisher, times(2)).publish(eventCaptor.capture(), any());
            DomainEvent createdEvent = eventCaptor.getAllValues().get(0);
            assertThat(createdEvent).isInstanceOf(UserCreatedEvent.class);
            assertThat(createdEvent.getPayload())
                    .containsEntry("userId", response.userId())
                    .containsEntry("email", "test@example.com")
                    .containsEntry("nickname", "테스터");
        }

        @Test
        @DisplayName("성공 - 동기 profile 생성 feature flag OFF 시 이벤트만 발행")
        void signup_success_whenSyncProfileCreateFlagOff() {
            // given
            AuthService asyncOnlyAuthService = new AuthService(
                    userRepository,
                    statusHistoryRepository,
                    emailVerificationRepository,
                    keycloakAdminClient,
                    profileServiceClient,
                    eventPublisher,
                    entityManager,
                    "don-moa",
                    "http://localhost:43217",
                    "don-moa-gateway",
                    "gateway-secret",
                    false,
                    "test",
                    ""
            );

            SignupRequest request = new SignupRequest("flag-off@example.com", "password123", "플래그오프");
            when(userRepository.existsByEmail("flag-off@example.com")).thenReturn(false);

            RealmResource realmResource = mock(RealmResource.class);
            UsersResource usersResource = mock(UsersResource.class);
            UserResource userResource = mock(UserResource.class);
            Response kcResponse = mock(Response.class);

            when(keycloakAdminClient.realm("don-moa")).thenReturn(realmResource);
            when(realmResource.users()).thenReturn(usersResource);
            when(usersResource.create(any(UserRepresentation.class))).thenReturn(kcResponse);
            when(kcResponse.getStatus()).thenReturn(201);
            when(kcResponse.getHeaderString("Location"))
                    .thenReturn("http://localhost/users/kc-user-id-flag-off");
            when(usersResource.get(anyString())).thenReturn(userResource);
            when(userResource.toRepresentation()).thenReturn(new UserRepresentation());

            when(userRepository.save(any(User.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(statusHistoryRepository.save(any(UserStatusHistory.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            SignupResponse response = asyncOnlyAuthService.signup(request);

            // then
            assertThat(response.email()).isEqualTo("flag-off@example.com");
            verify(profileServiceClient, never()).createProfile(any(), anyString(), anyString());
            verify(eventPublisher, times(2)).publish(any(DomainEvent.class), any());
        }

        @Test
        @DisplayName("실패 - 이메일 중복")
        void signup_fail_duplicateEmail() {
            // given
            SignupRequest request = new SignupRequest("dup@example.com", "password123", "테스터");
            when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(AuthErrorCode.EMAIL_ALREADY_EXISTS));

            verify(keycloakAdminClient, never()).realm(anyString());
        }

        @Test
        @DisplayName("실패 - Profile 생성 실패 시 Keycloak 보상 삭제")
        void signup_fail_profileCreationRollsBackKeycloak() {
            // given
            SignupRequest request = new SignupRequest("test@example.com", "password123", "테스터");

            when(userRepository.existsByEmail("test@example.com")).thenReturn(false);

            RealmResource realmResource = mock(RealmResource.class);
            UsersResource usersResource = mock(UsersResource.class);
            UserResource userResource = mock(UserResource.class);
            Response kcResponse = mock(Response.class);

            when(keycloakAdminClient.realm("don-moa")).thenReturn(realmResource);
            when(realmResource.users()).thenReturn(usersResource);
            when(usersResource.create(any(UserRepresentation.class))).thenReturn(kcResponse);
            when(kcResponse.getStatus()).thenReturn(201);
            when(kcResponse.getHeaderString("Location"))
                    .thenReturn("http://localhost/users/kc-user-id-456");
            when(usersResource.get(anyString())).thenReturn(userResource);
            when(userResource.toRepresentation()).thenReturn(new UserRepresentation());

            when(userRepository.save(any(User.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(statusHistoryRepository.save(any(UserStatusHistory.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            doThrow(new TechnicalException(AuthErrorCode.SIGNUP_PROFILE_FAILED))
                    .when(profileServiceClient).createProfile(any(), anyString(), anyString());

            // when & then
            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(TechnicalException.class);

            // Keycloak 보상 삭제 호출 확인
            verify(usersResource).delete("kc-user-id-456");
        }
    }

    // ─── 내부 조회 테스트 ─────────────────────────────────

    @Nested
    @DisplayName("내부 조회")
    class InternalQueryTest {

        @Test
        @DisplayName("사용자 정보 조회 - 존재하지 않는 사용자")
        void getUserInfo_notFound() {
            // given
            UserQueryService queryService = new UserQueryService(userRepository);
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> queryService.getUserInfo(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(AuthErrorCode.USER_NOT_FOUND));
        }

        @Test
        @DisplayName("사용자 존재 확인 - 존재하지 않는 경우")
        void checkUserExists_notFound() {
            // given
            UserQueryService queryService = new UserQueryService(userRepository);
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            // when
            var response = queryService.checkUserExists(999L);

            // then
            assertThat(response.exists()).isFalse();
            assertThat(response.userId()).isNull();
        }
    }

    // ─── 탈퇴 테스트 ─────────────────────────────────────

    @Nested
    @DisplayName("계정 탈퇴")
    class WithdrawTest {

        @Test
        @DisplayName("실패 - 사용자를 찾을 수 없음")
        void withdraw_userNotFound() {
            // given
            WithdrawRequest request = new WithdrawRequest("password123", "탈퇴합니다");
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> authService.withdraw(999L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(AuthErrorCode.USER_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("이메일 인증")
    class VerifyEmailTest {

        @Test
        @DisplayName("성공 - develop override 코드 72816 허용")
        void verifyEmail_success_withDevelopOverrideCode() {
            AuthService developAuthService = new AuthService(
                    userRepository,
                    statusHistoryRepository,
                    emailVerificationRepository,
                    keycloakAdminClient,
                    profileServiceClient,
                    eventPublisher,
                    entityManager,
                    "don-moa",
                    "http://localhost:43217",
                    "don-moa-gateway",
                    "gateway-secret",
                    true,
                    "develop",
                    "72816"
            );

            EmailVerification verification = EmailVerification.create(
                    "dev@example.com",
                    "REAL12",
                    java.time.LocalDateTime.now().plusMinutes(5)
            );
            when(emailVerificationRepository.findTopByEmailAndVerifiedFalseOrderByCreatedAtDesc("dev@example.com"))
                    .thenReturn(Optional.of(verification));

            RealmResource realmResource = mock(RealmResource.class);
            UsersResource usersResource = mock(UsersResource.class);
            UserResource userResource = mock(UserResource.class);
            UserRepresentation userRepresentation = new UserRepresentation();
            userRepresentation.setId("kc-dev-user");
            when(keycloakAdminClient.realm("don-moa")).thenReturn(realmResource);
            when(realmResource.users()).thenReturn(usersResource);
            when(usersResource.searchByEmail("dev@example.com", true)).thenReturn(java.util.List.of(userRepresentation));
            when(usersResource.get("kc-dev-user")).thenReturn(userResource);

            var response = developAuthService.verifyEmail("dev@example.com", "72816");

            assertThat(response.verified()).isTrue();
            assertThat(response.email()).isEqualTo("dev@example.com");
            assertThat(verification.isVerified()).isTrue();
            verify(userResource).update(any(UserRepresentation.class));
        }

        @Test
        @DisplayName("실패 - non-develop 에서는 override 코드 거부")
        void verifyEmail_fail_withOverrideCodeOutsideDevelop() {
            AuthService nonDevelopAuthService = new AuthService(
                    userRepository,
                    statusHistoryRepository,
                    emailVerificationRepository,
                    keycloakAdminClient,
                    profileServiceClient,
                    eventPublisher,
                    entityManager,
                    "don-moa",
                    "http://localhost:43217",
                    "don-moa-gateway",
                    "gateway-secret",
                    true,
                    "prod",
                    "72816"
            );

            EmailVerification verification = EmailVerification.create(
                    "prod@example.com",
                    "REAL12",
                    java.time.LocalDateTime.now().plusMinutes(5)
            );
            when(emailVerificationRepository.findTopByEmailAndVerifiedFalseOrderByCreatedAtDesc("prod@example.com"))
                    .thenReturn(Optional.of(verification));

            assertThatThrownBy(() -> nonDevelopAuthService.verifyEmail("prod@example.com", "72816"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(AuthErrorCode.VERIFICATION_CODE_INVALID));
        }
    }
}
