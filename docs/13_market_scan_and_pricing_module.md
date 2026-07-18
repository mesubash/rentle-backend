# Market Scan & Admin-Configurable Pricing Module — Findings & Design

**Status:** research complete (2026-07-17, all claims adversarially verified against live
primary sources); pricing-module design proposed for review. Extends docs/12 — pricing
policy becomes Layer-2 category configuration using the existing settings + nullable-override
mechanism. No new generic machinery.

---

## 1. What the market scan found (verified)

### 1.1 Nepal — the local operating norms (direct competitors exist)

| Player | What it is | What matters for Rentle |
|--------|-----------|------------------------|
| **Sajilo Sewa** (sajilosewa.com) | Active home-services marketplace — electrician, plumbing, appliance repair, cleaning — in **exactly our geography** (Kathmandu, Lalitpur, Bhaktapur, Pokhara) | Direct competitor for the future plumbing/electrical phase. Uses **per-unit, labor-only rate cards** (platform-defined prices, not provider-set) and **pay-after-completion via cash/eSewa/Khalti** |
| **Rent in Nepal** (rentinnepal.com) | Owned-inventory camera/drone rental, Kathmandu (Canon/Nikon/Sony, DJI drones, citywide delivery) | Direct competitor for the camera category — but owned-inventory, not P2P. Our differentiation is supply breadth via P2P; theirs is control/consistency |
| **Rents Nepal** (rentsnepal.com.np, iOS/Android apps updated Jan 2026) | Rental + mover-services marketplace (rooms, vehicles, item transport) | Overlaps moving/transport. Monetizes via **owner subscriptions for listing visibility — not commission**. Traction unproven (placeholder download counters), but the model precedent matters |

**The local norm (verified with caveat):** post-service, off-platform, wallet/cash payment
with **no escrow** is how Nepali marketplaces operate today. Rentle's Phase-1 off-platform
deposit design is not a compromise — it *is* the market standard. (2-1 verification vote;
rests partly on generalization from few platforms.)

### 1.2 Global patterns that transfer (all 3-0 verified on primary sources)

**Royal Brothers (India bike rental — the richest policy template):**
- **Per-category deposit bands**, not per-item: ~INR 500 scooters / 1,000 bikes / 3,000+
  superbikes; refunded to source account in 3–7 working days. Deposit policy is a category
  attribute with a **refund window**.
- **Five-tier time-before-pickup cancellation schedule** on rental charges — no-show 100%;
  0–6h 75%; 6–24h 50%; 24–72h 25%; 72h+ 10% — with the deposit **always refunded
  regardless**. Cancellation penalty and deposit are fully decoupled instruments.
- **Damage-liability caps per value band** (INR 15k sub-200cc / 25k above; insurance claim
  above the cap, cap still levied). Renter liability is *bounded and pre-declared*, which is
  what makes renters willing to transact.
- **Document-heavy, tiered KYC by customer origin** (locals: licence + Aadhaar; foreigners:
  home licence + IDP + visa + passport) and **physical collateral** — original licence
  surrendered at pickup in most cities. ⚠ For Nepal: holding *government ID* as collateral
  must be checked against Nepali law and clashes with our KYC-privacy posture — treat as a
  legal question, not a default (see §3 decision 5).

**ShareGrid (US camera gear):** coverage is a **mandatory checkout step** — no rental
proceeds uncovered — with coverage tiers **banded by cumulative gear value** (damage-only
allowed under $6k replacement value; damage+theft or insurance above). Nepal has no such
insurance products yet; the transferable pattern is *"a required deposit-or-coverage
selection step at booking, banded by declared item value"* — which our declared-value field
(GAP P2-18) enables.

**TaskRabbit (services):** platform fee charged **entirely to the client** as a percentage
on top of the provider-set hourly rate — providers keep 100% of their rate (strong
supply-acquisition story). Cancellation fee (within 24h / no-show) ≈ one hour at the
provider's rate — i.e. the cancellation penalty **compensates the provider**, not the
platform.

**Airtasker (services):** background-check verification is an **optional paid trust badge**,
not an entry gate — verification as a competitive advantage providers *buy* to win jobs.
Composable with our per-category mandatory credentials: mandatory floor (docs/07/12) +
optional paid badges later.

