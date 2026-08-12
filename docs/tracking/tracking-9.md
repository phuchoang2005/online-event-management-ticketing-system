# Tracking Sheet — Iteration 9

> Date: 2026-08-11
> Scope: tactical Domain-Driven Design **inside** the Modulith modules — 13 typed
> vocabularies (enums) replacing 87 status string literals, four value objects,
> rich aggregates that own their own state transitions across all 14 entities, and
> a domain exception free of `HttpStatus`. Three latent defects are fixed as a
> by-product, plus three silent breaks the refactor itself surfaced. The module
> layout, the 10 published facets, the DB schema and the HTTP contract are
> **unchanged**; no Flyway migration was needed.
> Result: **727 → 881 tests**, green at every sprint boundary.
> Baseline: [`tracking-8.md`](./tracking-8.md) — **727 tests green** at `db4c77d`
> (tracking-8 recorded 725; two were added after that sheet closed).
> Plan of record: [`../../TACTICAL_DDD_REFACTOR_PLAN.md`](../../TACTICAL_DDD_REFACTOR_PLAN.md).
> Decision record: [`../adr/0013-tactical-ddd-aggregates.md`](../adr/0013-tactical-ddd-aggregates.md).
>
> Note on the baseline links: `tracking-7.md` and `tracking-8.md` both cite
> `SPRING_MODULITH_REFACTOR_PLAN.md` as plan of record. That file was retired and
> deleted in `db4c77d` once its Sprints 0–5 completed, so those two links are
> dead. Historical sheets are immutable records and were **not** edited; the
> successor plan is `TACTICAL_DDD_REFACTOR_PLAN.md` and the dangling reference in
> `CLAUDE.md` was corrected in Sprint 0 below.

---

## Sprint 0 — Plan of record, ADR, tracking skeleton ✅

Goal: establish the decision contract and the progress log before any code moves.

| # | Change | Status |
|---|---|---|
| 1 | New [`TACTICAL_DDD_REFACTOR_PLAN.md`](../../TACTICAL_DDD_REFACTOR_PLAN.md) at repo root — successor to the retired `SPRING_MODULITH_REFACTOR_PLAN.md`. Records the eight findings that shape the work, the five cross-cutting rules (R1–R5), the 8-sprint breakdown and the ranked risk list | ✅ |
| 2 | New [`adr/0013-tactical-ddd-aggregates.md`](../adr/0013-tactical-ddd-aggregates.md) (**Proposed**) + index row in [`adr/README.md`](../adr/README.md). Orthogonal to ADR-0011/0012 — it supersedes nothing; those govern where the boundaries are, this governs what lives inside them | ✅ |
| 3 | Fix the dangling `SPRING_MODULITH_REFACTOR_PLAN.md` link in `CLAUDE.md` and point it at the successor plan | ✅ |
| 4 | This tracking sheet | ✅ |

### Impact

- **Decision contract first.** The three highest-risk constraints — no enum crosses a module
  boundary, request DTOs stay `String`, and `SeatInventoryImpl`'s mutators stay `@Transactional` —
  are written down before the first line of code moves, so a later "cleanup" that violates one is a
  review-block rather than a silent regression.
- **Baseline corrected.** The true baseline is **727** tests, not the 725 recorded in tracking-8.
- **Tests.** 727 → 727 (docs-only sprint).

### Verification

| Check | Result |
|---|---|
| `cd backend && mvn test` (JDK 21) | ✅ BUILD SUCCESS — 727 tests, 0 failures, 0 errors |
| Doc links resolve (`TACTICAL_DDD_REFACTOR_PLAN.md`, ADR-0013, ADR index, `CLAUDE.md`) | ✅ |
| Behaviour / API / schema | unchanged |

### Notes

- **Eight findings from the pre-refactor survey**, all verified against the code and recorded in the
  plan because each one changes the approach:
  1. **0 `.builder()` calls in tests; 96 setter calls.** `.builder()` is a main-code idiom only, so
     dropping `@Builder` breaks *main* and dropping `@Setter` breaks *tests* — the inverse of the
     usual assumption. Solved with package-private fixtures in the entity's own package under
     `src/test`, which Modulith's `verify()` does not analyse.
  2. `ReliabilityMatrixTest` is **604 of the 727 tests** and re-implements the rules as private
     predicates — it verifies nothing about production code today.
  3. Six `@CsvSource`/`@ValueSource` blocks deliberately construct out-of-vocabulary statuses
     (`BOOKED`, `HELD`, `REFUND_PENDING`, `ARCHIVED`, `EXPIRED`); those rows become uncompilable under
     enums. This, not `.builder()`, is the real churn driver.
  4. **`AnalyticsService` queries five statuses that nothing ever writes** (`EXPIRED`,
     `REFUND_PENDING`, `REFUNDED`, `PENDING`, `FAILED`), today silently returning 0. A naive
     `valueOf()` in a facet impl would throw and 500 the whole admin dashboard — the highest-risk
     regression in the refactor, and the reason facets keep `String` with lenient parsing.
  5. **Seven `@Query` JPQL strings compare status to a bare literal** (`OrderRepository` ×3 `'PAID'`,
     `EventSeatRepository` ×3). They break silently under `@Enumerated` and must become bound
     parameters.
  6. **No test in the repo boots Hibernate, Spring or a DB.** `mvn test` therefore cannot catch a
     wrong `@Enumerated`, a broken JPQL query, or a `ddl-auto: validate` mismatch — hence the
     mandatory manual smoke gate on every sprint, and the optional Sprint 7 that closes the hole.
  7. A `Clock` bean already exists (`CacheConfig`, used by `EventService`); aggregates take `Instant`
     and stay clock-free rather than adding a second time source.
  8. The frontend is **Next.js 14 + TypeScript**, not React + Vite as `CLAUDE.md` states, and
     `frontend/types/index.ts` pins the status strings as TS unions — so the wire format is frozen.
     The `CLAUDE.md` correction lands in the Sprint 5 docs closeout.

