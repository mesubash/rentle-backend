# Category Rollout Platform — Design & Plan

**Status:** proposal for review. Extends `07_provider_verification_plan.md` — same template
machinery, wider mandate. Nothing here is built yet.

**The goal:** the marketplace grows **one category at a time, on the admin's schedule, with
zero deploys**. Launch with two categories. Next season, an admin opens the console, creates
"Plumbing", defines what a plumber must prove and what a plumbing job request must contain,
flips it ACTIVE — and the marketplace has a new vertical. Pause "Traditional Clothing" after
Dashain/Tihar; reactivate next season. The **category is the unit of launch**, and launching
is an admin action, not an engineering project.

This replaces the "all four categories, all at once, forever" assumption baked into the
current seed data.

---

## 1. Why gradual

- **Supply density beats breadth.** 30 camera listings in one city is a marketplace; 8
  listings spread over 4 categories is a ghost town in every one of them. Launching narrow
  concentrates the seeded supply where demand can actually find it.
- **Verification capacity is manual.** Every service category multiplies admin review load
  (KYC + provider credentials). Adding categories at the pace the review desk can absorb is
  an operational necessity, not just a strategy.
- **Each category needs different data to transact honestly** (gap analysis round 2):
  clothing needs sizes, cameras need serials, moving needs job addresses, photography needs
  deliverable expectations. Gradual rollout means each category's fields and rules get
  designed *when that category launches*, by configuration — not all guessed up front.
- **Seasonality is real.** Traditional clothing is a Dashain/Tihar/wedding-season business.
  A category you can pause and reopen matches how the supply actually behaves.

---

## 2. Category lifecycle

Add `status` to the existing `categories` table. Four states, each defined purely by what it
gates:

| State | In explore/nav/search | New listings | New bookings | Existing bookings |
|-------|----------------------|--------------|--------------|-------------------|
| **DRAFT** | hidden | no | no | — (none exist) |
| **COMING_SOON** *(optional, Phase 2 of this plan)* | teaser card, no listings | no | no | — |
| **ACTIVE** | yes | yes | yes | run normally |
| **PAUSED** | hidden from browse; direct listing URLs show "temporarily unavailable" | no | no | **continue to completion** — pausing never strands money or possession mid-flight |

Rules:

- Search, explore, category nav, and the listing wizard's category picker read **ACTIVE
  only** (one `WHERE c.status = 'ACTIVE'` join added to each).
- `ListingService.create/update` and `BookingService.createBooking` validate category
  ACTIVE server-side (frontend gating is UX; backend is authoritative — same principle as IAM).
- PAUSED → ACTIVE is instant and lossless; that's the seasonal lever.
- Guardrail: a SERVICE category cannot go ACTIVE unless it has a verification template
  attached **or** the admin explicitly marks it "no credentials required" (deliberate, logged
  choice — never a silent default).

---

## 2b. Architecture: where configurability lives (the four layers)

Everything admin-adjustable in the platform belongs to exactly one of four layers. This is
the whole extensibility model — one page, no rules engine. When a future "should the admin
be able to change X?" question comes up, place X in a layer and the design is done.

### Layer 0 — Code (deploy required, deliberately NOT configurable)

Booking state machine and transition guards, pricing arithmetic, deposit/money handling,
IAM *enforcement*, KYC identity flow, double-booking constraints, storage/security rules.
These are correctness- and money-critical: making them "dynamic" is how marketplaces get
robbed. They change by pull request, with tests. **This is a feature of the design, not a
limitation.**

### Layer 1 — Platform settings (admin-editable singletons)

One `platform_settings` table (key, value jsonb, updated_by, updated_at) + one
`PlatformSettingsService` with a short-TTL cache, seeded from the current
`application.yml` values, edited on one admin screen. Settings move here **only when the
business actually needs to turn the knob without a deploy** — starting set:

- `platform_fee_percent` (currently dead config — becomes live via the P0-3 fee ledger)
- `booking_request_expiry_hours` (consumed by the P1-3 scheduler)
- `review_window_days`
- `deposit_guidance_bands` (the P2-18 warning thresholds)
- `support_contact` (email/phone shown by the P1-20 support surface)
- `districts` (the launch-market list — wizard select + explore filter chips read the
  same list, fixing the P2-17 vocabulary split; **city expansion becomes an admin action**,
  same gradual-growth principle as categories — Phase 3's "two-city expansion" is a
  settings edit plus seeding, not a release)

