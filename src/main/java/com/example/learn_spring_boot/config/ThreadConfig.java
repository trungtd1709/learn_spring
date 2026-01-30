package com.example.learn_spring_boot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Cấu hình Thread Pool cho async processing
 * 
 * @EnableAsync: Kích hoạt xử lý bất đồng bộ với @Async annotation
 * Khi method có @Async được gọi, Spring sẽ chạy method đó trên thread riêng
 */
@Configuration
@EnableAsync
public class ThreadConfig {
    
    /**
     * Thread pool mặc định cho các tác vụ background
     */
    @Bean
    public ThreadPoolTaskExecutor threadPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);  // minimum number of threads
        executor.setMaxPoolSize(50);   // maximum number of threads
        executor.setQueueCapacity(100);  // the capacity of the queue for waiting tasks
        executor.setThreadNamePrefix("sub-thread-");  // optional: to prefix thread names
        executor.initialize();
        return executor;
    }
}
