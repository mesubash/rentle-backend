# Rentle — Project Proposal Document
**Version:** 1.0  
**Date:** May 2026  
**Author:** Subash Dhami  
**Status:** Draft for Review

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Problem Statement](#2-problem-statement)
3. [Market Research & Context](#3-market-research--context)
4. [Identified Gaps](#4-identified-gaps)
5. [Proposed Solution](#5-proposed-solution)
6. [Objectives](#6-objectives)
7. [Target Users](#7-target-users)
8. [Phase 1 Scope](#8-phase-1-scope)
9. [Feature Implementation Plan](#9-feature-implementation-plan)
10. [Feasibility Assessment](#10-feasibility-assessment)
11. [Risk Analysis](#11-risk-analysis)
12. [Success Metrics](#12-success-metrics)
13. [Timeline](#13-timeline)
14. [Future Roadmap](#14-future-roadmap)

---

## 1. Executive Summary

**Rentle** is a peer-to-peer marketplace platform for Nepal that unifies the renting of physical products and booking of local services under one trusted platform. Unlike existing platforms that treat products and services separately, Rentle operates on a single listing abstraction — every listing is either a product you can rent or a service you can book — backed by one shared trust layer: verified profiles, deposits, dual-sided reviews, and structured booking-bound communication.

The platform is designed with an incremental delivery strategy. Phase 1 launches two product categories (cameras & tech gear, traditional clothing) and two service categories (moving & transport, event & photography services) in Kathmandu Valley and Pokhara — the two Nepali cities with sufficient digital payment infrastructure and a young, mobile-first user base.

The platform is not built to be feature-complete on day one. It is built so that every new category, service type, or city added in the future requires no structural change to the codebase — only configuration and data.

**Estimated Phase 1 Build:** 10–12 weeks with 2 developers  
**Target Launch Markets:** Kathmandu Valley, Pokhara  
**Revenue Model:** Platform commission (5–8% of each transaction)

---

## 2. Problem Statement

### 2.1 The Core Problem

In Nepal, a large volume of everyday transactions — renting a camera for a wedding, hiring a mover for a house shift, booking a photographer for an event, borrowing a saree for a festival — happen through personal networks, WhatsApp groups, and Facebook posts. These transactions have three fundamental problems:

**No trust infrastructure.** There is no formal way to verify who you are dealing with. You lend your camera to someone a friend-of-a-friend recommended. If it comes back damaged, you have no recourse.

**No coordination layer.** Booking, pricing, dates, and terms are all negotiated informally. There is no paper trail, no confirmation, no shared record of what was agreed.

**No discovery.** If you need a mover in Lalitpur, you ask in a WhatsApp group or post on Facebook. There is no place to browse verified, reviewed providers with transparent pricing.

### 2.2 The Result

- Item owners lose money to damage with no recourse
- Service providers have no way to build a reputation beyond their personal network
- Renters and hirers have no way to compare providers, verify quality, or resolve disputes
- The market remains informal, low-trust, and therefore small

### 2.3 Why This Has Not Been Solved

Existing digital platforms in Nepal address adjacent problems — real estate rental (GharBheti, Rentalnepal.com), property listing (Lalpurja), ride-sharing (Tootle, Pathao) — but none address the P2P renting of everyday items or the marketplace booking of local skilled services. The informal economy, estimated at 41% of Nepal's GDP, has been left largely undigitised in this space.

---

## 3. Market Research & Context

### 3.1 Digital Infrastructure Readiness

Nepal's digital infrastructure has reached a threshold that makes this platform viable in 2026 in a way it was not three years ago.

**Mobile penetration:** Mobile connections in Nepal are now equivalent to 109% of total population, with 82.8% of those connections being 3G/4G/5G broadband. Smartphone penetration exceeds 60%.

**Digital payments:** Nepal's digital payments reached NPR 98.43 trillion in FY 2024/25 — a 71% year-on-year growth. eSewa, Khalti (now merged with IME Pay as IME Khalti), and Fonepay are part of daily life for urban Nepalis. Fonepay crossed 1 million QR transactions in a single day in 2025.

**Social media:** 14.8 million user identities aged 18+ are active on social media in Nepal, representing 75.6% of adults — meaning your target users are already online and comfortable with digital discovery.

**Internet penetration:** Broadband internet penetration is at approximately 72%, though meaningful digital commerce activity remains concentrated in urban centres — primarily Kathmandu Valley, Pokhara, and Chitwan.

### 3.2 Gig Economy Growth

The gig economy contributes approximately 6% to Nepal's GDP, with gig workers predominantly aged 18–35 and concentrated in urban areas. Platforms like Tootle and Pathao have proven that young Nepalis will adopt digital service platforms quickly when they solve a real problem with a trustworthy UX.

### 3.3 Key Consumer Behaviour Insights

- Over 60% of young consumers in Kathmandu report trust issues regarding payment security and after-sales service as key barriers to online platforms
- Digital payment adoption is strong among urban middle-class and upper-class users
- COVID-19 significantly accelerated digital adoption — people who shifted to online transactions during lockdown have largely maintained those habits
- Consumer trust problems are identified as persistent in Nepal's gig economy research, underscoring that trust mechanisms are not optional features — they are the product

### 3.4 Competitive Landscape

| Platform | What it does | Gap it leaves |
|----------|-------------|---------------|
| GharBheti / Lalpurja | Property rental/sale | No everyday items or services |
| Tootle / Pathao | Ride-sharing | Transport only, no P2P |
| Hamro Bazaar | Buy/sell classified | No rental, no services, no trust layer |
| Facebook Groups | Informal discovery | No booking, no verification, no recourse |
| Instagram / WhatsApp | Informal booking | No structure, no protection |

**No platform in Nepal currently enables structured, trusted P2P rental of everyday items or booking of local services with verified profiles and deposit protection.**

---

## 4. Identified Gaps

### 4.1 Trust Gap
There is no mechanism for establishing trust between strangers for asset lending or service hiring in Nepal's informal market. No identity verification, no review history, no deposit system, no dispute resolution pathway.

### 4.2 Discovery Gap
Finding a specific item to rent or a local service provider requires existing personal networks or passive social media discovery. There is no searchable, filterable, structured directory.

### 4.3 Coordination Gap
After initial contact, coordination happens across multiple platforms (phone, WhatsApp, Facebook Messenger) with no single record of what was agreed, when, at what price, and what the outcome was.

### 4.4 Recourse Gap
When something goes wrong — a rented item is damaged, a service provider doesn't show up, a deposit isn't returned — there is no formal recourse mechanism. The transaction was informal and leaves no trail.

### 4.5 Supply Formalisation Gap
Many skilled workers and item owners in Nepal are willing to offer their goods and services to strangers but have no platform to do so professionally. A furniture mover works informally; a photographer books through Facebook DMs; a saree owner has no way to monetise during Dashain. The supply exists but is unstructured.

### 4.6 Unified Platform Gap
Products and services are not currently served by any single platform. A user who needs to rent a camera and hire a photographer for an event needs to use completely separate, unrelated channels — even though both needs are part of the same task.

---

## 5. Proposed Solution

### 5.1 What Rentle Is

A unified peer-to-peer marketplace where every listing is either:
- A **product** that can be rented for a period (with a deposit)
- A **service** that can be booked for a date/time (with a fixed or hourly price)

Both live under the same platform, the same trust layer, and the same booking flow. A user builds one reputation that works whether they are renting out their camera or offering their moving truck.

### 5.2 The Trust Layer

Trust is not a feature — it is the platform's primary value proposition. Every mechanism below is designed to make strangers trust each other enough to transact:

- **Citizenship card verification** — uploaded and manually reviewed before a user can list items or offer services
- **Phone OTP verification** — required at registration
- **Deposits** — set by the owner/provider, tracked in the system
- **Dual-sided reviews** — both parties review after a completed booking; reviews are only unlocked after completion
- **Booking-scoped messaging** — all communication is on-record within the booking context
- **Report system** — users can flag listings, providers, or renters

### 5.3 The Booking Flow

```
Renter/Client discovers listing
        ↓
Requests booking (date, time, notes)
        ↓
Owner/Provider approves or rejects
        ↓
Booking confirmed → deposit discussed via message
        ↓
Rental/service active
        ↓
Both parties mark complete
        ↓
30-day review window opens for both sides
```

### 5.4 What Rentle Is NOT

- Not a property/real estate platform
- Not a vehicle rental platform (Phase 1)
- Not a freelancing/remote work platform
- Not a delivery or logistics platform
- Not a buy/sell marketplace

---

## 6. Objectives

### 6.1 Business Objectives

1. Create the first structured, trusted P2P rental and service marketplace native to Nepal
2. Formalise the informal economy of item lending and casual service booking in urban Nepal
3. Build a two-sided network with sufficient supply and demand in Kathmandu Valley and Pokhara within 6 months of launch
4. Establish a sustainable commission-based revenue model

### 6.2 Product Objectives

1. Achieve zero double-booking incidents through database-level enforcement
2. Achieve a dispute rate below 2% of completed bookings
3. Achieve an average platform review rating above 4.2 within 3 months of launch
4. Achieve 25%+ repeat user rate within 6 months

### 6.3 Technical Objectives

1. Build a modular monolith that supports adding new listing types (product categories, service types) without structural code changes
2. Build the Listing abstraction from day one so product and service share all infrastructure
3. Ensure the schema and codebase can support payment gateway integration (Phase 2) without migration debt
4. Achieve sub-200ms API response time at p95 under normal load
5. Maintain 99.9% uptime post-launch

---

## 7. Target Users

### 7.1 Primary Users — Kathmandu Valley, Pokhara

**The Renter / Client (demand side)**
- Age 20–35, university students and young professionals
- Comfortable with eSewa/Khalti, active on Instagram/Facebook
- Needs items for events, projects, or short-term use
- Currently solves this through personal networks or Facebook groups
- Pain: no trust, no recourse, no easy discovery

**The Owner / Provider (supply side)**
- Age 22–45, individuals or micro-entrepreneurs
- Has underutilised assets (camera, clothing, vehicle) or offers a skilled service (photography, moving, event work)
- Currently books through word-of-mouth or Facebook DMs
- Pain: no professional presence, no reputation portability, no deposit protection

### 7.2 Secondary Users (Phase 2+)

- Small businesses offering equipment rental
- Home service professionals (plumbers, electricians)
- Trekking gear shops in Pokhara
- Tailors and artisans offering made-to-occasion services

---

## 8. Phase 1 Scope

### 8.1 Geographic Scope

**Kathmandu Valley** (Kathmandu, Lalitpur, Bhaktapur) and **Pokhara** only.

Rationale: These are the only cities where all three prerequisites co-exist — sufficient digital payment adoption, a critical mass of young urban users, and reasonable trust baseline from prior platform use (Pathao, Tootle, Daraz).

### 8.2 Category Scope

#### Products — Launch

| Category | Rationale |
|----------|-----------|
| Cameras & tech gear | High demand from students, content creators, wedding circuit. High item value justifies deposit mechanism. Clear condition standards exist. |
| Traditional clothing & costumes | Sarees, daura suruwal, cultural costumes for Dashain, Tihar, weddings, bratabandha. Already informally shared — formalise it. Low damage risk. |

#### Services — Launch

| Category | Rationale |
|----------|-----------|
| Moving & transport help | Acute unmet need in Kathmandu — students, families, offices. Currently done via WhatsApp. Simple to scope: person + vehicle + hours. Directly complements product rental users. |
| Event & photography services | Photographers, videographers, MCs, decorator helpers. Nepal's dense event culture (weddings, pasni, bratabandha) creates constant demand. Booked informally via Facebook/Instagram now. |

#### Deferred to Phase 1b

- Sports & outdoor gear (add after damage inspection process is defined)
- Home services — plumbing, electrical (add after review volume enables quality surfacing)

#### Not in Phase 1

- Vehicles (liability framework unclear, dispute risk too high)
- Tutoring / online services (crowded, not differentiating)
- Multi-city beyond KTM + Pokhara

### 8.3 Payment Scope (Phase 1)

**No in-app payment gateway in Phase 1.** Payment is handled as follows:

1. Owner/provider sets deposit amount in the listing
2. After booking is approved, renter sends deposit via eSewa/Khalti directly to the owner
3. Renter screenshots the transaction and uploads it to the booking
4. Owner confirms receipt → booking moves to Active
5. Platform fee is invoiced at end of month (manual for first 3 months)

Rationale: This removes 6 weeks of payment gateway integration risk and lets both teams focus on booking flow and trust features. In-app payment integration (eSewa API, Khalti API) is Phase 2.

---

## 9. Feature Implementation Plan

### 9.1 Must-Have Features (Phase 1 Launch)

#### Authentication & Identity
- Phone number + OTP registration (Sparrow SMS or Aakash SMS)
- Email as secondary identifier
- Citizenship card photo upload (stored in S3/Cloudinary, manually reviewed by admin)
- User status: PENDING_VERIFICATION → VERIFIED → SUSPENDED
- JWT-based authentication (access token 15 min, refresh token 7 days)

#### Listing Management
- Create listing with type: PRODUCT or SERVICE
- Fields: title, description, category, price, price unit (PER_DAY / PER_HOUR / FLAT), images (up to 5), location (district + area text), deposit amount
- Product-specific: condition (NEW / GOOD / FAIR), brand, model
- Service-specific: service area radius, typical duration, minimum notice hours
- Owner can activate/deactivate listing
- Availability management: owner blocks specific dates

#### Booking Flow
- Renter requests booking with dates and optional message
- Owner receives notification (SMS + in-app)
- Owner approves or rejects with optional reason
- On approval: booking moves to CONFIRMED, messaging thread opens
- Renter confirms deposit paid (with screenshot upload)
- Owner confirms deposit received → booking goes ACTIVE
- Either party can mark complete (owner-confirmed preferred)
- Cancellation with reason (policies to be displayed, no automated refund in Phase 1)

#### Messaging
- All messaging is scoped to a specific booking
- Messages stored with booking_id, sender_id, timestamp
- No standalone DMs between users
- Message read receipts (basic)
- SMS notification on new message (first message in a booking only, to avoid spam)

#### Reviews
- Unlocked only after booking status = COMPLETED
- Both renter and owner/provider can leave a review
- Review window: 30 days after completion
- Fields: rating (1–5 stars), comment (optional, max 500 chars)
- Cannot edit after submission
- Review count and average rating shown on user profile and listing

#### Search & Discovery
- Browse by category (product / service / all)
- Keyword search (title + description — PostgreSQL full-text, Phase 1)
- Filter by district / city
- Filter by listing type (product or service)
- Sort: newest, highest rated, lowest price
- Listing detail page with all images, owner profile, availability, reviews

#### User Profiles
- Public profile: display name, profile photo, member since, verification badge, average rating, review count, listings
- Private fields: phone, email, citizenship card (admin-only)
- Provider profile additions: service description, portfolio link (optional)

#### Notifications
- SMS (Sparrow/Aakash) for: booking request received, booking approved/rejected, deposit confirmation, booking activated, booking completed, new message (first per booking)
- Email for: registration welcome, booking confirmation, review received

#### Admin (Minimal)
- View all users, listings, bookings (read-only dashboard)
- Approve/reject citizenship card verification
- Suspend/unsuspend a user
- Manually mark platform fee invoiced

### 9.2 Explicitly NOT in Phase 1

| Feature | Reason Deferred |
|---------|----------------|
| In-app eSewa/Khalti payment | 6+ weeks integration, Phase 2 |
| Automatic deposit escrow | Requires licensed payment flow |
| Geolocation / map view | Nice-to-have, not critical for launch |
| WebSocket real-time chat | Polling every 30s is sufficient for Phase 1 volume |
| Mobile app (iOS/Android) | Web-first, validate before native |
| RabbitMQ / Elasticsearch | Overkill under 1000 users |
| Dispute resolution flow | Manual via admin in Phase 1 |
| Insurance layer | Legal complexity, Phase 3+ |
| Multi-language (Nepali) | Phase 1b (2 months post launch) |

---

## 10. Feasibility Assessment

### 10.1 Technical Feasibility: HIGH

The technology stack (Spring Boot, PostgreSQL, Redis) is well-understood and Nepal has a growing pool of Java developers. No novel technology is required. The phased approach means the first version is deliberately minimal in complexity.

### 10.2 Payment Feasibility: HIGH (Phase 1), MEDIUM (Phase 2)

Phase 1 uses manual payment confirmation — this is technically trivial. Phase 2 integration with eSewa and Khalti APIs is well-documented and both providers have developer portals. The 13% VAT on digital transactions is a cost to factor into platform fee design.

### 10.3 Market Feasibility: HIGH for Urban KTM + Pokhara

The infrastructure prerequisites (smartphone, digital wallet, social media habit) are met by the target demographic. The informal market being formalised is large and the demand signal is proven (Facebook groups for camera rental, saree lending, movers — all active).

### 10.4 Trust Feasibility: MEDIUM

This is the hardest problem. Nepal's online market has persistent trust issues. The citizenship card verification requirement is a strong signal — it is the same document required for a SIM card, a bank account, and most formal transactions. The dual-sided review system builds trust over time. However, initial cold-start means early users are taking a leap of faith. A referral launch strategy (launch with known users first, let reviews accumulate) is recommended.

### 10.5 Regulatory Feasibility: HIGH (Phase 1)

Phase 1 avoids all legally ambiguous categories (vehicle rental, financial services, insurance). The platform is a marketplace — it does not own inventory or employ service providers — which keeps it outside most regulatory friction points for Phase 1.

---

## 11. Risk Analysis

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Low supply-side adoption (not enough listings) | Medium | High | Seed the platform: manually onboard 20–30 verified owners/providers before public launch |
| Low demand-side trust (users don't book strangers) | High | High | Referral launch — start with closed community, let reviews build trust before open launch |
| Dispute rate exceeds 2% | Medium | Medium | Strict citizenship verification, clear terms on listing, manual admin review for Phase 1 |
| Payment confirmation abuse (fake screenshots) | Medium | Medium | Admin spot-check of deposit confirmations in Phase 1; Phase 2 replaces with API |
| VAT / regulatory scrutiny on transactions | Low | Medium | Keep Phase 1 platform fee manual and below registration threshold; consult legal in Month 2 |
| Developer bandwidth (2-person team) | High | High | Strict feature discipline — nothing outside Phase 1 scope gets built |
| Competitor copies model | Low | Low | First-mover and trust network are the moat; a copycat without users has no reviews |
| SMS delivery failure (OTP) | Low | High | Use two SMS providers with fallback; implement email OTP as backup |

---

## 12. Success Metrics

### 12.1 Phase 1 Launch Targets (Month 1–3)

| Metric | Target |
|--------|--------|
| Verified listings live at launch | 30+ (seeded) |
| Completed bookings in Month 1 | 20+ |
| Completed bookings in Month 3 | 100+ |
| Average review rating | ≥ 4.2 |
| Dispute rate | < 2% |
| User verification completion rate | > 70% of signups |

### 12.2 Phase 1 End Targets (Month 6)

| Metric | Target |
|--------|--------|
| Active listings | 200+ |
| Registered verified users | 500+ |
| Completed bookings | 500+ |
| Repeat booking rate | ≥ 25% |
| GMV (Gross Merchandise Value) | NPR 500,000+ |
| Platform revenue (commission) | NPR 25,000–40,000 |

### 12.3 Technical Health Metrics

| Metric | Target |
|--------|--------|
| API response time p95 | < 200ms |
| Uptime | > 99.5% |
| Double-booking incidents | 0 |
| Zero data loss events | 0 |

---

## 13. Timeline

### Phase 1 — 12-Week Plan

```
Week 1–2: Foundation
├── Project setup (Spring Boot, PostgreSQL, Redis, Docker)
├── Database schema v1 (users, listings, bookings, reviews, messages)
├── Auth system (register, login, JWT, OTP via SMS)
└── Base entity structure, exception handling, audit log

Week 3–4: Listing System
├── Listing CRUD (product + service types)
├── Product detail and service detail tables
├── Image upload (Cloudinary)
├── Category management
└── Availability management (blocked date ranges)

Week 5–6: Booking Flow
├── Booking creation (with overlap prevention at DB level)
├── Booking state machine (REQUESTED → APPROVED → ACTIVE → COMPLETED)
├── Deposit confirmation upload flow
├── Booking notifications (SMS)
└── Booking cancellation

Week 7–8: Messaging & Reviews
├── Booking-scoped messaging
├── Message read status
├── Dual-sided review system
├── Review window enforcement (30-day DB trigger)
└── User trust score calculation

Week 9–10: Search & Discovery
├── Category browse and listing detail
├── PostgreSQL full-text search
├── Filters (district, type, category)
├── Sort options
└── User public profiles

Week 11: Admin & Verification
├── Admin API for user management
├── Citizenship card verification workflow
├── User status management (PENDING → VERIFIED → SUSPENDED)
└── Minimal admin read dashboard

Week 12: Hardening & Launch Prep
├── Integration tests (Testcontainers)
├── Security review (rate limiting, input validation, JWT hardening)
├── Performance testing (baseline)
├── Deployment (AWS / DigitalOcean)
└── Seed data (30+ real listings from pre-recruited owners)
```

### Key Milestones

| Week | Milestone |
|------|-----------|
| Week 2 | Auth working end-to-end, users can register and verify phone |
| Week 4 | Owners can create and publish listings with images |
| Week 6 | Complete booking flow works from request to completion |
| Week 8 | Reviews live, messaging live |
| Week 10 | Search and discovery complete |
| Week 12 | Production launch, 30 seeded listings live |

---

## 14. Future Roadmap

### Phase 2 (Months 4–6 post launch)
- In-app eSewa and Khalti payment integration
- Automated deposit escrow
- Sports & outdoor gear category
- Home services category (with skill verification)
- Nepali language support (UI translation)
- Geolocation / map view for listing discovery

### Phase 3 (Months 7–12 post launch)
- Mobile app (React Native — iOS and Android)
- Advanced search (Elasticsearch)
- Vehicle rental category (after legal framework clarified)
- Insurance partnership for high-value item rentals
- Automated owner payout scheduling
- Chitwan / Biratnagar city expansion

### Phase 4 (Year 2)
- Multi-city expansion (all major Nepal cities)
- AI-based pricing suggestions for listings
- Fraud detection model
- Microservices extraction (payment domain, notification domain)
- International remittance / cross-border capability research

---

*This document represents the full project proposal for Rentle Phase 1. It is intended as a working document — to be updated as the project evolves and market feedback is incorporated.*
