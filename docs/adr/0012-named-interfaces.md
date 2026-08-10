# ADR-0012: Named interfaces as the published module API surface

- **Status:** Accepted
- **Date:** 2026-08-10
- **Deciders:** Tech lead
- **Supersedes:** partially supersedes [ADR-0011](0011-spring-modulith.md) — its
  "API = the whole base package" rule and its "shared kernel is flat, named interfaces
  rejected" paragraph. Everything else in ADR-0011 (the 9-module slicing, the DAG, the
  three cycle-breakers, `verify()` as the build gate) stands.

## Context

ADR-0011 defined a module's API as **its entire base package**. Enforcement then only
answers "may module A talk to module B?", never "*to what in B?*" — which is far coarser
than what the modules actually publish:

- `catalog`'s base package holds 12 public types, of which exactly two (`EventCatalog`,
  `SeatInventory`) are meant for other modules. Because `sales` declared
  `allowedDependencies = {"catalog", …}`, injecting `AdminEventService`, `EventService`
  or `SeatCatalogService` into `OrderService` was **legal and verified clean**. The one
  regression the module split exists to prevent was undetectable.
- Same shape in `iam` (`AuthService`/`AuthController` exposed next to `UserDirectory`),
  `ticketing` (`TicketService`/`CheckInService` next to `TicketIssuance`) and `sales`
  (`OrderService` next to `SalesReporting`).
- `analytics` needed `ticketing`'s read-only aggregates but was granted the whole module,
  including ticket *issuance*.

ADR-0011 rejected named interfaces on a premise that does not hold for the pinned
version. Verified directly against `spring-modulith-api`/`-core` **1.1.12**:

| Claim in ADR-0011 | Actual 1.1.12 behaviour |
|---|---|
| "1.1.12 does not merge same-named interfaces across packages" | `NamedInterfaces.discoverNamedInterfaces` = `unnamed(pkg).and(ofAnnotatedPackages(pkg)).and(ofAnnotatedTypes(pkg))`, and `and()` **merges** by name via `NamedInterface.merge()` |
| (implied) `@NamedInterface` is package-only, so facets need sub-packages | `@NamedInterface` is `@Target({PACKAGE, TYPE})`; `ofAnnotatedTypes` walks the whole module tree via `JavaPackage.stream()` |
| Needs Modulith 1.2 / Boot 3.3 | Nothing here needs 1.2. `spring-modulith-starter-core` is **compile** scope, so the annotation is already on the main compile classpath |

The remaining true part of ADR-0011 stands: `OPEN_TOKEN` constrains only a module's
*outgoing* dependencies, and 1.1 has no `type = Type.OPEN`.

## Decision

**A module exposes across boundaries only the types annotated `@NamedInterface`.**
Consumers declare `allowedDependencies = "<module>::<facet>"`.

Declared **on the types themselves**, not by moving them into API sub-packages:

```java
@NamedInterface("inventory")
public interface SeatInventory {

    List<SeatDetail> lockSeats(Long eventId, Long userId, List<Long> seatIds);

    @NamedInterface("inventory")
    record SeatDetail(Long id, /* … */ String status) {}
}
```

The facets:

| Facet | Types |
|---|---|
| `shared::errors` | `AppException`, `ApiErrorEnvelope` (+ `ErrorBody`, `FieldDetail`) |
| `shared::security` | `AuthPrincipal`, `CurrentUser` |
| `shared::audit` | `Auditable` |
| `shared::contracts` | `TicketsIssuedEvent`, `EventDeletedEvent` |
| `iam::directory` | `UserDirectory` (+ `UserRef`) |
| `catalog::events` | `EventCatalog` (+ `EventSummary`, `EventStats`) |
| `catalog::inventory` | `SeatInventory` (+ `SeatDetail`) |
| `ticketing::issuance` | `TicketIssuance` (+ `TicketOrder`, `TicketLine`) |
| `ticketing::reporting` | `TicketingReporting` |
| `sales::reporting` | `SalesReporting` (+ `DailyRevenue`) |

`analytics`, `audit`, `feedback`, `notification` publish nothing. The resulting DAG,
now facet-precise:

