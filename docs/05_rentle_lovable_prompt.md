# Rentle — Lovable Build Prompt (Full App)

> Paste everything below this line into Lovable as the starting prompt.
> Before running the app, set the environment variable `VITE_API_BASE_URL`
> to your running backend (local dev: `http://localhost:8080`).

---

## 1. What you are building

Build the complete **Rentle web application** — a desktop-first, fully
mobile-responsive React frontend for an existing, finished REST backend. Do NOT
build any backend, do NOT use Supabase, do NOT add your own auth provider or
database. Every piece of data comes from the REST API described in section 5,
reachable at `import.meta.env.VITE_API_BASE_URL`.

**Rentle** is a peer-to-peer marketplace for Nepal where people rent physical items
(cameras, tech gear, sarees, traditional costumes) and book local services (movers,
photographers, event help) from verified people nearby — think "Airbnb's trust model
applied to everyday things and skills", built for Kathmandu Valley and Pokhara.

Today these transactions happen through WhatsApp groups and Facebook posts: no
verification, no record of what was agreed, no recourse when a camera comes back
damaged. Rentle's entire value is **trust between strangers**: citizenship-card
verification, phone OTP, deposits, dual-sided reviews unlocked only after a
completed booking, and messaging always scoped to a specific booking so everything
stays on record.

**Users:**

- **Renters/Clients** — ages 20–35, students and young professionals, mobile-first,
  wary of online platforms. The UI must constantly, quietly reassure.
- **Owners/Providers** — ages 22–45, monetising an idle camera or offering services.
  For many this is their first "storefront".
- **Admins** — small internal team reviewing citizenship cards.

**The transaction (drives most screens):**

1. Renter finds a listing and requests dates.
2. Owner approves or rejects.
3. On approval a message thread opens. Renter pays the deposit directly via
   eSewa/Khalti (outside the app), then uploads a screenshot as proof.
4. Owner confirms deposit received → booking becomes Active.
5. After the rental, a party marks it Complete.
6. Both sides get 30 days to review each other.

Every listing is a **PRODUCT** (per-day price, condition/brand, deposit) or a
**SERVICE** (per-hour or flat price, service area, minimum notice, time slots).
One layout family must handle both.

---

## 2. Tech constraints

- React + TypeScript + Vite + Tailwind. shadcn/ui components are fine as a base but
  restyle them fully to the design system below — no default shadcn look.
- **TanStack Query** for all server state (no data in Redux/context except auth).
- **react-router** with the routes in section 6.
- API client: a single thin `fetch` wrapper that
  - prefixes `VITE_API_BASE_URL`,
  - attaches `Authorization: Bearer <accessToken>` when logged in,
  - unwraps the response envelope (section 5.1),
  - on 401: calls `POST /api/v1/auth/refresh` with the stored refresh token,
    stores the new token pair, retries the original request once; if refresh
    fails, clears auth and redirects to `/login`.
- Store the token pair in localStorage (`rentle.accessToken`, `rentle.refreshToken`)
  plus the current user object from the auth response.
- All uploads are `multipart/form-data` with field name `file` (or `files` for
  listing images) — do not JSON-encode files.
- Dates are ISO `YYYY-MM-DD`, times `HH:mm`, money is a decimal number in NPR.
  Always render prices as `NPR 2,500 / day` style — currency and unit visible.

---

## 3. Design system

### Personality

Neighborly, capable, calm. People use this to hand a NPR 300,000 camera to a
stranger — it should feel like a well-run local institution: warm but precise,
friendly but never cute. Not a Silicon Valley SaaS, not a crypto app. Every screen
answers the silent question: *"can I trust this person, and what happens next?"*

### Color (exact tokens — solid only, no gradients)

- **Paper** `#FAF7F2` — app background. Never pure white, never grey-blue.
- **Ink** `#1F2421` — primary text; toasts are ink-colored.
- **Pine** `#1E5748` — primary actions, active states, links, masthead.
- **Marigold** `#E8A13A` — accents: ratings, highlights. When used as *text on
  paper*, darken to `#B07515` for WCAG AA contrast.
