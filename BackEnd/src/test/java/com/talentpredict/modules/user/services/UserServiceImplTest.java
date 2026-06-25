package com.talentpredict.modules.user.services;

import com.talentpredict.modules.user.dto.UserDto;
import com.talentpredict.modules.user.entities.User;
import com.talentpredict.modules.user.repositories.UserRepository;
import com.talentpredict.shared.exception.BadRequestException;
import com.talentpredict.shared.exception.ResourceNotFoundException;
import com.talentpredict.shared.exception.UnauthorizedException;
import com.talentpredict.shared.security.interfaces.IPoliciesService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link UserServiceImpl}.
 * <p>
 * Les dépendances (repository, policies, EntityManager) sont toutes mockées.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl — Tests Unitaires")
class UserServiceImplTest {

    /* =========================================================
     *  MOCKS & SUT
     * ========================================================= */

    @Mock private UserRepository userRepository;
    @Mock private IPoliciesService policiesService;
    @Mock private EntityManager entityManager;

    @InjectMocks
    private UserServiceImpl userService;

    /* =========================================================
     *  FIXTURES
     * ========================================================= */

    private User adminUser;
    private User regularUser;

    @BeforeEach
    void setUp() {
        // Inject EntityManager via reflection (PersistenceContext cannot be injected by Mockito)
        ReflectionTestUtils.setField(userService, "entityManager", entityManager);

        adminUser = new User();
        adminUser.setId(UUID.randomUUID());
        adminUser.setEmail("admin@talentpredict.com");
        adminUser.setRole(User.Role.ADMIN);
        adminUser.setFirstName("Admin");
        adminUser.setLastName("User");
        adminUser.setPassword("hashed");

        regularUser = new User();
        regularUser.setId(UUID.randomUUID());
        regularUser.setEmail("user@talentpredict.com");
        regularUser.setRole(User.Role.USER);
        regularUser.setFirstName("Regular");
        regularUser.setLastName("User");
        regularUser.setPassword("hashed");
    }

    /* ==========================================================
     *  listUsers()
     * ========================================================== */

    @Nested
    @DisplayName("listUsers()")
    class ListUsersTests {

        @Test
        @DisplayName("✅ Retourne la liste de tous les utilisateurs")
        void shouldReturnAllUsers() {
            when(userRepository.findAll()).thenReturn(List.of(adminUser, regularUser));

            List<User> result = userService.listUsers();

            assertThat(result).hasSize(2).contains(adminUser, regularUser);
        }

        @Test
        @DisplayName("✅ Retourne une liste vide si aucun utilisateur")
        void shouldReturnEmptyList() {
            when(userRepository.findAll()).thenReturn(List.of());

            assertThat(userService.listUsers()).isEmpty();
        }
    }

    /* ==========================================================
     *  getLeaderboard()
     * ========================================================== */

    @Nested
    @DisplayName("getLeaderboard()")
    class LeaderboardTests {

        @Test
        @DisplayName("✅ Retourne le top 10 mappé correctement")
        void shouldReturnMappedLeaderboard() {
            regularUser.setUsername("alice");
            regularUser.setXp(500);
            regularUser.setLevel(3);
            when(userRepository.findTop10ByOrderByXpDesc()).thenReturn(List.of(regularUser));

            List<UserDto.LeaderboardResponse> result = userService.getLeaderboard();

            assertThat(result).hasSize(1);
            UserDto.LeaderboardResponse entry = result.get(0);
            assertThat(entry.getUsername()).isEqualTo("alice");
            assertThat(entry.getXp()).isEqualTo(500);
            assertThat(entry.getLevel()).isEqualTo(3);
        }

        @Test
        @DisplayName("✅ Retourne XP=0 et Level=1 si les valeurs sont null")
        void shouldDefaultXpAndLevelWhenNull() {
            regularUser.setXp(null);
            regularUser.setLevel(null);
            when(userRepository.findTop10ByOrderByXpDesc()).thenReturn(List.of(regularUser));

            List<UserDto.LeaderboardResponse> result = userService.getLeaderboard();

            assertThat(result.get(0).getXp()).isZero();
            assertThat(result.get(0).getLevel()).isEqualTo(1);
        }
    }

    /* ==========================================================
     *  getUserById(id, currentUser)
     * ========================================================== */

