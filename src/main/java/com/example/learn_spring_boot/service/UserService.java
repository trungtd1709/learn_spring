package com.example.learn_spring_boot.service;

import com.example.learn_spring_boot.model.dto.request.UserDTO;
import com.example.learn_spring_boot.response.ApiResponse;
import org.springframework.http.ResponseEntity;

public interface UserService {
    ResponseEntity<ApiResponse<UserDTO>> createUser(UserDTO userDTO);

    Integer generateFakeUsers();

    void generateFakeUsersAsync();
}
