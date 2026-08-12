# Tactical DDD Refactor — Plan of Record

> Successor to `SPRING_MODULITH_REFACTOR_PLAN.md` (Sprints 0–5, retired and deleted in commit
> `db4c77d` once complete). That plan established **where the boundaries are**; this one establishes
> **what lives inside them**.
>
> Decision record: [`docs/adr/0013-tactical-ddd-aggregates.md`](docs/adr/0013-tactical-ddd-aggregates.md).
> Progress log: [`docs/tracking/tracking-9.md`](docs/tracking/tracking-9.md).
> Baseline: 727 tests green at `db4c77d`.

## Why

The Spring Modulith structure is sound — 9 capability modules, 10 published `@NamedInterface` facets,
boundaries enforced at build time by `ModularityTests`. The domain model *inside* those modules is
not:

- **Zero enums in the entire codebase.** Every status is a `String` compared with
  `"AVAILABLE".equals(s.getStatus())` — 87 literal sites across 8 service classes. The vocabularies
  exist only as three private `Set<String>` constants (`AdminEventService`, `FeedbackService`,
  `TicketService`), none of them near the entity that stores the value.
- **All 14 JPA entities are `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`
  structs.** Their only methods are `@PrePersist` defaulting plus two mapping helpers. No constructor
  guards, no invariants — any caller can write any status.
- **No value objects, no `@Embeddable` anywhere.** Prices are raw `BigDecimal`; seat identity is a
  `(section, rowLabel, seatNumber)` triple hand-carried through every layer.
- **Business rules live in services.** `SeatInventoryImpl` holds the whole `AVAILABLE → LOCKED → SOLD`
  machine plus `LOCK_TTL_MINUTES = 10`; `OrderService` holds the order state machine as `if/else` on
  strings and totals prices with a bare `reduce`.

Three real defects follow directly from that:

| Bug | Detail |
|---|---|
| **(a) Payment funnel always reports zeros** | `MockPaymentGateway` only ever writes `"SUCCEEDED"`, while `AnalyticsService` counts `"PENDING"`/`"FAILED"`. `PaymentResult.success()` exists and is never read. |
| **(b) Seat mutation back doors** | `SeatLockSweeperJob` and `AdminEventService` mutate seat status behind `SeatInventory`'s back. `AdminEventService` can reprice a **SOLD** seat — and `EventSeatRepository.sumSoldPriceForEvent` sums `price WHERE status='SOLD'`, so an admin edit silently rewrites reported revenue. |
| **(c) Dead retry infrastructure** | `PaymentRetryService.recordAttempt` has zero callers; `OrderService` has no payment-failure branch at all. |

## Scope and constraints

1. **Tactical DDD in place.** Keep `module/` + `module/internal/`. No domain/application/infrastructure
   layering, no ports-and-adapters, no jMolecules.
2. **All 9 modules, uniformly.**
3. **Schema-neutral.** `@Enumerated(EnumType.STRING)` onto the existing VARCHAR columns. **No Flyway
   migration.** Every enum's longest constant fits its column (verified: `event_seats.status`
   VARCHAR(16) vs `AVAILABLE` = 9; `orders.status` VARCHAR(20) vs `CANCELLED` = 9; all 13 columns
   pass). Keeping each field's `length` attribute is mandatory so prod `ddl-auto: validate` passes.
4. **Fix bugs (a), (b), (c)** as part of the work.
5. **Wire format frozen.** The Next.js frontend pins the exact status strings as TypeScript unions in
   `frontend/types/index.ts`. Persisted and serialized values stay byte-identical.

## Findings that shape the plan

