package com.talentpredict.modules.user.mappers;

import com.talentpredict.modules.user.dto.UserDto;
import com.talentpredict.modules.user.entities.User;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;


@Component
public class UserMapperImpl implements IUserMapper {

    @Override
    public UserDto.Response toResponse(User account) {
        if (account == null) {
            return null;
        }

        return UserDto.Response.builder()
                .id(account.getId())
                .username(account.getUsername())
                .email(account.getEmail())
                .firstName(account.getFirstName())
                .lastName(account.getLastName())
                .department(account.getDepartment())
                .position(account.getPosition())
                .hireDate(account.getHireDate())
                .profilePictureUrl(account.getProfilePictureUrl())
                .isActive(account.getIsActive())
                .role(account.getRole())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }

    @Override
    public List<UserDto.Response> toResponseList(List<User> users) {
        if (users == null) {
            return null;
        }
        return users.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
}