- **Brick** `#B4552D` — destructive/urgent actions, unread badges, form errors.
- **Stone** `#8A8578` — secondary text and placeholders. Borders use the warmer
  `#DDD6C8` / `#E7E1D4`, not stone itself.
- **Status tints:** soft 8–16% alpha washes of pine/marigold for chips and tags —
  e.g. Product tag `rgba(30,87,72,.09)`, Service tag `rgba(232,161,58,.16)`.
  Every booking state also differs by label and icon, never color alone.

Paper dominates; pine is the one confident primary; accents appear only when they
mean something.

### Typography

- **Display:** Fraunces (Google Fonts, serif) — screen titles, listing titles,
  prices on detail pages, empty-state headlines. Weights 600–700, tight
  line-height, letter-spacing `-0.01em`.
- **Body/UI:** Instrument Sans (Google Fonts) — weights 400/500/600/700,
  body ≥ 15px mobile / 16px desktop. Never Inter, never system-only.
- Tabular figures (`font-variant-numeric: tabular-nums`) on all prices and dates;
  format `NPR 2,500 / day`, never a bare number. Strong scale jumps (32/24/18/15/13).

### Key moves (signature patterns — use these)

- Solid **pine masthead** containing a paper-colored search field and the desktop
  nav; outlined category pills below — active pill = marigold fill with pine text.
- A marigold-tinted **trust strip** directly under the masthead:
  "Every owner is citizenship-verified…".
- Flat white cards, 1px warm border (`#E7E1D4`), 6–7px radius, **no shadows**;
  hover state = pine border.
- Listing rows/cards: 4:3 photo left, serif title, pine-colored price.
- Photo placeholders: striped paper-tone blocks with small monospace labels —
  no grey boxes.
- Lucide stroke icons at one weight throughout.
- Mobile bottom tabs (Explore / Bookings / Messages / Profile) ↔ desktop top nav
  inside the masthead; content max-width 1200px; 44px+ touch targets.
- Toasts in ink; all transitions 200ms ease-out.

### Layout

- **Desktop-first at ~1280px** (content max-width ~1200px), fully responsive to
  390px. Desktop: slim top bar — logo, search, Explore, Bookings, Messages (unread
  badge), profile menu, persistent pine "List an item" button. At phone widths the
  top bar collapses and a bottom tab bar takes over: Explore, Bookings, Messages,
  Profile.
- One layout primitive repeated: flat card on paper, 1px stone border, 6–8px
  radius, no drop shadows (one subtle elevation for overlays only). Density over
  airiness — a busy healthy market, not a sparse portfolio.
- Listing card: 4:3 photo, serif title, price with unit, district + pin glyph,
  marigold star + review count, owner verified badge, small Product/Service tag.
- Trust surfaces are first-class: verified badge (pine shield + "Verified"), trust
  score, review count, member since, deposit stated before any request.
- **Booking status timeline** — horizontal stepper of the five states on every
  booking detail, each state paired with the one action it unlocks.
- Forms: labels above fields, never placeholder-as-label, inline errors in brick.
- Icons: Lucide, one stroke weight. No emoji in UI chrome, no sparkle icons.
- Photography only in imagery — no 3D illustrations, no abstract blobs.

### Voice & mock content

Plain, concrete, calm: "Your booking request was sent. Sunita usually responds
within a few hours." — never "🎉 Awesome!". Use real Nepali content everywhere:
Canon EOS R5, Bhaktapur, daura suruwal, NPR 1,500/day, Dashain week. Never
"John Doe" or "Lorem ipsum".

### UX quality bar

- One primary pine action per screen; next step obvious within two seconds.
- Skeleton placeholders (paper-tone, no shimmer circus) for loading lists; never a
  blank screen or lone full-page spinner.
