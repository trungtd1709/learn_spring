package com.example.learn_spring_boot.config;

import com.github.javafaker.Faker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FakerConfig {

    @Bean
    public Faker faker() {
        // You can use any locale you want, here is an example for English
        return new Faker();  // This returns the fully functional Faker instance
    }
}