---

## Sprint 1 — Typed vocabularies ✅

Goal: replace every status string literal with a typed enum, byte-identical on the wire and in the
database, with no Flyway migration.

| # | Change | Status |
|---|---|---|
| 1 | 13 enums, each declared in the module that persists it: `catalog::internal` `SeatStatus`/`EventStatus`; `sales::internal` `OrderStatus`/`PaymentRetryStatus`; `sales.payment` `PaymentStatus`/`PaymentMethod`; `ticketing::internal` `TicketStatus`/`CheckInStatus`; `feedback::internal` `FeedbackStatus`/`FeedbackCategory`; `notification::internal` `NotificationStatus`/`NotificationChannel`; `iam::internal` `UserStatus`. Each publishes a lenient `parse(String) → Optional<T>` that tolerates case/whitespace and **never throws** | ✅ |
| 2 | Retire the three private `Set<String>` vocabularies (`AdminEventService.ALLOWED_STATUSES`, `FeedbackService.VALID_STATUSES`/`VALID_CATEGORIES`, `TicketService.TICKET_STATUSES`) in favour of `EnumSet.allOf(...)` beside the column they govern | ✅ |
| 3 | Map 13 entity columns with `@Enumerated(EnumType.STRING)`, `length` preserved on every field; `@PrePersist` defaults become enum constants | ✅ |
| 4 | **Convert all 7 JPQL status literals to bound parameters** (`OrderRepository` ×4 including `revenueByDay`, `EventSeatRepository` ×3), keeping the old method names as `default` wrappers so no call site moved. Retype 14 derived-query parameters across 6 repositories | ✅ |
| 5 | **Make the three reporting facet impls the anti-corruption layer**: `SalesReportingImpl`, `TicketingReportingImpl` and `EventCatalogImpl` lenient-parse their `String` argument and answer `0L` for anything they do not model. Published facets, request DTOs and response DTOs all keep `String` | ✅ |
| 6 | Bug (a) part 1: `PaymentResult` gains a typed `PaymentStatus` and an `errorCode`; `PaymentGateway.supports` / `PaymentGatewayResolver.resolve` / `PaymentRequest.provider` take `PaymentMethod`; `OrderService.pay` parses `req.method()` and rejects garbage as `400 VALIDATION_FAILED` | ✅ |
| 7 | **Fix three always-false comparisons in `AdminEventService`** — see Notes. New `AdminEventServiceReliabilityTest` (5 tests) covers them | ✅ |
| 8 | New `domain/EnumVocabularyTest` (53 tests) pinning every constant's `name()` against the legacy on-disk string, every constant's length against its column width, the `parse` round-trip, and the lenient-on-unknown contract | ✅ |
| 9 | Tests: rewrite 6 out-of-vocabulary `@CsvSource`/`@ValueSource` blocks to `@EnumSource`; make the two `ReliabilityMatrixTest` matrices exhaustive over the enums instead of hand-listed; convert 22 enum-vs-String assertions | ✅ |

### Impact

- **The vocabulary is now finite and checked by the compiler.** 87 status literals across 26 main
  files became 13 enums. A typo is a compile error rather than a row nothing will ever match.
- **`@Enumerated(EnumType.STRING)` keeps the persisted bytes identical**, so no Flyway migration was
  needed and prod `ddl-auto: validate` is unaffected. Verified per column: the longest constant is
  9 characters (`AVAILABLE`, `CANCELLED`, `SUCCEEDED`, `PUBLISHED`) against a 16–32 character column.
  `EnumVocabularyTest` now enforces both properties on every build.
- **The admin dashboard is protected.** `AnalyticsService` counts five statuses no code path writes
  (`EXPIRED`, `REFUND_PENDING`, `REFUNDED`, `PENDING`, `FAILED`). A bare `valueOf` in the reporting
  facets would have thrown `IllegalArgumentException` and 500'd `/v1/admin/analytics` on every
  request; the lenient contract answers `0` exactly as the raw-string columns did.
