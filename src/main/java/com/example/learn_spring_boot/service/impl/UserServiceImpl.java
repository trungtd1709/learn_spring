package com.example.learn_spring_boot.service.impl;

import com.example.learn_spring_boot.core.repository.UserRepository;
import com.example.learn_spring_boot.model.dto.request.UserDTO;
import com.example.learn_spring_boot.model.entity.User;
import com.example.learn_spring_boot.response.ApiResponse;
import com.example.learn_spring_boot.service.UserService;
import com.example.learn_spring_boot.utils.mapper.UserMapper;
import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

//@RequiredArgsConstructor
@Slf4j
@Service
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final UserRepository userRepository;
    private final Faker faker;

    public UserServiceImpl(UserMapper userMapper, UserRepository userRepository) {
        this.userMapper = userMapper;
        this.userRepository = userRepository;
        this.faker = new Faker();
    }

    @Override
    public ResponseEntity<ApiResponse<UserDTO>> createUser(UserDTO userDTO) {
        User newUser = userMapper.userDTOToUser(userDTO);
        userRepository.save(newUser);
        ApiResponse<UserDTO> response = new ApiResponse<UserDTO>();
        response.setData(userMapper.userToUserDTO(newUser));
        return ResponseEntity.ok(response);
    }

    @Override
    public Integer generateFakeUsers() {
        log.info("Start generating fake users");

        List<User> users = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            User user = User.builder()
                    .username(faker.name().username())
                    .password(faker.internet().password())
                    .firstName(faker.name().firstName())
                    .lastName(faker.name().lastName())
                    .email(faker.internet().emailAddress())
                    .phoneNumber(faker.phoneNumber().phoneNumber())
                    .address(faker.address().streetAddress())
                    .city(faker.address().city())
                    .country(faker.address().country())
                    .build();
            users.add(user);
//            userRepository.save(user);
            log.info("User number {} created", i);
        }
        userRepository.saveAll(users);
        log.info("End generating fake users");
        return users.size();
    }
}
