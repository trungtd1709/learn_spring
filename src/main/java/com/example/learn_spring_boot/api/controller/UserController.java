package com.example.learn_spring_boot.api.controller;

import com.example.learn_spring_boot.api.UserEndPoint;
import com.example.learn_spring_boot.core.repository.UserRepository;
import com.example.learn_spring_boot.model.dto.request.UserDTO;
import com.example.learn_spring_boot.response.ApiResponse;
import com.example.learn_spring_boot.service.UserService;
import com.example.learn_spring_boot.utils.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController implements UserEndPoint {
    private final UserMapper userMapper;
    private final UserService userService;

//    @Override
//    public ResponseEntity<ApiResponse<UserDTO>> createUser(UserDTO userDTO) {
//        return userService.createUser(userDTO);
//    }
//
//    @Override
//    public ResponseEntity<ApiResponse<Integer>> generateFakeUsers() {
//        ApiResponse<Integer> response = new ApiResponse<Integer>();
//        response.setData(0);
//        return ResponseEntity.ok(response);
//    }
}