- **No enum crosses a module boundary**, so `ModularityTests.verify()` and the facet table are
  untouched — zero `@NamedInterface`, `allowedDependencies` or doc-table changes in this sprint.
- **Tests.** 727 → 758 (+31 net): +53 `EnumVocabularyTest`, +5 `AdminEventServiceReliabilityTest`,
  −20 net from `ReliabilityMatrixTest` (604 → 584, see Notes), −7 from the deleted
  out-of-vocabulary parameterized rows.

### Verification

| Check | Result |
|---|---|
| `cd backend && mvn clean test` (JDK 21) | ✅ BUILD SUCCESS — 758 tests, 0 failures, 0 errors |
| `ModularityTests.verify()` + `exposesTheDeclaredNamedInterfaces()` | ✅ green, unchanged — no enum is published, so the 10-facet table is untouched |
| **Grep gate** — status string literals outside an enum declaration | ✅ only the intentional ones remain: `AnalyticsService`'s facet arguments (`String` by contract, rule 1) and two `"IN_APP"` arguments to `NotificationService.create`'s `String` channel parameter |
| **Negative check** — reintroduce `"PUBLISHED".equals(e.getStatus())` in `AdminEventService.delete` | ✅ now **fails**: `delete_givenPublishedEvent_refusesAndDoesNotCascade` → `Expecting code to raise a throwable.` Probe reverted; suite re-run green |
| Persisted values / wire format / DB schema | unchanged — no migration added |

### Notes

- **Three silent breaks that the type system did not catch, and neither did 727 tests.**
  `AdminEventService` compared a status with `"PUBLISHED".equals(e.getStatus())`. Once the getter
  returned an enum this still *compiled* — `String.equals(Object)` accepts any argument — but
  evaluated to `false` forever. The consequences were real: `delete()` would no longer refuse to
  delete a **PUBLISHED** event (cascading away its orders and tickets), and `buildSections` tallied
  `0 sold / 0 available` for every section. Two sibling sites at lines 342–343 had the same shape.
  None of the three had any test coverage, which is why the suite stayed green through the break.
  They were found by grepping for surviving literals — the gate is now a standing verification row,
  and `AdminEventServiceReliabilityTest` pins all three. **This is the single most important finding
  of the sprint**: `String.equals(enum)` is invisible to both `javac` and a green test run.
- **`NotificationChannel` models `EMAIL` and `SMS`, `OrderStatus` does not model `EXPIRED`.** The
  distinction is *who writes the value*. `NotificationService.create` accepts a caller-supplied
  channel and persists it verbatim — `NotificationServiceReliabilityTest` exercises `EMAIL` and
  `SMS` — so leaving them out would have silently coerced them to `IN_APP`, a data change. Nothing
  anywhere writes `EXPIRED` or `REFUND_PENDING`; they are only ever *queried* by analytics, so
  modelling them would make the dashboard report invented states as real ones. Rule recorded in
  ADR-0013 §1 and pinned by `EnumVocabularyTest.analyticsFictionsAreDeliberatelyNotModelled`.
- **`ReliabilityMatrixTest` went 604 → 584 and got stricter.** Its two hand-written matrices asserted
  on seven statuses no code path can produce (`BOOKED`, `HELD`, `REFUND_PENDING` on seats;
  `REFUNDED`, `PENDING`, `EXPIRED` on tickets) — untypeable once the vocabulary is finite. They were
  replaced with matrices exhaustive over `SeatStatus × {no lock, lapsed lock, live lock}` (9 cases)
  and `TicketStatus × {prior check-in, none}` (6 cases). Fewer tests, complete coverage of a domain
  that is now actually bounded. Re-pointing them at real aggregate methods is Sprint 3/5 work.
- **The 7 JPQL literals were the quiet risk.** `WHERE o.status = 'PAID'` compiles and passes every
  test in this repo regardless of whether Hibernate resolves it correctly, because nothing here boots
  Hibernate (baseline note 6). All 7 are now bound parameters. The manual smoke gate is the only
  pre-production check for this class of change until the optional Sprint 7 lands.
- **Request DTOs deliberately still speak `String`.** `PayRequest.method` keeps its
  `@Pattern(regexp = "MOMO|VNPAY|MOCK")`; `OrderService.pay` parses it explicitly. An enum-typed
  record component would make Jackson throw `HttpMessageNotReadableException`, which the catch-all
  handler turns into `500 INTERNAL_ERROR` — silently breaking the `400 VALIDATION_FAILED` contract
  the frontend depends on.
- **Deviation from the plan: 13 enums, not 15.** The plan's count double-counted; the vocabularies
  it enumerated map to 13 types. No vocabulary was dropped.

## Sprint 2 — `DomainException` + `ErrorCatalog` ✅

Goal: give aggregates an exception they can throw without importing `HttpStatus`, without changing
the API contract or touching a single existing call site.

