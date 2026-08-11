# Tracking Sheet — Iteration 9

> Date: 2026-08-11
> Scope: tactical Domain-Driven Design **inside** the Modulith modules — typed
> vocabularies (enums) replacing 87 status string literals, five value objects,
> rich aggregates that own their own state transitions, and a domain exception
> free of `HttpStatus`. Three latent defects are fixed as a by-product. The
> module layout, the 10 published facets, the DB schema and the HTTP contract are
> **unchanged**.
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

## Sprint 2 — `DomainException` + `ErrorCatalog`

_Pending._

## Sprint 3 — Seat aggregate; close the mutation back doors

_Pending._

## Sprint 4 — Order/Payment aggregates, `Money`, payment failure path

_Pending._

## Sprint 5 — Remaining aggregates, `QrCode`, docs closeout

_Pending._

## Sprint 6 — `SeatLabel` embeddable (optional)

_Pending._

## Sprint 7 — Persistence slice test (optional)

_Pending._
