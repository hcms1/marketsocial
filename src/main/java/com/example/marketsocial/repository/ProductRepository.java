package com.example.marketsocial.repository;

import com.example.marketsocial.model.Product;
import com.example.marketsocial.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @EntityGraph(attributePaths = {"seller", "images"})
    List<Product> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"seller", "images"})
    List<Product> findBySellerOrderByCreatedAtDesc(User seller);

    @Override
    @EntityGraph(attributePaths = {"seller", "images"})
    java.util.Optional<Product> findById(Long id);

    void deleteBySeller(User seller);
}