| # | Change | Status |
|---|---|---|
| 1 | New `shared/DomainException` (`@NamedInterface("errors")`) — a stable error `code` + message, **no HTTP status**. This is what an aggregate throws | ✅ |
| 2 | `AppException` re-parented to extend it; its public signature `(code, message, status)` and both getters are byte-identical | ✅ |
| 3 | New module-private `shared/ErrorCatalog` — a code → `HttpStatus` table extracted from all 32 existing `new AppException(...)` call sites, defaulting to **409 CONFLICT**. Deliberately *not* a named-interface type | ✅ |
| 4 | `GlobalExceptionHandler` gains `@ExceptionHandler(DomainException.class)`; Spring's most-specific dispatch keeps `AppException` on the existing handler with its pinned status | ✅ |
| 5 | Facet tables updated in `CLAUDE.md`, `architecture/modulith/README.md` and `adr/0012` (`shared::errors` gains one type); `shared/package-info.java` javadoc rewritten to distinguish the two exceptions and note that `ErrorCatalog` stays unpublished | ✅ |
| 6 | New `shared/DomainErrorHandlingTest` (20 tests) pinning the subtype relationship, all 9 registered mappings, the 409 default, and that both handlers render the identical envelope | ✅ |

### Impact

- **Aggregates can now enforce invariants without knowing HTTP exists** — the precondition for
  Sprints 3–5, where the state machines move into `EventSeat`, `Order` and `Ticket`.
- **Zero changes outside `shared`.** All 32 `new AppException(code, msg, HttpStatus.X)` sites compile
  untouched, and the existing `isInstanceOf(AppException.class)` and `.extracting("status")`
  assertions in 8 test classes still pass — confirmed by the sprint needing **no test edits at all**.
- **The 409 default is load-bearing, not a fallback.** Every aggregate-level rule this system has
  (`SEAT_TAKEN`, `LOCK_EXPIRED`, `ORDER_STATE_INVALID`, `ORDER_ALREADY_PAID`, `TICKET_ALREADY_USED`)
  already answers 409, so a new invariant is correct without anyone remembering to register it.
- **Tests.** 758 → 778 (+20 `DomainErrorHandlingTest`).

### Verification

| Check | Result |
|---|---|
| `cd backend && mvn clean test` (JDK 21) | ✅ BUILD SUCCESS — 778 tests, 0 failures, 0 errors |
| Existing tests edited | ✅ **none** — the re-parenting is source-compatible by construction |
| `ModularityTests` | ✅ green and **unchanged** — it asserts facet *names*, and `shared::errors` already existed; no `allowedDependencies` changed because all 6 consumers already declare it |
| Rendered envelope for a bare `DomainException("SEAT_TAKEN", …)` | ✅ `409` + `{ error: { code: "SEAT_TAKEN", message, details: [], traceId } }` — pinned by `domainException_rendersTheStandardEnvelopeWithTheCatalogStatus` rather than a throwaway probe |
| Behaviour / API / schema | unchanged |

### Notes

- **Subclassing rather than replacing was the whole point.** The alternative — making `AppException`
  HTTP-free and routing every code through the table — touches 32 call sites and 3 test assertions
  in the same change that moves the state machines. The table now exists and can absorb them
  incrementally, one module at a time, whenever that is worth doing.
- **`ErrorCatalog` is package-private on purpose.** It is how `shared` renders errors, not a contract
  other modules consume. Publishing it would invite modules to reason about HTTP status codes, which
  is exactly the coupling this sprint removes.
- **The `handleApp` / `handleDomain` dispatch is worth a test, not a comment.** Now that
  `AppException extends DomainException`, a single-handler mistake would silently reroute every
  service exception through the catalog. `EVENT_NOT_FOUND` (catalog: 404, pinned: 404) would hide
  such a bug, so `appException_keepsItsPinnedStatusRatherThanTheCatalogDefault` asserts the
  dispatch explicitly.

## Sprint 3 — Seat aggregate; close the mutation back doors ✅

Goal: make `EventSeat` the only code that can change a seat's state, without altering the
transactional or caching guarantees the Golden Hour depends on.

