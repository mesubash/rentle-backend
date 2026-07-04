# Rentle Backend

Peer-to-peer rental & service-booking marketplace for Nepal — Phase 1 backend.
Design docs live in [docs/](docs/); the authoritative references are the
[project proposal](docs/01_rentle_project_proposal.md),
[backend technical guide](docs/02_rentle_backend_technical.md) and
[schema design](docs/03_rentle_schema_design.md).

## Stack

- Java 21, Spring Boot 4, Gradle (Kotlin DSL)
- PostgreSQL 16 (Flyway migrations, GiST exclusion constraints, triggers, FTS)
- Redis 7 (OTP, refresh tokens, JWT revocation, rate limiting)
- JWT auth — RS256 access tokens (15 min) + rotating opaque refresh tokens (7 days)
- Storage: local disk in dev, Cloudinary in prod (`RENTLE_STORAGE=cloudinary`)
- SMS: logging stub in dev, Sparrow SMS in prod (`RENTLE_SMS=sparrow`)

## Run locally

```bash
docker compose up -d          # Postgres on :5433, Redis on :6380
./gradlew bootRun             # migrations run automatically via Flyway
```

No env vars needed in dev — an ephemeral JWT keypair is generated at startup
and SMS/email are logged instead of sent. See `.env.example` for production
configuration.

## Test

```bash
./gradlew test                # unit + Testcontainers integration tests (needs Docker)
```

Integration tests cover the full booking lifecycle, double-booking prevention
at both service and database level, self-booking prevention, the booking state
machine, review rules and message access control.

## Architecture notes

- **Modular monolith** — `domain/{user,listing,booking,review,messaging,admin}`,
  each with its own model/dto/repository/service/controller. Cross-module side
  effects go through Spring application events (`shared/event`).
- **Everything is a Listing** — `type` = `PRODUCT` or `SERVICE`; type-specific
  fields live in `product_details` / `service_details` satellite tables.
- **Correctness is DB-enforced** — booking overlaps are impossible (GiST
  exclusion constraint); state-machine transitions, self-booking, the 30-day
  review window and rating aggregates are enforced by triggers
  (`V007__add_triggers.sql`). The Java layer enforces the same rules first for
  friendly error messages.
- **API envelope** — every response is `{ data, error, timestamp }`; paginated
  payloads add `content/page/size/totalElements/totalPages/last`.

## API surface

Base path `/api/v1` — auth, users, categories, listings (+ images,
availability), bookings (+ state actions, deposit flow), booking-scoped
messages, reviews, admin. Full endpoint map:
[docs/02_rentle_backend_technical.md §7](docs/02_rentle_backend_technical.md).
