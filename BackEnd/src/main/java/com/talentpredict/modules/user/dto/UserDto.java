package com.talentpredict.modules.user.dto;

import com.talentpredict.modules.user.entities.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class UserDto {


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private UUID id;
        private String username;
        private String email;
        private String firstName;
        private String lastName;
        private String department;
        private String position;
        private LocalDate hireDate;
        private String profilePictureUrl;
        private Boolean isActive;
        private User.Role role;
        private Instant createdAt;
        private Instant updatedAt;
    }


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {

        @NotBlank(message = "Le nom d'utilisateur est requis")
        @Size(min = 3, max = 50, message = "Le nom d'utilisateur doit contenir entre 3 et 50 caractères")
        private String username;
        @NotBlank(message = "L'email est requis")
        @Email(message = "Format d'email invalide")
        private String email;
        /**
         * Required only on creation. For update, leave null to keep existing password.
         * Rules: min 8 chars, at least one letter and one digit.
         */
        @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
        @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
            message = "Le mot de passe doit contenir au moins une lettre et un chiffre"
        )
        private String password;
        @NotBlank(message = "Le prénom est requis")
        @Size(max = 100)
        private String firstName;
        @NotBlank(message = "Le nom de famille est requis")
        @Size(max = 100)
        private String lastName;
        @Size(max = 100)
        private String department;
        @Size(max = 100)
        private String position;
        private LocalDate hireDate;
        private String profilePictureUrl;
        @Builder.Default
        private Boolean isActive = true;
        private String role;
    }



    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateRequest {
        @Size(min = 1, max = 100)
        private String firstName;
        @Size(min = 1, max = 100)
        private String lastName;
        @Size(max = 100)
        private String department;
        @Size(max = 100)
        private String position;
        private LocalDate hireDate;
        private String profilePictureUrl;
        private Boolean isActive;
        private String role;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LeaderboardResponse {
        private UUID id;
        private String username;
        private Integer xp;
        private Integer level;
    }
}