| # | Finding | Consequence |
|---|---|---|
| 1 | **0 `.builder()` calls in tests; 96 setter calls.** `.builder()` is a main-code idiom only | Dropping `@Builder` breaks main; dropping `@Setter` breaks tests. The migration tactic inverts — see R3 |
| 2 | `ReliabilityMatrixTest` is **604 of the 727 tests** (5 `@TestFactory` methods) and re-implements the rules locally as private predicates | It tests nothing today. Pointing it at the real aggregates is the single biggest win |
| 3 | Tests deliberately construct statuses **outside** every vocabulary (`BOOKED`, `HELD`, `REFUND_PENDING`, `ARCHIVED`, `EXPIRED`) in 6 `@CsvSource`/`@ValueSource` blocks | Those rows become uncompilable under enums — the real churn driver |
| 4 | `AnalyticsService` queries **5 statuses nothing ever writes** (`EXPIRED`, `REFUND_PENDING`, `REFUNDED`, `PENDING`, `FAILED`), today silently returning 0 | A naive `valueOf()` in a facet impl throws and **500s the whole admin dashboard**. Highest-risk regression |
| 5 | **7 `@Query` JPQL strings compare status to a bare literal** — `OrderRepository:16,19,22` (`'PAID'`), `EventSeatRepository:26,29,35` (`'SOLD'`, `'SOLD'`, `'LOCKED'`) | Must become bound parameters, or they break silently under `@Enumerated` |
| 6 | **No test boots Hibernate, Spring, or a DB.** All 17 classes are plain Mockito/JUnit | `mvn test` cannot catch a broken JPQL query or a `validate` mismatch. Every sprint needs a manual smoke gate |
| 7 | A `Clock` bean already exists (`CacheConfig`, used by `EventService`) | Reuse it; aggregates take `Instant` and never call `Instant.now()` |
| 8 | Frontend is **Next.js 14 + TypeScript**, not React + Vite as `CLAUDE.md` claimed | Correct the doc in closeout |

---

## Cross-cutting rules

### R1 — No enum crosses a module boundary

Enums are declared in the module that persists them (`module/internal/`, except the payment pair in
`sales/payment/`). **Every published facet, every request DTO and every response DTO keeps `String`.**
The facet impl is the anti-corruption layer.

Publishing an enum would re-couple exactly what ADR-0012 decoupled, and makes finding #4 unsolvable —
a facet typed `countOrdersByStatus(OrderStatus)` cannot express `"EXPIRED"` at all. **Zero facet
changes, zero `allowedDependencies` changes, `ModularityTests` untouched.**

Request DTOs staying `String` is load-bearing: an enum-typed record component makes Jackson throw
`HttpMessageNotReadableException`, which falls through to `@ExceptionHandler(Exception.class)` →
**500 `INTERNAL_ERROR`**, breaking the `400 VALIDATION_FAILED` contract.

Every enum gets a lenient parser. Facet impls return `0` on `Optional.empty()` (matching today's DB
behaviour); services map it to `VALIDATION_FAILED`:

```java
public static Optional<SeatStatus> parse(String raw) {
    if (raw == null || raw.isBlank()) return Optional.empty();
    try { return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT))); }
    catch (IllegalArgumentException e) { return Optional.empty(); }
}
```

**15 enums:** `catalog/internal/{SeatStatus,EventStatus}`, `sales/internal/{OrderStatus,PaymentRetryStatus}`,
`sales/payment/{PaymentStatus,PaymentMethod}`, `ticketing/internal/{TicketStatus,CheckInStatus}`,
`feedback/internal/{FeedbackStatus,FeedbackCategory}`,
`notification/internal/{NotificationStatus,NotificationChannel}`, `iam/internal/UserStatus`.

`OrderStatus = {PENDING, PAID, CANCELLED, REFUNDED}`. `EXPIRED` and `REFUND_PENDING` are **not**
added — they are analytics fictions; lenient parse returns 0, identical to today.

**Explicitly not enums** (free-form data, not vocabularies): `Role.name`, `Notification.type`,
`EventCategory.name`, `Venue.name`, `Section.name`, `TicketType.name`.

### R2 — Value objects: five accepted

| VO | File | Persisted |
|---|---|---|
| `Money(BigDecimal)` — `zero/of/sum/plus`, rejects negatives | `sales/internal/Money.java` | **No** — columns stay `BigDecimal` |
| `SeatLock(lockedBy, lockedUntil)` | `catalog/internal/SeatLock.java` | **Yes**, `@Embeddable` → `locked_by` / `locked_until` |
| `LockPolicy(Duration ttl)` — owns the 10-minute TTL | `catalog/internal/LockPolicy.java` | No |
| `QrCode(String)` — invariant `32 × [0-9A-F]`, `static generate()` | `ticketing/internal/QrCode.java` | **No** — column stays `String` |
| `SeatLabel(section, rowLabel, seatNumber)` | `catalog/internal/SeatLabel.java` | **Yes**, `@Embeddable` (Sprint 6, droppable) |

