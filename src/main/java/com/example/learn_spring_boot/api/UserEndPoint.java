package com.example.learn_spring_boot.api;

import com.example.learn_spring_boot.model.dto.request.UserDTO;
import com.example.learn_spring_boot.model.entity.User;
import com.example.learn_spring_boot.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/user")
public interface UserEndPoint {
    @PostMapping("/create")
    ResponseEntity<ApiResponse<UserDTO>> createUser(@RequestBody UserDTO user);
}
