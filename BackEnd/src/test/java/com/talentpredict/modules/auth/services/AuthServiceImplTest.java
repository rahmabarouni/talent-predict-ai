package com.talentpredict.modules.auth.services;

import com.talentpredict.modules.auth.dto.AuthDto;
import com.talentpredict.modules.auth.entities.EmailVerificationToken;
import com.talentpredict.modules.auth.entities.PasswordResetToken;
import com.talentpredict.modules.auth.entities.RefreshToken;
import com.talentpredict.modules.auth.repositories.EmailVerificationTokenRepository;
import com.talentpredict.modules.auth.repositories.PasswordResetTokenRepository;
import com.talentpredict.modules.auth.repositories.RefreshTokenRepository;
import com.talentpredict.modules.user.entities.User;
import com.talentpredict.modules.user.repositories.UserRepository;
import com.talentpredict.shared.exception.ConflictException;
import com.talentpredict.shared.exception.ResourceNotFoundException;
import com.talentpredict.shared.security.JwtService;
import com.talentpredict.shared.sms.SmsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link AuthServiceImpl}.
 * <p>
 * Stratégie : pure couche service (Mockito uniquement, pas de contexte Spring).
 * Chaque méthode métier est testée avec les cas nominal, cas limite, et cas d'erreur.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl — Tests Unitaires")
class AuthServiceImplTest {

    /* =========================================================
     *  MOCKS & SUT
     * ========================================================= */

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private JwtService jwtService;
    @Mock private SmsService smsService;

    @InjectMocks
    private AuthServiceImpl authService;

    /* =========================================================
     *  FIXTURES
     * ========================================================= */

    private User buildUser() {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail("alice@talentpredict.com");
        u.setFirstName("Alice");
        u.setLastName("Dupont");
        u.setPassword("$2a$10$hashedpassword");
        u.setEmailVerified(false);
        u.setRole(User.Role.USER);
        u.setFailedLoginAttempts(0);
        return u;
    }

    private AuthDto.RegisterRequest buildRegisterRequest() {
        AuthDto.RegisterRequest req = new AuthDto.RegisterRequest();
        req.setEmail("alice@talentpredict.com");
        req.setFirstName("Alice");
        req.setLastName("Dupont");
        req.setPassword("Passw0rd!");
        return req;
    }

    @BeforeEach
    void setUp() {
        // Inject @Value fields that Mockito cannot set automatically
        ReflectionTestUtils.setField(authService, "frontendBaseUrl",     "http://localhost:4200");
        ReflectionTestUtils.setField(authService, "maxFailedAttempts",   5);
        ReflectionTestUtils.setField(authService, "lockDurationMinutes", 15L);
        ReflectionTestUtils.setField(authService, "emailVerificationExpirationMinutes", 1440L);
    }

    /* ==========================================================
     *  createUser()
     * ========================================================== */

    @Nested
    @DisplayName("createUser()")
    class CreateUserTests {

        @Test
        @DisplayName("✅ Doit créer un utilisateur avec rôle USER par défaut")
        void shouldCreateUserWithDefaultRole() {
            // Arrange
            AuthDto.RegisterRequest req = buildRegisterRequest();
            when(userRepository.existsByEmail(req.getEmail())).thenReturn(false);
            when(passwordEncoder.encode(req.getPassword())).thenReturn("hashed");
            User saved = buildUser();
            when(userRepository.save(any(User.class))).thenReturn(saved);
            doNothing().when(emailVerificationTokenRepository).deleteByUser(any());
            when(emailVerificationTokenRepository.save(any())).thenReturn(null);

            // Act
            User result = authService.createUser(req);

            // Assert
            assertThat(result).isNotNull();
            verify(userRepository).save(argThat(u ->
                u.getEmail().equals(req.getEmail()) &&
                u.getRole() == User.Role.USER &&
                !u.getEmailVerified()
            ));
        }