Env-level infrastructure config (DB, SMTP, storage keys, JWT) stays in yml/env — secrets
and wiring are ops, not admin product decisions.

### Layer 2 — Category platform (this doc)

Lifecycle status + the three template scopes + per-category **policy overrides**: nullable
columns on the category row (e.g. `review_window_days_override`,
`booking_request_expiry_hours_override`) that fall back to Layer-1 settings when NULL.
Plain columns, added one at a time when a real category needs one (photography's longer
review window, P2-24, is the first candidate). Explicitly **not** a generic policy engine —
a nullable column with a fallback is the entire pattern.

The **pricing module** (docs/13) is the first major tenant of this layer:
`category_pricing_policy` — pricing mode (owner-set vs admin rate card), allowed price
units, duration-tier discounts, deposit bands + damage caps keyed to declared item value,
cancellation tiers, per-category commission override with incidence. Policy is data;
arithmetic stays Layer-0 code.

### Layer 3 — IAM catalog (already built)

Roles, permissions, assignments — admin-manageable at runtime since IAM Iteration 2. New
admin capabilities from this plan add permission keys to the existing catalog
(`08_permission_catalog.md`); nothing new architecturally.

### The two generic mechanisms (and there are only two)

1. **The field-template engine** (§3) — for *data collection* that varies by category.
2. **Settings + nullable overrides** (Layers 1–2) — for *policy values* that vary by
   platform or category.

Everything admin-configurable is expressed through these two. Explicit non-goals, so this
never metastasizes: no workflow/rules builder, no admin-defined states or transitions, no
conditional-field logic, no plugin/hook system, no admin-editable notification templates
(copy changes are deploys until proven otherwise), no per-user overrides. Each of those is
a large system with real failure modes; none has a Phase-1-or-2 justification. If one ever
does, it gets its own one-page design first.

### How the pieces talk (unchanged, for orientation)

Domain events (Spring `ApplicationEvent`, already the house pattern —
`BookingCreated/Approved/...`) remain the seam between domain actions and side effects
(notifications, and later the fee ledger and audit rows). New features subscribe to
events; they don't reach into other domains' services. The modular monolith stays a
monolith — extraction is a Phase-4 docs topic, not a Phase-1 abstraction tax.

---

## 3. One template engine, three attachment points

docs/07 already defines the requirement-field schema (`key`, `label`, `type` TEXT | NUMBER |
DATE | SELECT | DOCUMENT | DOCUMENT_LIST, `required`, `options`, `help`). **Do not build
three form systems.** Build that engine once, admin-editable, and attach templates to a
category in three scopes:

| Scope | Who fills it, when | Where answers live | Examples |
|-------|--------------------|--------------------|----------|
| **VERIFICATION** | provider, once per category, before first listing | `provider_verification.fields` JSONB + private documents (docs/07 unchanged) | trade licence + licence no. (plumbing); driving licence + vehicle papers (moving); portfolio (photography) |
| **LISTING** | owner, per listing, in the wizard | `listings.attributes` JSONB, rendered on the detail page | size + measurements (clothing); serial number + declared value (cameras); vehicle type + capacity (moving) |
| **BOOKING** | renter/client, per booking request, in the booking sheet | `bookings.attributes` JSONB, shown to the owner before approve/reject | pickup + dropoff address, floor/rooms (moving); venue + event type + hours (photography) |

What this absorbs from the gap analysis, for free, as *data entered by an admin*:

- Clothing sizes (P2-27) → LISTING template on Traditional Clothing
- Camera serial + declared item value (parts of P1-22, P2-18) → LISTING template on Cameras
- Service job location/scope (P1-30) → BOOKING template on Moving and on Event & Photography
- Per-category provider credentials (P1-17 / docs/07) → VERIFICATION scope, unchanged

Shared mechanics across all three scopes (build once):

- **Validation:** one service validates a JSONB answer set against a template (required
  present, types correct, SELECT values legal, documents uploaded). Called from provider
  verification submit, listing create/update, booking create.
- **Rendering:** one frontend component renders a template as a form section (and one
  renders answers read-only). Wizard, booking sheet, verification form, and all admin review
  screens reuse it.
- **Versioning:** every submission snapshots `template_version` (docs/07 §7). Template edits
  never invalidate existing listings/bookings/verifications; admins may explicitly request
  re-submission. Same grandfathering rule everywhere.
- `DOCUMENT` fields are only allowed in VERIFICATION scope for now (private storage);
  listing/booking attachments are a later decision, not a default.