`Money` is domain-only: as an `@Embeddable` it renames the column without `@AttributeOverride` and
breaks `SUM(o.totalAmount)` in four queries. The *arithmetic* is what needs a home, not the column.
`Order.total()` returns `Money`; `getTotalAmount()` stays for DTO mapping.

`QrCode` is a **generator** VO only — do not convert the column or `findByQrCode(String)`.
`CheckInService` receives arbitrary scanner input; a strict parse on the query path would turn a bad
scan from `404` into a 500.

`SeatLock` trap: Hibernate reads an all-null embeddable back as `null`, so the field is never
exposed — `EventSeat` exposes `lockedBy()`, `lockedUntil()`, `isLockExpiredAt(now)` and null-checks
internally.

**`Email` rejected, on the record.** It would touch `UserRepository`, `UserDirectoryImpl`,
`JwtService` and `AuthPrincipal` (in the **published** `shared::security` facet) to encapsulate one
`trim().toLowerCase()` that Bean Validation already guards. This is the discipline example.

JPA note: `@Embeddable` records are unsupported — `SeatLabel`/`SeatLock` are classes with `@Getter`,
private no-arg + private all-args constructors, and a public static factory. `Money`/`LockPolicy`/
`QrCode` are plain records JPA never sees.

### R3 — Aggregate migration tactic: factories + package-private test fixtures

