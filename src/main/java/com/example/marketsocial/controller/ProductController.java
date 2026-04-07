package com.example.marketsocial.controller;

import com.example.marketsocial.model.Product;
import com.example.marketsocial.model.User;
import com.example.marketsocial.repository.ProductRepository;
import com.example.marketsocial.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public ProductController(ProductRepository productRepository, UserRepository userRepository) {
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<ProductResponse> allProducts() {
        return productRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(ProductResponse::from)
                .toList();
    }

    @GetMapping("/mine")
    @Transactional(readOnly = true)
    public List<ProductResponse> myProducts(Authentication authentication) {
        User user = requireUser(authentication);
        return productRepository.findBySellerOrderByCreatedAtDesc(user).stream()
                .map(ProductResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ProductResponse product(@PathVariable Long id) {
        return productRepository.findById(id)
                .map(ProductResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @RequestBody ProductRequest request,
            Authentication authentication
    ) {
        User seller = requireUser(authentication);
        ensureSeller(seller);
        Product product = new Product(
                null,
                seller,
                requireText(request.title(), "Title is required"),
                normalizeOptionalText(request.description()),
                requirePrice(request.price()),
                normalizeOptionalText(request.category()),
                LocalDateTime.now(),
                normalizeImages(request.images())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductResponse.from(productRepository.save(product)));
    }

    @PutMapping("/{id}")
    public ProductResponse updateProduct(
            @PathVariable Long id,
            @RequestBody ProductRequest request,
            Authentication authentication
    ) {
        User seller = requireUser(authentication);
        ensureSeller(seller);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        ensureOwner(product, seller);
        product.setTitle(requireText(request.title(), "Title is required"));
        product.setDescription(normalizeOptionalText(request.description()));
        product.setPrice(requirePrice(request.price()));
        product.setCategory(normalizeOptionalText(request.category()));
        product.setImages(normalizeImages(request.images()));
        return ProductResponse.from(productRepository.save(product));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id, Authentication authentication) {
        User seller = requireUser(authentication);
        ensureSeller(seller);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        ensureOwner(product, seller);
        productRepository.delete(product);
        return ResponseEntity.noContent().build();
    }

    private User requireUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private void ensureOwner(Product product, User seller) {
        if (!product.getSeller().getId().equals(seller.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only manage your own products");
        }
    }

    private void ensureSeller(User user) {
        if (!user.canSell()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Switch your account to seller before publishing listings");
        }
    }

    private String requireText(String value, String message) {
        String normalized = normalizeOptionalText(value);
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private String normalizeOptionalText(String value) {
        return value == null ? "" : value.trim();
    }

    private Double requirePrice(Double price) {
        if (price == null || price <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price must be greater than zero");
        }
        return price;
    }

    private List<String> normalizeImages(List<String> images) {
        if (images == null) {
            return List.of();
        }

        return images.stream()
                .map(this::normalizeOptionalText)
                .filter(value -> !value.isEmpty())
                .limit(6)
                .toList();
    }

    public record ProductRequest(
            String title,
            String description,
            Double price,
            String category,
            List<String> images
    ) {
    }

    public record ProductResponse(
            Long id,
            String title,
            String description,
            Double price,
            String category,
            List<String> images,
            String sellerUsername,
            String sellerDisplayName,
            LocalDateTime createdAt
    ) {
        public static ProductResponse from(Product product) {
            return new ProductResponse(
                    product.getId(),
                    product.getTitle(),
                    product.getDescription(),
                    product.getPrice(),
                    product.getCategory(),
                    List.copyOf(product.getImages() == null ? List.of() : product.getImages()),
                    product.getSeller().getUsername(),
                    product.getSeller().getDisplayName(),
                    product.getCreatedAt()
            );
        }
    }
}
