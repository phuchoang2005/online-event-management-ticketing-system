package com.odoomaster.ticketing.iam.internal;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/** Builders for IAM aggregates. See {@code CatalogFixtures} for why this lives here. */
public final class IamFixtures {

    private IamFixtures() {
    }

    public static User user(Long id, String email) {
        return user(id, email, UserStatus.ACTIVE, Set.of());
    }

    public static User user(Long id, String email, UserStatus status, Set<Role> roles) {
        return new User(id, email, "hash", "Full Name", "0900000000",
                new HashSet<>(roles), status, Instant.now(), Instant.now());
    }

    public static Role role(Long id, String name) {
        return new Role(id, name);
    }
}
