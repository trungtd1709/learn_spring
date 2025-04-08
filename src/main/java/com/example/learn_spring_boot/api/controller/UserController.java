package com.example.learn_spring_boot.api.controller;

import com.example.learn_spring_boot.api.UserEndPoint;
import com.example.learn_spring_boot.core.repository.UserRepository;
import com.example.learn_spring_boot.model.dto.request.UserDTO;
import com.example.learn_spring_boot.model.entity.User;
import com.example.learn_spring_boot.response.ApiResponse;
import com.example.learn_spring_boot.utils.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController implements UserEndPoint {
    private final UserMapper userMapper;
    private final UserRepository userRepository;

    @Override
    public ResponseEntity<ApiResponse<UserDTO>> createUser(UserDTO userDTO) {
        User newUser = userMapper.userDTOToUser(userDTO);
        userRepository.save(newUser);
        ApiResponse<UserDTO> response = new ApiResponse<UserDTO>();
        response.setData(userMapper.userToUserDTO(newUser));
        return ResponseEntity.ok(response);
    }
}
