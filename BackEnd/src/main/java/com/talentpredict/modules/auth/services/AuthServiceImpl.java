package com.talentpredict.modules.auth.services;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements IAuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditLogService auditLogService;
    private final JwtService jwtService;
    private final SmsService smsService;


    @Value("${frontend.base-url:http://localhost:4200}")
    private String frontendBaseUrl;

    @Value("${security.login.max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${security.login.lock-duration-minutes:15}")
    private long lockDurationMinutes;

    @Value("${auth.email-verification.expiration-minutes:1440}")
    private long emailVerificationExpirationMinutes;

    /** Optional: injected only if mail is configured. Won't fail if absent. */
    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Override
    @Transactional
    public User createUser(AuthDto.RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email '" + request.getEmail() + "' is already in use");
        }

        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()
                && userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new ConflictException("Phone number '" + request.getPhoneNumber() + "' is already in use");
        }

        User user = new User();
        user.setLastName(request.getLastName());
        user.setFirstName(request.getFirstName());
        user.setEmail(request.getEmail());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmailVerified(false);
        user.setEmailVerifiedAt(null);


        // Set role from request — default to USER for safety
        User.Role role = User.Role.USER;
        if (StringUtils.hasText(request.getRole())) {
            try {
                role = User.Role.valueOf(request.getRole().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid role provided: {}, defaulting to USER", request.getRole());
            }
        }
        user.setRole(role);

        log.info("Creating user for {} with role={}", request.getEmail(), role);
        User created = userRepository.save(user);
        sendVerificationEmail(created);
        return created;
    }

    @Override
    public User getUserById(UUID targetUserId) {
        UUID safeTargetUserId = Objects.requireNonNull(targetUserId, "targetUserId must not be null");
        return userRepository.findById(safeTargetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("user not found with id: " + targetUserId));
    }

    @Override
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("user not found with email: " + email));
    }

    @Transactional
    public void recordFailedLoginAttempt(String email, String ipAddress) {
        userRepository.findByEmail(email).ifPresent(user -> {
            Integer currentAttempts = user.getFailedLoginAttempts();
            int nextAttempts = (currentAttempts != null ? currentAttempts : 0) + 1;
            user.setFailedLoginAttempts(nextAttempts);

            if (nextAttempts >= maxFailedAttempts) {
                user.setLockUntil(Instant.now().plusSeconds(lockDurationMinutes * 60));
                user.setFailedLoginAttempts(0);
                log.warn("Account temporarily locked for {} after too many failed attempts", email);
                auditLogService.logAccountLocked(email, ipAddress);
            }

            userRepository.save(user);
        });
    }

    @Transactional
    public void registerSuccessfulLogin(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setFailedLoginAttempts(0);
            user.setLockUntil(null);
            user.setLastLoginAt(Instant.now());
            userRepository.save(user);
        });
    }

    /**
     * Generate a refresh token for the user
     */
    @Transactional
    public String generateRefreshToken(User user, String deviceId) {
        String token = jwtService.generateRefreshToken(user.getEmail());
        Instant expiryDate = Instant.now().plusSeconds(604800); // 7 days

        RefreshToken refreshToken = RefreshToken.builder()
            .token(token)
            .user(user)
            .expiryDate(expiryDate)
            .deviceId(deviceId)
            .build();

        refreshTokenRepository.save(Objects.requireNonNull(refreshToken, "refreshToken must not be null"));
        log.info("Refresh token generated for user: {}", user.getEmail());
        return token;
    }

    /**
     * Validate and refresh access token using refresh token
     */
    @Transactional
    public String refreshAccessToken(String refreshToken) {
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByToken(refreshToken);

        if (tokenOpt.isEmpty() || !tokenOpt.get().isValid()) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        RefreshToken rtToken = tokenOpt.get();
        User user = rtToken.getUser();

        // Revoke old refresh token (token rotation)
        rtToken.setRevoked(true);
        refreshTokenRepository.save(rtToken);

        // Generate new access token; caller handles refresh token rotation
        String newAccessToken = jwtService.generateAccessToken(user.getEmail());

        log.info("Access token refreshed for user: {}", user.getEmail());
        return newAccessToken;
    }

    @Transactional
    public String changePassword(AuthDto.ChangePasswordRequest request, User currentUser, String ipAddress) {
        UUID currentUserId = Objects.requireNonNull(currentUser.getId(), "currentUser.id must not be null");
        User user = userRepository.findById(currentUserId)
            .orElseThrow(() -> new ResourceNotFoundException("user not found with id: " + currentUserId));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new IllegalArgumentException("New password must be different from current password.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Invalidate all sessions for this user (refresh token rotation)
        refreshTokenRepository.revokeAllUserTokens(user);

        log.info("Password changed successfully for {}", user.getEmail());
        auditLogService.logPasswordChange(user, ipAddress);
        return "Password updated successfully.";
    }

    // ---------------------------------------------------------------
    // TASK 3 — Forgot Password
    // ---------------------------------------------------------------

    /**
     * Initiates a password reset by generating a one-time token (valid 15 min).
     * If email is not found, returns silently (security: don't reveal valid emails).
     * If mail is not configured, the token is logged to console.
     */
    @Transactional
    public String forgotPassword(AuthDto.ForgotPasswordRequest request) {
        AuthDto.DeliveryChannel channel = request.getChannel() != null
                ? request.getChannel()
                : AuthDto.DeliveryChannel.EMAIL;

        Optional<User> userOpt;
        if (channel == AuthDto.DeliveryChannel.SMS) {
            userOpt = userRepository.findByPhoneNumber(request.getPhoneNumber());
        } else {
            userOpt = userRepository.findByEmail(request.getEmail());
        }

        if (userOpt.isEmpty()) {
            log.info("Forgot-password ({}): no account found for email={} phone={} — silent return (security)",
                    channel, request.getEmail(), request.getPhoneNumber());
            return "Si votre compte est enregistré, vous recevrez un lien de réinitialisation.";
        }

        User user = userOpt.get();
        String token = UUID.randomUUID().toString();
        LocalDateTime expiry = LocalDateTime.now().plusMinutes(15);

        PasswordResetToken resetToken = new PasswordResetToken(token, user, expiry);
        passwordResetTokenRepository.save(resetToken);

        // Try to send email — if not configured, fall back to console log
        String resetLink = frontendBaseUrl + "/auth/reset-password?token=" + token;

        if (channel == AuthDto.DeliveryChannel.SMS) {
            smsService.sendResetToken(user.getPhoneNumber(), resetLink, token);
        } else {
            try {
                if (mailSender != null) {
                    SimpleMailMessage mail = new SimpleMailMessage();
                    mail.setTo(user.getEmail());
                    mail.setSubject("TalentPredict — Réinitialisation de votre mot de passe");
                    mail.setText(
                            "Bonjour " + user.getFirstName() + ",\n\n" +
                                    "Cliquez sur ce lien pour réinitialiser votre mot de passe (valide 15 min) :\n" +
                                    resetLink + "\n\n" +
                                    "Si vous n'avez pas fait cette demande, ignorez cet email.\n\n" +
                                    "— Équipe TalentPredict");
                    mailSender.send(mail);
                    log.info("Password reset email sent to {}", user.getEmail());
                } else {
                    log.info("Mail not configured — RESET TOKEN for {}: {}", user.getEmail(), token);
                }
            } catch (RuntimeException e) {
                log.warn("Failed to send reset email to {}, logging token instead: {}", user.getEmail(), token);
            }
        }

        return "Si votre compte est enregistré, vous recevrez un lien de réinitialisation.";
    }

    /**
     * Validates the token and resets the password.
     * Invalidates all existing sessions after reset.
     */
    @Transactional
    public String resetPassword(AuthDto.ResetPasswordRequest request, String ipAddress) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new IllegalArgumentException("Lien invalide ou expiré."));

        if (resetToken.isUsed()) {
            throw new IllegalArgumentException("Ce lien a déjà été utilisé.");
        }

        if (resetToken.isExpired()) {
            throw new IllegalArgumentException("Ce lien est expiré. Veuillez refaire une demande.");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        // Invalidate all refresh tokens (session revocation)
        refreshTokenRepository.revokeAllUserTokens(user);

        log.info("Password successfully reset for account: {}", user.getEmail());
        auditLogService.logPasswordReset(user, ipAddress);
        return "Mot de passe mis à jour avec succès !";
    }



    @Transactional
    public String resendVerificationEmail(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return "If your account exists, a verification email was sent.";
        }

        User user = userOpt.get();
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            return "This email is already verified.";
        }

        sendVerificationEmail(user);
        return "Verification email sent.";
    }

    @Transactional
    public String verifyEmailToken(String token, String ipAddress) {
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification link."));

        if (verificationToken.isUsed()) {
            throw new IllegalArgumentException("This verification link has already been used.");
        }

        if (verificationToken.isExpired()) {
            throw new IllegalArgumentException("This verification link has expired.");
        }

        User user = verificationToken.getUser();
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(Instant.now());
        userRepository.save(user);

        verificationToken.setUsed(true);
        emailVerificationTokenRepository.save(verificationToken);

        auditLogService.logCustomEvent(
                user,
                "EMAIL_VERIFIED",
                ipAddress,
                "Email address verified successfully",
                null,
                null);
        return "Email verified successfully. You can now log in.";
    }

    private void sendVerificationEmail(User user) {
        emailVerificationTokenRepository.deleteByUser(user);

        String token = UUID.randomUUID().toString();
        LocalDateTime expiry = LocalDateTime.now().plusMinutes(emailVerificationExpirationMinutes);
        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .token(token)
                .user(user)
                .expiryDate(expiry)
                .used(false)
                .build();

        emailVerificationTokenRepository.save(verificationToken);

        String verifyLink = frontendBaseUrl + "/auth/verify-email?token=" + token;
        if (mailSender != null) {
            try {
                SimpleMailMessage mail = new SimpleMailMessage();
                mail.setTo(user.getEmail());
                mail.setSubject("TalentPredict - Verify your email");
                mail.setText(
                        "Hello " + user.getFirstName() + ",\n\n"
                                + "Please verify your email by clicking the link below:\n"
                                + verifyLink + "\n\n"
                                + "This link expires in " + emailVerificationExpirationMinutes + " minutes.\n\n"
                                + "- TalentPredict Team");
                mailSender.send(mail);
                log.info("Verification email sent to {}", user.getEmail());
                return;
            } catch (RuntimeException ex) {
                log.warn("Failed to send verification email to {}", user.getEmail(), ex);
            }
        }

        log.info("Mail not configured - EMAIL VERIFICATION TOKEN for {}: {}", user.getEmail(), verifyLink);
    }
}
