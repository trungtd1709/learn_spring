package com.example.learn_spring_boot.utils.mapper;

import com.example.learn_spring_boot.model.dto.request.UserDTO;
import com.example.learn_spring_boot.model.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserDTO userToUserDTO(User user);

    User userDTOToUser(UserDTO userDTO);
}
