# ADR-0013: Tactical DDD — typed vocabularies, value objects, and rich aggregates inside modules

- **Status:** Accepted
- **Date:** 2026-08-11
- **Deciders:** Backend team
- **Supersedes:** none — orthogonal to [ADR-0011](0011-spring-modulith.md) and [ADR-0012](0012-named-interfaces.md), which govern *where the module boundaries are*. This ADR governs *what lives inside them*.
- **Plan of record:** [`TACTICAL_DDD_REFACTOR_PLAN.md`](../../TACTICAL_DDD_REFACTOR_PLAN.md)

## Context

ADR-0011 and ADR-0012 gave us a well-enforced modular monolith: 9 capability modules, 10 published
`@NamedInterface` facets, boundaries checked at build time by `ModularityTests`. What they did not
address is the model *inside* each module, which is anemic to the point of being a liability:

- **Zero enums in the entire codebase.** Every status is a `String` compared with
  `"AVAILABLE".equals(s.getStatus())` — 87 literal sites across 8 service classes. The de-facto
  vocabularies survive only as three private `Set<String>` constants (`AdminEventService`,
  `FeedbackService`, `TicketService`), each in a different service and none of them near the entity
  that stores the value. Nothing prevents a typo'd status from being persisted.
- **All 14 JPA entities are `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`
  structs.** Their only methods are `@PrePersist` defaulting and two mapping helpers. There is no
  constructor guard and no invariant anywhere in the persistence model.
- **No value objects and no `@Embeddable`.** Prices are raw `BigDecimal`; seat identity is a
  `(section, rowLabel, seatNumber)` triple hand-carried through every layer.
- **Rules live in services.** `SeatInventoryImpl` owns the entire `AVAILABLE → LOCKED → SOLD` machine
  and the 10-minute lock TTL; `OrderService` owns the order state machine as `if/else` on strings.

This is not merely aesthetic. Three production defects follow directly from it:

1. **The payment funnel always reports zeros.** `MockPaymentGateway` only ever writes `"SUCCEEDED"`,
   while `AnalyticsService` counts `"PENDING"` / `"FAILED"`. `PaymentResult.success()` exists and is
   never read, so `OrderService` has no failure branch at all.
2. **Seat mutation has back doors.** `SeatLockSweeperJob` and `AdminEventService` mutate seat status
   without going through `SeatInventory`. `AdminEventService` can reprice a **SOLD** seat — and
   `EventSeatRepository.sumSoldPriceForEvent` sums `price WHERE status = 'SOLD'`, so an ordinary admin
   edit silently rewrites already-reported revenue.
3. **`PaymentRetryService.recordAttempt` is dead code** — zero callers.

The constraint that shapes every decision below: the system is a live ticketing platform whose Next.js
frontend pins the exact status strings as TypeScript unions, and whose prod profile runs
`ddl-auto: validate`. The refactor must be **schema-neutral and wire-neutral**.

## Decision

Adopt **tactical DDD in place** — typed vocabularies, a small set of value objects, and rich
aggregates that own their own state transitions — without changing the module layout, the published
facets, the database schema, or the HTTP contract.

Explicitly **not** adopted: domain/application/infrastructure layering, ports-and-adapters, jMolecules,
and Spring Modulith's externalized event publication. Those are bigger commitments than the problem
warrants, and the last one is incompatible with the schema-neutrality constraint.

### 1. Enums are module-internal; boundaries speak `String`

13 enums are introduced, each declared in the module that persists it (`module/internal/`, except the
payment pair in `sales/payment/`) and mapped with `@Enumerated(EnumType.STRING)` onto the existing
VARCHAR column, with the `length` attribute preserved.

**No enum crosses a module boundary.** Published facets, request DTOs and response DTOs all keep
`String`; the facet implementation is the anti-corruption layer, parsing leniently:

```java
public static Optional<SeatStatus> parse(String raw) {
    if (raw == null || raw.isBlank()) return Optional.empty();
    try { return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT))); }
    catch (IllegalArgumentException e) { return Optional.empty(); }
}
```

