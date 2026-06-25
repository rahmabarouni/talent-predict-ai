package com.talentpredict.modules.user.mappers;

import com.talentpredict.modules.user.dto.UserDto;
import com.talentpredict.modules.user.entities.User;

import java.util.List;

public interface IUserMapper {
    UserDto.Response toResponse(User account);
    List<UserDto.Response> toResponseList(List<User> users);
}
