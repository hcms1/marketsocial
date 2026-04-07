package com.example.marketsocial.controller;

import com.example.marketsocial.model.Product;
import com.example.marketsocial.model.User;
import com.example.marketsocial.repository.MessageRepository;
import com.example.marketsocial.repository.PostRepository;
import com.example.marketsocial.repository.ProductRepository;
import com.example.marketsocial.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
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

import java.util.List;

@RestController
@RequestMapping("/api/profiles")
public class ProfileController {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final PostRepository postRepository;
    private final MessageRepository messageRepository;
    private final PasswordEncoder passwordEncoder;

    public ProfileController(
            UserRepository userRepository,
            ProductRepository productRepository,
            PostRepository postRepository,
            MessageRepository messageRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.postRepository = postRepository;
        this.messageRepository = messageRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/me")
    @Transactional(readOnly = true)
    public ProfileResponse me(Authentication authentication) {
        User user = requireUser(authentication);
        return ProfileResponse.from(user, productRepository.findBySellerOrderByCreatedAtDesc(user));
    }

    @PutMapping("/me")
    public ProfileResponse updateProfile(@RequestBody UpdateProfileRequest request, Authentication authentication) {
        User user = requireUser(authentication);
        user.setDisplayName(requiredDisplayName(request.displayName()));
        user.setBio(normalize(request.bio(), 1200));
        user.setCity(normalize(request.city(), 120));
        String email = normalizeEmail(request.email());
        ensureEmailAvailable(email, user.getId());
        user.setEmail(email);
        user.setEmailNotificationsEnabled(user.getEmail() != null && Boolean.TRUE.equals(request.emailNotificationsEnabled()));
        user.setRole(resolveUpdatedRole(user, request.accountType()));
        return ProfileResponse.from(userRepository.save(user), productRepository.findBySellerOrderByCreatedAtDesc(user));
    }

    @PostMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @RequestBody ChangePasswordRequest request,
            Authentication authentication
    ) {
        User user = requireUser(authentication);

        if (request.currentPassword() == null || request.currentPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is required");
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }

        String nextPassword = validatePassword(request.newPassword());
        user.setPassword(passwordEncoder.encode(nextPassword));
        userRepository.save(user);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    @Transactional
    public ResponseEntity<Void> deleteAccount(
            @RequestBody DeleteAccountRequest request,
            Authentication authentication
    ) {
        User user = requireUser(authentication);

        if (request.password() == null || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required to delete your account");
        }
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is incorrect");
        }
        ensureAdminAccountRemains(user, User.ROLE_USER);

        deleteUserData(user);
        userRepository.delete(user);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{username}")
    @Transactional(readOnly = true)
    public ProfileResponse profile(@PathVariable String username) {
        User user = userRepository.findByUsername(username.trim().toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
        List<Product> products = productRepository.findBySellerOrderByCreatedAtDesc(user);
        return ProfileResponse.from(user, products);
    }

    private User requireUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private String requiredDisplayName(String value) {
        String displayName = normalize(value, 120);
        if (displayName.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Display name is required");
        }
        return displayName;
    }

    private String normalize(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Value is too long");
        }
        return normalized;
    }

    private String resolveUpdatedRole(User user, String value) {
        if (User.ROLE_ADMIN.equals(user.getRole())) {
            return user.getRole();
        }
        if (value == null || value.isBlank()) {
            return user.getRole();
        }

        String normalized = value.trim().toUpperCase();
        return switch (normalized) {
            case User.ROLE_USER, "BROWSER", "BUYER" -> User.ROLE_USER;
            case User.ROLE_SELLER -> User.ROLE_SELLER;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account type must be USER or SELLER");
        };
    }

    private String validatePassword(String value) {
        if (value == null || value.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters");
        }
        return value;
    }

    private String normalizeEmail(String value) {
        String normalized = normalize(value, 255).toLowerCase();
        if (normalized.isEmpty()) {
            return null;
        }
        if (!normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email address must be valid");
        }
        return normalized;
    }

    private void ensureAdminAccountRemains(User user, String updatedRole) {
        if (!User.ROLE_ADMIN.equals(user.getRole())) {
            return;
        }
        if (User.ROLE_ADMIN.equals(updatedRole)) {
            return;
        }
        if (userRepository.countByRole(User.ROLE_ADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot remove the last admin account");
        }
    }

    private void ensureEmailAvailable(String email, Long currentUserId) {
        if (email == null) {
            return;
        }
        userRepository.findByEmail(email)
                .filter(existing -> !existing.getId().equals(currentUserId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Email address already exists");
                });
    }

    private void deleteUserData(User user) {
        messageRepository.deleteBySenderOrReceiver(user, user);
        postRepository.deleteByAuthor(user);
        productRepository.deleteBySeller(user);
    }

    public record UpdateProfileRequest(
            String displayName,
            String bio,
            String city,
            String accountType,
            String email,
            Boolean emailNotificationsEnabled
    ) {
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword) {
    }

    public record DeleteAccountRequest(String password) {
    }

    public record ProfileResponse(
            Long id,
            String username,
            String displayName,
            String bio,
            String city,
            String role,
            String email,
            boolean emailNotificationsEnabled,
            int productCount,
            List<ProductController.ProductResponse> products
    ) {
        public static ProfileResponse from(User user, List<Product> products) {
            List<ProductController.ProductResponse> productResponses = products.stream()
                    .map(ProductController.ProductResponse::from)
                    .toList();

            return new ProfileResponse(
                    user.getId(),
                    user.getUsername(),
                    user.getDisplayName(),
                    user.getBio(),
                    user.getCity(),
                    user.getRole(),
                    user.getEmail(),
                    user.isEmailNotificationsEnabled(),
                    productResponses.size(),
                    productResponses
            );
        }
    }
}
