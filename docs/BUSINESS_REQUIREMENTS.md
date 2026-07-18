# Rentle — Business Requirements

> Related: [Features](./FEATURES.md) · [System Design](./SYSTEM_DESIGN.md) · [Market scan & pricing](./13_market_scan_and_pricing_module.md)

## 1. Problem

In Nepal, everyday P2P transactions — renting a camera for a wedding, hiring a mover, booking a
photographer, borrowing a saree for a festival — happen through personal networks, WhatsApp groups,
and Facebook posts. Three problems recur: **no verification** (you don't know who you're dealing
with), **no shared record** (no agreed terms, no proof), and **no recourse** (damage or a no-show
leaves you with nothing). Existing Nepali platforms cover adjacent needs (real estate, ride-share)
but not P2P item rental or marketplace booking of local skilled services.

Enabling conditions are strong: digital payments (eSewa, Khalti/IME, Fonepay) are part of daily
urban life, and ~75% of adults are active on social media — the target users are already online.

## 2. Vision

A single trusted marketplace where a **product to rent** and a **service to book** live under one
listing model, one verification layer, and one booking flow. **Trust is the product**, not a
feature: identity verification, owner-set deposits, recorded agreements, and dual reviews.

## 3. Target users

- **Renters/clients** — urban Nepalis (18–35, mobile-first) who need an item or a local pro occasionally.
- **Owners/providers** — individuals with idle gear or a skill to sell; and **businesses** (moving
  or photography companies) that operate through registered workers.

## 4. Scope

### Phase 1 (built)
- Four launch categories: Cameras & Tech, Traditional Clothing (products); Moving & Transport,
  Event & Photography (services). Launched **gradually** — categories can be paused/launched by an
  admin (current live set: Cameras & Tech + Event & Photography).
- Two markets: Kathmandu Valley, Pokhara.
- Trust: citizenship KYC, phone/email verification, deposits, dual reviews, reports/disputes.
- Booking lifecycle with DB-guaranteed no double-booking; off-platform deposit with proof.
- Business accounts + worker registry.

### Payment scope (Phase 1)
**No in-app gateway.** The renter pays the owner's eSewa/Khalti directly and uploads proof; the
owner confirms. This removes payment-integration risk and keeps focus on booking + trust. The schema
carries a fee ledger and a dormant payments table so in-app payments (Phase 2) need no migration debt.

### Explicit non-goals (Phase 1)
- No in-app payment gateway (Phase 2). No automated refunds.
- Not a vehicle-rental platform (Phase 3, after the legal framework).
- Not real estate, freelancing, delivery/logistics, or a buy/sell marketplace.

## 5. Key business rules

| Rule | Where enforced |
|------|----------------|
| Only verified users (phone + email + citizenship) can list or book | Backend, at listing/booking creation |
| A service listing may require per-category provider credentials | Category verification templates |
| No two confirmed bookings overlap on a listing | DB GiST exclusion constraint |
| Deposits settle off-platform; the platform records amount/proof/confirmation | Booking service |
| Reviews only after a completed booking, and only for the counterparty | Review service |
| Commission is 0 % during free launch; configurable per platform settings | Fee ledger + settings |
| A booking completes only after its rental period has started | Booking service (anti-farming) |

## 6. Monetization

Free launch (0 % commission) to build liquidity; the per-booking fee ledger and admin invoicing are
in place to switch on commission — or subscription/featured-listing revenue — via configuration when
payments move on-platform. VAT and the exact rate are a Month-2 legal-consult item.

## 7. Success metrics (targets)

- Zero double-bookings (hard system guarantee).
- Dispute rate below **2 %** of completed bookings.
- Average rating **4.2+**; **25 %+** repeat-user rate within 6 months.
- Schema/codebase supports Phase-2 payment integration without migration debt.

## 8. Roadmap beyond Phase 1

- **Phase 2** — in-app eSewa/Khalti payments + escrow, more categories, Nepali language, map/geo.
- **Phase 3** — mobile apps, vehicle rental, insurance partnership, automated payouts, second-city expansion.
- **Phase 4** — nationwide, AI pricing, fraud detection, service extraction from the monolith.

Compliance note: Nepal's Electronic Commerce Act 2081 (2025) requires marketplace registration,
on-site operator disclosure, and a consumer grievance channel — addressed by the operator disclosure,
Terms/Privacy pages, and the support/grievance surface.
