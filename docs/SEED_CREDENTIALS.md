# Seeded Accounts & Demo Data

This document lists every account created automatically on a fresh install, so you
can log in as each persona and exercise every point of view.

- The **super admin** is created by the Flyway migration `V014__seed_superadmin.java`.
- Everything else is created at startup by `DemoDataSeeder` — **idempotent** (skips once
  it has run) and gated by the `rentle.demo-seed` property.

> **Security:** these are demo accounts with a shared, well-known password. **Disable the
> seeder and change the admin password before any public launch.** Turn the seeder off with
> `RENTLE_SEED_DEMO=false` (it is already off during tests).

---

## Accounts

All demo accounts use the password **`Rentle@123`**. The super admin uses **`Admin@123`**.

| Persona | Email | Password | Role / scope |
|---|---|---|---|
| Super admin | `admin@rentle.online` | `Admin@123` | SUPER_ADMIN (platform) |
| KYC reviewer | `kyc@rentle.online` | `Rentle@123` | KYC_REVIEWER (platform) |
| Support | `support@rentle.online` | `Rentle@123` | SUPPORT (platform) |
| Individual service provider | `provider@rentle.online` | `Rentle@123` | Verified user, INDIVIDUAL |
| Everest Movers — owner | `ever.owner@rentle.online` | `Rentle@123` | ORG_OWNER |
| Everest Movers — admin | `ever.admin@rentle.online` | `Rentle@123` | ORG_ADMIN |
| Everest Movers — staff | `ever.staff@rentle.online` | `Rentle@123` | ORG_STAFF |
| Kathmandu Plumbers — owner | `ktm.owner@rentle.online` | `Rentle@123` | ORG_OWNER |

### Pending invite (to test the accept-invite flow)
`ever.invitee@rentle.online` is invited to **Everest Movers** as ORG_STAFF but has not
accepted. Log in as the owner/admin to copy the invite link, or accept it at
`/organizations/invites/<token>` after registering that email.

### Workers (labour records — not login accounts)
- Everest Movers: **Ram Bahadur** (Driver), **Shyam Lama** (Mover), **Hari Magar** (Mover)
- Kathmandu Plumbers: **Gopal Tamang** (Plumber)

---

## Organizations

| Organization | What it demonstrates |
|---|---|
| **Everest Movers** | A service company with a full team (owner + admin + staff), workers, a pending invite, and moving/transport listings. |
| **Kathmandu Plumbers** | A licensed trade — has an **approved provider verification** in the gated *Home Services* category, then lists there. |

---

## Seeded listings

| Listing | Type | Category | Provider |
|---|---|---|---|
| Wedding & event photography | Service | Event & Photography | provider (individual) |
| Sony A7 III camera kit | Product | Cameras & Tech | provider (individual) |
| DJI Mini 3 drone | Product | Cameras & Tech | provider (individual) |
| Trek mountain bike | Product | Bikes & Scooters | provider (individual) |
| 4-person camping tent | Product | Outdoor & Camping | provider (individual) |
| Full house shifting | Service | Moving & Transport | Everest Movers |
| Office relocation | Service | Moving & Transport | Everest Movers |
| Moving trolley & packing kit | Product | Tools & Equipment | Everest Movers |
| Emergency plumbing | Service | Home Services | Kathmandu Plumbers |
| Electrical wiring & repair | Service | Home Services | Kathmandu Plumbers |

Listings are seeded without photos (they show a "No photo yet" placeholder). To test the
booking → deposit → completion flow you will need a buyer account; register any email as a
normal user, verify it, and book one of the listings above.

---

## Points of view covered

- **Platform:** super admin, KYC reviewer, support (three distinct role bundles).
- **Individual provider:** lists services and rents out personal gear.
- **Organization hierarchy:** owner vs admin vs staff vs worker, plus a pending invite.
- **Provider verification:** an org verified in a gated category before it can list there.
- **Multi-tenant isolation:** two independent organizations.
