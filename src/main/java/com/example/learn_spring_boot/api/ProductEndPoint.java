package com.example.learn_spring_boot.api;

import com.example.learn_spring_boot.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")

public interface ProductEndPoint {

    @GetMapping("")
    String getAllProducts();

    @PostMapping("/kafka")
    ResponseEntity<ApiResponse<Object>> sendKafkaMessage(@RequestBody String message);
}