**Not in scope (deliberate):** no conditional field logic ("show B if A = x"), no computed
fields, no per-field workflow states, no cross-field validation rules. Flat field lists
cover every launch category. Revisit only when a real category proves flat insufficient.

---

## 4. Data model

```
categories                          (existing table — add columns)
  status            DRAFT | COMING_SOON | ACTIVE | PAUSED   (default DRAFT for new rows)
  requires_provider_verification  boolean                    (the explicit "no credentials" opt-out)

category_field_template
  id                uuid
  category_id       uuid
  scope             VERIFICATION | LISTING | BOOKING
  version           int          (bumped on any field change)
  fields            jsonb        (ordered array of requirement definitions, docs/07 §3.1)
  updated_by        uuid
  created_at / updated_at
  UNIQUE (category_id, scope, version)

listings   + attributes           jsonb, attributes_template_version int
bookings   + attributes           jsonb, attributes_template_version int
provider_verification             (docs/07 unchanged — its template now lives here as scope=VERIFICATION)
```

JSONB, not EAV — same reasoning as docs/07 §4. Filtering/searching *inside* attributes
(e.g. "size M only") is explicitly deferred; attributes render and validate, they don't
filter, until a category proves the need.

Seed migration: existing four categories get `status = ACTIVE` (or per §7's launch decision),
existing listings/bookings get `attributes = '{}'`.

---

## 5. Admin console — the rollout cockpit

Extends the existing admin area (this supersedes gap P2-11 "category management UI").
Permission keys follow the existing IAM pattern (`platform.category.manage`,
`platform.template.manage` — exact keys per `08_permission_catalog.md` conventions).

- **Category list**: all categories with status, listing count, pending-verification count.
  Create / edit basics (name, slug, type, icon, sort, parent).
- **Template editor** per category: three tabs (Verification / Listing / Booking). Add,
  remove, reorder fields; toggle required; edit labels, options, help text. **Live preview**
  renders the form exactly as providers/renters will see it (same shared renderer — the
  preview is free). Saving bumps the version.
- **Status control** with guardrails: the ACTIVE flip runs a checklist — verification
  template present (or "no credentials" explicitly checked), listing template reviewed
  (empty is allowed, but confirmed), at least the basics (icon, description) set. Shows a
  soft warning if the category would go live with zero seeded listings.
- **Requirements preview page** link — what users will see (§6).
- Every status change and template edit is recorded (this workstream lands the first rows
  of the audit-trail gap, P2-6, for category actions at minimum).

---

## 6. User-facing surfaces

- **Explore/nav**: ACTIVE categories only. No dead tabs, no empty shelves.
- **Category requirements page** (public, per service category): "Offer plumbing on Rentle —
  you'll need: ① identity verification (citizenship), ② plumbing licence, ③ …" — rendered
  from the VERIFICATION template. Linked from the wizard's category picker and from
  "become a provider" entry points. This is the user's answer to *"what do I need?"* and it
  updates the moment an admin edits the template.
- **Wizard**: category picker (ACTIVE only) → if service category and provider not yet
  verified for it → inline gate showing the requirements page + "start verification" (reuses
  the KYC-gate pattern). Then the LISTING template renders as an extra wizard step.
- **Booking sheet**: BOOKING template renders below the date/time fields; owner sees the
  answers on the request card — approving blind (P1-21/P1-30) ends for structured facts.
- **COMING_SOON teaser** (only if/when that state ships): category card with "opening soon"
  + optional notify-me email capture. Cheap demand signal for sequencing next launches.

---

## 7. Launch sequencing (recommendation — decide, then it's just data)

Current seed assumes 4 categories at once. Recommended instead:

1. **Launch: 2 categories.** *Cameras & Tech* (product — deepest seeded supply, highest
   deposit learning value) + **one** service. Photography is operationally easiest
   (portfolio-based verification, no licence checks); plumbing is viable if individual
   plumbers with trade licences are the target (BUSINESS accounts / worker registry —
   docs/07 Phase B — stays deferred until shop/company suppliers matter).
2. **Season 2 (pre-Dashain): Traditional Clothing**, launched ~6 weeks before the festival
   window with the size template in place, paused after the season if supply goes dormant.
3. **Season 3: Moving & Transport** — hardest category (vehicle + licence verification,
   address/scope booking fields, distance pricing pressure) — launched once the review desk
   and dispute process have reps in.
4. Each subsequent category = admin console + this playbook. No deploys.

**Per-category launch checklist** (ops, one page): templates configured and previewed →
requirements page reviewed → 10–20 supply commitments pre-onboarded (providers verified
*before* the flip, while DRAFT) → flip ACTIVE → announce.

---

## 8. Build plan

Sits in Milestone 1 where P1-17 currently sits — this *is* P1-17 plus the category
machinery around it. Order:

0. **Platform settings (Layer 1)** — `platform_settings` table + service + one shadcn
   admin screen; seed from yml; migrate the `districts` list here first (fixes P2-17 and
   unblocks the wizard select + filter chips immediately). Small.
1. **Schema + lifecycle enforcement** — category status column, ACTIVE checks in search /
   wizard picker / listing create / booking create. Small, ships value alone (pausing
   works even before templates exist).
   **[PARTIAL — shipped 2026-07-17]** Using the existing `is_active` boolean as the interim
   launch/pause switch: `V017` seeds Cameras + Photography active, Moving + Clothing hidden;
   search (`ListingRepository`) and booking (`BookingService.createBooking`) now enforce
   category-active (category picker + listing-create already did). Still to do here: replace
   the boolean with the DRAFT/ACTIVE/PAUSED enum, add the admin toggle endpoint + console so
   growth is an admin action rather than a migration, and the public "temporarily
   unavailable" state on direct listing URLs for paused categories.
2. **Template engine** — `category_field_template` table, validation service, shared form
   renderer + read-only renderer, versioning.
3. **Provider verification** (docs/07 Phase A) on top of the engine — submission, private
   docs, admin review queue, listing gate.
4. **LISTING + BOOKING scopes** — wizard step, booking-sheet section, detail-page and
   request-card rendering.
5. **Admin console** — category CRUD, template editor with preview, guarded status flips.
6. **Requirements page + gates** — public per-category requirements, wizard inline gate.
7. *(Later, optional)* COMING_SOON + notify-me.

Steps 1–2 unblock everything; 3 is the largest single chunk; 4–6 are parallelizable.

### Frontend notes (apply to every step)

- **No backend-only steps.** Every step above ships its frontend surface in the same step —
  a category status column without the wizard picker filtering on it, or a template engine
  without its renderer, is not done. (Round-1 lesson: the backend has several dormant,
  frontend-less capabilities today; this workstream adds none.)
- **Admin console = shadcn/ui**, inside the existing `.admin-scope` boundary
  (`src/app/admin/admin.css`) — reuse the established admin component vocabulary
  (`AdminRowActions`, `AdminTableRowLink`, Tabs, the `Can`/`PermissionGuardedPage` IAM
  template). The template editor and status controls are ordinary shadcn forms/tables, not
  bespoke UI.
- **Marketplace surfaces** (wizard template step, booking-sheet section, requirements page,
  verification submission) use the marketplace design system (`globals.css` components) —
  per the repo rule, no `src/components/ui/` imports outside `/admin`.
- **The shared renderer is headless + two skins.** One module owns field logic (template →
  field list, answer state, validation messages); two thin presentational wrappers style it —
  admin (shadcn, also used by the live preview) and marketplace. Logic is written once;
  the preview being pixel-identical to the marketplace form is a non-goal (it previews
  *content and order*, not marketplace CSS).
- Both skins meet the same bar: labeled inputs, inline per-field errors, keyboard/AA
  accessibility, mobile layout — the wizard and booking sheet are conversion-critical.

---

## 9. What this changes in GAP_ANALYSIS.md

- **P1-17** → becomes this plan (steps 2–3 + 5–6). Same priority, clearer shape.
- **P1-30** (service booking scope fields), **P2-27** (clothing sizes), **P2-11** (admin
  category UI) → absorbed; close them as separate items.
- **P1-22 / P2-18** — serial number and declared item value become LISTING template fields
  on Cameras; the handover-evidence *flow* itself stays a separate gap.
- **P2-24** (photography COMPLETED semantics) — not absorbed, but per-category policy
  fields (e.g. review-window override) get an obvious future home on the category row.
- Adds one new ops line: per-category launch checklist owner.

---

## 10. Decisions — RESOLVED 2026-07-17

1. **Launch pair**: Cameras & Tech + Event & Photography. ✅ (owner decision)
2. **Seeded categories' fate**: Moving & Transport and Traditional Clothing flip to DRAFT
   at migration; relaunched seasonally via the console. ✅ (default)
3. **COMING_SOON**: deferred. ✅ (default)
4. **Template field types**: add BOOLEAN and MULTISELECT to the docs/07 set now. ✅ (default)
5. **Template editing**: any admin holding the template-manage permission key. ✅ (default)
