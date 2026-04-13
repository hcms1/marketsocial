package com.example.marketsocial.controller;

import com.example.marketsocial.model.Order;
import com.example.marketsocial.model.User;
import com.example.marketsocial.repository.OrderRepository;
import com.example.marketsocial.repository.ProductRepository;
import com.example.marketsocial.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public OrderController(
            OrderRepository orderRepository,
            ProductRepository productRepository,
            UserRepository userRepository
    ) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/mine")
    @Transactional(readOnly = true)
    public List<OrderResponse> myOrders(Authentication authentication) {
        User user = requireUser(authentication);
        List<Order> orders = orderRepository.findByBuyerOrderByCreatedAtDesc(user);
        return orders.stream().map(OrderResponse::from).toList();
    }

    @GetMapping("/sold")
    @Transactional(readOnly = true)
    public List<OrderResponse> mySoldOrders(Authentication authentication) {
        User user = requireUser(authentication);
        List<Order> orders = orderRepository.findBySellerOrderByCreatedAtDesc(user);
        return orders.stream().map(OrderResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestBody CreateOrderRequest request,
            Authentication authentication
    ) {
        User buyer = requireUser(authentication);
        User seller = userRepository.findById(request.sellerId())
                .orElseThrow(() -> new RuntimeException("Seller not found"));

        List<Long> productIds = request.productIds() == null ? List.of() : request.productIds();
        if (productIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one product is required");
        }

        double totalAmount = productIds.stream()
                .map(productRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .mapToDouble(com.example.marketsocial.model.Product::getPrice)
                .sum();

        if (totalAmount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid products selected");
        }

        Order order = new Order(
                null,
                buyer,
                seller,
                productIds,
                totalAmount,
                Order.OrderStatus.PENDING,
                LocalDateTime.now(),
                null
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OrderResponse.from(orderRepository.save(order)));
    }

    @PutMapping("/{id}/confirm")
    public OrderResponse confirmOrder(
            @PathVariable Long id,
            Authentication authentication
    ) {
        User user = requireUser(authentication);
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        if (!order.getSeller().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only seller can confirm order");
        }

        order.setStatus(Order.OrderStatus.CONFIRMED);
        return OrderResponse.from(orderRepository.save(order));
    }

    @PutMapping("/{id}/ship")
    public OrderResponse shipOrder(
            @PathVariable Long id,
            Authentication authentication
    ) {
        User user = requireUser(authentication);
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        if (!order.getSeller().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only seller can mark as shipped");
        }

        order.setStatus(Order.OrderStatus.SHIPPED);
        return OrderResponse.from(orderRepository.save(order));
    }

    @PutMapping("/{id}/deliver")
    public OrderResponse deliverOrder(
            @PathVariable Long id,
            Authentication authentication
    ) {
        User user = requireUser(authentication);
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        if (!order.getBuyer().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only buyer can mark as delivered");
        }

        order.setStatus(Order.OrderStatus.DELIVERED);
        order.setCompletedAt(LocalDateTime.now());
        return OrderResponse.from(orderRepository.save(order));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelOrder(
            @PathVariable Long id,
            Authentication authentication
    ) {
        User user = requireUser(authentication);
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        if (!order.getBuyer().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only buyer can cancel order");
        }

        if (order.getStatus() == Order.OrderStatus.DELIVERED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot cancel delivered order");
        }

        order.setStatus(Order.OrderStatus.CANCELLED);
        orderRepository.save(order);
        return ResponseEntity.noContent().build();
    }

    private User requireUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    public record CreateOrderRequest(Long sellerId, List<Long> productIds) {
    }

    public record OrderResponse(
            Long id,
            Long buyerId,
            String buyerUsername,
            Long sellerId,
            String sellerUsername,
            List<Long> productIds,
            Double totalAmount,
            Order.OrderStatus status,
            LocalDateTime createdAt,
            LocalDateTime completedAt
    ) {
        public static OrderResponse from(Order order) {
            return new OrderResponse(
                    order.getId(),
                    order.getBuyer().getId(),
                    order.getBuyer().getUsername(),
                    order.getSeller().getId(),
                    order.getSeller().getUsername(),
                    order.getProductIds(),
                    order.getTotalAmount(),
                    order.getStatus(),
                    order.getCreatedAt(),
                    order.getCompletedAt()
            );
        }
    }
}
