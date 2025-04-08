package com.example.learn_spring_boot.api.controller;

import com.example.learn_spring_boot.api.ProductEndPoint;
import com.example.learn_spring_boot.core.repository.kafka.KafkaProducer;
import com.example.learn_spring_boot.response.ApiResponse;
import com.example.learn_spring_boot.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ProductController implements ProductEndPoint {
    private final KafkaProducer kafkaProducer;
    private final ProductService productService;

    @Override
    public String getAllProducts() {
        return "Get all products";
    }

    @Override
    public ResponseEntity<ApiResponse<Object>> sendKafkaMessage(String message) {
        productService.sendKafka(message);
        ApiResponse<Object> response = new ApiResponse<>();
        return ResponseEntity.ok(response);
    }
}
