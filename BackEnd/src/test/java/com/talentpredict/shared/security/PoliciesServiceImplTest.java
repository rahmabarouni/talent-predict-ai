package com.talentpredict.shared.security;

import com.talentpredict.modules.user.entities.User;
import com.talentpredict.modules.user.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link PoliciesServiceImpl}.
 * <p>
 * Vérifie les règles d'autorisation : un admin peut tout faire,
 * un utilisateur normal peut seulement agir sur son propre compte.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PoliciesServiceImpl — Tests Unitaires")
class PoliciesServiceImplTest {

    @Mock private UserRepository userRepository;

    @InjectMocks
    private PoliciesServiceImpl policiesService;

    private User adminUser;
    private User regularUser;
    private User otherUser;

    @BeforeEach
    void setUp() {
        adminUser = new User();
        adminUser.setId(UUID.randomUUID());
        adminUser.setRole(User.Role.ADMIN);

        regularUser = new User();
        regularUser.setId(UUID.randomUUID());
        regularUser.setRole(User.Role.USER);

        otherUser = new User();
        otherUser.setId(UUID.randomUUID());
        otherUser.setRole(User.Role.USER);
    }

    /* ==========================================================
     *  canViewUser()
     * ========================================================== */

    @Nested
    @DisplayName("canViewUser()")
    class CanViewUserTests {

        @Test
        @DisplayName("✅ Admin peut voir n'importe quel utilisateur")
        void adminCanViewAnyUser() {
            when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));
            when(userRepository.findById(otherUser.getId())).thenReturn(Optional.of(otherUser));

            assertThat(policiesService.canViewUser(adminUser.getId(), otherUser.getId())).isTrue();
        }

        @Test
        @DisplayName("✅ Un utilisateur peut voir son propre profil")
        void userCanViewOwnProfile() {
            when(userRepository.findById(regularUser.getId())).thenReturn(Optional.of(regularUser));

            assertThat(policiesService.canViewUser(regularUser.getId(), regularUser.getId())).isTrue();
        }

        @Test
        @DisplayName("❌ Un utilisateur ne peut pas voir le profil d'un autre")
        void userCannotViewOtherProfile() {
            when(userRepository.findById(regularUser.getId())).thenReturn(Optional.of(regularUser));
            when(userRepository.findById(otherUser.getId())).thenReturn(Optional.of(otherUser));

            assertThat(policiesService.canViewUser(regularUser.getId(), otherUser.getId())).isFalse();
        }

        @Test
        @DisplayName("❌ Retourne false si l'authentifié n'existe pas")
        void returnsFalseWhenAuthUserNotFound() {
            UUID unknown = UUID.randomUUID();
            when(userRepository.findById(unknown)).thenReturn(Optional.empty());

            assertThat(policiesService.canViewUser(unknown, otherUser.getId())).isFalse();
        }

        @Test
        @DisplayName("❌ Retourne false si l'utilisateur cible n'existe pas")
        void returnsFalseWhenTargetNotFound() {
            UUID unknownTarget = UUID.randomUUID();
            when(userRepository.findById(regularUser.getId())).thenReturn(Optional.of(regularUser));
            when(userRepository.findById(unknownTarget)).thenReturn(Optional.empty());

            assertThat(policiesService.canViewUser(regularUser.getId(), unknownTarget)).isFalse();
        }
    }

    /* ==========================================================
     *  canUpdateUser()
     * ========================================================== */

    @Nested
    @DisplayName("canUpdateUser()")
    class CanUpdateUserTests {

        @Test
        @DisplayName("✅ Admin peut mettre à jour n'importe quel utilisateur")
        void adminCanUpdateAnyUser() {
            when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));
            when(userRepository.findById(otherUser.getId())).thenReturn(Optional.of(otherUser));

            assertThat(policiesService.canUpdateUser(adminUser.getId(), otherUser.getId())).isTrue();
        }

        @Test
        @DisplayName("✅ Un utilisateur peut se mettre à jour lui-même")
        void userCanUpdateOwnAccount() {
            when(userRepository.findById(regularUser.getId())).thenReturn(Optional.of(regularUser));

            assertThat(policiesService.canUpdateUser(regularUser.getId(), regularUser.getId())).isTrue();
        }

        @Test
        @DisplayName("❌ Un utilisateur ne peut pas mettre à jour un autre compte")
        void userCannotUpdateOtherAccount() {
            when(userRepository.findById(regularUser.getId())).thenReturn(Optional.of(regularUser));
            when(userRepository.findById(otherUser.getId())).thenReturn(Optional.of(otherUser));

            assertThat(policiesService.canUpdateUser(regularUser.getId(), otherUser.getId())).isFalse();
        }
    }

    /* ==========================================================
     *  canDeleteUser()
     * ========================================================== */

    @Nested
    @DisplayName("canDeleteUser()")
    class CanDeleteUserTests {

        @Test
        @DisplayName("✅ Admin peut supprimer n'importe quel utilisateur")
        void adminCanDeleteAnyUser() {
            when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));
            when(userRepository.findById(otherUser.getId())).thenReturn(Optional.of(otherUser));

            assertThat(policiesService.canDeleteUser(adminUser.getId(), otherUser.getId())).isTrue();
        }

        @Test
        @DisplayName("✅ Un utilisateur peut supprimer son propre compte")
        void userCanDeleteOwnAccount() {
            when(userRepository.findById(regularUser.getId())).thenReturn(Optional.of(regularUser));

            assertThat(policiesService.canDeleteUser(regularUser.getId(), regularUser.getId())).isTrue();
        }

        @Test
        @DisplayName("❌ Un utilisateur ne peut pas supprimer le compte d'un autre")
        void userCannotDeleteOtherAccount() {
            when(userRepository.findById(regularUser.getId())).thenReturn(Optional.of(regularUser));
            when(userRepository.findById(otherUser.getId())).thenReturn(Optional.of(otherUser));

            assertThat(policiesService.canDeleteUser(regularUser.getId(), otherUser.getId())).isFalse();
        }

        @Test
        @DisplayName("❌ Admin supprimant un utilisateur inexistant retourne false")
        void returnsFalseWhenTargetNotFoundForAdmin() {
            UUID unknownTarget = UUID.randomUUID();
            when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));
            when(userRepository.findById(unknownTarget)).thenReturn(Optional.empty());

            assertThat(policiesService.canDeleteUser(adminUser.getId(), unknownTarget)).isFalse();
        }
    }
}