        @Test
        @DisplayName("✅ Doit créer un utilisateur avec rôle ADMIN si spécifié")
        void shouldCreateUserWithAdminRole() {
            AuthDto.RegisterRequest req = buildRegisterRequest();
            req.setRole("ADMIN");
            when(userRepository.existsByEmail(req.getEmail())).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("hashed");
            User saved = buildUser();
            saved.setRole(User.Role.ADMIN);
            when(userRepository.save(any(User.class))).thenReturn(saved);
            doNothing().when(emailVerificationTokenRepository).deleteByUser(any());
            when(emailVerificationTokenRepository.save(any())).thenReturn(null);

            User result = authService.createUser(req);

            verify(userRepository).save(argThat(u -> u.getRole() == User.Role.ADMIN));
        }

        @Test
        @DisplayName("✅ Doit retomber sur USER si le rôle est invalide")
        void shouldFallbackToUserRoleOnInvalidRole() {
            AuthDto.RegisterRequest req = buildRegisterRequest();
            req.setRole("SUPERADMIN_INVALID");
            when(userRepository.existsByEmail(req.getEmail())).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("hashed");
            User saved = buildUser();
            when(userRepository.save(any(User.class))).thenReturn(saved);
            doNothing().when(emailVerificationTokenRepository).deleteByUser(any());
            when(emailVerificationTokenRepository.save(any())).thenReturn(null);

            authService.createUser(req);

            verify(userRepository).save(argThat(u -> u.getRole() == User.Role.USER));
        }