- Errors are recoverable instructions: show the API's `error` string, keep the
  user's input, offer retry. Booking date conflicts (HTTP 409) say which dates clash.
- OTP boxes auto-advance; numeric keypad on numeric fields; wizard shows progress;
  sensible defaults (start date = tomorrow).
- Animation only where it explains change, 150–250ms ease-out. Nothing bounces.
- WCAG AA contrast, visible focus states, labels tied to inputs, 44px touch targets
  at phone widths, hover states on desktop.

### Hard avoid-list (none may appear)

Purple/violet/indigo or any gradient · Inter/system-only fonts · glassmorphism,
glowing orbs, mesh backgrounds · uniform 16px-radius white cards on grey with soft
shadows · vague hero copy ("Rent the future") · colored left-border accent strips ·
cyan-on-dark dashboard aesthetic or default dark mode · sparkles/rockets/emoji as
design elements · timid evenly-spread pastels · centered-everything symmetry.

---

## 4. Auth model (frontend behavior)

- Register returns tokens immediately; user status is `PENDING_VERIFICATION`.
- After register, route to OTP screen; `POST /auth/otp/verify` with phone + 6-digit
  code sets phone verified.
- To **create listings** a user must have status `VERIFIED` (admin approves their
  citizenship card). If a non-verified user hits "List an item", show a friendly
  gate screen explaining the two steps (verify phone → upload citizenship card →
  wait for approval) with links to do each.
- `role: "ADMIN"` unlocks `/admin`. Hide admin nav from everyone else and guard the
  routes.
- Suspended users get 403s with an explanatory message — surface it and log out.
- Access token expires in 15 min — the refresh-retry interceptor (section 2)
  handles it invisibly.

---

## 5. API contract

Base URL: `VITE_API_BASE_URL`, all paths below are under `/api/v1`.

### 5.1 Response envelope

Every response:

```json
{ "data": <payload or null>, "error": <string or null>, "timestamp": "ISO-8601" }
```

Paginated payloads:

```json
{ "content": [...], "page": 0, "size": 20, "totalElements": 154, "totalPages": 8, "last": false }
```

Errors: 400 validation/business (`error` explains), 401 bad/expired token,
403 forbidden/suspended, 404 not found, 409 conflict (double-booking, invalid
state transition, duplicate review), 429 rate limited. Show `error` verbatim in a
toast or inline.

### 5.2 Auth

| Method & path | Body | Returns |
|---|---|---|
| POST `/auth/register` | `{phoneNumber, email, password, fullName}` | AuthResponse |
| POST `/auth/login` | `{identifier, password}` (identifier = phone or email) | AuthResponse |
| POST `/auth/refresh` | `{refreshToken}` | AuthResponse (new pair — old refresh token dies) |
| POST `/auth/logout` | `{refreshToken}` (+ Bearer header) | — |
| POST `/auth/otp/send` | `{phoneNumber}` | — (3/hour limit) |
| POST `/auth/otp/verify` | `{phoneNumber, code}` | — |

AuthResponse: `{accessToken, refreshToken, tokenType: "Bearer", expiresIn, user}`
where `user` = `{id, phoneNumber, email, fullName, profilePhotoUrl, role, status,
phoneVerified, citizenshipVerified, citizenshipUploaded, trustScore, createdAt}`.

### 5.3 Users

| Method & path | Notes |
|---|---|
| GET `/users/me` | full own profile |
| PUT `/users/me` | `{fullName?, email?}` |
| POST `/users/me/photo` | multipart `file` (JPEG/PNG/WebP ≤ 2MB) |
| POST `/users/me/citizenship` | multipart `file` (≤ 5MB) |
| GET `/users/{id}` | public: `{id, fullName, profilePhotoUrl, verified, trustScore, memberSince}` |
| GET `/users/{id}/listings` | paginated, active listings only |
| GET `/users/{id}/reviews` | paginated reviews about this user |

### 5.4 Categories

