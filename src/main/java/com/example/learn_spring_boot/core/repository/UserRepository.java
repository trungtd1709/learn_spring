package com.example.learn_spring_boot.core.repository;

import com.example.learn_spring_boot.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

}