| # | Change | Status |
|---|---|---|
| 1 | New `catalog/internal/SeatLock` (`@Embeddable`) binding `lockedBy` + `lockedUntil` into one value. Maps onto the same two columns with no `@AttributeOverride` — schema-neutral | ✅ |
| 2 | New `catalog/internal/LockPolicy(Duration ttl)` with `DEFAULT` = 10 minutes, replacing `SeatInventoryImpl.LOCK_TTL_MINUTES` | ✅ |
| 3 | **`EventSeat` becomes the aggregate root.** `@Setter`/`@Builder` removed; `@NoArgsConstructor(PROTECTED)` for JPA + `@AllArgsConstructor(PACKAGE)` as the test seam. Public API: `create`, `requireBelongsTo`, `isLockableAt`, `lockFor`, `markSold`, `releaseHold`, `releaseSale`, `releaseExpiredLock`, `reprice`, `relabelSection`, `isSold`, `isLockExpiredAt`, `lockedBy`, `lockedUntil`, `label` | ✅ |
| 4 | `SeatInventoryImpl` reduced from 161 to ~120 lines and now purely orchestrates: load → one aggregate call per seat → `saveAll` → evict. Takes the existing `Clock` bean so the aggregate never reads the clock | ✅ |
| 5 | **Back door 1 closed:** `SeatLockSweeperJob` calls `EventSeat.releaseExpiredLock(now)` instead of writing `setStatus`, and re-checks expiry against the same instant — so a hold renewed between the query and the sweep is no longer yanked from its buyer | ✅ |
| 6 | **Back door 2 closed:** `AdminEventService.updateSection` uses `relabelSection` + `reprice`, with an up-front check rejecting the whole request when a price change would touch a SOLD seat (bug (b)) | ✅ |
| 7 | `EventSeat.builder()` in `AdminEventService` and `CatalogDataSeeder` → `EventSeat.create(...)`; `EventSeatRepository`'s expired-lock query navigates `s.lock.lockedUntil` | ✅ |
| 8 | New `catalog/internal/CatalogFixtures` test seam; `SeatInventoryReliabilityTest`, `SeatLockSweeperJobTest`, `ReliabilityMatrixTest` and `AdminEventServiceReliabilityTest` migrated onto it | ✅ |
| 9 | New pure-unit `EventSeatTest` (20) and `SeatLockAndPolicyTest` (7) — no Mockito, no Spring. `ReliabilityMatrixTest`'s seat matrix now calls the real `EventSeat.isLockableAt` | ✅ |
| 10 | New `updateSection` regression tests (3) covering refuse-on-sold, rename-without-reprice, and reprice-when-unsold | ✅ |

### Impact

- **One writer.** `EventSeat` is now the only code in the system that can change a seat's status,
  price or hold. The sweeper, the admin screens, the seeder and the ordering path all go through the
  same checked transitions.
- **Bug (b) fixed.** Repricing a SOLD seat is refused with `SEAT_SOLD_IMMUTABLE`. This was not
  cosmetic: `sumSoldPriceForEvent` totals sold seats' prices, so an ordinary section edit silently
  rewrote revenue that had already been collected and reported.
- **A latent sweeper race closed as a by-product.** The old job trusted its query result and reset
  every row it loaded. `releaseExpiredLock` re-checks expiry against the sweep's own instant, so a
  hold taken or renewed in the window between query and write survives.
- **`SeatLock` makes half-set holds unrepresentable** — `lockedBy` without `lockedUntil`, or the
  reverse, no longer type-checks.
- **The concurrency guarantees are byte-for-byte intact:** `@Transactional` on all four mutators,
  `@Version` optimistic locking, `saveAll` before eviction, and all three cache evictions on every
  mutation. `OrderService.pay()` remains a single transaction.
- **Tests.** 778 → 806 (+28: +18 `EventSeatTest`, +7 `SeatLockAndPolicyTest`, +3 `updateSection`).

### Verification

| Check | Result |
|---|---|
| `cd backend && mvn clean test` (JDK 21) | ✅ BUILD SUCCESS — 806 tests, 0 failures, 0 errors |
| `ModularityTests` | ✅ green — `SeatLock`/`LockPolicy`/`CatalogFixtures` are all module-internal; the `catalog::inventory` facet is unchanged |
| **Negative check** — reintroduce `s.setStatus(...)` / `s.setLockedUntil(null)` in `SeatLockSweeperJob` | ✅ now **fails to compile**: `cannot find symbol` ×2. The back door is not merely discouraged, it is unavailable |
| **Negative check** — reprice a section containing a SOLD seat | ✅ `409 SEAT_SOLD_IMMUTABLE`, `saveAll` never called, nothing half-applied — pinned by `updateSection_givenSoldSeats_refusesToRewriteRealisedRevenue` |
| `markSold` guard ordering (`SEAT_TAKEN` before `LOCK_EXPIRED`) | ✅ preserved — pinned by the existing `@CsvSource` and by `anAlreadySoldSeatReportsSeatTakenRatherThanLockExpired` |
| DB schema | unchanged — the embeddable maps to the existing `locked_by` / `locked_until` columns, no migration |

### Notes

- **A no-op reprice is still a reprice.** The first cut called `reprice(req.price())` unconditionally
  inside the rename loop, so renaming a section containing sold seats threw even when the price was
  identical. Caught by `updateSection_givenSoldSeatsButNoPriceChange_stillRenames`. The service now
  reprices only when the value actually moves — which is also why the up-front guard tests for a
  *change* rather than for the mere presence of a price.
- **The sweeper stayed out of the published facet.** Adding `releaseExpiredLocks()` to
  `SeatInventory` would have handed every consumer a way to mass-release holds. The invariant that
  matters is "only the aggregate mutates seat state", not "only the facet does", so the job remains
  in `catalog.internal` and calls `EventSeat` directly. Zero facet change.
