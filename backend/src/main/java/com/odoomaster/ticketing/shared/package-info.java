/**
 * Shared kernel: cross-cutting types every capability module may depend on.
 *
 * <p>The kernel is <strong>flat</strong> — all types live directly in this base package — but its
 * API surface is sliced by {@code @NamedInterface} annotations on the types themselves, so a module
 * declares the facets it actually uses rather than the whole kernel:
 *
 * <ul>
 *   <li>{@code shared::errors} — the API error contract: {@code DomainException} (thrown by
 *       aggregates; carries a code, no HTTP status), {@code AppException} (its subclass, thrown by
 *       services that pin a status), and {@code ApiErrorEnvelope} (with its nested
 *       {@code ErrorBody}/{@code FieldDetail}). {@code ErrorCatalog}, which resolves a bare code to
 *       a status, is an implementation detail and stays in the unnamed interface.</li>
 *   <li>{@code shared::security} — the authenticated caller: {@code AuthPrincipal},
 *       {@code CurrentUser}.</li>
 *   <li>{@code shared::audit} — the {@code @Auditable} marker consumed by {@code audit}'s aspect.</li>
 *   <li>{@code shared::contracts} — the published inter-module event contracts:
 *       {@code TicketsIssuedEvent}, {@code EventDeletedEvent}.</li>
 * </ul>
 *
 * <p>What is left in the unnamed interface — {@code GlobalExceptionHandler}, {@code TraceIdFilter},
 * {@code HealthController} — is Spring-wired infrastructure that no module imports, and is therefore
 * unreachable across boundaries. Type-level {@code @NamedInterface} removes a base-package type from
 * the unnamed interface, so {@code allowedDependencies = "shared"} no longer grants the facets above;
 * every consumer names them explicitly.
 *
 * <p><strong>Write facet references without spaces around {@code ::}</strong> —
 * {@code "shared::errors"}, never {@code "shared :: errors"}. Modulith 1.1.12's
 * {@code ApplicationModule.DeclaredDependency.of} trims the module segment but looks the interface up
 * with the <em>untrimmed</em> one, so the spaced form documented upstream fails with a misleading
 * "No named interface named 'errors' found!" (it reports the trimmed name it never searched for).
 *
 * <p>As the base of the dependency graph the kernel must not depend on any capability module;
 * {@code OPEN_TOKEN} here only declares its <em>outgoing</em> dependencies unconstrained, and it
 * references none.
 */
@ApplicationModule(
        displayName = "Shared Kernel",
        allowedDependencies = ApplicationModule.OPEN_TOKEN)
package com.odoomaster.ticketing.shared;

import org.springframework.modulith.ApplicationModule;