Per entity: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder` → `@Getter`,
`@NoArgsConstructor(access = PROTECTED)` (JPA + proxies), `@AllArgsConstructor(access = PACKAGE)`
(test-fixture seam). Field access is preserved (`@Id` is on the field), so removing setters changes
nothing for Hibernate. `@Builder` is replaced by a **public static factory** per entity that applies
today's `@PrePersist` defaults eagerly and enforces invariants; `@PrePersist` stays as a null-guard.
**No `set*` survives on any entity** — legitimate mutations become named behaviour (`reprice`,
`relabelSection`, `resolve`, …).

The 96 test setter calls are solved by **package-private fixtures in the entity's own package under
`src/test`** — package-private access works across source roots, and Modulith's `verify()` only
analyses `src/main`, so these are invisible to it:

`src/test/java/com/odoomaster/ticketing/{catalog,sales,ticketing,feedback,notification,iam}/internal/*Fixtures.java`

Each exposes `public static` builders (`CatalogFixtures.lockedSeat(...)`, `SalesFixtures.pendingOrder(...)`)
calling the package-private all-args constructor — the only way to fake a persisted `id`.

Caveat: `@AllArgsConstructor` is positional, so reordering entity fields breaks the 6 fixture files —
at compile time, which is acceptable.

### R4 — Domain errors: `DomainException` as `AppException`'s superclass

```java
// shared/DomainException.java  (NEW, @NamedInterface("errors"))
public class DomainException extends RuntimeException {
    private final String code;   // + getCode()
}
// shared/AppException.java — public API byte-identical
public class AppException extends DomainException { private final HttpStatus status; }
```

`GlobalExceptionHandler` gains an `@ExceptionHandler(DomainException.class)` (Spring picks the most
specific) resolving status via a new module-private `shared/ErrorCatalog.java` — a
`Map<String, HttpStatus>` seeded from today's call sites, **defaulting to 409 CONFLICT**, which is
what every aggregate-adjacent code already returns (`SEAT_TAKEN`, `LOCK_EXPIRED`,
`ORDER_STATE_INVALID`, `ORDER_ALREADY_PAID`, `TICKET_ALREADY_USED`, …).

**Blast radius: zero code changes outside `shared`.** All ~40 `new AppException(code, msg, HttpStatus.X)`
sites compile untouched; `isInstanceOf(AppException.class)` in 8 test classes still passes;
`.extracting("status")` assertions still pass. Aggregates in `…internal` throw `DomainException` —
HTTP-free, which is the DDD requirement.

`shared::errors` gains one type → update the three facet **tables** (`CLAUDE.md`,
`docs/architecture/modulith/README.md`, ADR-0012), but **not** `ModularityTests` (which asserts facet
*names*) and **not** any `allowedDependencies`.

### R5 — Domain events: change nothing

`EventDeletedEvent` **must stay synchronous + `MANDATORY`**. It is a distributed cascade, not a
notification. `@ApplicationModuleListener` (= `@Async` + `AFTER_COMMIT` + `REQUIRES_NEW`) would run
after catalog already committed the delete, make `Propagation.MANDATORY` throw
`IllegalTransactionStateException`, orphan orders/tickets on async failure, and require the
`event_publication` table — violating the schema-neutral constraint. A javadoc paragraph records this
so nobody "improves" it later.

`AbstractAggregateRoot` + `@DomainEvents` is on the classpath but **rejected**: it forces every entity
to extend a Spring Data base class, and events fire on `save()` — but `OrderService.pay()` saves the
order *before* issuing tickets, while `TicketsIssuedEvent` carries `ticketCount`/`eventTitle` the
`Order` doesn't have. Adopting it would force a flow reorder or a `shared::contracts` payload change.

---

## Sprints

Each is independently mergeable with `mvn test` green and lands as its own commit. Because of finding
#6, each ends with a **manual smoke gate** (JDK 21 pinned;
`docker compose -f docker-compose.dev.yml up --build`): `GET /v1/events` → `GET /v1/events/{id}/seats`
→ `POST /v1/orders` → `POST /v1/orders/{id}/pay` → `GET /v1/tickets` → `GET /v1/admin/analytics` →
`GET /v1/admin/events`.

### Sprint 0 — Plan doc + ADR stub (docs only) ✅

This file; `docs/adr/0013-tactical-ddd-aggregates.md` (**Proposed**) + index row;
`docs/tracking/tracking-9.md` skeleton; fix the dangling `SPRING_MODULITH_REFACTOR_PLAN.md` link in
`CLAUDE.md`. Historical tracking sheets 7 and 8 are **not** edited — records are immutable; their
dangling links are noted in tracking-9's baseline instead.

### Sprint 1 — Typed vocabularies *(highest risk)*

Replace 87 status literals across 26 main files with the 15 enums from R1.

- **Entities:** `@Enumerated(EnumType.STRING)`, `length` preserved, `@PrePersist` defaults become enum
  constants.
- **The JPQL trap (finding #5):** convert all 7 literals to bound parameters; keep the old method
  names as `default` wrappers so call sites don't move. Retype derived-query params.
- **Facet impls become the ACL (finding #4):** `SalesReportingImpl`, `TicketingReportingImpl`,
  `EventCatalogImpl` lenient-parse → `0L` on unknown. **This is what keeps `AnalyticsService` from
  500-ing.** `toDetail`/`toSummary`/`toStats` emit `.name()`.
- **Bug (a) part 1:** `PaymentResult` gains a typed `PaymentStatus` + `errorCode`;
  `PaymentGateway.supports` / `PaymentGatewayResolver.resolve` take `PaymentMethod`.
- **Tests:** rewrite the 6 out-of-vocabulary blocks to `@EnumSource`; add 15 `EnumVocabularyTest`s
  pinning `name()` against the legacy strings — the schema-neutrality guard `mvn test` *can* enforce.

### Sprint 2 — `DomainException` + `ErrorCatalog`

Per R4. Four files in `shared`, **zero test edits expected**.

### Sprint 3 — Seat aggregate + close the back doors *(bug (b))*

New `SeatLock` (`@Embeddable`) + `LockPolicy`. Target `EventSeat` API:

```java
static EventSeat create(Long eventId, Long seatId, Long ticketTypeId,
                        String section, String rowLabel, String seatNumber, BigDecimal price);
void requireBelongsTo(Long eventId);                            // SEAT_NOT_IN_EVENT
boolean isLockableAt(Instant now);                              // AVAILABLE || (LOCKED && expired)
void lockFor(Long userId, Instant now, LockPolicy policy);      // SEAT_TAKEN
void markSold(Instant now);                                     // SEAT_TAKEN then LOCK_EXPIRED
void releaseHold();  void releaseSale();
boolean releaseExpiredLock(Instant now);                        // sweeper; true iff changed
void reprice(BigDecimal price);                                 // SEAT_SOLD_IMMUTABLE if sold
void relabelSection(String section);
SeatStatus status();  Long lockedBy();  Instant lockedUntil();
```

`SeatInventoryImpl` collapses to ~60 lines: load → loop one aggregate call → `saveAll` →
`evictEventCaches`. **Preserve exactly:** `@Transactional` on all four mutators (so
`OrderService.pay()` stays *one* transaction — narrowing this reintroduces double-booking), `saveAll`
before eviction, all three cache evictions on every mutation, and `markSold`'s guard ordering
(`SOLD → SEAT_TAKEN` *before* expiry → `LOCK_EXPIRED`). Inject the existing `Clock` bean.

**Back doors closed:** the sweeper calls `releaseExpiredLock` (kept in `catalog.internal` calling the
aggregate directly — the invariant is "only the aggregate mutates status", not "only the facet does";
**zero facet change**); `AdminEventService` uses `relabelSection` + `reprice`, with an up-front check
rejecting the whole request if a price change would touch a SOLD seat.

### Sprint 4 — Order/Payment aggregates + `Money` + failure path *(bugs (a) part 2, (c))*

`Order.place/isOwnedBy/isPayable/isPaid/pay/cancel/total()`; `Payment.record`; `OrderItem.forSeat`;
`PaymentRetry.attempt` + `static int nextAttemptNo(long)`. `OrderService.pay()` keeps its early return
on `isPaid()` — the aggregate can't skip the side effects, so idempotency lives in both places.

Wire the failure path (`PaymentResult.success()` currently never read): on failure, record a retry
attempt and throw `PAYMENT_FAILED`. `MockPaymentGateway` keeps always succeeding — no randomness in a
test-visible path. The value is that the funnel becomes *capable* of non-zero `PENDING`/`FAILED` and
`recordAttempt` gets its first real caller.

### Sprint 5 — Remaining aggregates + `QrCode` + docs closeout

`Ticket`, `CheckIn`, `QrCode`, `Event`, `TicketType`, `Feedback`, `Notification`, `User`, `Role`,
`AuditLog`, `Venue`/`Section`/`Seat`/`EventCategory`.

**Preserve exactly:** `CheckInService`'s guard precedence (`USED||existing → ALREADY_USED`, then
`!VALID → TICKET_NOT_VALID`); `FeedbackService` sets `resolvedAt` only on transition to `RESOLVED` and
never clears it.

`ReliabilityMatrixTest`'s 604 tests are re-pointed at the real aggregates.

Docs closeout: ADR-0013 → `Accepted`; tracking-9 completed; a new **"Domain model"** subsection in
`CLAUDE.md` stating the enum/VO/aggregate rules and the `String`-at-the-boundary rule, plus the
frontend-stack correction (finding #8).

### Sprint 6 — `SeatLabel` embeddable *(optional, droppable)*

`EventSeat`'s three String fields collapse into `private SeatLabel label`; same columns, no
`@AttributeOverride`; `uk_event_seat` is declared by column name on `@Table` and is unaffected.
Two repository method renames, ~10 call sites. **Cut this if budget runs out** — nothing downstream
depends on it.

### Sprint 7 — one persistence slice test *(optional, recommended)*

Finding #6 means the suite is blind to exactly the class of bug this refactor can introduce. Add
`@DataJpaTest` with an embedded MySQL-mode DB and three tests: round-trip every enum-bearing entity
asserting the persisted column string via native SQL; exercise all 7 rewritten `@Query` methods; boot
Hibernate with `ddl-auto: validate` against the Flyway-migrated schema.

---

## Risks, ranked

1. **Lenient parse in the three reporting facet impls (Sprint 1)** — miss it and `/v1/admin/analytics`
   500s on every request.
2. **The 7 JPQL literals (Sprint 1)** — invisible to `mvn test`; only the manual gate or Sprint 7
   catches them.
3. **`markSold` guard ordering (Sprint 3)** — a plausible "cleanup" reorder silently swaps
   `SEAT_TAKEN` and `LOCK_EXPIRED`.
4. **`@Transactional` on `SeatInventoryImpl`'s mutators (Sprint 3)** — narrowing it splits `pay()`
   into multiple transactions and reintroduces double-booking.
5. **Request DTOs must not become enums (Sprint 1)** — turns `400 VALIDATION_FAILED` into
   `500 INTERNAL_ERROR`.
6. **`@AllArgsConstructor(PACKAGE)` is positional** — reordering entity fields breaks the 6 fixture
   files, at compile time.