- **`@Embedded` reads back as `null`, not as an all-null instance.** `EventSeat` therefore never
  exposes the `lock` field; callers use `lockedBy()`, `lockedUntil()` and `isLockExpiredAt(now)`,
  which null-check internally. The repository query navigates `s.lock.lockedUntil`.
- **This is the first sprint whose mapping change `mvn test` genuinely cannot verify.** The
  embeddable is schema-neutral by construction, but nothing in the suite boots Hibernate, so the
  manual smoke gate — seat map, lock countdown, sweeper log line — is the real check here.
- **`SeatInventoryImpl` gained a constructor parameter**, so its two test constructors were updated.
  `SeatLockSweeperJobTest` moved off `@InjectMocks`: a mocked `Clock` hands the aggregate a null
  instant, so the job is now built explicitly with `Clock.systemUTC()`.

## Sprint 4 — Order/Payment aggregates, `Money`, payment failure path ✅

Goal: give the order lifecycle an owner, give the arithmetic a type, and make a declined payment
actually fail.

| # | Change | Status |
|---|---|---|
| 1 | New `sales/internal/Money` — `zero/of/sum/plus/isZero`, rejects negatives at construction, equality by amount rather than by scale. **Domain-only**: the columns stay `BigDecimal` so the four `SUM(o.totalAmount)` queries keep working | ✅ |
| 2 | `Order` becomes an aggregate root: `place`, `pay`, `cancel`, `isOwnedBy`, `isPayable`, `isPaid`, `total()`. Setters and builder removed | ✅ |
| 3 | `OrderItem.forSeat(...)` + `amount()`; `Payment.record(...)` + `isSucceeded()`; `PaymentRetry.attempt(...)` + `static nextAttemptNo(long)`. All three lose their setters and builders | ✅ |
| 4 | `OrderService` delegates ownership and state to the aggregate, totals with `Money.sum(...)`, and takes the `Clock` bean instead of calling `Instant.now()` | ✅ |
| 5 | **Bugs (a) part 2 and (c): the payment failure path is wired.** `OrderService.pay` now reads `PaymentResult.success()`; on a decline it records the payment row, appends a `PaymentRetry` and throws `PAYMENT_FAILED` (402), rolling the transaction back | ✅ |
| 6 | `PaymentRetryService.recordAttempt` gains its first caller in the system's history, takes a typed `PaymentRetryStatus`, and stamps `attemptedAt` from the `Clock` | ✅ |
| 7 | New `sales/internal/SalesFixtures` test seam, including `withId(...)` which stamps a generated key the way Hibernate does — by reflection, rather than reopening the aggregate with a `setId` | ✅ |
| 8 | New pure-unit `OrderAggregateTest` (17) covering `Money`, the order lifecycle, payment records and retry numbering; new `pay_givenDeclinedCharge_recordsARetryAndIssuesNoTickets` | ✅ |

### Impact

- **A declined payment can no longer mint tickets.** `PaymentResult.success()` had existed and gone
  unread since the field was written, so the only reason no customer ever got free tickets is that
  `MockPaymentGateway` never declines. The branch is now real and tested against a declining gateway.
- **The payment funnel can move.** Declines are persisted as `PaymentStatus.FAILED` rows, so
  `AnalyticsService`'s `countPaymentsByStatus("FAILED")` — which structurally could only ever return
  0 — has something to count. Combined with Sprint 1's lenient parsing, bug (a) is fully closed.
- **`PaymentRetry` stops being dead infrastructure.** Table, entity and service existed with zero
  callers; `recordAttempt` is now invoked on every decline.
- **Order state has one owner.** `isPayable`/`isPaid`/`cancel` replaced `if/else` chains that
  answered the same question in three places.
- **`Money` gives the arithmetic a home** without touching a column. It also fixes a latent trap:
  `BigDecimal.equals` distinguishes `100` from `100.00`, `Money.equals` does not.
- **Tests.** 806 → 824 (+18: +17 `OrderAggregateTest`, +1 payment-decline regression).

### Verification

| Check | Result |
|---|---|
| `cd backend && mvn clean test` (JDK 21) | ✅ BUILD SUCCESS — **824 tests**, 0 failures, 0 errors |
| `ModularityTests` | ✅ green — `Money`, `SalesFixtures` and the aggregates are all module-internal; `sales::reporting` unchanged |
| Declined charge | ✅ `402 PAYMENT_FAILED`, a `FAILED` payment row, one `PaymentRetry`, **no** ticket issuance, **no** notification, order left `PENDING` — pinned by `pay_givenDeclinedCharge_recordsARetryAndIssuesNoTickets` |
| Idempotent re-pay | ✅ preserved at both levels — `OrderService` returns early on `isPaid()`, and `Order.pay` is itself a no-op when already paid |
| `OrderService.pay()` remains a single transaction | ✅ unchanged — no propagation attribute was touched |
| DB schema / wire format | unchanged |

### Notes