### 1.3 What the scan could NOT verify (honest gaps — do not treat as answered)

- Failure post-mortems (Spinlister shutdown, Fat Llama pivot, KitSplit/ShareGrid merger
  economics) — no verified findings; the "liquidity + insurance cost kills P2P rental"
  hypothesis remains unconfirmed folklore.
- Flyrobe/Indian ethnic-wear rental mechanics (sizing, dry-cleaning turnaround, garment
  deposits) — closest analog to the clothing category; nothing verified. Worth a focused
  follow-up before the clothing season launch.
- Urban Company commission rates and rate-card mechanics — nearest regional benchmark for
  home services; unverified.
- All Wedio insurance claims were refuted 0-3 — no reliable Wedio data; do not cite it.
- Whether eSewa/Khalti can support escrow-style holds, and Nepali law on ID collateral —
  open legal/partnership questions (feed the Month-2 legal consult).

---

## 2. Business-model implications (adopt / consider / reject)

**Adopt:**
1. **Deposit bands per category + declared item value** (Royal Brothers × ShareGrid):
   admin-set bands keyed to item-value ranges, decoupled from cancellation, with declared
   refund window. Replaces "owner types any number" (GAP P2-18).
2. **Time-banded cancellation tiers with a provider-compensation share** (Royal Brothers ×
   TaskRabbit): N configurable tiers by hours-before-start. **Phase-1 honesty:** with money
   off-platform we cannot *withhold* anything — the tiers are displayed pre-booking,
   snapshotted onto the booking (feeds the P1-28 rental agreement), and serve as the
   admin's arbitration reference; automatic enforcement arrives with Phase-2 payments.
3. **Damage-liability caps per band**: bounded, pre-declared renter liability. Cheap to
   configure, transformative for renter willingness on high-value gear.
4. **Fee-incidence flexibility** (TaskRabbit): commission may be charged to owner, client,
   or split — make incidence a config field now (a one-column decision), because the
   supply-side pitch "providers keep 100% of their rate" is powerful in a cold-start.
5. **Subscription / featured-listing monetization as a Phase-1 revenue candidate**
   (Rents Nepal precedent): while payments are off-platform, commission is evadable and
   invoicing has no leverage (GAP P1-24/P1-25) — but owners *will* pay for visibility.
   Does not replace the P0-3 fee ledger (commission returns with Phase-2 payments); it
   de-risks Phase-1 revenue. Decision 4 below.

**Consider (decisions, not defaults):**
- **Admin rate cards for service categories** (Sajilo Sewa model): platform-priced
  per-unit labor beats provider-set pricing for standardizable jobs (plumbing), but fits
  photography/moving poorly. Design supports both via `pricing_mode` per category.
- **Physical collateral for high-value gear** — pending the legal check; if lawful, a
  *non-ID* variant (secondary document or cash collateral recorded on the booking) is
  safer than holding citizenship cards.

**Reject:**
- Dynamic/surge pricing, ML price suggestions — no Phase-1/2 justification.
- Building an insurance product — no Nepali underwriting partner exists; bands + caps +
  deposits are the Phase-1 substitute. Revisit at Phase 3 (proposal already lists
  insurance partnership there).

---

## 3. The pricing module (admin-configurable, no new machinery)

**Principle (docs/12 §2b applied): policy is data, arithmetic is code.** Admins configure
*parameters*; `PricingService`/`BookingService` consume them; no formula editor, no rules
engine.

### 3.1 Configuration shape

Layer-1 platform defaults (in `platform_settings`), each overridable per category via the
Layer-2 nullable-override pattern (new `category_pricing_policy` — one row per category,
JSONB columns per block):

