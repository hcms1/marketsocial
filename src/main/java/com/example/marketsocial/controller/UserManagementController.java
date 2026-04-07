package com.example.marketsocial.controller;

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
@RequestMapping("/api/users")
public class UserManagementController {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final PostRepository postRepository;
    private final MessageRepository messageRepository;
    private final PasswordEncoder passwordEncoder;

    public UserManagementController(
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

    @GetMapping("/manage")
    public List<ManagedUserResponse> managedUsers(Authentication authentication) {
        User currentUser = requireUser(authentication);
        ensureAdmin(currentUser);

        return userRepository.findAll().stream()
                .sorted((left, right) -> left.getUsername().compareToIgnoreCase(right.getUsername()))
                .map(user -> ManagedUserResponse.from(user, currentUser.getId().equals(user.getId())))
                .toList();
    }

    @PostMapping
    public ResponseEntity<AuthController.AuthUserResponse> createUser(
            @RequestBody CreateUserRequest request,
            Authentication authentication
    ) {
        User currentUser = requireUser(authentication);
        ensureAdmin(currentUser);

        String username = normalizeUsername(request.username());
        String password = validatePassword(request.password());
        String displayName = normalizeBoundedText(request.displayName(), 120, "Display name is required");
        String role = normalizeManagedRole(request.role());
        String city = normalizeOptionalText(request.city(), 120);
        String bio = normalizeOptionalText(request.bio(), 1200);
        String email = normalizeOptionalEmail(request.email());
        boolean emailNotificationsEnabled = email != null && Boolean.TRUE.equals(request.emailNotificationsEnabled());

        if (username.length() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username must be at least 3 characters");
        }

        if (userRepository.findByUsername(username).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        ensureEmailAvailable(email, null);

        User createdUser = userRepository.save(new User(
                null,
                username,
                passwordEncoder.encode(password),
                role,
                displayName,
                bio,
                city,
                email,
                emailNotificationsEnabled
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(AuthController.AuthUserResponse.from(createdUser));
    }

    @PutMapping("/{id}")
    public ManagedUserResponse updateUser(
            @PathVariable Long id,
            @RequestBody UpdateUserRequest request,
            Authentication authentication
    ) {
        User currentUser = requireUser(authentication);
        ensureAdmin(currentUser);

        User managedUser = findUser(id);
        String updatedRole = normalizeManagedRole(request.role());
        ensureAdminAccountRemains(managedUser, updatedRole);

        managedUser.setDisplayName(normalizeBoundedText(request.displayName(), 120, "Display name is required"));
        managedUser.setCity(normalizeOptionalText(request.city(), 120));
        managedUser.setBio(normalizeOptionalText(request.bio(), 1200));
        String email = normalizeOptionalEmail(request.email());
        ensureEmailAvailable(email, managedUser.getId());
        managedUser.setEmail(email);
        managedUser.setEmailNotificationsEnabled(managedUser.getEmail() != null && Boolean.TRUE.equals(request.emailNotificationsEnabled()));
        managedUser.setRole(updatedRole);

        return ManagedUserResponse.from(
                userRepository.save(managedUser),
                currentUser.getId().equals(managedUser.getId())
        );
    }

    @PostMapping("/{id}/password")
    public ResponseEntity<Void> resetPassword(
            @PathVariable Long id,
            @RequestBody ResetPasswordRequest request,
            Authentication authentication
    ) {
        User currentUser = requireUser(authentication);
        ensureAdmin(currentUser);

        User managedUser = findUser(id);
        managedUser.setPassword(passwordEncoder.encode(validatePassword(request.newPassword())));
        userRepository.save(managedUser);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deleteUser(@PathVariable Long id, Authentication authentication) {
        User currentUser = requireUser(authentication);
        ensureAdmin(currentUser);

        User managedUser = findUser(id);
        if (currentUser.getId().equals(managedUser.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use the account danger zone to delete your own account");
        }
        ensureAdminAccountRemains(managedUser, User.ROLE_USER);

        deleteUserData(managedUser);
        userRepository.delete(managedUser);
        return ResponseEntity.noContent().build();
    }

    private User requireUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private void ensureAdmin(User currentUser) {
        if (!User.ROLE_ADMIN.equals(currentUser.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can manage users");
        }
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private String normalizeUsername(String value) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required");
        }
        return value.trim().toLowerCase();
    }

    private String normalizeManagedRole(String value) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role is required");
        }

        return switch (value.trim().toUpperCase()) {
            case User.ROLE_USER, "BROWSER", "BUYER" -> User.ROLE_USER;
            case User.ROLE_SELLER -> User.ROLE_SELLER;
            case User.ROLE_ADMIN -> User.ROLE_ADMIN;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role must be USER, SELLER, or ADMIN");
        };
    }

    private String normalizeBoundedText(String value, int maxLength, String requiredMessage) {
        String normalized = normalizeOptionalText(value, maxLength);
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, requiredMessage);
        }
        return normalized;
    }

    private String normalizeOptionalText(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Value is too long");
        }
        return normalized;
    }

    private String validatePassword(String value) {
        if (value == null || value.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters");
        }
        return value;
    }

    private String normalizeOptionalEmail(String value) {
        String normalized = normalizeOptionalText(value, 255).toLowerCase();
        if (normalized.isEmpty()) {
            return null;
        }
        if (!normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email address must be valid");
        }
        return normalized;
    }

    private void ensureEmailAvailable(String email, Long currentUserId) {
        if (email == null) {
            return;
        }
        userRepository.findByEmail(email)
                .filter(existing -> currentUserId == null || !existing.getId().equals(currentUserId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Email address already exists");
                });
    }

    private void ensureAdminAccountRemains(User managedUser, String updatedRole) {
        if (!User.ROLE_ADMIN.equals(managedUser.getRole())) {
            return;
        }
        if (User.ROLE_ADMIN.equals(updatedRole)) {
            return;
        }
        if (userRepository.countByRole(User.ROLE_ADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot remove the last admin account");
        }
    }

    private void deleteUserData(User user) {
        messageRepository.deleteBySenderOrReceiver(user, user);
        postRepository.deleteByAuthor(user);
        productRepository.deleteBySeller(user);
    }

    public record CreateUserRequest(
            String username,
            String password,
            String displayName,
            String role,
            String city,
            String bio,
            String email,
            Boolean emailNotificationsEnabled
    ) {
    }

    public record UpdateUserRequest(
            String displayName,
            String role,
            String city,
            String bio,
            String email,
            Boolean emailNotificationsEnabled
    ) {
    }

    public record ResetPasswordRequest(String newPassword) {
    }

    public record ManagedUserResponse(
            Long id,
            String username,
            String role,
            String displayName,
            String bio,
            String city,
            String email,
            boolean emailNotificationsEnabled,
            boolean currentUser
    ) {
        public static ManagedUserResponse from(User user, boolean currentUser) {
            return new ManagedUserResponse(
                    user.getId(),
                    user.getUsername(),
                    user.getRole(),
                    user.getDisplayName(),
                    user.getBio(),
                    user.getCity(),
                    user.getEmail(),
                    user.isEmailNotificationsEnabled(),
                    currentUser
            );
        }
    }
}
