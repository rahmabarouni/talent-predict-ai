package com.talentpredict.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

public class AuthDto {

    private static final String PASSWORD_POLICY =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$";

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Response {
        private String token;
        private String type = "Bearer";
        private UUID id;
        private String email;
        private String role;
        private String nom;
        private String prenom;
        private String redirectUrl;
        private Boolean emailVerified;

        public Response(String token, UUID id, String email, String role,
                String nom, String prenom, String redirectUrl) {
            this.token = token;
            this.type = "Bearer";
            this.id = id;
            this.email = email;
            this.role = role;
            this.nom = nom;
            this.prenom = prenom;
            this.redirectUrl = redirectUrl;
            this.emailVerified = false;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisterRequest {
        @NotBlank(message = "Last name is required")
        private String lastName;
        @NotBlank(message = "First name is required")
        private String firstName;
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;
        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        @Pattern(regexp = PASSWORD_POLICY)
        private String password;
        private String phoneNumber;
        private String role;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;
        @NotBlank(message = "Password is required")
        private String password;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResendVerificationRequest {
        @NotBlank(message = "Email is required")
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForgotPasswordRequest {
        private DeliveryChannel channel = DeliveryChannel.EMAIL;
        private String email;
        private String phoneNumber;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResetPasswordRequest {
        @NotBlank(message = "Token is required")
        private String token;
        @NotBlank(message = "New password is required")
        @Pattern(regexp = PASSWORD_POLICY)
        private String newPassword;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangePasswordRequest {
        @NotBlank(message = "Current password is required")
        private String currentPassword;
        @NotBlank(message = "New password is required")
        @Pattern(regexp = PASSWORD_POLICY)
        private String newPassword;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MessageResponse {
        private String message;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RefreshResponse {
        private String accessToken;
        private String type = "Bearer";
    }

    public enum DeliveryChannel {
        EMAIL, SMS
    }
}