Two reasons this is not negotiable:

- **`AnalyticsService` queries five statuses that nothing ever writes** (`EXPIRED`, `REFUND_PENDING`,
  `REFUNDED`, `PENDING`, `FAILED`), today silently returning 0. A facet typed
  `countOrdersByStatus(OrderStatus)` cannot express `"EXPIRED"` at all, and a naive `valueOf()` in the
  facet impl throws `IllegalArgumentException` and 500s the entire admin dashboard. Lenient parse
  returning `0L` preserves today's behaviour exactly.
- **An enum-typed request DTO component breaks the error contract.** Jackson throws
  `HttpMessageNotReadableException` on a bad value, which falls through to
  `@ExceptionHandler(Exception.class)` → `500 INTERNAL_ERROR`, instead of the `400 VALIDATION_FAILED`
  the frontend expects. `PayRequest`'s `@Pattern(regexp = "MOMO|VNPAY|MOCK")` stays.

`OrderStatus = {PENDING, PAID, CANCELLED, REFUNDED}`. `EXPIRED` and `REFUND_PENDING` are deliberately
**not** modelled — they are analytics fictions. Their zero counts mean "not modelled", not "broken".

Free-form data stays `String` and does not become an enum: `Role.name`, `Notification.type`,
`EventCategory.name`, `Venue.name`, `Section.name`, `TicketType.name`.

### 2. Four value objects, one deferred, and a documented rejection

| VO | Persisted | Note |
|---|---|---|
| `Money(BigDecimal)` | **No** | Domain-only. As an `@Embeddable` it renames the column without `@AttributeOverride` and breaks `SUM(o.totalAmount)` in four queries. The *arithmetic* needs a home, not the column. |
| `SeatLock(lockedBy, lockedUntil)` | **Yes**, `@Embeddable` | Makes `lockedUntil`-without-`lockedBy` unrepresentable. |
| `LockPolicy(Duration ttl)` | No | Gives the 10-minute TTL a name and one owner. |
| `QrCode(String)` | **No** | Generator only. The column and `findByQrCode(String)` stay `String`: `CheckInService` receives arbitrary scanner input, and a strict parse on the query path would turn a bad scan from `404` into a 500. |
| `SeatLabel(section, rowLabel, seatNumber)` | **Yes**, `@Embeddable` | Retires the hand-carried triple. **Deferred** — sequenced last precisely because nothing depends on it, and cut when the four above proved sufficient. |

**`Email` is rejected, on the record.** It would touch `UserRepository`, `UserDirectoryImpl`,
`JwtService` and `AuthPrincipal` — the last of which is in the **published** `shared::security`
facet — to encapsulate one `trim().toLowerCase()` that Bean Validation already guards on the DTO.
A value object that only moves a call is churn. This is the discipline example: *a VO must remove a
representable-but-invalid state or give arithmetic a home, or it does not get created.*

`@Embeddable` records are unsupported by JPA, so `SeatLock` (and `SeatLabel`, if it lands) is a class
with a private no-arg constructor, a private all-args constructor, and a public static factory. Hibernate reads an
all-null embeddable back as `null`, so the embedded field is never exposed — the owning aggregate
exposes accessors that null-check internally.

### 3. Aggregates own their transitions; entities lose their setters

Each entity moves from
`@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder` to
`@Getter`, `@NoArgsConstructor(access = PROTECTED)`, `@AllArgsConstructor(access = PACKAGE)`.

- Hibernate is unaffected: `@Id` is on the field, so field access is already in use.
- `@Builder` is replaced by a **public static factory** per entity that applies the old `@PrePersist`
  defaults eagerly and enforces invariants. `@PrePersist` remains as a null-guard.
- **No `set*` survives on any entity.** Legitimate mutations become named behaviour: `reprice`,
  `relabelSection`, `markSold`, `releaseHold`, `resolve`, `markReadAt`, `publish`, `revertToDraft`.
- Aggregates never call `Instant.now()` — callers pass an `Instant`, sourced from the existing `Clock`
  bean. Aggregates stay clock-free and trivially unit-testable.

