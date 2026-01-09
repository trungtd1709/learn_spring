package com.example.learn_spring_boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(
        exclude = {
                DataSourceAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class
        }
)
@EnableCaching
@EnableAsync
public class LearnSpringBootApplication {
    private static final Object LOCK = new Object();

    public static void main(String[] args) throws InterruptedException {
        SpringApplication.run(LearnSpringBootApplication.class, args );
        System.out.println("PROJECT IS RUNNING");

        Thread threadA = new Thread(() -> {
            try {
                System.out.println("Thread A: going to sleep");
                Thread.sleep(3000); // TIMED_WAITING
                System.out.println("Thread A: woke up");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Thread-A");

        // THREAD B: giữ lock
        Thread threadB = new Thread(() -> {
            synchronized (LOCK) {
                try {
                    System.out.println("Thread B: holding lock");
                    Thread.sleep(5000); // giữ lock lâu
                    System.out.println("Thread B: releasing lock");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "Thread-B");

        // THREAD C: bị BLOCKED
        Thread threadC = new Thread(() -> {
            synchronized (LOCK) {
                System.out.println("Thread C: acquired lock");
            }
        }, "Thread-C");

        // ==== TRẠNG THÁI NEW ====
        System.out.println("Initial states:");
        printStates(threadA, threadB, threadC);

        // Start A và B
        threadA.start();
        threadB.start();

        Thread.sleep(200);

        // Start C sau để chắc chắn bị BLOCKED
        threadC.start();

        Thread.sleep(200);

        // ==== TRẠNG THÁI SAU KHI CHẠY ====
        System.out.println("\nStates after starting:");
        printStates(threadA, threadB, threadC);

        // Đợi tất cả kết thúc
        threadA.join();
        threadB.join();
        threadC.join();

        // ==== TERMINATED ====
        System.out.println("\nFinal states:");
        printStates(threadA, threadB, threadC);
    }

    private static void printStates(Thread... threads) {
        for (Thread t : threads) {
            System.out.println(t.getName() + " -> " + t.getState());
        }
    }

}