    @Nested
    @DisplayName("getUserById()")
    class GetUserByIdTests {

        @Test
        @DisplayName("✅ Admin peut voir n'importe quel utilisateur")
        void adminShouldViewAnyUser() {
            when(policiesService.canViewUser(adminUser.getId(), regularUser.getId())).thenReturn(true);
            when(userRepository.findById(regularUser.getId())).thenReturn(Optional.of(regularUser));

            User result = userService.getUserById(regularUser.getId(), adminUser);

            assertThat(result).isEqualTo(regularUser);
        }

        @Test
        @DisplayName("✅ Un utilisateur peut voir son propre compte")
        void userShouldViewOwnProfile() {
            when(policiesService.canViewUser(regularUser.getId(), regularUser.getId())).thenReturn(true);
            when(userRepository.findById(regularUser.getId())).thenReturn(Optional.of(regularUser));

            User result = userService.getUserById(regularUser.getId(), regularUser);

            assertThat(result).isEqualTo(regularUser);
        }

        @Test
        @DisplayName("❌ Lance UnauthorizedException si currentUser est null")
        void shouldThrowWhenCurrentUserIsNull() {
            assertThatThrownBy(() -> userService.getUserById(regularUser.getId(), null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("not authenticated");
        }

        @Test
        @DisplayName("❌ Lance UnauthorizedException si la politique le refuse")
        void shouldThrowWhenPoliciesDeny() {
            when(policiesService.canViewUser(regularUser.getId(), adminUser.getId())).thenReturn(false);

            assertThatThrownBy(() -> userService.getUserById(adminUser.getId(), regularUser))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("permission");
        }

        @Test
        @DisplayName("❌ Lance ResourceNotFoundException si l'utilisateur cible n'existe pas")
        void shouldThrowWhenTargetNotFound() {
            UUID missingId = UUID.randomUUID();
            when(policiesService.canViewUser(adminUser.getId(), missingId)).thenReturn(true);
            when(userRepository.findById(missingId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUserById(missingId, adminUser))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(missingId.toString());
        }
    }

    /* ==========================================================
     *  updateUser()
     * ========================================================== */

    @Nested
    @DisplayName("updateUser()")
    class UpdateUserTests {

        @Test
        @DisplayName("✅ Met à jour les champs de base par un utilisateur autorisé")
        void shouldUpdateBasicFieldsWhenAuthorized() {
            when(userRepository.findById(regularUser.getId())).thenReturn(Optional.of(regularUser));
            when(policiesService.canUpdateUser(regularUser.getId(), regularUser.getId())).thenReturn(true);
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UserDto.UpdateRequest req = UserDto.UpdateRequest.builder()
                .firstName("Nouvel")
                .lastName("Nom")
                .department("IT")
                .position("Dev")
                .build();

            User result = userService.updateUser(regularUser.getId(), req, regularUser);

            assertThat(result.getFirstName()).isEqualTo("Nouvel");
            assertThat(result.getLastName()).isEqualTo("Nom");
            assertThat(result.getDepartment()).isEqualTo("IT");
            assertThat(result.getPosition()).isEqualTo("Dev");
        }

        @Test
        @DisplayName("✅ Admin peut changer le rôle d'un utilisateur")
        void adminShouldUpdateRole() {
            when(userRepository.findById(regularUser.getId())).thenReturn(Optional.of(regularUser));
            when(policiesService.canUpdateUser(adminUser.getId(), regularUser.getId())).thenReturn(true);
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UserDto.UpdateRequest req = UserDto.UpdateRequest.builder()
                .role("ADMIN")
                .build();

            User result = userService.updateUser(regularUser.getId(), req, adminUser);

            assertThat(result.getRole()).isEqualTo(User.Role.ADMIN);
        }

        @Test
        @DisplayName("❌ Utilisateur non-admin ne peut pas changer le rôle")
        void nonAdminShouldNotUpdateRole() {
            when(userRepository.findById(regularUser.getId())).thenReturn(Optional.of(regularUser));
            when(policiesService.canUpdateUser(regularUser.getId(), regularUser.getId())).thenReturn(true);

            UserDto.UpdateRequest req = UserDto.UpdateRequest.builder()
                .role("ADMIN")
                .build();

            assertThatThrownBy(() -> userService.updateUser(regularUser.getId(), req, regularUser))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Only admins");
        }

        @Test
        @DisplayName("❌ Lance BadRequestException si le rôle est invalide")
        void shouldThrowOnInvalidRole() {
            when(userRepository.findById(regularUser.getId())).thenReturn(Optional.of(regularUser));
            when(policiesService.canUpdateUser(adminUser.getId(), regularUser.getId())).thenReturn(true);

            UserDto.UpdateRequest req = UserDto.UpdateRequest.builder()
                .role("INVALID_ROLE")
                .build();

            assertThatThrownBy(() -> userService.updateUser(regularUser.getId(), req, adminUser))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid role");
        }

        @Test
        @DisplayName("❌ Lance UnauthorizedException si currentUser est null")
        void shouldThrowWhenCurrentUserIsNull() {
            assertThatThrownBy(() ->
                userService.updateUser(regularUser.getId(), new UserDto.UpdateRequest(), null)
            ).isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("❌ Lance ResourceNotFoundException si l'utilisateur cible n'existe pas")
        void shouldThrowWhenUserNotFound() {
            UUID missingId = UUID.randomUUID();
            when(userRepository.findById(missingId)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                userService.updateUser(missingId, new UserDto.UpdateRequest(), adminUser)
            ).isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("✅ Ne met pas à jour les champs si les valeurs sont null (PATCH-like)")
        void shouldNotOverwriteNullFields() {
            regularUser.setFirstName("Alice");
            when(userRepository.findById(regularUser.getId())).thenReturn(Optional.of(regularUser));
            when(policiesService.canUpdateUser(regularUser.getId(), regularUser.getId())).thenReturn(true);
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UserDto.UpdateRequest req = new UserDto.UpdateRequest(); // all fields null

            User result = userService.updateUser(regularUser.getId(), req, regularUser);

            assertThat(result.getFirstName()).isEqualTo("Alice"); // unchanged
        }
    }

    /* ==========================================================
     *  deleteUser()
     * ========================================================== */

    @Nested
    @DisplayName("deleteUser()")
    class DeleteUserTests {

        /**
         * Helper pour mocker toutes les requêtes JPQL de nettoyage.
         */
        private void mockAllCleanupQueries() {
            Query mockQuery = mock(Query.class);
            when(entityManager.createQuery(anyString())).thenReturn(mockQuery);
            when(mockQuery.setParameter(anyString(), any())).thenReturn(mockQuery);
            when(mockQuery.executeUpdate()).thenReturn(0);
        }

        @Test
        @DisplayName("✅ Supprime l'utilisateur et exécute le nettoyage des dépendances")
        void shouldDeleteUserAndCleanUp() {
            mockAllCleanupQueries();
            when(policiesService.canDeleteUser(adminUser.getId(), regularUser.getId())).thenReturn(true);
            when(userRepository.findById(regularUser.getId())).thenReturn(Optional.of(regularUser));
            doNothing().when(userRepository).delete(regularUser);

            assertThatCode(() -> userService.deleteUser(regularUser.getId(), adminUser))
                .doesNotThrowAnyException();

            verify(userRepository).delete(regularUser);
            // Vérifie qu'au moins 5 JPQL de nettoyage ont été exécutées
            verify(entityManager, atLeast(5)).createQuery(anyString());
        }

        @Test
        @DisplayName("❌ Lance UnauthorizedException si currentUser est null")
        void shouldThrowWhenCurrentUserIsNull() {
            assertThatThrownBy(() -> userService.deleteUser(regularUser.getId(), null))
                .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("❌ Lance UnauthorizedException si la politique refuse la suppression")
        void shouldThrowWhenPoliciesDeny() {
            when(policiesService.canDeleteUser(regularUser.getId(), adminUser.getId())).thenReturn(false);

            assertThatThrownBy(() -> userService.deleteUser(adminUser.getId(), regularUser))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("permission");
        }

        @Test
        @DisplayName("❌ Lance ResourceNotFoundException si l'utilisateur cible n'existe pas")
        void shouldThrowWhenUserNotFound() {
            UUID missingId = UUID.randomUUID();
            when(policiesService.canDeleteUser(adminUser.getId(), missingId)).thenReturn(true);
            when(userRepository.findById(missingId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.deleteUser(missingId, adminUser))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
