package com.example.learn_spring_boot.service.impl;

import com.example.learn_spring_boot.core.repository.ProductRepository;
import com.example.learn_spring_boot.core.repository.kafka.KafkaProducer;
import com.example.learn_spring_boot.model.entity.Product;
import com.example.learn_spring_boot.response.ApiResponse;
import com.example.learn_spring_boot.service.BackgroundService;
import com.example.learn_spring_boot.service.ProductService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductServiceImpl implements ProductService {
    ProductRepository productRepository;
    BackgroundService backgroundService;
    KafkaProducer kafkaProducer;

    @Override
    public void sendKafka(String message) {
        String topic = "test";
        log.info("[START sending kafka message]");
//        for (int i = 0; i < 1000; i++) {
//            kafkaProducer.send(topic, i + "_" + message);
//        }
        kafkaProducer.send(topic, message);
        log.info("[END sending kafka message]");
        backgroundService.doHeavyTask();
    }

//    @Override
//    public List<Product> findAll() {
//        return List.of();
//    }
//
//    @Override
//    public Product create(Product product) {
//        return productRepository.save(product);
//    }
}
