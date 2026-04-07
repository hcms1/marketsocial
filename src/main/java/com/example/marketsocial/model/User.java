package com.example.marketsocial.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "APP_USER") // safe table name
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    public static final String ROLE_USER = "USER";
    public static final String ROLE_SELLER = "SELLER";
    public static final String ROLE_ADMIN = "ADMIN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role; // USER, SELLER, or ADMIN

    @Column(nullable = false)
    private String displayName;

    @Column(length = 1200)
    private String bio;

    private String city;

    @Column(unique = true)
    private String email;

    @Column(nullable = false)
    private boolean emailNotificationsEnabled;

    public boolean canSell() {
        return ROLE_SELLER.equals(role) || ROLE_ADMIN.equals(role);
    }

    public boolean canReceiveEmailNotifications() {
        return email != null && !email.isBlank() && emailNotificationsEnabled;
    }
}
