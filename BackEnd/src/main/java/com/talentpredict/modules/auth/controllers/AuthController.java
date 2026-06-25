package com.talentpredict.modules.auth.controllers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.talentpredict.modules.ai.services.ProfileAnalysisOrchestrator;
import com.talentpredict.modules.auth.dto.AuthDto;
import com.talentpredict.modules.auth.services.AuditLogService;
import com.talentpredict.modules.auth.services.AuthServiceImpl;
import com.talentpredict.modules.user.entities.User;
import com.talentpredict.shared.security.JwtService;
import com.talentpredict.shared.security.UserDetailsImpl;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthServiceImpl authServiceImpl;
    private final ProfileAnalysisOrchestrator profileAnalysisOrchestrator;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final AuditLogService auditLogService;

    @Value("${security.cookie.secure:false}")
    private boolean refreshCookieSecure;

    @PostMapping("/register")
    public ResponseEntity<AuthDto.Response> register(@Valid @RequestBody AuthDto.RegisterRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        User user = authServiceImpl.createUser(request);
        boolean emailVerified = Boolean.TRUE.equals(user.getEmailVerified());
        String accessToken = null;

        if (emailVerified) {
            accessToken = jwtService.generateAccessToken(user.getEmail());
            String deviceId = resolveDeviceId(httpRequest);
            String refreshToken = authServiceImpl.generateRefreshToken(user, deviceId);

            // Return refresh token in HttpOnly cookie
            Cookie cookie = buildRefreshCookie(refreshToken, 604800);
            response.addCookie(cookie);
        }

        String redirectUrl = emailVerified ? getRedirectUrl(user) : "/auth/verify-email";

        AuthDto.Response responseDto = new AuthDto.Response(
                accessToken,
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.getLastName(),
                user.getFirstName(),
                redirectUrl);
        responseDto.setEmailVerified(emailVerified);

        log.info("Registered: {} role={} → {}", user.getEmail(), user.getRole(), redirectUrl);
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
    }

    @PostMapping("/login")
        public ResponseEntity<?> login(@Valid @RequestBody AuthDto.LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        } catch (LockedException ex) {
            return ResponseEntity.status(HttpStatus.LOCKED)
                .body(new AuthDto.MessageResponse("Compte temporairement verrouillé."));
        } catch (BadCredentialsException ex) {
            authServiceImpl.recordFailedLoginAttempt(request.getEmail(), resolveClientIp(httpRequest));
            auditLogService.logFailedLogin(
                request.getEmail(),
                resolveClientIp(httpRequest),
                httpRequest.getHeader("User-Agent"),
                "Invalid credentials");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new AuthDto.MessageResponse("Email ou mot de passe incorrect."));
        }

        User user = authServiceImpl.getUserByEmail(request.getEmail());

        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new AuthDto.MessageResponse(
                    "Please verify your email before logging in. Use the verification link sent to your inbox."));
        }



        authServiceImpl.registerSuccessfulLogin(request.getEmail());
        String accessToken = jwtService.generateAccessToken(user.getEmail());
        String deviceId = resolveDeviceId(httpRequest);
        String refreshToken = authServiceImpl.generateRefreshToken(user, deviceId);

        // Return refresh token in HttpOnly cookie
        Cookie cookie = buildRefreshCookie(refreshToken, 604800);
        response.addCookie(cookie);

        String redirectUrl = getRedirectUrl(user);

        AuthDto.Response responseDto = new AuthDto.Response(
                accessToken,
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.getLastName(),
                user.getFirstName(),
                redirectUrl);
        responseDto.setEmailVerified(Boolean.TRUE.equals(user.getEmailVerified()));

        log.info("Login: {} role={} → {}", user.getEmail(), user.getRole(), redirectUrl);
        auditLogService.logLogin(
                user,
                resolveClientIp(httpRequest),
                httpRequest.getHeader("User-Agent"),
                deviceId);
        log.info("🤖 Déclenchement analyse IA pour account: {}", responseDto.getId());
        profileAnalysisOrchestrator.analyserProfil(responseDto.getId());
        return ResponseEntity.ok(responseDto);
    }

    @GetMapping("/verify-email")
    public ResponseEntity<AuthDto.MessageResponse> verifyEmail(
            @RequestParam(value = "token", required = false) String token,
            HttpServletRequest httpRequest) {
        if (token == null || token.isEmpty()) {
            return ResponseEntity.badRequest().body(new AuthDto.MessageResponse("Le jeton de vérification est manquant."));
        }
        try {
            String message = authServiceImpl.verifyEmailToken(token, resolveClientIp(httpRequest));
            return ResponseEntity.ok(new AuthDto.MessageResponse(message));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new AuthDto.MessageResponse(ex.getMessage()));
        }
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<AuthDto.MessageResponse> resendVerification(
            @Valid @RequestBody AuthDto.ResendVerificationRequest request) {
        String message = authServiceImpl.resendVerificationEmail(request.getEmail());
        return ResponseEntity.ok(new AuthDto.MessageResponse(message));
    }

    /**
     * TASK 3 — POST /api/auth/forgot-password (PUBLIC)
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<AuthDto.MessageResponse> forgotPassword(
            @Valid @RequestBody AuthDto.ForgotPasswordRequest request) {
        String message = authServiceImpl.forgotPassword(request);
        return ResponseEntity.ok(new AuthDto.MessageResponse(message));
    }

    /**
     * TASK 3 — POST /api/auth/reset-password (PUBLIC)
     */
    @PostMapping("/reset-password")
    public ResponseEntity<AuthDto.MessageResponse> resetPassword(
            @Valid @RequestBody AuthDto.ResetPasswordRequest request,
            HttpServletRequest httpRequest) {
        try {
            String message = authServiceImpl.resetPassword(request, resolveClientIp(httpRequest));
            return ResponseEntity.ok(new AuthDto.MessageResponse(message));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new AuthDto.MessageResponse(e.getMessage()));
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<AuthDto.MessageResponse> changePassword(
            @Valid @RequestBody AuthDto.ChangePasswordRequest request,
            @AuthenticationPrincipal UserDetailsImpl principal,
            HttpServletRequest httpRequest) {
        String message = authServiceImpl.changePassword(request, principal.getUser(), resolveClientIp(httpRequest));
        return ResponseEntity.ok(new AuthDto.MessageResponse(message));
    }

    /**
     * SECURITY FEATURE: Refresh access token using refresh token (stored in HttpOnly cookie)
     */
    @PostMapping("/refresh-token")
    public ResponseEntity<AuthDto.RefreshResponse> refreshToken(
            @CookieValue(value = "refreshToken", required = false) String refreshToken,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        try {
            if (refreshToken == null || refreshToken.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new AuthDto.RefreshResponse(null, "Missing refresh token"));
            }

            String newAccessToken = authServiceImpl.refreshAccessToken(refreshToken);
            String newRefreshToken = authServiceImpl.generateRefreshToken(
                authServiceImpl.getUserByEmail(jwtService.extractUsername(newAccessToken)),
                resolveDeviceId(httpRequest)
            );

            // Return new refresh token in HttpOnly cookie
            Cookie cookie = buildRefreshCookie(newRefreshToken, 604800);
            response.addCookie(cookie);

            return ResponseEntity.ok(new AuthDto.RefreshResponse(newAccessToken, "Bearer"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /**
     * SECURITY FEATURE: Logout by invalidating refresh token
     */
    @PostMapping("/logout")
    public ResponseEntity<AuthDto.MessageResponse> logout(
            @AuthenticationPrincipal UserDetailsImpl principal,
            HttpServletRequest httpRequest,
            @CookieValue(value = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {
        // Clear refresh token cookie regardless of auth state
        Cookie clear = buildRefreshCookie(null, 0);
        response.addCookie(clear);

        if (principal != null && principal.getUser() != null) {
            User currentUser = principal.getUser();
            auditLogService.logLogout(currentUser, resolveClientIp(httpRequest));
            log.info("User logout: {}", currentUser.getEmail());
        }

        return ResponseEntity.ok(new AuthDto.MessageResponse("Déconnecté avec succès."));
    }

    private Cookie buildRefreshCookie(String token, int maxAgeSeconds) {
        Cookie cookie = new Cookie("refreshToken", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(refreshCookieSecure);
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setAttribute("SameSite", "Lax");
        return cookie;
    }

    private String getRedirectUrl(User user) {
        if (user.getRole() == User.Role.ADMIN) {
            return "/admin/dashboard";
        }
        return "/dashboard";
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String resolveDeviceId(HttpServletRequest request) {
        String headerDeviceId = request.getHeader("X-Device-Id");
        if (StringUtils.hasText(headerDeviceId)) {
            return headerDeviceId.trim();
        }

        String userAgent = request.getHeader("User-Agent");
        if (StringUtils.hasText(userAgent)) {
            return "ua-" + Integer.toHexString(userAgent.hashCode());
        }

        return "unknown-device";
    }
}
