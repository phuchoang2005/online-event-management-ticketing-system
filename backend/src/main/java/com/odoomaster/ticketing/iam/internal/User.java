package com.odoomaster.ticketing.iam.internal;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.Locale;
import java.util.Objects;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JPA entity mapping the persistence row for a user.
 */
@Entity
@Table(name = "users", indexes = @Index(name = "idx_users_email", columnList = "email", unique = true))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", length = 255)
    private String fullName;

    @Column(length = 50)
    private String phone;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Register a new active account.
     *
     * <p>Normalising the e-mail is done here rather than in {@code AuthService} so that every path
     * that creates a user — registration, the seeder, a future import — stores it the same way; the
     * unique index on {@code email} is case-sensitive.
     */
    public static User register(String email, String passwordHash, String fullName, String phone,
                                Set<Role> roles, Instant now) {
        User u = new User();
        u.email = normaliseEmail(email);
        u.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        u.fullName = fullName;
        u.phone = phone;
        u.roles = roles == null ? new HashSet<>() : new HashSet<>(roles);
        u.status = UserStatus.ACTIVE;
        u.createdAt = Objects.requireNonNull(now, "now");
        u.updatedAt = now;
        return u;
    }

    /** Normalise an address for storage and lookup: trimmed and lower-cased. */
    public static String normaliseEmail(String email) {
        Objects.requireNonNull(email, "email");
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public void grant(Role role) {
        if (roles == null) roles = new HashSet<>();
        roles.add(Objects.requireNonNull(role, "role"));
    }

    /** Update the editable parts of a profile. Blank input leaves the current value alone. */
    public void updateProfile(String fullName, String phone) {
        if (fullName != null && !fullName.isBlank()) this.fullName = fullName.trim();
        if (phone != null && !phone.isBlank()) this.phone = phone.trim();
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash, "passwordHash");
    }


    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (status == null) status = UserStatus.ACTIVE;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public Set<String> getRoleNames() {
        return roles == null ? Set.of() : roles.stream().map(Role::getName).collect(Collectors.toSet());
    }
}