        @Test
        @DisplayName("❌ Doit lancer ConflictException si l'email existe déjà")
        void shouldThrowConflictWhenEmailAlreadyExists() {
            AuthDto.RegisterRequest req = buildRegisterRequest();
            when(userRepository.existsByEmail(req.getEmail())).thenReturn(true);

            assertThatThrownBy(() -> authService.createUser(req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(req.getEmail());
        }

        @Test
        @DisplayName("❌ Doit lancer ConflictException si le téléphone existe déjà")
        void shouldThrowConflictWhenPhoneAlreadyExists() {
            AuthDto.RegisterRequest req = buildRegisterRequest();
            req.setPhoneNumber("+33612345678");
            when(userRepository.existsByEmail(req.getEmail())).thenReturn(false);
            when(userRepository.existsByPhoneNumber(req.getPhoneNumber())).thenReturn(true);

            assertThatThrownBy(() -> authService.createUser(req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(req.getPhoneNumber());
        }
    }

    /* ==========================================================
     *  getUserById() / getUserByEmail()
     * ========================================================== */

    @Nested
    @DisplayName("getUserById() / getUserByEmail()")
    class GetUserTests {

        @Test
        @DisplayName("✅ getUserById : retourne l'utilisateur trouvé")
        void getUserById_shouldReturnUser() {
            User user = buildUser();
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

            User result = authService.getUserById(user.getId());

            assertThat(result).isEqualTo(user);
        }

        @Test
        @DisplayName("❌ getUserById : lance ResourceNotFoundException si introuvable")
        void getUserById_shouldThrowWhenNotFound() {
            UUID id = UUID.randomUUID();
            when(userRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.getUserById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(id.toString());
        }

        @Test
        @DisplayName("❌ getUserById : lance NullPointerException si id est null")
        void getUserById_shouldThrowOnNullId() {
            assertThatThrownBy(() -> authService.getUserById(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("✅ getUserByEmail : retourne l'utilisateur trouvé")
        void getUserByEmail_shouldReturnUser() {
            User user = buildUser();
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

            User result = authService.getUserByEmail(user.getEmail());

            assertThat(result).isEqualTo(user);
        }

        @Test
        @DisplayName("❌ getUserByEmail : lance ResourceNotFoundException si introuvable")
        void getUserByEmail_shouldThrowWhenNotFound() {
            String email = "unknown@example.com";
            when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.getUserByEmail(email))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(email);
        }
    }

    /* ==========================================================
     *  recordFailedLoginAttempt() / registerSuccessfulLogin()
     * ========================================================== */

    @Nested
    @DisplayName("Gestion des tentatives de connexion")
    class LoginAttemptTests {

        @Test
        @DisplayName("✅ recordFailedLoginAttempt : incrémente le compteur si < max")
        void shouldIncrementFailedAttempts() {
            User user = buildUser();
            user.setFailedLoginAttempts(2);
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenReturn(user);

            authService.recordFailedLoginAttempt(user.getEmail(), "127.0.0.1");

            assertThat(user.getFailedLoginAttempts()).isEqualTo(3);
            assertThat(user.getLockUntil()).isNull();
        }

        @Test
        @DisplayName("✅ recordFailedLoginAttempt : verrouille le compte au 5ème échec")
        void shouldLockAccountOnMaxAttempts() {
            User user = buildUser();
            user.setFailedLoginAttempts(4); // next attempt = 5 = lock
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenReturn(user);

            authService.recordFailedLoginAttempt(user.getEmail(), "127.0.0.1");

            assertThat(user.getLockUntil()).isNotNull().isAfter(Instant.now());
            assertThat(user.getFailedLoginAttempts()).isZero(); // réinitialisé après verrouillage
            verify(auditLogService).logAccountLocked(user.getEmail(), "127.0.0.1");
        }

        @Test
        @DisplayName("✅ recordFailedLoginAttempt : ne fait rien si l'email est inconnu")
        void shouldDoNothingForUnknownEmail() {
            when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

            assertThatCode(() ->
                authService.recordFailedLoginAttempt("ghost@test.com", "10.0.0.1")
            ).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("✅ registerSuccessfulLogin : réinitialise les tentatives et met à jour lastLoginAt")
        void shouldResetOnSuccessfulLogin() {
            User user = buildUser();
            user.setFailedLoginAttempts(3);
            user.setLockUntil(Instant.now().plusSeconds(60));
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenReturn(user);

            authService.registerSuccessfulLogin(user.getEmail());

            assertThat(user.getFailedLoginAttempts()).isZero();
            assertThat(user.getLockUntil()).isNull();
            assertThat(user.getLastLoginAt()).isNotNull().isBeforeOrEqualTo(Instant.now());
        }
    }

    /* ==========================================================
     *  generateRefreshToken() / refreshAccessToken()
     * ========================================================== */

    @Nested
    @DisplayName("Gestion des Refresh Tokens")
    class RefreshTokenTests {

        @Test
        @DisplayName("✅ generateRefreshToken : persiste le token et le retourne")
        void shouldGenerateAndPersistRefreshToken() {
            User user = buildUser();
            String generatedToken = "jwt.refresh.token";
            when(jwtService.generateRefreshToken(user.getEmail())).thenReturn(generatedToken);
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            String result = authService.generateRefreshToken(user, "device-001");

            assertThat(result).isEqualTo(generatedToken);
            ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenRepository).save(captor.capture());
            RefreshToken saved = captor.getValue();
            assertThat(saved.getToken()).isEqualTo(generatedToken);
            assertThat(saved.getUser()).isEqualTo(user);
            assertThat(saved.getDeviceId()).isEqualTo("device-001");
            assertThat(saved.getExpiryDate()).isAfter(Instant.now());
        }

        @Test
        @DisplayName("✅ refreshAccessToken : révoque l'ancien et retourne un nouveau access token")
        void shouldRefreshAccessTokenAndRevokeOld() {
            User user = buildUser();
            RefreshToken oldToken = RefreshToken.builder()
                .token("old-refresh")
                .user(user)
                .expiryDate(Instant.now().plusSeconds(3600))
                .revoked(false)
                .deviceId("dev-1")
                .build();

            when(refreshTokenRepository.findByToken("old-refresh")).thenReturn(Optional.of(oldToken));
            when(jwtService.generateAccessToken(user.getEmail())).thenReturn("new-access-token");
            when(refreshTokenRepository.save(any())).thenReturn(oldToken);

            String result = authService.refreshAccessToken("old-refresh");

            assertThat(result).isEqualTo("new-access-token");
            assertThat(oldToken.isRevoked()).isTrue();
        }

        @Test
        @DisplayName("❌ refreshAccessToken : lance IllegalArgumentException si token invalide")
        void shouldThrowOnInvalidRefreshToken() {
            when(refreshTokenRepository.findByToken("bad-token")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refreshAccessToken("bad-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired refresh token");
        }

        @Test
        @DisplayName("❌ refreshAccessToken : lance IllegalArgumentException si token révoqué")
        void shouldThrowOnRevokedRefreshToken() {
            User user = buildUser();
            RefreshToken revokedToken = RefreshToken.builder()
                .token("revoked-token")
                .user(user)
                .expiryDate(Instant.now().plusSeconds(3600))
                .revoked(true)
                .deviceId("dev-1")
                .build();

            when(refreshTokenRepository.findByToken("revoked-token")).thenReturn(Optional.of(revokedToken));

            assertThatThrownBy(() -> authService.refreshAccessToken("revoked-token"))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /* ==========================================================
     *  changePassword()
     * ========================================================== */

    @Nested
    @DisplayName("changePassword()")
    class ChangePasswordTests {

        @Test
        @DisplayName("✅ Doit changer le mot de passe et révoquer les sessions")
        void shouldChangePasswordSuccessfully() {
            User user = buildUser();
            AuthDto.ChangePasswordRequest req = new AuthDto.ChangePasswordRequest();
            req.setCurrentPassword("OldPass1!");
            req.setNewPassword("NewPass2@");

            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("OldPass1!", user.getPassword())).thenReturn(true);
            when(passwordEncoder.matches("NewPass2@", user.getPassword())).thenReturn(false);
            when(passwordEncoder.encode("NewPass2@")).thenReturn("newHashed");
            when(userRepository.save(any())).thenReturn(user);
            doNothing().when(refreshTokenRepository).revokeAllUserTokens(user);

            String result = authService.changePassword(req, user, "192.168.1.1");

            assertThat(result).contains("success");
            verify(userRepository).save(argThat(u -> "newHashed".equals(u.getPassword())));
            verify(refreshTokenRepository).revokeAllUserTokens(user);
            verify(auditLogService).logPasswordChange(user, "192.168.1.1");
        }

        @Test
        @DisplayName("❌ Doit rejeter si le mot de passe actuel est incorrect")
        void shouldRejectWrongCurrentPassword() {
            User user = buildUser();
            AuthDto.ChangePasswordRequest req = new AuthDto.ChangePasswordRequest();
            req.setCurrentPassword("WrongPass!");
            req.setNewPassword("NewPass2@");

            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("WrongPass!", user.getPassword())).thenReturn(false);

            assertThatThrownBy(() -> authService.changePassword(req, user, "ip"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("incorrect");
        }

        @Test
        @DisplayName("❌ Doit rejeter si le nouveau mot de passe est identique à l'ancien")
        void shouldRejectSamePassword() {
            User user = buildUser();
            AuthDto.ChangePasswordRequest req = new AuthDto.ChangePasswordRequest();
            req.setCurrentPassword("Same1Pass!");
            req.setNewPassword("Same1Pass!");

            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("Same1Pass!", user.getPassword())).thenReturn(true);

            assertThatThrownBy(() -> authService.changePassword(req, user, "ip"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different");
        }
    }

    /* ==========================================================
     *  forgotPassword()
     * ========================================================== */

    @Nested
    @DisplayName("forgotPassword()")
    class ForgotPasswordTests {

        @Test
        @DisplayName("✅ Retourne un message générique si l'email n'est pas trouvé (sécurité)")
        void shouldReturnGenericMessageWhenEmailNotFound() {
            AuthDto.ForgotPasswordRequest req = new AuthDto.ForgotPasswordRequest();
            req.setEmail("ghost@example.com");
            req.setChannel(AuthDto.DeliveryChannel.EMAIL);
            when(userRepository.findByEmail(req.getEmail())).thenReturn(Optional.empty());

            String result = authService.forgotPassword(req);

            assertThat(result).contains("Si votre compte");
            verify(passwordResetTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("✅ Persiste le token de réinitialisation si l'email est trouvé")
        void shouldPersistResetTokenWhenEmailFound() {
            User user = buildUser();
            AuthDto.ForgotPasswordRequest req = new AuthDto.ForgotPasswordRequest();
            req.setEmail(user.getEmail());
            req.setChannel(AuthDto.DeliveryChannel.EMAIL);
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
            when(passwordResetTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            authService.forgotPassword(req);

            verify(passwordResetTokenRepository).save(argThat(t ->
                t.getUser().equals(user) && t.getToken() != null && !t.isUsed()
            ));
        }

        @Test
        @DisplayName("✅ Canal SMS : retourne message générique si téléphone non trouvé")
        void shouldReturnGenericMessageWhenPhoneNotFound() {
            AuthDto.ForgotPasswordRequest req = new AuthDto.ForgotPasswordRequest();
            req.setPhoneNumber("+33600000000");
            req.setChannel(AuthDto.DeliveryChannel.SMS);
            when(userRepository.findByPhoneNumber(req.getPhoneNumber())).thenReturn(Optional.empty());

            String result = authService.forgotPassword(req);

            assertThat(result).contains("Si votre compte");
        }
    }

    /* ==========================================================
     *  resetPassword()
     * ========================================================== */

    @Nested
    @DisplayName("resetPassword()")
    class ResetPasswordTests {

        @Test
        @DisplayName("✅ Réinitialise le mot de passe et révoque les sessions")
        void shouldResetPasswordSuccessfully() {
            User user = buildUser();
            PasswordResetToken token = new PasswordResetToken(
                "valid-token", user, LocalDateTime.now().plusMinutes(10)
            );

            when(passwordResetTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(token));
            when(passwordEncoder.encode("NewPass99!")).thenReturn("newHash");
            when(userRepository.save(any())).thenReturn(user);
            when(passwordResetTokenRepository.save(any())).thenReturn(token);
            doNothing().when(refreshTokenRepository).revokeAllUserTokens(user);

            AuthDto.ResetPasswordRequest req = new AuthDto.ResetPasswordRequest();
            req.setToken("valid-token");
            req.setNewPassword("NewPass99!");

            String result = authService.resetPassword(req, "10.0.0.1");

            assertThat(result).containsIgnoringCase("succès");
            assertThat(token.isUsed()).isTrue();
            verify(refreshTokenRepository).revokeAllUserTokens(user);
            verify(auditLogService).logPasswordReset(user, "10.0.0.1");
        }

        @Test
        @DisplayName("❌ Lance une exception si le token est inconnu")
        void shouldThrowOnUnknownToken() {
            when(passwordResetTokenRepository.findByToken("bad")).thenReturn(Optional.empty());

            AuthDto.ResetPasswordRequest req = new AuthDto.ResetPasswordRequest();
            req.setToken("bad");
            req.setNewPassword("Pass1!");

            assertThatThrownBy(() -> authService.resetPassword(req, "ip"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("❌ Lance une exception si le token est déjà utilisé")
        void shouldThrowOnUsedToken() {
            User user = buildUser();
            PasswordResetToken token = new PasswordResetToken(
                "used-token", user, LocalDateTime.now().plusMinutes(10)
            );
            token.setUsed(true);
            when(passwordResetTokenRepository.findByToken("used-token")).thenReturn(Optional.of(token));

            AuthDto.ResetPasswordRequest req = new AuthDto.ResetPasswordRequest();
            req.setToken("used-token");
            req.setNewPassword("Pass1!");

            assertThatThrownBy(() -> authService.resetPassword(req, "ip"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("utilisé");
        }

        @Test
        @DisplayName("❌ Lance une exception si le token est expiré")
        void shouldThrowOnExpiredToken() {
            User user = buildUser();
            PasswordResetToken token = new PasswordResetToken(
                "expired-token", user, LocalDateTime.now().minusMinutes(5)
            );
            when(passwordResetTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(token));

            AuthDto.ResetPasswordRequest req = new AuthDto.ResetPasswordRequest();
            req.setToken("expired-token");
            req.setNewPassword("Pass1!");

            assertThatThrownBy(() -> authService.resetPassword(req, "ip"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expiré");
        }
    }

    /* ==========================================================
     *  verifyEmailToken()
     * ========================================================== */

    @Nested
    @DisplayName("verifyEmailToken()")
    class VerifyEmailTests {

        @Test
        @DisplayName("✅ Marque l'email comme vérifié")
        void shouldVerifyEmailSuccessfully() {
            User user = buildUser();
            EmailVerificationToken evToken = EmailVerificationToken.builder()
                .token("verify-token")
                .user(user)
                .expiryDate(LocalDateTime.now().plusHours(24))
                .used(false)
                .build();

            when(emailVerificationTokenRepository.findByToken("verify-token"))
                .thenReturn(Optional.of(evToken));
            when(userRepository.save(any())).thenReturn(user);
            when(emailVerificationTokenRepository.save(any())).thenReturn(evToken);

            String result = authService.verifyEmailToken("verify-token", "127.0.0.1");

            assertThat(result).containsIgnoringCase("verified");
            assertThat(user.getEmailVerified()).isTrue();
            assertThat(user.getEmailVerifiedAt()).isNotNull();
            assertThat(evToken.isUsed()).isTrue();
        }

        @Test
        @DisplayName("❌ Lance une exception si le token de vérification est invalide")
        void shouldThrowOnInvalidVerificationToken() {
            when(emailVerificationTokenRepository.findByToken("bad"))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.verifyEmailToken("bad", "ip"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid verification link");
        }

        @Test
        @DisplayName("❌ Lance une exception si le token de vérification est déjà utilisé")
        void shouldThrowOnAlreadyUsedVerificationToken() {
            User user = buildUser();
            EmailVerificationToken evToken = EmailVerificationToken.builder()
                .token("used-ev")
                .user(user)
                .expiryDate(LocalDateTime.now().plusHours(1))
                .used(true)
                .build();
            when(emailVerificationTokenRepository.findByToken("used-ev"))
                .thenReturn(Optional.of(evToken));

            assertThatThrownBy(() -> authService.verifyEmailToken("used-ev", "ip"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already been used");
        }

        @Test
        @DisplayName("❌ Lance une exception si le token de vérification est expiré")
        void shouldThrowOnExpiredVerificationToken() {
            User user = buildUser();
            EmailVerificationToken evToken = EmailVerificationToken.builder()
                .token("exp-ev")
                .user(user)
                .expiryDate(LocalDateTime.now().minusHours(1))
                .used(false)
                .build();
            when(emailVerificationTokenRepository.findByToken("exp-ev"))
                .thenReturn(Optional.of(evToken));

            assertThatThrownBy(() -> authService.verifyEmailToken("exp-ev", "ip"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
        }
    }

    /* ==========================================================
     *  resendVerificationEmail()
     * ========================================================== */

    @Nested
    @DisplayName("resendVerificationEmail()")
    class ResendVerificationTests {

        @Test
        @DisplayName("✅ Retourne un message générique si l'email est inconnu")
        void shouldReturnGenericWhenEmailUnknown() {
            when(userRepository.findByEmail("ghost@x.com")).thenReturn(Optional.empty());

            String result = authService.resendVerificationEmail("ghost@x.com");

            assertThat(result).contains("If your account exists");
        }

        @Test
        @DisplayName("✅ Retourne un message si l'email est déjà vérifié")
        void shouldReturnAlreadyVerifiedMessage() {
            User user = buildUser();
            user.setEmailVerified(true);
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

            String result = authService.resendVerificationEmail(user.getEmail());

            assertThat(result).containsIgnoringCase("already verified");
        }

        @Test
        @DisplayName("✅ Envoie un nouveau token si l'email n'est pas vérifié")
        void shouldSendNewTokenWhenNotVerified() {
            User user = buildUser(); // emailVerified = false
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
            doNothing().when(emailVerificationTokenRepository).deleteByUser(user);
            when(emailVerificationTokenRepository.save(any())).thenReturn(null);

            String result = authService.resendVerificationEmail(user.getEmail());

            assertThat(result).containsIgnoringCase("Verification email sent");
            verify(emailVerificationTokenRepository).deleteByUser(user);
            verify(emailVerificationTokenRepository).save(any(EmailVerificationToken.class));
        }
    }
}