```
pricing_mode          OWNER_SET | RATE_CARD
                      OWNER_SET: owner prices the listing (P2P gear, photography, moving)
                      RATE_CARD: admin publishes per-unit prices; listings attach to a rate
                      card entry (Sajilo Sewa model — future plumbing/electrical)

allowed_price_units   subset of PER_HOUR | PER_DAY | PER_WEEK | PER_MONTH | FLAT | PER_UNIT
                      (adds PER_WEEK/PER_MONTH/PER_UNIT to today's enum; per-category so
                      clothing can be PER_DAY+FLAT while services are PER_HOUR+FLAT)

duration_tiers        [{ min_days, discount_pct }]           e.g. 7d→10%, 30d→25%
                      admin defines the tier structure and a max discount bound; owner
                      opts in per listing (on/off or milder %, never steeper than bound).
                      Answers GAP P2-20 without owner-invented side-deals.

billing_increment     for hourly units: minimum billable block + rounding rule
                      (fixes GAP P2-3 hourly truncation as configuration)

deposit_bands         [{ value_min, value_max, deposit_min, deposit_max, damage_cap }]
                      keyed to the listing's declared item value (LISTING template field);
                      wizard enforces deposit within band; booking displays the damage cap.
                      deposit_refund_window_days + refund_method shown at booking.

cancellation_tiers    [{ hours_before_start, withhold_pct, provider_share_pct }]
                      + no_show tier. Phase 1: displayed + snapshotted on booking
                      (arbitration reference). Phase 2: enforced in payment flow.

commission            { pct (0 allowed), incidence: OWNER | CLIENT | SPLIT }
                      per-category override of the Layer-1 platform fee; written into the
                      per-booking fee snapshot (P0-3) at booking time.
```

### 3.2 What stays code (Layer 0 — deliberately)

Price arithmetic (`PricingService` consuming units/tiers/increments), fee-snapshot
computation, deposit-band validation, state-machine consequences of cancellation. Config
supplies numbers; code applies them; tests pin the arithmetic.

### 3.3 Surfaces

- **Admin** (shadcn, `.admin-scope`): a "Pricing & policies" tab on the category screen in
  the docs/12 console — band editors, tier editors, commission override. Live preview
  renders the renter-facing price/policy breakdown.
- **Wizard**: price-unit picker limited to `allowed_price_units`; deposit field bounded by
  the band for the declared value; optional duration-discount opt-in.
- **Listing page / booking sheet**: price breakdown incl. tier discount; deposit + refund
  window + damage cap; cancellation schedule — all *before* request submission. This is
  the trust surface the research says converts renters.
- **Booking snapshot**: unit, tiers applied, deposit band, damage cap, cancellation
  schedule version, commission pct+incidence — frozen per booking (same grandfathering
  rule as templates; feeds P1-28 agreement and the P0-3 ledger).

### 3.4 Build order (extends docs/12 §8)

1. Enum + schema: new price units, `category_pricing_policy`, booking policy snapshot
   columns. 2. `PricingService` reads units/tiers/increments. 3. Deposit bands + declared
   value wiring (with the Cameras LISTING template). 4. Cancellation tiers display +
   snapshot. 5. Admin "Pricing & policies" tab. 6. RATE_CARD mode — **deferred until the
   first rate-card category (plumbing) is scheduled**; the enum value reserves the slot.

### 3.5 Decisions — RESOLVED 2026-07-17

1. **Launch commission posture**: FREE LAUNCH — platform fee 0% for the first season to
   maximize liquidity; the P0-3 fee ledger ships anyway (snapshots 0%), so commission or
   subscription/featured listings switch on later as admin configuration, no deploy.
   ✅ (owner decision)
2. **Commission incidence**: default OWNER; the `incidence` config field ships so
   client-side/split is a settings change when fees activate. ✅ (default)
3. **Duration tiers**: built with the module (cameras benefit immediately). ✅ (default)
4. **Deposit band values**: admin fills per category once the declared-value field is live;
   engineering ships the bands empty-but-functional. ✅ (default)
5. **Physical collateral**: default NO; question goes to the Month-2 legal consult. ✅ (default)

---

## 4. GAP_ANALYSIS.md effects

- **P2-18** (deposit sizing/value) and **P2-20** (duration tiers) → absorbed here.
- **P2-3** (hourly truncation) → becomes `billing_increment` config.
- **P1-2** (cancellation policy display) → policy content now comes from
  `cancellation_tiers`; the event/notification part of P1-2 is unchanged.
- **P1-24** (commission terms/leverage) → decision 1 here; fee snapshot unchanged (P0-3).
- New P3: optional paid trust badges (Airtasker pattern) — post-launch.
- New follow-ups for the legal consult: eSewa/Khalti escrow capability; ID-collateral
  legality; (research) Flyrobe-style garment-care mechanics before the clothing launch.