Reference-data entities (`Venue`, `Section`, `Seat`, `EventCategory`, `Role`) get factories and no
behaviour. That is the correct outcome for reference data, not an omission.

**Test seam.** The test suite builds entities with 96 setter calls and zero `.builder()` calls, so
removing setters breaks tests, not main code. Rather than weaken the aggregates, each module gets a
package-private fixture class in the entity's own package under `src/test`
(`…catalog.internal.CatalogFixtures`, etc.). Package-private access works across source roots, and
Modulith's `verify()` only analyses `src/main`, so the fixtures are invisible to boundary
verification. They call the package-private all-args constructor — the only way to fake a persisted
`id`.

### 4. `DomainException` under `AppException`

Aggregates in `…internal` must be able to reject an invalid transition, but `AppException` is bound to
`org.springframework.http.HttpStatus` — an HTTP concern that has no business in a domain object.

A new `shared/DomainException(code, message)` becomes `AppException`'s **superclass**;
`AppException(code, message, status)` keeps its exact public signature. `GlobalExceptionHandler` gains
a second handler for `DomainException` that resolves the status through a new module-private
`ErrorCatalog` — a code → `HttpStatus` map seeded from today's call sites and **defaulting to 409
CONFLICT**, which is what every aggregate-adjacent code already returns (`SEAT_TAKEN`, `LOCK_EXPIRED`,
`ORDER_STATE_INVALID`, `ORDER_ALREADY_PAID`, `TICKET_ALREADY_USED`, …).

Blast radius is zero outside `shared`: all ~40 `new AppException(...)` sites compile untouched, and
the existing `isInstanceOf(AppException.class)` and `.extracting("status")` test assertions still pass.
`shared::errors` gains one type, so the facet **tables** in `CLAUDE.md`,
`docs/architecture/modulith/README.md` and ADR-0012 are updated — but not `ModularityTests` (which
asserts facet *names*) and not any `allowedDependencies`.

The alternative — making `AppException` HTTP-free and mapping everything through the table — was
rejected for *this* refactor: it touches 40 call sites and 3 test assertions in the same change that
moves the state machines. The catalog now exists and can absorb them incrementally later.

### 5. Domain events are left exactly as they are

`EventDeletedEvent` **stays synchronous with `Propagation.MANDATORY` listeners.** It is a distributed
cascade delete, not a notification. Converting it to `@ApplicationModuleListener` (= `@Async` +
`AFTER_COMMIT` + `REQUIRES_NEW`) would run the cleanup after catalog had already committed the delete,
make `MANDATORY` throw `IllegalTransactionStateException`, orphan orders and tickets whenever the
async leg failed, and require the `event_publication` table — violating schema-neutrality. A javadoc
paragraph on the event records this so it is not "improved" later.

Spring Data's `AbstractAggregateRoot` + `@DomainEvents` is on the classpath but rejected: it forces
every entity to extend a Spring Data base class, and it publishes on `save()` — but `OrderService.pay()`
saves the order *before* issuing tickets, while `TicketsIssuedEvent` carries `ticketCount` and
`eventTitle` that `Order` does not have. Adopting it would force either a flow reorder or a
`shared::contracts` payload change, for no gain.

## Consequences

### Easier

- **Invalid states become unrepresentable.** A typo'd status is a compile error, not a silently
  persisted row. `SeatLock` makes a half-set lock impossible. `Money` rejects negative amounts at
  construction.
- **The seat state machine has exactly one owner.** After the back doors close, `EventSeat` is the only
  code that can change a seat's status — including for the sweeper and admin edits.
- **Testing without mocks.** Aggregate and VO rules become pure unit tests with no Mockito at all. The
  604-test `ReliabilityMatrixTest` — which today re-implements the rules as private predicates and
  therefore verifies nothing about production code — is re-pointed at the real aggregate methods.
- **Three real bugs fixed** as a by-product: the payment funnel gains a working failure path, admin
  edits can no longer rewrite reported revenue, and `PaymentRetryService` gets its first caller.

