package com.example.marketsocial.controller;

import com.example.marketsocial.model.User;
import com.example.marketsocial.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthUserResponse> register(@RequestBody RegisterRequest request) {
        boolean firstAccount = userRepository.count() == 0;
        RegistrationInput input = validateRegistration(request, firstAccount);
        String role = firstAccount ? User.ROLE_ADMIN : input.accountType();

        if (userRepository.findByUsername(input.username()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        if (input.email() != null && userRepository.findByEmail(input.email()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email address already exists");
        }

        User user = userRepository.save(new User(
                null,
                input.username(),
                passwordEncoder.encode(input.password()),
                role,
                input.username(),
                "",
                "",
                input.email(),
                input.emailNotificationsEnabled()
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthUserResponse.from(user));
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        User user = requireUser(authentication);
        return Map.of(
                "authenticated", true,
                "user", AuthUserResponse.from(user)
        );
    }

    private User requireUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase();
    }

    private String normalizeAccountType(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.trim().toUpperCase();
        return switch (normalized) {
            case User.ROLE_USER, "BROWSER", "BUYER" -> User.ROLE_USER;
            case User.ROLE_SELLER -> User.ROLE_SELLER;
            default -> "";
        };
    }

    private RegistrationInput validateRegistration(RegisterRequest request, boolean firstAccount) {
        List<String> details = new ArrayList<>();
        String username = normalize(request.username());
        String password = request.password();
        String accountType = firstAccount ? User.ROLE_ADMIN : normalizeAccountType(request.accountType());
        String email = normalizeEmail(request.email(), details);
        boolean emailNotificationsEnabled = email != null && Boolean.TRUE.equals(request.emailNotificationsEnabled());

        if (username.isBlank()) {
            details.add("Username is required.");
        } else {
            if (username.length() < 3) {
                details.add("Username must be at least 3 characters.");
            }
            if (!username.matches("[a-z0-9._-]+")) {
                details.add("Username can only contain letters, numbers, dots, underscores, and hyphens.");
            }
        }

        if (password == null || password.isBlank()) {
            details.add("Password is required.");
        } else if (password.length() < 8) {
            details.add("Password must be at least 8 characters.");
        }

        if (!firstAccount && accountType.isBlank()) {
            details.add("Account type must be Browser / Buyer or Seller.");
        }

        if (!details.isEmpty()) {
            throw new ApiValidationException(HttpStatus.BAD_REQUEST, details);
        }

        return new RegistrationInput(username, password, accountType, email, emailNotificationsEnabled);
    }

    private String normalizeEmail(String value, List<String> details) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String email = value.trim().toLowerCase();
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            details.add("Email address must be valid.");
            return null;
        }
        return email;
    }

    public record RegisterRequest(String username, String password, String accountType, String email, Boolean emailNotificationsEnabled) {
    }

    private record RegistrationInput(
            String username,
            String password,
            String accountType,
            String email,
            boolean emailNotificationsEnabled
    ) {
    }

    public record AuthUserResponse(
            Long id,
            String username,
            String role,
            String displayName,
            String bio,
            String city,
            String email,
            boolean emailNotificationsEnabled
    ) {
        public static AuthUserResponse from(User user) {
            return new AuthUserResponse(
                    user.getId(),
                    user.getUsername(),
                    user.getRole(),
                    user.getDisplayName(),
                    user.getBio(),
                    user.getCity(),
                    user.getEmail(),
                    user.isEmailNotificationsEnabled()
            );
        }
    }
}
