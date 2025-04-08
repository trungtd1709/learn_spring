package com.example.learn_spring_boot.service.impl;

import com.example.learn_spring_boot.service.BackgroundService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class BackgroundServiceImpl implements BackgroundService {
    @Override
    @Async
    public void doHeavyTask() {
        System.out.println("Thread name: " + Thread.currentThread().getName());
        try {
            Thread.sleep(5000); // Mô phỏng tác vụ nặng
            System.out.println("Tác vụ nặng đã hoàn thành");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
