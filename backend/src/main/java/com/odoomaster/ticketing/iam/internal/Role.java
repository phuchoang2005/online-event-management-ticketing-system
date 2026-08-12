package com.odoomaster.ticketing.iam.internal;

import jakarta.persistence.*;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.*;

/**
 * JPA entity mapping the persistence row for a role.
 */
@Entity
@Table(name = "roles",
        uniqueConstraints = @UniqueConstraint(name = "uk_roles_name", columnNames = "name"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String name;
    /** A security role such as {@code ROLE_ADMIN}. Reference data: no lifecycle, no transitions. */
    public static Role named(String name) {
        Role role = new Role();
        role.name = Objects.requireNonNull(name, "name");
        return role;
    }
}
