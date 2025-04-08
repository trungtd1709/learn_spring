package com.example.learn_spring_boot.controller;

import com.example.learn_spring_boot.model.dto.RedisRequest;
import com.example.learn_spring_boot.service.RedisService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/redis")
public class RedisController {

    private final RedisService redisService;

    public RedisController(RedisService redisService) {
        this.redisService = redisService;
    }

    @PostMapping("/save")
    public String saveToRedis(@RequestBody RedisRequest request) {
        redisService.saveData(request.getKey(), request.getValue(), request.getTimeout());
        return "Data saved in Redis!";
    }

    @GetMapping("/get")
    public String getFromRedis(@RequestParam String key) {
        return redisService.getData(key);
    }

    @DeleteMapping("/delete")
    public String deleteFromRedis(@RequestParam String key) {
        redisService.deleteData(key);
        return "Data deleted from Redis!";
    }
}