```
iam          → shared::errors, shared::security
catalog      → shared::errors, shared::contracts
ticketing    → catalog::events, catalog::inventory,
               shared::errors, shared::security, shared::contracts
sales        → catalog::events, catalog::inventory, ticketing::issuance,
               shared::errors, shared::security, shared::audit, shared::contracts
notification → iam::directory, shared::errors, shared::security, shared::contracts
feedback     → catalog::events, iam::directory, shared::errors, shared::security
analytics    → catalog::events, sales::reporting, ticketing::reporting
audit        → shared::audit, shared::security
shared       → OPEN_TOKEN (outgoing only; references no module)
```

**The kernel is sliced too.** It stays physically flat — the facets are annotations, not
packages — but a module now names the facets it uses instead of taking the whole kernel.
`GlobalExceptionHandler`, `TraceIdFilter` and `HealthController` are Spring-wired and
imported by nobody, so they stay in the unnamed interface and become unreachable across
boundaries. `analytics` uses no kernel type at all and declares no `shared` facet.

**`ModularityTests.exposesTheDeclaredNamedInterfaces()` pins the table above**, because
`verify()` alone cannot catch a *dropped* annotation: the type would silently fall back
into the unnamed interface and surface later as a confusing violation in a consumer.

### Two traps, both load-bearing

1. **Nested types need their own annotation.** Modulith treats `EventCatalog.EventSummary`
   as a separate class; unannotated, it stays in the unnamed interface while its enclosing
   interface is exposed, and every consumer using the record breaks.
2. **No spaces around `::`.** Write `"catalog::events"`. Modulith 1.1.12's
   `ApplicationModule.DeclaredDependency.of` splits on `"::"`, trims the *module* segment,
   but looks the interface up with the **untrimmed** one — then reports the trimmed name in
   the error. The upstream-documented `"catalog :: events"` therefore fails with a
   thoroughly misleading `No named interface named 'events' found!` for an interface that
   demonstrably exists. Fixed in later Modulith lines; until we upgrade, the spaced form is
   banned.

## Consequences

**Easier:** the published API of every module is now explicit, greppable and pinned by a
test. Reaching for a non-API type fails the build *at the reference*, naming the allowed
facets. Splitting `ticketing` into `issuance` vs `reporting` (and `catalog` into `events`
vs `inventory`) means `sales` cannot report and `analytics` cannot issue tickets or touch
the concurrency-critical seat state machine — least privilege between modules, not just
between layers.

**Harder:** publishing a new cross-module type is now two steps (annotate it, add the
facet to the consumer) rather than one. Adding a nested record to an existing API type is
an easy thing to forget — trap #1 above.

**Accepted:** zero file moves and zero import changes, so this is a pure metadata change —
`OrderService.pay()`'s single-transaction lock→sell→issue path is untouched. The kernel is
no longer "freely usable": every consumer enumerates its facets, which is the friction
ADR-0011 wanted to avoid and we now take deliberately, in exchange for seeing exactly which
cross-cutting concerns each module actually depends on. `Documenter` 1.1.12 does not render
named interfaces into the module canvases, so the facet table is maintained by hand in
[`../architecture/modulith/README.md`](../architecture/modulith/README.md).

## Alternatives considered

- **Package-based named interfaces** (move each API type into `catalog/inventory/` etc.
  with `@NamedInterface` on its `package-info`). The canonical Modulith idiom, and nested
  records are covered automatically. Rejected: ~60 import lines across 20 main and test
  files for no enforcement gain, and it scatters a module's API across sub-packages instead
  of stating the contract on the type.
- **Leave the shared kernel open** (facets for capability modules only). Simpler
  `allowedDependencies`, but the kernel keeps its blanket grant and `analytics`' "uses no
  kernel type at all" stays invisible. Rejected in favour of full precision.
- **Coarse alias facet** (`@NamedInterface({"kernel", "errors"})`, letting a module declare
  either `shared::kernel` or the precise facets). Rejected: two ways to express the same
  dependency, and the loose one wins under deadline pressure.
- **Upgrade to Modulith 1.2 for open modules + the `::` trim fix.** Still rejected, and
  still for ADR-0011's reason: 1.2 targets Boot 3.3. Nothing here requires it.

## Implementation rules

- A type is cross-module API **only** if annotated `@NamedInterface`. Never widen a facet
  to make a call compile — ask whether the call belongs.
- Annotate **every nested type** you expose, with the same facet name.
- Write facet references **without spaces**: `"module::facet"`.
- Adding or removing a facet MUST update `ModularityTests.exposesTheDeclaredNamedInterfaces()`
  and the table in `docs/architecture/modulith/README.md`.
