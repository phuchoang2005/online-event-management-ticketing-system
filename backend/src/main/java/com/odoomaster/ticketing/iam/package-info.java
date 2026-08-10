/**
 * IAM: identity, authentication and authorization.
 *
 * <p>Owns {@code User}, {@code Role} (in {@code …internal}), the auth flow ({@code AuthService},
 * {@code JwtService}, {@code JwtAuthenticationFilter}) and the global {@code SecurityConfig}.
 *
 * <p>Publishes {@code iam::directory} ({@code UserDirectory} + {@code UserRef} — user lookup for
 * {@code feedback}/{@code notification}). {@code AuthService}, {@code AuthController} and
 * {@code UserController} stay in the unnamed interface and are unreachable from other modules.
 * As an infrastructure module it depends only on the kernel's error and security facets.
 */
@ApplicationModule(
        displayName = "IAM",
        allowedDependencies = {"shared::errors", "shared::security"})
package com.odoomaster.ticketing.iam;

import org.springframework.modulith.ApplicationModule;
