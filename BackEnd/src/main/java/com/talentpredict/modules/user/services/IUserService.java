package com.talentpredict.modules.user.services;

import com.talentpredict.modules.user.dto.UserDto;
import com.talentpredict.modules.user.entities.User;

import java.util.List;
import java.util.UUID;


public interface IUserService {
    // read
    List<User> listUsers();
    User getUserById(UUID targetUserId, User currentUser);
    List<UserDto.LeaderboardResponse> getLeaderboard();


    // write
    User updateUser(UUID targetUserId, UserDto.UpdateRequest request, User currentUser);
    void deleteUser(UUID targetUserId, User currentUser);
}
