# Rentle — System Design

> Related: [Features](./FEATURES.md) · [API Reference](./API.md) · [Business Requirements](./BUSINESS_REQUIREMENTS.md)

## Table of contents

1. [Overview](#1-overview)
2. [Architecture](#2-architecture)
3. [Domain model](#3-domain-model)
4. [Authentication & authorization (IAM)](#4-authentication--authorization-iam)
5. [Booking & availability](#5-booking--availability)
6. [Money flow](#6-money-flow)
7. [Category rollout platform](#7-category-rollout-platform)
8. [Configurability model](#8-configurability-model)
9. [Storage](#9-storage)
10. [Notifications](#10-notifications)
11. [Data & migrations](#11-data--migrations)
12. [Deployment & operations](#12-deployment--operations)

---

## 1. Overview

Rentle is a **modular monolith** — a single Spring Boot application organized into independent
domain modules that communicate through domain events, not cross-service calls. This keeps the
Phase-1 system simple to run and reason about while leaving clean seams for later extraction.

- **Backend**: Java 21, Spring Boot 4, PostgreSQL 16, Redis 7, Flyway.
- **Frontend**: Next.js (App Router) with a thin BFF proxy that turns the JWT into httpOnly cookies.
- **Market**: Nepal (Kathmandu Valley + Pokhara at launch). Money settles off-platform via eSewa/Khalti.

## 2. Architecture

```
Browser ── Next.js (SSR + BFF proxy) ── Spring Boot API ── PostgreSQL
                    │ httpOnly cookies         │              Redis (OTP, rate limits, perm cache)
                    └── static + SSR pages     └── domain events → listeners (notifications, ledger)
```

- The **BFF proxy** (`/api/rentle/[...path]`) forwards to the backend, attaches the access token
  from an httpOnly cookie, and transparently refreshes on 401. The browser never holds the JWT.
- **Domain events** (`ApplicationEvent`) are the seam between a domain action and its side effects
  (SMS/email, notifications). New reactions subscribe to events; they don't reach into other services.

## 3. Domain model

Modules under `com.rentle.domain`:

| Module | Owns |
|--------|------|
| `user` | accounts, auth, OTP, KYC, profiles, business accounts |
| `listing` | listings, categories, product/service detail, availability, search |
| `booking` | booking lifecycle, pricing, deposit, condition evidence, fee snapshot |
| `review` | dual reviews, trust-score aggregation (DB triggers) |
| `messaging` | booking-scoped threads, unread counts |
| `notification` | persisted in-app notifications |
| `trust` | reports / disputes |
| `favorite` | saved listings |
| `template` | category field templates (verification / listing / booking scopes) |
| `pricing` | per-category deposit bands + cancellation schedule |
| `business` | worker registry, per-booking worker assignment |
| `platform` | IAM catalog, roles, permissions, assignments, settings |
| `admin` | operator console endpoints |

Shared kernel (`com.rentle.shared`): API envelope, exceptions + global handler, security utils,
storage services, notification interfaces, domain-event records, base entity (UUID + auditing).

## 4. Authentication & authorization (IAM)

- **Authentication**: RSA-signed JWT. Access token 15 min, refresh token 7 days with rotation
  (a used refresh token is invalidated). Tokens live in Redis for revocation; suspension revokes
  immediately.
- **Authorization**: fully **permission-based**. Each protected endpoint declares a permission key
  (`domain.resource.action`, e.g. `listing.category.manage`). The catalog is code-defined and
  seeded; roles bundle permissions; assignments bind a subject to a role at a scope. Resolved
  permissions are cached in Redis and invalidated on role/assignment change.
- Frontend gating (`Can`, `PermissionGuardedPage`) is for UX only — the backend is authoritative.

## 5. Booking & availability

- The booking **state machine** enforces valid transitions; a PostgreSQL **GiST exclusion
  constraint** guarantees no two `APPROVED`+ bookings overlap on a listing (timestamp-range aware,
  so hourly slots on the same day don't collide).
- A `REQUESTED` booking does **not** occupy the calendar — several renters can request the same
  window and the owner picks one; exclusivity begins at approval. Open requests per renter are
  capped.
- Availability responses combine owner-blocked ranges and confirmed bookings.

## 6. Money flow

Phase 1 is deliberately **off-platform**: the renter pays the owner's eSewa/Khalti directly and
uploads a proof screenshot; the owner confirms receipt to activate the booking. The platform
records the facts (amount, proof, confirmation) but does not hold funds.

- **Fee ledger**: at completion each booking snapshots the platform-fee percent (0 % at free
  launch) and amount; admins mark fees invoiced. The percent is an admin-editable platform setting.
- **Pricing policy**: per category, admins set deposit guidance bands (keyed to declared item
  value, with a damage cap) and a time-before-start cancellation schedule, snapshotted onto bookings.

## 7. Category rollout platform

The marketplace grows **one category at a time**, as admin configuration, with no deploy:

- **Lifecycle**: categories are launched or paused; a hidden category is excluded from search, the
  listing wizard, and new bookings (existing bookings still complete).
- **Field-template engine**: one validation engine used in three scopes — `VERIFICATION` (provider
  credentials), `LISTING` (extra listing fields), `BOOKING` (extra request fields). Templates are
  admin-edited, versioned (existing records keep the version they answered), and answers are stored
  as JSONB and validated on write.

See [`12_category_rollout_platform_plan.md`](./12_category_rollout_platform_plan.md) and
[`13_market_scan_and_pricing_module.md`](./13_market_scan_and_pricing_module.md) for the design
rationale and the market research behind the pricing module.

## 8. Configurability model

Everything admin-adjustable falls into one of four layers, so "should the admin be able to change
X?" always has a clear home:

| Layer | What | Mechanism |
|-------|------|-----------|
| 0 — Code | State machine, pricing arithmetic, security, KYC flow | Pull request + tests (deliberately not runtime-editable) |
| 1 — Platform settings | Fee %, review window, districts | `platform_settings` table, admin screen |
| 2 — Category policy | Field templates, deposit bands, cancellation tiers | Per-category rows / JSONB |
| 3 — IAM catalog | Roles, permissions, assignments | Runtime-managed |

Two generic mechanisms cover all of it: the **field-template engine** (data collection) and
**settings + per-category overrides** (policy values). No rules engine, no workflow builder.

## 9. Storage

- **Public** (listing images): local disk by default, Cloudinary in production (`rentle.storage`).
- **Private** (KYC documents, deposit proofs, condition photos): stored outside the public root and
  streamed only through authenticated, ownership-checked endpoints — never via `/files/**`.

## 10. Notifications

- Interfaces `EmailService` / `SmsService` with pluggable implementations. Defaults (`logging`)
  write to the log and mirror to a Discord webhook (the dev event channel). `SmtpEmailService`
  (`rentle.email=smtp`) and `SparrowSmsService` (`rentle.sms=sparrow`) activate from config.
- In-app notifications are persisted per user and written at booking/KYC lifecycle points.

## 11. Data & migrations

- Schema is versioned with **Flyway** (`V001`…). All feature work ships additive migrations.
- Trust scores and listing ratings are maintained by **database triggers** on review insert.
- JSONB is used for template answers, field definitions, deposit bands, and cancellation schedules —
  flexible shapes that don't warrant normalized tables at this scale.

## 12. Deployment & operations

- **Containers**: multi-stage, non-root Dockerfiles for backend and frontend; a root
  `docker-compose.yml` runs Postgres + Redis + both apps, fully env-driven (`.env.example`).
- **Prod profile** (`application-prod.yml`): no dev fallbacks — a missing secret fails the boot.
- **Observability**: Actuator health/info/metrics + a Prometheus registry (`/actuator/prometheus`);
  structured JSON logs in prod.
- **Backups**: `ops/backup.sh` runs `pg_dump` and syncs the private/public upload directories.
