package com.talentpredict.shared.security.interfaces;

import java.util.UUID;

public interface IPoliciesService {
    // get
    boolean canViewUser(UUID authUserId, UUID targetUserId);

    // update
    boolean canUpdateUser(UUID authUserId, UUID targetUserId);

    boolean canDeleteUser(UUID authUserId, UUID targetUserId);
}
