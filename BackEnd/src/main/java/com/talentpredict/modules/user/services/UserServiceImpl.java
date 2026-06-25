package com.talentpredict.modules.user.services;

import com.talentpredict.modules.user.dto.UserDto;
import com.talentpredict.modules.user.entities.User;
import com.talentpredict.modules.user.repositories.UserRepository;
import com.talentpredict.shared.exception.BadRequestException;
import com.talentpredict.shared.exception.ResourceNotFoundException;
import com.talentpredict.shared.exception.UnauthorizedException;
import com.talentpredict.shared.security.interfaces.IPoliciesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.UUID;


@Service
@RequiredArgsConstructor

public class UserServiceImpl implements IUserService {

    private final UserRepository userRepository;
    private final IPoliciesService policiesService;

    @PersistenceContext
    private EntityManager entityManager;


    @Override
    @Transactional(readOnly = true)
    public List<User> listUsers() {
        return userRepository.findAll();
    }

    @Override
    public List<UserDto.LeaderboardResponse> getLeaderboard() {
        return userRepository.findTop10ByOrderByXpDesc().stream().map(u -> UserDto.LeaderboardResponse.builder()
                .id(u.getId())
                .username(u.getUsername())
                .xp(u.getXp() != null ? u.getXp() : 0)
                .level(u.getLevel() != null ? u.getLevel() : 1)
                .build()).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public User getUserById(UUID targetUserId, User currentUser) {
        if (currentUser == null) {
            throw new UnauthorizedException("User not authenticated");
        }
        // policies
        if (!policiesService.canViewUser(currentUser.getId(), targetUserId)) {
            throw new UnauthorizedException("You do not have permission to view user with ID: " + targetUserId);
        }

        return userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + targetUserId));
    }

    @Override
    @Transactional
    
    public void deleteUser(UUID targetUserId, User currentUser) {
        if (currentUser == null) {
            throw new UnauthorizedException("User not authenticated");
        }
        // policies
        if (!policiesService.canDeleteUser(currentUser.getId(), targetUserId)) {
            throw new UnauthorizedException("You do not have permission to delete user with ID: " + targetUserId);
        }

        // Get the user to ensure it exists and to handle manual cleanup if needed
        User userToDelete = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + targetUserId));

        // Manual cleanup for dependencies
        entityManager.createQuery("DELETE FROM CandidateBadge cb WHERE cb.user.id = :userId")
                .setParameter("userId", targetUserId).executeUpdate();
        
        entityManager.createQuery("DELETE FROM CandidateTestResult ctr WHERE ctr.user.id = :userId")
                .setParameter("userId", targetUserId).executeUpdate();

        entityManager.createQuery("DELETE FROM JobMatch jm WHERE jm.user.id = :userId")
                .setParameter("userId", targetUserId).executeUpdate();

        entityManager.createQuery("DELETE FROM UserNotification un WHERE un.user.id = :userId")
                .setParameter("userId", targetUserId).executeUpdate();

        entityManager.createQuery("DELETE FROM EmailVerificationToken evt WHERE evt.user.id = :userId")
                .setParameter("userId", targetUserId).executeUpdate();

        entityManager.createQuery("DELETE FROM PasswordResetToken prt WHERE prt.user.id = :userId")
                .setParameter("userId", targetUserId).executeUpdate();

        entityManager.createQuery("DELETE FROM AuditLog al WHERE al.user.id = :userId")
                .setParameter("userId", targetUserId).executeUpdate();



        entityManager.createQuery("DELETE FROM UserPrivacySetting ups WHERE ups.userId = :userId")
                .setParameter("userId", targetUserId).executeUpdate();
        
        // Profiles usually have a one-to-one with user
        entityManager.createQuery("DELETE FROM Profile p WHERE p.user.id = :userId")
                .setParameter("userId", targetUserId).executeUpdate();

        userRepository.delete(userToDelete);
    }

    @Override
    @Transactional
    public User updateUser(UUID targetUserId, @Valid UserDto.UpdateRequest request, User currentUser) {
        if (currentUser == null) {
            throw new UnauthorizedException("User not authenticated");
        }
        // get account
        var user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + targetUserId));

        // policies
        if (!policiesService.canUpdateUser(currentUser.getId(), targetUserId)) {
            throw new UnauthorizedException("You do not have permission to update user with ID: " + targetUserId);
        }


        // update fields
        if(request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }

        if(request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }

        if(request.getDepartment() != null) {
            user.setDepartment(request.getDepartment());
        }

        if(request.getPosition() != null) {
            user.setPosition(request.getPosition());
        }

        if(request.getHireDate() != null) {
            user.setHireDate(request.getHireDate());
        }

        if(request.getProfilePictureUrl() != null) {
            user.setProfilePictureUrl(request.getProfilePictureUrl());
        }

        boolean hasAdminOnlyChanges = request.getRole() != null || request.getIsActive() != null;
        if (hasAdminOnlyChanges && !User.Role.ADMIN.equals(currentUser.getRole())) {
            throw new UnauthorizedException("Only admins can update role or active state");
        }

        if (request.getIsActive() != null) {
            user.setIsActive(request.getIsActive());
        }

        if (request.getRole() != null) {
            try {
                user.setRole(User.Role.valueOf(request.getRole().trim().toUpperCase()));
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("Invalid role: " + request.getRole());
            }
        }

        // save & return
        return userRepository.save(user);
    }
}
