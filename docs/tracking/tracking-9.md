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

## Sprint 1 — Typed vocabularies

_Pending._

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
