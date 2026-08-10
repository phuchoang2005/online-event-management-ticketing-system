package com.odoomaster.ticketing.iam;

import org.springframework.modulith.NamedInterface;

import java.util.Optional;

/**
 * Published iam API for resolving user identities across module boundaries.
 *
 * <p>Consumers ({@code feedback}, {@code notification}) call this instead of reaching into iam's
 * {@code User} entity or its repository, so the user schema stays private to the module. Returns the
 * lightweight {@link UserRef} projection rather than the JPA entity.
 *
 * <p>The whole of {@code iam}'s cross-module surface: consumers declare
 * {@code allowedDependencies = "iam::directory"}, which leaves {@code AuthService},
 * {@code AuthController} and the rest of the base package unreachable.
 */
@NamedInterface("directory")
public interface UserDirectory {

    /**
     * Look up a user by id.
     *
     * @param userId the user to read
     * @return the reference, or {@link Optional#empty()} if not found
     */
    Optional<UserRef> find(Long userId);

    /**
     * Look up a user by email (unique).
     *
     * @param email the address to resolve
     * @return the reference, or {@link Optional#empty()} if not found
     */
    Optional<UserRef> findByEmail(String email);

    /**
     * Immutable projection of a {@code User} exposed across module boundaries.
     */
    @NamedInterface("directory")
    record UserRef(Long id, String email) {}
}