- **Two behaviours changed shape, not outcome.** `Order.cancel()` on a paid order and
  `Order.pay()` in a terminal state now raise `DomainException` rather than `AppException`. Both
  codes (`ORDER_ALREADY_PAID`, `ORDER_STATE_INVALID`) are unregistered in `ErrorCatalog` and so still
  render **409** — the client sees no difference. One test assertion moved from `AppException` to
  `DomainException` to say so explicitly.
- **`PAYMENT_FAILED` is deliberately `402 Payment Required`**, not the 409 default, so it is
  registered on the `AppException` at the throw site rather than in `ErrorCatalog`. A decline is not
  a state conflict; the client should offer another method.
- **`MockPaymentGateway` still always succeeds.** The decline path is exercised by a test-local
  `DecliningGateway`. Injecting randomness into the mock would make the whole suite flaky in exchange
  for nothing — the branch is what needed to exist, not a dice roll.
- **`SalesFixtures.withId` uses reflection on purpose.** Several tests stub `repository.save(...)` to
  mimic the database assigning a key to the instance the service still holds. Adding a
  production-visible `setId` to satisfy a mock would reopen the aggregate to exactly the arbitrary
  mutation this refactor closed off; identity assignment is the persistence layer's job, so the test
  seam does what Hibernate does.
- **Correction to the Sprint 3 figures recorded above.** The per-sprint totals were being read with a
  script that summed one line per surefire report file, which silently skips `@Nested` classes.
  Maven's own `Results:` line is authoritative. Sprint 3's true total was **806**, not the 788 first
  recorded (`EventSeatTest` contributes 18 tests, all of which did run); the Impact and Verification
  rows in Sprint 3 have been corrected in place. Sprints 0–2 are unaffected — they had no nested test
  classes — so the 727 → 758 → 778 progression stands.

## Sprint 5 — Remaining aggregates, `QrCode`, docs closeout ✅

Goal: finish the job across all nine modules, so **no entity anywhere** can be mutated arbitrarily.

