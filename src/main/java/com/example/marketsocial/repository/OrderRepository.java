package com.example.marketsocial.repository;

import com.example.marketsocial.model.Order;
import com.example.marketsocial.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByBuyerOrderByCreatedAtDesc(User buyer);
    List<Order> findBySellerOrderByCreatedAtDesc(User seller);
    List<Order> findByStatusOrderByCreatedAtDesc(Order.OrderStatus status);
}
