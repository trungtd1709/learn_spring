package com.example.learn_spring_boot.service;

import com.example.learn_spring_boot.model.entity.Product;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;

import java.util.List;

//public interface ProductService {
//    List<Product> findAll();
//
//    Product create(Product product);
//}

public interface ProductService {

    // Cache the result of this method
//    @Cacheable(value = "products", key = "#id")
//    public String getProductById(String id) {
//        // Simulating a slow database query
//        try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
//        return "Product: " + id;
//    }
//
//    // Update cache
//    @CachePut(value = "products", key = "#id")
//    public String updateProduct(String id, String updatedValue) {
//        return updatedValue;
//    }
//
//    // Remove from cache
//    @CacheEvict(value = "products", key = "#id")
//    public void deleteProduct(String id) {
//        System.out.println("Removed from cache: " + id);
//    }
    public void sendKafka(String message);
}