package com.example.learn_spring_boot.service;

import org.springframework.scheduling.annotation.Async;

public interface BackgroundService {
    public void doHeavyTask();
}
