package com.example.learn_spring_boot.core.repository;

import com.example.learn_spring_boot.model.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
