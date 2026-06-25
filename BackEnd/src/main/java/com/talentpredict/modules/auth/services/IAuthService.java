package com.talentpredict.modules.auth.services;

import com.talentpredict.modules.user.entities.User;
import com.talentpredict.modules.auth.dto.AuthDto;

import java.util.UUID;

public interface IAuthService {
    // Write
    User createUser(AuthDto.RegisterRequest request);

    // Read
    User getUserById(UUID targetUserId);

    User getUserByEmail(String email);
}
