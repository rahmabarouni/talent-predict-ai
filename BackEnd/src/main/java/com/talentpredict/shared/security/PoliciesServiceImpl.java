package com.talentpredict.shared.security;

import com.talentpredict.modules.user.entities.User;
import com.talentpredict.modules.user.repositories.UserRepository;
import com.talentpredict.shared.security.interfaces.IPoliciesService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;


@Component
@RequiredArgsConstructor

public class PoliciesServiceImpl implements IPoliciesService {

    private final UserRepository userRepository;


    @Override
    public boolean canViewUser(UUID authUserId, UUID targetUserId) {
        return isAdminOrOwnsAccount(authUserId, targetUserId);
    }

    @Override
    public boolean canUpdateUser(UUID authUserId, UUID targetUserId) {
        return isAdminOrOwnsAccount(authUserId, targetUserId);
    }

    @Override
    public boolean canDeleteUser(UUID authUserId, UUID targetUserId) {
        return isAdminOrOwnsAccount(authUserId, targetUserId);
    }


    // private helpers
    private boolean isAdminOrOwnsAccount(UUID authUserId, UUID targetUserId) {
        var authUser = userRepository.findById(authUserId).orElse(null);
        if (authUser == null) {
            return false;
        }
        var targetUser = userRepository.findById(targetUserId).orElse(null);
        if (targetUser == null) {
            return false;
        }

        if (authUser.getRole().equals(User.Role.ADMIN)) {
            return true;
        }

        return authUserId.equals(targetUserId);
    }
}
