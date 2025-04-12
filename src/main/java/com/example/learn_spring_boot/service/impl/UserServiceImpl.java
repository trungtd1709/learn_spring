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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Slf4j
@Service
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final UserRepository userRepository;
    private final Faker faker;

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
        int numOfUsers = 0;

        List<User> users = new ArrayList<>();
        String threadName = Thread.currentThread().getName();
        for (int i = 0; i < 2000000; i++) {
            User user = User.builder()
                    .username(faker.name().username() + " " + threadName)
                    .password(faker.internet().password())
                    .firstName(faker.name().firstName())
                    .lastName(faker.name().lastName())
                    .email(faker.internet().emailAddress())
                    .phoneNumber(faker.phoneNumber().phoneNumber())
                    .address(faker.address().streetAddress())
                    .city(faker.address().city())
                    .country(faker.address().country())
                    .birthday(faker.date().birthday())
                    .build();
            users.add(user);

            if (users.size() == 50) { // Batch size of 50, you can adjust it for better performance
                userRepository.saveAll(users);  // Perform batch insert
                numOfUsers+=users.size();
                users.clear();  // Clear the list to start adding the next batch
                log.info("Inserted 50 users into database: {}", numOfUsers);
            }
        }

        if (!users.isEmpty()) {
            userRepository.saveAll(users);  // Save remaining users if they exist
            log.info("Inserted remaining users");
        }

        log.info("End generating fake users");
        return numOfUsers;
    }

    @Async
    public void generateFakeUsersAsync (){
        this.generateFakeUsers();
    }
}