| # | Change | Status |
|---|---|---|
| 1 | New `ticketing/internal/QrCode` — a generator value object with the `32 × [0-9A-F]` invariant. The column and `findByQrCode(String)` stay `String`, because gate scanners hand us arbitrary input | ✅ |
| 2 | `Ticket`: `issue`, `isOwnedBy`, `isScannable(existingCheckIn)`, `markUsed`, `cancel`. `CheckIn.record(...)` | ✅ |
| 3 | `Event`: `draft`, `isOnSale`, `isDeletable`, `publish(seatCount)`, `revertToDraft(hasSoldSeats)`, `cancel`, `complete`, `changeStatusTo(...)`, `describe`, `reschedule`, `categorise`. `AdminEventService.changeStatus` collapses into one `changeStatusTo` call | ✅ |
| 4 | `TicketType`: `create`, `addCapacity`, `reprice`. `User`: `register`, `normaliseEmail`, `isActive`, `grant`, `updateProfile`, `changePassword`. `Feedback`: `submit`, `moveTo`, `attachAdminNote`. `Notification`: `send`, `isUnread`, `isOwnedBy`, `markReadAt`. `AuditLog.of` | ✅ |
| 5 | Reference data (`Venue`, `Section`, `Seat`, `EventCategory`, `Role`) gets factories and no behaviour — the correct outcome for reference data, recorded as such rather than left looking unfinished | ✅ |
| 6 | **All 14 entities now carry `@Getter` + `@NoArgsConstructor(PROTECTED)` + `@AllArgsConstructor(PACKAGE)`.** `@Setter` and `@Builder` appear nowhere in `src/main` | ✅ |
| 7 | 12 services/controllers/seeders migrated onto the factories, including `AuthService` (which hands e-mail normalisation to `User`), `UserController`, `CheckInService`, `TicketService`, `AuditAspect`, both seeders | ✅ |
| 8 | Four new fixture classes (`TicketingFixtures`, `FeedbackFixtures`, `NotificationFixtures`, `IamFixtures`); `CatalogFixtures` gains `event(...)` and `withId(...)` | ✅ |
| 9 | New pure-unit `TicketAggregateTest` (34) and `EventAggregateTest` (15). `ReliabilityMatrixTest`'s ticket matrix now builds through a fixture rather than a bare constructor | ✅ |
| 10 | **Docs closeout** — ADR-0013 → **Accepted** + index row; new **"Domain model (tactical DDD)"** section in `CLAUDE.md` stating all nine rules; `CLAUDE.md` frontend section corrected to Next.js 14 + App Router + TypeScript (finding #8), including the stale `VITE_API_BASE_URL` convention; this sheet | ✅ |

### Impact

- **The refactor's central claim is now true everywhere**: `grep -rl "@Setter\|@Builder" src/main` returns
  nothing. Every state change in the system goes through a method that checked an invariant first.
- **Event publication rules moved out of the service.** `AdminEventService.changeStatus` was a chain
  of `if` statements mixing parsing, seat queries and assignment; it is now a parse, two repository
  reads, and `e.changeStatusTo(target, seatCount, hasSoldSeats)`. The seat facts are passed in
  because seat inventory is a different aggregate — the aggregate does not get to query.
- **E-mail normalisation has one owner.** `User.normaliseEmail` is used by both `register` and
  `AuthService.register`'s existence check, so the two can no longer disagree about what a duplicate
  address is. (Note this is *not* the rejected `Email` value object — it is one static method on the
  aggregate that already owns the field, which costs nothing.)
- **The QR generator is finally tested.** `QrCode.generate()` is now the single definition of the
  shape; the 20 repeated + 500-sample uniqueness assertions exercise production code rather than a
  copy of it.
- **Tests.** 824 → 881 (+57: +34 `TicketAggregateTest`, +15 `EventAggregateTest`, +8 from nested
  parameterised expansion).

### Verification

| Check | Result |
|---|---|
| `cd backend && mvn clean test` (JDK 21) | ✅ BUILD SUCCESS — **881 tests**, 0 failures, 0 errors |
| `ModularityTests.verify()` + `exposesTheDeclaredNamedInterfaces()` | ✅ green — the 10-facet table is byte-identical to the tracking-8 baseline apart from `shared::errors` gaining `DomainException` in Sprint 2 |
| **`grep -rl "@Setter\|@Builder" src/main`** | ✅ **no matches** — no entity in any module is open to arbitrary mutation |
| `grep` for `set*` calls on entities in `src/main` | ✅ none; remaining hits are Spring/JDK APIs (`SecurityContextHolder.setAuthentication`, `BigDecimal.setScale`, …) |
| DB schema / wire format / API contract | unchanged — no migration across all six sprints |

### Notes

- **`CatalogDataSeeder` now publishes explicitly.** `Event.draft(...)` starts in `DRAFT` by
  definition, so the demo seeder calls `publish(1)` immediately afterwards to keep the sample catalog
  on sale as before. Making the factory take a status would have re-opened the very hole the
  aggregate closes.
- **Reference-data entities getting only a factory is the answer, not a shortfall.** `Venue`,
  `Section`, `Seat`, `EventCategory` and `Role` have no lifecycle to model. Inventing transitions for
  them would be the same mistake as inventing the `Email` value object.
- **Two ADR-0013 figures were corrected while marking it Accepted.** It was drafted saying "15 enums"
  (the vocabularies it enumerated map to 13 types) and listed `SeatLabel` among the value objects that
  exist. `SeatLabel` is **deferred**: it was sequenced last precisely because nothing depends on it,
  and the four VOs that did land — `Money`, `SeatLock`, `LockPolicy`, `QrCode` — each removed a
  representable-but-invalid state, which `SeatLabel` would not have. Recording it as deferred rather
  than quietly dropping it keeps the option open.
- **`CLAUDE.md`'s frontend section was substantially wrong**, not just the framework name: it
  described a React Router SPA with `.js` services and `pages/` routing. The actual app is Next.js 14
  App Router with TypeScript services and `app/` segments. Corrected against the real tree, and the
  `VITE_API_BASE_URL` convention line updated to `NEXT_PUBLIC_API_BASE_URL`.
- **Sprints 6 and 7 remain open and optional.** Sprint 6 (`SeatLabel`) is droppable by design.
  Sprint 7 — a `@DataJpaTest` persistence slice — is the one genuinely worth doing: baseline
  finding 6 (no test boots Hibernate) is *still true*, so six sprints of mapping changes rest
  entirely on the manual smoke gate.

## Sprint 6 — `SeatLabel` embeddable (optional) — **not done, deferred**

Cut deliberately. It was sequenced last because nothing downstream depends on it, and unlike the four
value objects that did land it removes no representable-but-invalid state — `(section, rowLabel,
seatNumber)` is a legitimate triple, just a verbose one. The cost (two repository method renames plus
~10 call sites) buys tidiness, not correctness. The design stays recorded in
[`../../TACTICAL_DDD_REFACTOR_PLAN.md`](../../TACTICAL_DDD_REFACTOR_PLAN.md) should that trade change.

## Sprint 7 — Persistence slice test (optional) — **not done, recommended next**

The highest-value work remaining, and the one open risk this iteration did not close. Baseline
finding 6 still holds: **no test in this repository boots Hibernate, Spring or a database.** Six
sprints of `@Enumerated` mappings, an `@Embedded` value object and seven rewritten `@Query` methods
therefore rest entirely on the manual smoke gate. A `@DataJpaTest` with an embedded MySQL-mode
database and three tests would close it:

1. round-trip every enum-bearing entity and assert the persisted column string via native SQL;
2. exercise all seven rewritten `@Query` methods, including `s.lock.lockedUntil`;
3. boot Hibernate with `ddl-auto: validate` against the Flyway-migrated schema.

It is listed separately because it is new test infrastructure rather than a refactor, and it should
be judged on its own merits.
