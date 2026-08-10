package com.odoomaster.ticketing.shared;

import org.springframework.http.HttpStatus;
import org.springframework.modulith.NamedInterface;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Helper for reading the authenticated {@link AuthPrincipal} from the security context.
 *
 * <p>Part of the {@code shared::security} named interface.
 */
@Component
@NamedInterface("security")
public class CurrentUser {

    /**
     * @return the current authenticated principal
     * @throws AppException with {@code UNAUTHENTICATED} (401) if no user is authenticated
     */
    public AuthPrincipal require() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal p)) {
            throw new AppException("UNAUTHENTICATED", "Authentication required.", HttpStatus.UNAUTHORIZED);
        }
        return p;
    }
}
