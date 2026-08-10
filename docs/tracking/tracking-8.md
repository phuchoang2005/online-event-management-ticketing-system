# Tracking Sheet — Iteration 8

> Date: 2026-08-10
> Scope: narrow the Modulith API surface from "the whole base package" to
> explicitly published **named interfaces**, so build-time enforcement answers
> *what* one module may reach in another, not merely *whether*. Pure metadata —
> no file moves, no import changes, no logic/schema/API change.
> Baseline: [`tracking-7.md`](./tracking-7.md). Plan of record:
> [`../../SPRING_MODULITH_REFACTOR_PLAN.md`](../../SPRING_MODULITH_REFACTOR_PLAN.md)
> (Sprint 5). Decision record: [`../adr/0012-named-interfaces.md`](../adr/0012-named-interfaces.md).

---

## Sprint 5 — Named interfaces as the published API surface ✅

Goal: make each module's cross-module contract explicit and least-privilege, and
pin it in the test suite.

| # | Change | Status |
|---|---|---|
| 1 | Restore the `spring.modulith.version` property (`1.1.12`) in `backend/pom.xml` — it had been deleted in the working tree while line 36 still referenced `${spring.modulith.version}` for the BOM import, leaving the build unable to resolve dependencies | ✅ |
| 2 | Annotate the 13 published types **and every nested record** with `@NamedInterface`, in place: `shared::errors` (`AppException`, `ApiErrorEnvelope`+2), `shared::security` (`AuthPrincipal`, `CurrentUser`), `shared::audit` (`Auditable`), `shared::contracts` (`TicketsIssuedEvent`, `EventDeletedEvent`), `iam::directory` (`UserDirectory`+1), `catalog::events` (`EventCatalog`+2), `catalog::inventory` (`SeatInventory`+1), `ticketing::issuance` (`TicketIssuance`+2), `ticketing::reporting` (`TicketingReporting`), `sales::reporting` (`SalesReporting`+1) | ✅ |
| 3 | Rewrite all 9 `package-info.java` `allowedDependencies` to facet form (`"catalog::events"`, …) and refresh their javadoc to name the facets and what each module can no longer reach | ✅ |
| 4 | Drop `analytics`' `"shared"` dependency entirely — it imports no kernel type at all | ✅ |
| 5 | Add `ModularityTests.exposesTheDeclaredNamedInterfaces()` pinning the facet table per module, so a dropped annotation fails at its cause instead of as a downstream violation | ✅ |
| 6 | Docs: new [`adr/0012-named-interfaces.md`](../adr/0012-named-interfaces.md) (+ ADR index row); mark the superseded parts of [`adr/0011`](../adr/0011-spring-modulith.md) and **correct its factually wrong rejection rationale**; add the facet table + two traps to `architecture/modulith/README.md`; update `architecture/system-architecture.md` §3; add Sprint 5 to `SPRING_MODULITH_REFACTOR_PLAN.md` and flag its stale "OPEN `shared`" rows; rewrite the module-structure section of `CLAUDE.md`; this tracking entry | ✅ |

### Impact

- **Least privilege between modules.** Before this sprint `sales` declared `"catalog"` and could
  therefore legally inject `AdminEventService`, `EventService` or `SeatCatalogService` into
  `OrderService` — `verify()` passed. Now `sales` declares `catalog::events` + `catalog::inventory`
  and that reference fails the build. Splitting `ticketing` into `issuance` vs `reporting` (and
  `catalog` into `events` vs `inventory`) additionally means `sales` cannot report and `analytics`
  can neither issue tickets nor touch the concurrency-critical seat state machine.
- **Kernel sliced, not moved.** `shared` stays physically flat (Modulith 1.1 has no open modules) but
  is now four facets, so each module names the cross-cutting concerns it actually uses.
  `GlobalExceptionHandler`, `TraceIdFilter` and `HealthController` are Spring-wired and imported by
  nobody, so they stay in the unnamed interface and became unreachable across boundaries.
- **ADR-0011 correction.** Its rejection of named interfaces rested on "1.1.12 does not merge
  same-named interfaces across packages". Verified false against the pinned jars:
  `NamedInterfaces.discoverNamedInterfaces` = `unnamed(pkg).and(ofAnnotatedPackages(pkg)).and(ofAnnotatedTypes(pkg))`
  with `and()` merging by name, and `@NamedInterface` is `@Target({PACKAGE, TYPE})`. No Modulith/Boot
  upgrade was needed; `spring-modulith-starter-core` is compile scope, not runtime as ADR-0011 stated.
- **Zero churn in application code.** Every type stayed in its package, so the ~60 cross-module import
  lines across 20 main and test files are untouched and `OrderService.pay()`'s single-transaction
  lock→sell→issue path is byte-for-byte unchanged.
- **Tests.** 724 → 725 (`exposesTheDeclaredNamedInterfaces`).

### Verification

| Check | Result |
|---|---|
| `cd backend && mvn test` (JDK 21) | ✅ BUILD SUCCESS — 725 tests, 0 failures, 0 errors |
| `ModularityTests.verify()` | ✅ green — no cycles, no boundary violations under the narrowed facets |
| `ModularityTests.exposesTheDeclaredNamedInterfaces()` | ✅ green — 10 facets across `shared`/`iam`/`catalog`/`ticketing`/`sales` |
| **Negative check** — temporarily inject `catalog.AdminEventService` into `sales.OrderService` (legal and verified-clean before this sprint) | ✅ now **fails**: `Module 'sales' depends on module 'catalog' via …OrderService -> …AdminEventService. Allowed targets: catalog::events, catalog::inventory, ticketing::issuance, shared::errors, shared::security, shared::audit, shared::contracts.` Probe reverted; suite re-run green |
| `Documenter` regeneration | ✅ runs clean; **no semantic diff** — see Notes |
| Behaviour / API / schema | unchanged |

### Notes

- **Modulith 1.1.12 parsing bug — facet references must have no spaces around `::`.**
  `ApplicationModule.DeclaredDependency.of` splits the identifier on `"::"`, trims the *module*
  segment, but looks the named interface up with the **untrimmed** one — then formats the error with
  the trimmed name. So the upstream-documented `"catalog :: events"` fails with
  `No named interface named 'events' found!` for an interface that demonstrably exists. Cost an
  investigation this sprint; the constraint is now recorded in ADR-0012, `CLAUDE.md`, and
  `shared/package-info.java`.
- **Nested types need their own annotation.** `EventCatalog.EventSummary` etc. are separate classes to
  Modulith; annotating only the enclosing interface leaves the records in the unnamed interface and
  breaks every consumer. All 8 nested records are annotated.
- **Deviation from the plan: generated docs not re-committed.** `Documenter` 1.1.12 does not render
  named interfaces into the module canvases — regenerating produced only nondeterministic reordering
  in 4 `.puml` files and no `.adoc` change at all. Committing that would have been pure noise, so the
  snapshot is left as-is and the facet table is maintained by hand in
  `docs/architecture/modulith/README.md`, which now says so.
- **Pre-existing working-tree breakage.** `backend/pom.xml` arrived with `spring.modulith.version`
  deleted, so the build could not resolve the Modulith BOM. Restored to `1.1.12` (change #1) and the
  baseline confirmed green before any refactor work began.

---

## Iteration 8 — done

The Modulith API surface is now explicit: 10 named interfaces across 5 modules, every consumer
declaring the exact facets it uses, and both the boundaries and the facet table enforced by
`mvn test`. Runtime behaviour, schema, and API are unchanged from the
[tracking-7](./tracking-7.md) baseline.