GET `/categories` (flat) and GET `/categories/tree`. Category:
`{id, parentId, name, slug, listingType: PRODUCT|SERVICE|BOTH, iconName, sortOrder, children}`.
Seeded: Cameras & Tech, Traditional Clothing (PRODUCT); Moving & Transport,
Event & Photography (SERVICE).

### 5.5 Listings

| Method & path | Notes |
|---|---|
| POST `/listings` | VERIFIED only. Body: `{title(5–120), description(20–2000), categoryId, type: PRODUCT\|SERVICE, pricePerUnit, priceUnit: PER_DAY\|PER_HOUR\|FLAT, district, locationText?, depositAmount?, product?: {condition: NEW\|GOOD\|FAIR, brand?, model?, minRentalDays?, maxRentalDays?}, service?: {serviceAreaKm?, typicalDuration?: HOURLY\|HALF_DAY\|FULL_DAY\|CUSTOM, minNoticeHours?, portfolioUrl?}}`. `product` required when type PRODUCT, `service` when SERVICE. Created as DRAFT. |
| GET `/listings` | search. Query: `q, type, categoryId, district, sort(newest\|price_asc\|price_desc\|rating), page, size`. Returns summaries: `{id, type, status, title, pricePerUnit, priceUnit, depositAmount, district, averageRating, reviewCount, coverImage, createdAt}` |
| GET `/listings/me` | own listings incl. drafts/inactive |
| GET `/listings/{id}` | full detail incl. owner public card, images[], product/service block, averageRating, reviewCount, totalBookings |
| PUT `/listings/{id}` | owner only; partial update; `status` accepts ACTIVE or INACTIVE (publish = set ACTIVE) |
| DELETE `/listings/{id}` | owner; soft delete |
| POST `/listings/{id}/images` | multipart `files` (≤5 total, 10MB each) |
| DELETE `/listings/{id}/images/{imageId}` | |
| GET `/listings/{id}/availability` | `{listingId, blocked: [{rangeId, startDate, endDate, source: OWNER_BLOCKED\|BOOKED}]}` — disable these dates in calendars |
| POST `/listings/{id}/availability` | owner blocks `{startDate, endDate, reason?}` |
| DELETE `/listings/{id}/availability/{rangeId}` | owner unblocks |

### 5.6 Bookings

| Method & path | Who | Notes |
|---|---|---|
| POST `/bookings` | renter | `{listingId, startDate, endDate, startTime?, endTime?, note?}` — times required for PER_HOUR listings |
| GET `/bookings/me/as-renter` | renter | paginated |
| GET `/bookings/me/as-owner` | owner | paginated |
| GET `/bookings/{id}` | participants | full detail |
| POST `/bookings/{id}/approve` | owner | REQUESTED → APPROVED |
| POST `/bookings/{id}/reject` | owner | `{reason?}` REQUESTED → REJECTED |
| POST `/bookings/{id}/deposit` | renter | multipart `file` screenshot; APPROVED → DEPOSIT_PENDING |
| POST `/bookings/{id}/confirm-deposit` | owner | → ACTIVE (works directly from APPROVED when deposit is 0) |
| POST `/bookings/{id}/complete` | either | ACTIVE → COMPLETED |
| POST `/bookings/{id}/cancel` | either | `{reason?}` any pre-terminal state → CANCELLED |

Booking object: `{id, listingId, listingTitle, listingType, ownerId, ownerName,
renterId, renterName, startDate, endDate, startTime, endTime, status, totalPrice,
depositAmount, depositPaid, depositProofUrl, renterNote, cancellationReason, createdAt}`.

**Action matrix — render exactly these actions per status and viewer:**

| Status | Renter sees | Owner sees |
|---|---|---|
| REQUESTED | Cancel | Approve · Decline |
| APPROVED | Upload deposit proof · Cancel | Cancel (Confirm deposit if depositAmount = 0) |
| DEPOSIT_PENDING | Re-upload proof · Cancel | Confirm deposit received · Cancel |
| ACTIVE | Mark complete · Cancel | Mark complete · Cancel |
| COMPLETED | Leave review (30 days, once) | Leave review (30 days, once) |
| CANCELLED / REJECTED | — (show reason) | — (show reason) |

