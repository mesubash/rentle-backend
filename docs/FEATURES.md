# Rentle — Feature Reference

A peer-to-peer marketplace for Nepal that unifies **product rental** (cameras, traditional
clothing, gear) and **local services** (photography, moving, events) under one Listing model and
one shared trust layer. This document lists what the platform does today, grouped by domain.

> Related: [System Design](./SYSTEM_DESIGN.md) · [API Reference](./API.md) · [Business Requirements](./BUSINESS_REQUIREMENTS.md)

---

## 1. Accounts & identity

| Feature | Notes |
|---------|-------|
| Email-first registration | Creates the account and a session immediately; JWT access (15 min) + refresh (7 days, rotating). |
| Google sign-in | Backend-driven OAuth code flow; activates when `GOOGLE_CLIENT_ID/SECRET` are set. |
| Password reset | Self-service forgot/reset via a one-time emailed link (Redis token, 1 h). |
| Email verification | Clickable link; required before KYC. |
| Phone verification | OTP over SMS (3/hour rate limit). |
| Citizenship KYC | Front/back card images to **private storage**, manual admin review; `PENDING → VERIFIED → SUSPENDED`. Verification gates listing and booking. |
| Individual vs Business accounts | A **Business** account lists as a company and registers workers (below). |
| Public profiles | Trust score, verified badge, member-since, registered-business badge — no private data exposed. |

## 2. Listings

| Feature | Notes |
|---------|-------|
| Unified Listing model | Every listing is a `PRODUCT` or `SERVICE` sharing booking, reviews, messaging, search. |
| Listing wizard | Title, category, price + unit (day/hour/flat), deposit, up to 5 images, product/service detail fields, rental terms. Verification-gated. |
| Publish lifecycle | `DRAFT → ACTIVE/INACTIVE`; a ≥1-image gate; publish activates the listing. |
| Category field templates | Per-category admin-defined extra fields (listing scope), validated dynamically on create. |
| Availability | Owner blocks date ranges; the booking picker disables booked/blocked dates. |
| Search | PostgreSQL full-text search + filters (category, type, district, price range) + sort; paginated. |
| Favorites | Save listings; heart toggle on cards; `/favorites` list. |

## 3. Bookings

| Feature | Notes |
|---------|-------|
| State machine | `REQUESTED → APPROVED → DEPOSIT_PENDING → ACTIVE → COMPLETED`, with `CANCELLED/REJECTED` exits. DB-level double-booking prevention (GiST). |
| Requests don't hold dates | Multiple renters may request the same window; exclusivity is claimed at approval (per-renter open-request cap). |
| Rental-term enforcement | Min/max rental days and service notice hours enforced server-side. |
| Owner price adjustment | Owner may adjust the agreed price before the deposit step (for scoped services). |
| Deposit flow | Off-platform (eSewa/Khalti): renter uploads payment proof (private storage), owner confirms. Owner wallet handle shown to the renter. |
| Rental agreement | Owner-set terms snapshotted onto each booking at request time. |
| Hand-over / return evidence | Photo + note captured at checkout and return for deposit-dispute protection. |
| Anti-farming guard | A booking can only be completed once its rental period has started. |
| Worker assignment | A business owner assigns which registered worker attends a booking; the renter sees the assigned worker. |
| Cancellation | Publishes an event to notify the counterparty; messaging stays open on cancelled bookings. |

## 4. Trust, safety & communication

| Feature | Notes |
|---------|-------|
| Booking-scoped messaging | Opens at `REQUESTED` (ask before approving); 30-second polling; per-thread unread + last-message. |
| Dual reviews | Unlocked only after completion; per-user already-reviewed state; DB-trigger trust-score aggregation. |
| Reports / disputes | Users flag a listing, user, or booking; admin queue resolves/dismisses with a note. |
| Notifications | Persisted in-app feed written on booking + KYC events; header bell unread badge. |
| Condition evidence & agreements | See Bookings — the deposit-dispute record. |

## 5. Business accounts & workers (Phase B)

| Feature | Notes |
|---------|-------|
| Business account type | Set in profile; lists under a business name with a "Registered business" badge. |
| Worker registry | Add/remove workers (name, phone, role); soft-remove keeps history. |
| Per-booking worker assignment | Assign a worker to an approved booking; snapshotted name shown to the client. |

## 6. Money & monetization

| Feature | Notes |
|---------|-------|
| Platform fee ledger | Per-booking commission snapshot at completion (0 % during free launch); admin invoicing view. |
| Platform settings | Admin-editable fee %, review window, etc. — no redeploy. |
| Pricing policy per category | Admin deposit guidance bands (by item value, with damage cap) + cancellation schedule; the schedule is snapshotted onto bookings. |

## 7. Category rollout platform (docs/12)

| Feature | Notes |
|---------|-------|
| Category lifecycle | Launch / pause categories from the admin console (gradual, seasonal growth). |
| Field-template engine | Admin-defined field templates in three scopes — **verification** (provider credentials), **listing**, **booking** — with dynamic validation and versioning. |
| Requirements surfacing | Public per-category field templates render the forms renters/providers fill in. |

## 8. Admin console

| Area | Capabilities |
|------|--------------|
| Verification queue | Review citizenship documents, approve/reject with reason. |
| Users | List, suspend/unsuspend (suspension hides the owner's listings), reset password. |
| Listings | Moderate (deactivate/remove). |
| Bookings | Read any booking's detail. |
| Categories | Launch/pause + field-template editor + pricing-policy editor. |
| Reports | Trust-and-safety queue. |
| Fees & settings | Invoice platform fees; edit platform settings. |
| Access management (IAM) | Roles, permissions, staff assignments — permission-based, runtime-managed. |

## 9. Platform & operations

- **IAM authorization** — every protected endpoint is guarded by a permission key; the catalog is seeded and role assignments are managed at runtime.
- **Notifications delivery** — email + SMS route through pluggable providers; the default (`logging`) mirrors to a Discord webhook for dev. SMTP (`rentle.email=smtp`) and Sparrow SMS (`rentle.sms=sparrow`) activate from config.
- **Storage** — public images (local or Cloudinary); private KYC/deposit/condition files served only through authenticated, ownership-checked endpoints.
- **Observability** — Actuator health/info/metrics + Prometheus registry; structured JSON logs in the prod profile.
- **Deployment** — backend + frontend Dockerfiles, an env-driven `docker-compose.yml`, a `prod` profile that fails fast on missing secrets, and a `pg_dump` backup script.
