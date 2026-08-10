package com.odoomaster.ticketing.shared;

import org.springframework.modulith.NamedInterface;

import java.util.Set;

/**
 * The authenticated caller, stored as the security context principal by the JWT authentication filter.
 *
 * <p>Part of the {@code shared::security} named interface.
 *
 * @param userId the authenticated user's id
 * @param email the user's email
 * @param roles the user's role names (without the {@code ROLE_} prefix)
 */
@NamedInterface("security")
public record AuthPrincipal(Long userId, String email, Set<String> roles) {

    /**
     * @param role a role name to check
     * @return {@code true} if the principal holds that role
     */
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