### 5.7 Messages (booking-scoped only — no standalone DMs)

| Method & path | Notes |
|---|---|
| GET `/bookings/{bookingId}/messages` | paginated ascending; participants only |
| POST `/bookings/{bookingId}/messages` | `{content}` (≤2000); allowed once status is APPROVED or later, not REQUESTED/REJECTED/CANCELLED |
| PUT `/bookings/{bookingId}/messages/read` | marks counterpart's messages read |

Poll the open thread every 30 seconds (no websockets in Phase 1).

### 5.8 Reviews

| Method & path | Notes |
|---|---|
| POST `/reviews` | `{bookingId, rating 1–5, comment? ≤500}` — participant, COMPLETED, within 30 days, once per author; 409/400 with clear `error` otherwise |
| GET `/listings/{id}/reviews` | paginated |
| GET `/users/{id}/reviews` | paginated |

### 5.9 Admin (role ADMIN)

| Method & path | Notes |
|---|---|
| GET `/admin/users?status=&page=&size=` | full profiles incl. `citizenshipUploaded` |
| PUT `/admin/users/{id}/verify` | approve citizenship → user becomes VERIFIED |
| PUT `/admin/users/{id}/suspend` / `unsuspend` | |
| GET `/admin/bookings` · GET `/admin/listings` | paginated read-only |

---

## 6. Routes & pages

| Route | Page |
|---|---|
| `/` | Explore: search bar, category row, district chips (Kathmandu, Lalitpur, Bhaktapur, Pokhara), sort control, listing grid (desktop 3–4 cols → 1 col mobile), URL-synced filters |
| `/listings/:id` | Listing detail: gallery, serif title, price + deposit block, specs (product) or service info, availability calendar with blocked dates disabled, owner trust card, reviews, booking panel — desktop: sticky right column; mobile: sticky bottom bar opening a booking sheet with live price breakdown (rate × duration + deposit separately, with one plain line explaining the eSewa/Khalti deposit hand-off) |
| `/login`, `/register`, `/verify-otp` | Auth; register → OTP; explain in one line why phone verification exists |
| `/bookings` | Two tabs: "Renting" and "My listings' bookings" — status chip, dates, counterpart, and the single action each booking currently needs |
| `/bookings/:id` | Status timeline stepper, booking facts, deposit proof upload/confirm per action matrix, message thread (right column desktop / below on mobile), review prompt when COMPLETED |
| `/messages` | Thread list across bookings (unread badges) linking into `/bookings/:id` — each thread headed by listing photo + dates |
| `/profile/:id` | Public profile: person-first, verified badge, trust score, member since, active listings, reviews |
| `/profile` | Own profile: edit, photo upload, citizenship upload with pending/approved state + one-line privacy reassurance |
| `/listings/new` | Create wizard: type → details → photos → price & deposit → review & publish (publish = PUT status ACTIVE); finish screen shows the listing as renters will see it |
| `/listings/mine` | Manage own listings: status toggle ACTIVE/INACTIVE, edit, images, blocked dates |
| `/admin` | Desktop-oriented: verification queue (pending users with citizenship uploaded → view image, approve), users/listings/bookings tables. Same design language, denser |

Guard all routes except `/`, `/listings/:id`, `/profile/:id`, and auth pages behind
login; guard `/admin` behind role ADMIN.

---

## 7. Latitude

The design system sets direction, not handcuffs. You are the senior product
designer: if a better pattern serves trust, clarity, or the two-sided marketplace
dynamic, take it — as long as the API contract is followed exactly, the palette
stays out of tech-default territory, the typography stays intentional, and nothing
from the avoid-list appears. The status timeline, the deposit hand-off explanation,
and the review moment are where this product wins or loses — spend your craft there.