### Harder

- **Two ways to construct an entity.** `@AllArgsConstructor(PACKAGE)` exists only for fixtures and is
  positional, so reordering fields breaks the six fixture files. This fails at compile time, which is
  the acceptable form of this cost.
- **A translation layer at every facet impl.** Enum → `String` on the way out, lenient parse on the way
  in. This is deliberate — it is what keeps the modules decoupled — but it is code that did not exist
  before.
- **`mvn test` still cannot see Hibernate.** No test in the repo boots a Spring context or a database,
  so a wrong `@Enumerated`, a broken JPQL query, or a `ddl-auto: validate` mismatch is invisible to the
  suite. Every sprint therefore carries a mandatory manual smoke gate. Closing this properly is
  proposed as an optional final sprint.

### Accepted

- The seven `@Query` JPQL strings that compare status to a bare literal (`WHERE o.status = 'PAID'`)
  must be converted to bound parameters. They would otherwise break silently.
- `EXPIRED`, `REFUND_PENDING` and `REFUNDED` order statuses stay unwritten; their analytics rows keep
  reporting zero.
- `MockPaymentGateway` continues to always succeed. The failure path is wired and reachable, but no
  randomness is injected into a test-visible code path.

## Alternatives considered

| Alternative | Why not |
|---|---|
| **Layered `domain` / `application` / `infrastructure` inside each module** | Large file movement across all 9 modules, and every `internal` rule and `package-info` would need re-declaring, for a structure the team is not currently asking for. The value — pure domain objects — is obtainable with rule 3 alone. |
| **jMolecules annotations + `jmolecules-archunit`** | Machine-verified DDD roles are attractive, but two new dependencies and a second verification framework alongside `ModularityTests` is a heavier commitment than the problem warrants. Reconsider once the aggregates exist. |
| **Promote status enums to `@NamedInterface` facets** | Re-couples exactly what ADR-0012 decoupled — catalog could no longer rename a constant without a cross-module break — and makes the `AnalyticsService` unknown-status problem unsolvable. |
| **`Money` as `@Embeddable` or `AttributeConverter`** | Breaks `SUM()` aggregation across four queries; Hibernate 6 aggregate functions over converted basic types are unreliable. Neither risk buys anything the domain-only record does not. |
| **Flyway migration adding `CHECK` constraints** | Would enforce the vocabulary at the last line of defence, but risks failing on any pre-existing out-of-vocabulary row and breaks the schema-neutrality constraint. Deferred, not rejected. |
| **`Result` / `Either` return types instead of exceptions** | Every caller becomes a branch, and the existing `AppException` → `ApiErrorEnvelope` pipeline already works. |

## Implementation rules

1. **An enum belongs to the module that persists it.** Published facets, request DTOs and response DTOs
   speak `String`. Facet impls parse leniently and return `0` / `Optional.empty()` for unknown values —
   never throw.
2. **Map enums with `@Enumerated(EnumType.STRING)` and keep the `@Column(length = …)` attribute.**
   Persisted values must stay byte-identical; `name()` is pinned against the legacy string by a
   per-module `EnumVocabularyTest`.
3. **Never compare a status to a literal in JPQL.** Bind it as a parameter.
4. **Entities expose no `set*`.** Construction goes through a static factory; mutation goes through
   named behaviour that enforces the invariant.
5. **Aggregates do not call `Instant.now()`** — callers pass an `Instant` from the `Clock` bean.
6. **A value object must remove a representable-but-invalid state or give arithmetic a home.**
   Otherwise it is churn — see the `Email` rejection.
7. **Only the aggregate mutates its own state.** Jobs, admin services and seeders route through
   aggregate methods, not setters.
8. **`SeatInventoryImpl`'s mutators stay `@Transactional`.** Narrowing this splits `OrderService.pay()`
   into multiple transactions and reintroduces double-booking.
9. **Domain code throws `DomainException`; only services that need a specific HTTP status throw
   `AppException`.** New codes are registered in `ErrorCatalog`, which defaults to 409.
