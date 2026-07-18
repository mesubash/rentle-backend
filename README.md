# Rentle Backend

Peer-to-peer rental and local-services marketplace for Nepal. A Spring Boot modular monolith
providing accounts, KYC, listings, bookings, deposits, reviews, messaging, notifications, trust &
safety, business/worker accounts, an admin console, and permission-based IAM.

## Documentation

| Doc | What |
|-----|------|
| [API Reference](docs/API.md) | Every endpoint with request/response tables |
| [System Design](docs/SYSTEM_DESIGN.md) | Architecture, domains, IAM, storage, ops |
| [Features](docs/FEATURES.md) | What the platform does, by domain |
| [Business Requirements](docs/BUSINESS_REQUIREMENTS.md) | Problem, scope, rules, metrics |
| [Category Rollout Plan](docs/12_category_rollout_platform_plan.md) · [Pricing & Market Scan](docs/13_market_scan_and_pricing_module.md) | Design rationale for the category-template and pricing platform |

## Stack

- Java 21, Spring Boot 4, Gradle (Kotlin DSL)
- PostgreSQL 16 — Flyway migrations, GiST exclusion constraints, triggers, full-text search, JSONB
- Redis 7 — OTP, refresh tokens, JWT revocation, rate limiting, permission cache
- RSA JWT — 15-min access + 7-day rotating refresh
- Storage — local disk (dev) or Cloudinary (`RENTLE_STORAGE=cloudinary`)
- Email — logging→Discord (dev) or SMTP (`RENTLE_EMAIL=smtp`)
- SMS — logging→Discord (dev) or Sparrow (`RENTLE_SMS=sparrow`)
- Google sign-in — activates when `GOOGLE_CLIENT_ID`/`SECRET` are set

## Run locally

```bash
docker compose up -d          # Postgres :5433, Redis :6380
./gradlew bootRun             # Flyway migrations run at startup
```

No env vars required in dev — a JWT keypair is generated at startup, and email/SMS are logged
(mirrored to a Discord webhook if `RENTLE_DISCORD_WEBHOOK` is set). See `../.env.example` for
production configuration and the `prod` profile (`application-prod.yml`), which fails fast on any
missing secret.

## Test

```bash
./gradlew test                # unit + Testcontainers integration (needs Docker)
```

Integration tests cover the full booking lifecycle, double-booking prevention (service + DB level),
the state machine, review rules, message access control, admin listing, and the field-template engine.

## Deploy

Multi-stage `Dockerfile` (non-root). The root `../docker-compose.yml` runs the full stack
(Postgres + Redis + backend + frontend), env-driven from `../.env`. `ops/backup.sh` runs `pg_dump`
and syncs upload directories.

## Conventions

- **Modular monolith** — `domain/{user,listing,booking,review,messaging,notification,trust,favorite,template,pricing,business,platform,admin}`; cross-module side effects go through Spring application events (`shared/event`).
- **Everything is a Listing** — `type` = `PRODUCT` or `SERVICE`; type-specific fields in satellite tables.
- **Correctness is DB-enforced** — booking overlaps are impossible (GiST exclusion constraint); rating aggregates and the review window are maintained by triggers. The Java layer enforces the same rules first for friendly errors.
- **Permission-based IAM** — every protected endpoint declares a `domain.resource.action` permission; frontend gating is UX only.
- **API envelope** — every response is `{ data, error, timestamp }`; paged payloads add `content/page/size/totalElements/last`.
