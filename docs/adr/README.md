# Architecture Decision Records (ADRs)

Short, dated records of architectural decisions for the Dề Dê ticketing platform. Each ADR captures **what** was decided, **why**, and **what we considered and rejected**. New decisions never edit old ones — they supersede.

## How to use

- Read the index below before raising a design question. Half the answers are already here.
- When you make a decision that meaningfully shapes the system, add a new ADR. Don't bury decisions in PR descriptions or chat.
- An ADR is a "decision contract" — once `Accepted`, deviating from it in code is a review-block.

## Template

```markdown
# ADR-NNNN: <title>

- **Status:** Proposed | Accepted | Superseded by ADR-XXXX
- **Date:** YYYY-MM-DD
- **Deciders:** <names / roles>
- **Context:** What is the problem? What constraints matter? Link to NFRs / requirements.
- **Decision:** The choice, stated plainly.
- **Consequences:** What gets easier, what gets harder, what we accept in exchange.
- **Alternatives considered:** What we evaluated and why we said no.
```

## Index

| # | Title | Status |
|---|---|---|
| [0001](0001-monolith-spring-boot.md) | Spring Boot monolith for Sprint 1 | Accepted |
| [0002](0002-optimistic-locking-event-seats.md) | Optimistic locking via `EVENT_SEATS.version` | Accepted |
| [0003](0003-redis-advisory-cache-only.md) | Redis is advisory cache, never source of truth | Accepted |
| [0004](0004-notifications-table-as-queue.md) | `NOTIFICATIONS` table as queue (no broker for Sprint 1) | Accepted |
| [0005](0005-flyway-for-migrations.md) | Flyway for schema migrations | Accepted |
| [0006](0006-idempotency-key-strategy.md) | `Idempotency-Key` header + persisted result | Accepted |
| [0007](0007-jwt-authentication.md) | JWT for authentication | Accepted |
| [0008](0008-mysql-8-as-primary-store.md) | MySQL 8 as primary data store | Accepted |
| [0009](0009-offline-first-mobile-checkin.md) | Offline-first mobile check-in with local SQLite | Accepted |
| [0010](0010-single-instance-sweeper.md) | Single-instance seat-lock sweeper via DB advisory lock | Accepted |
| [0011](0011-spring-modulith.md) | Spring Modulith modules with enforced boundaries | Accepted (API surface superseded by 0012) |
| [0012](0012-named-interfaces.md) | Named interfaces as the published module API surface | Accepted |
| [0013](0013-tactical-ddd-aggregates.md) | Tactical DDD — typed vocabularies, value objects, rich aggregates | Proposed |
