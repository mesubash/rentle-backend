# Rentle API Reference

**Version:** Phase 1 · **Base URL:** `/api/v1` · **Format:** JSON (UTF-8)

The Rentle backend is a REST API for a peer-to-peer marketplace: users rent
physical **products** and book local **services** from verified people, coordinate
through booking-scoped messages, settle deposits out-of-band, and review each other
after completion.

---

## Table of contents

- [1. Conventions](#1-conventions)
  - [1.1 Response envelope](#11-response-envelope)
  - [1.2 Pagination](#12-pagination)
  - [1.3 Errors & status codes](#13-errors--status-codes)
  - [1.4 Authentication](#14-authentication)
  - [1.5 Rate limits](#15-rate-limits)
  - [1.6 Data types & enums](#16-data-types--enums)
- [2. Authentication](#2-authentication)
  - [POST /auth/register](#post-authregister)
  - [POST /auth/register/verify](#post-authregisterverify)
  - [POST /auth/register/resend](#post-authregisterresend)
  - [POST /auth/login](#post-authlogin)
  - [Google sign-in (backend-driven)](#google-sign-in-backend-driven)
  - [GET /auth/verify-email](#get-authverify-email)
  - [POST /auth/refresh](#post-authrefresh)
  - [POST /auth/logout](#post-authlogout)
- [3. Users & profiles](#3-users--profiles)
  - [GET /users/me](#get-usersme)
  - [PUT /users/me](#put-usersme)
  - [POST /users/me/photo](#post-usersmephoto)
  - [POST /users/me/citizenship](#post-usersmecitizenship)
  - [GET /users/me/citizenship](#get-usersmecitizenship)
  - [GET /users/{id}](#get-usersid)
  - [GET /users/{id}/listings](#get-usersidlistings)
  - [GET /users/{id}/reviews](#get-usersidreviews)
- [4. Categories](#4-categories)
  - [GET /categories](#get-categories)
  - [GET /categories/tree](#get-categoriestree)
- [5. Listings](#5-listings)
  - [POST /listings](#post-listings)
  - [GET /listings](#get-listings-search)
  - [GET /listings/me](#get-listingsme)
  - [GET /listings/{id}](#get-listingsid)
  - [PUT /listings/{id}](#put-listingsid)
  - [DELETE /listings/{id}](#delete-listingsid)
  - [POST /listings/{id}/images](#post-listingsidimages)
  - [DELETE /listings/{id}/images/{imageId}](#delete-listingsidimagesimageid)
  - [GET /listings/{id}/availability](#get-listingsidavailability)
  - [POST /listings/{id}/availability](#post-listingsidavailability)
  - [DELETE /listings/{id}/availability/{rangeId}](#delete-listingsidavailabilityrangeid)
- [6. Bookings](#6-bookings)
  - [Booking lifecycle](#booking-lifecycle)
  - [POST /bookings](#post-bookings)
  - [GET /bookings/me/as-renter](#get-bookingsmeas-renter)
  - [GET /bookings/me/as-owner](#get-bookingsmeas-owner)
  - [GET /bookings/{id}](#get-bookingsid)
  - [POST /bookings/{id}/approve](#post-bookingsidapprove)
  - [POST /bookings/{id}/reject](#post-bookingsidreject)
  - [POST /bookings/{id}/deposit](#post-bookingsiddeposit)
  - [POST /bookings/{id}/confirm-deposit](#post-bookingsidconfirm-deposit)
  - [POST /bookings/{id}/complete](#post-bookingsidcomplete)
  - [POST /bookings/{id}/cancel](#post-bookingsidcancel)
- [7. Messages](#7-messages)
  - [GET /messages/unread-count](#get-messagesunread-count)
  - [GET /bookings/{bookingId}/messages](#get-bookingsbookingidmessages)
  - [POST /bookings/{bookingId}/messages](#post-bookingsbookingidmessages)
  - [PUT /bookings/{bookingId}/messages/read](#put-bookingsbookingidmessagesread)
- [8. Reviews](#8-reviews)
  - [POST /reviews](#post-reviews)
  - [GET /listings/{id}/reviews](#get-listingsidreviews)
  - [GET /users/{id}/reviews](#get-usersidreviews-1)
- [9. Admin](#9-admin)
  - [GET /admin/users](#get-adminusers)
  - [PUT /admin/users/{id}/verify](#put-adminusersidverify)
  - [PUT /admin/users/{id}/suspend](#put-adminusersidsuspend)
  - [PUT /admin/users/{id}/unsuspend](#put-adminusersidunsuspend)
  - [GET /admin/bookings](#get-adminbookings)
  - [GET /admin/listings](#get-adminlistings)
- [10. Object schemas](#10-object-schemas)

---

## 1. Conventions

### 1.1 Response envelope

Every response — success or failure — uses one envelope:

```json
{
  "data": { "...": "payload, or null on error" },
  "error": "human-readable message, or null on success",
  "timestamp": "2026-07-11T08:52:00.703613Z"
}
```

- On success (`2xx`): `data` is populated, `error` is `null`.
- On failure (`4xx`/`5xx`): `data` is `null`, `error` is a short message safe to show
  a user.

Clients should branch on the HTTP status, then read `data` or `error` accordingly.

### 1.2 Pagination

List endpoints return a page object as their `data`:

```json
{
  "content": [ /* items */ ],
  "page": 0,
  "size": 20,
  "totalElements": 154,
  "totalPages": 8,
  "last": false
}
```

Query parameters (all list endpoints): `page` (0-based, default `0`), `size`
(default `20`, capped per endpoint at 50–100). Ordering is fixed per endpoint and
documented inline.

### 1.3 Errors & status codes

| Status | Meaning | Typical `error` |
|--------|---------|-----------------|
| `400 Bad Request` | Validation or business-rule failure | `Validation failed: {field=message}` · `You cannot book your own listing` |
| `401 Unauthorized` | Missing, invalid, expired, or revoked token | `Invalid credentials` |
| `403 Forbidden` | Authenticated but not allowed (wrong owner/role, suspended) | `Only the listing owner can modify this listing` |
| `404 Not Found` | Resource absent or not visible to caller | `Listing not found` |
| `409 Conflict` | State/uniqueness conflict | `Listing is already booked for the selected dates` · `Cannot transition booking from REQUESTED to COMPLETED` |
| `429 Too Many Requests` | Rate limit exceeded | `Too many login attempts, try again later` |
| `500 Internal Server Error` | Unexpected failure (details logged, not leaked) | `Internal server error` |

Validation errors (`400`) enumerate offending fields inside `error`. The envelope
shape never changes.

### 1.4 Authentication

Auth is stateless JWT (RS256). Two tokens:

- **Access token** — 15-minute TTL. Sent as `Authorization: Bearer <token>` on every
  protected request. Carries `sub` (user id), `role`, `status`, `jti`.
- **Refresh token** — 7-day TTL, opaque, stored server-side in Redis. Exchanged at
  [`POST /auth/refresh`](#post-authrefresh) for a new pair. **Single-use**: each
  refresh rotates (invalidates) the old refresh token.

Logout revokes the access token by blacklisting its `jti` in Redis until expiry and
deletes the refresh token. A suspended user's status is embedded at token-mint time,
so suspension takes effect on the next token issuance.

Public endpoints (no token): `POST /auth/*`, `GET /categories*`, `GET /listings`,
`GET /listings/{id}`, `GET /listings/{id}/availability`, `GET /listings/{id}/reviews`,
`GET /users/{id}`, `GET /users/{id}/listings`, `GET /users/{id}/reviews`. Everything
else requires a valid access token. `/admin/**` additionally requires role `ADMIN`.

> **Note on deployment:** the reference frontend calls the API through a same-origin
> backend-for-frontend proxy and keeps tokens in HTTP-only cookies; the browser never
> receives raw tokens. A native or third-party client uses the bearer flow above
> directly.

### 1.5 Rate limits

Redis-backed, fixed-window. Exceeding a limit returns `429`.

| Scope | Limit |
|-------|-------|
| All API requests, per IP | 60 / minute |
| Login attempts, per IP | 5 / 15 minutes |
| OTP requests, per phone | 3 / hour |
| Listing creation, per user | 10 / day |
| Failed logins, per account | 5 → 15-minute account lock |

### 1.6 Data types & enums

- **UUID** — all resource ids.
- **Money** — decimal number, NPR, two places (e.g. `2500.00`). No currency field;
  NPR is implied Phase 1.
- **Date** — `YYYY-MM-DD`. **Time** — `HH:mm`. **Timestamp** — ISO-8601 UTC.

| Enum | Values |
|------|--------|
| `UserRole` | `USER`, `ADMIN` |
| `UserStatus` | `PENDING_VERIFICATION`, `VERIFIED`, `SUSPENDED` |
| `ListingType` | `PRODUCT`, `SERVICE` |
| `ListingStatus` | `DRAFT`, `ACTIVE`, `INACTIVE`, `REMOVED` |
| `PriceUnit` | `PER_DAY`, `PER_HOUR`, `FLAT` |
| `ItemCondition` | `NEW`, `GOOD`, `FAIR` |
| `ServiceDuration` | `HOURLY`, `HALF_DAY`, `FULL_DAY`, `CUSTOM` |
| `CategoryType` | `PRODUCT`, `SERVICE`, `BOTH` |
| `BookingStatus` | `REQUESTED`, `APPROVED`, `DEPOSIT_PENDING`, `ACTIVE`, `COMPLETED`, `CANCELLED`, `REJECTED` |

---

## 2. Authentication

### POST /auth/register

**Step 1 of two.** Registration is gated on phone verification: this endpoint does
**not** create an account. It validates the details, holds them for 15 minutes, and
sends a phone OTP. The account is created only by
[`/auth/register/verify`](#post-authregisterverify).

**Auth:** public · **Body:**

| Field | Type | Rules |
|-------|------|-------|
| `phoneNumber` | string | required, `^\+?[0-9]{7,15}$` |
| `email` | string | required, valid email, ≤100 |
| `password` | string | required, 8–72 chars |
| `fullName` | string | required, 2–100 |

**`200 OK`** → `{ "data": { "phoneNumber": "...", "otpRequired": true,
"expiresInSeconds": 900, "message": "..." }, ... }`. No account and no tokens yet.
**Errors:** `400` phone/email already registered or validation failure.

### POST /auth/register/verify

**Step 2.** Verify the phone OTP; on success the account is created (`phoneVerified:
true`, email unverified) and a session is issued. An email verification link is sent
(email can be confirmed later — it never blocks sign-up).

**Auth:** public · **Body:** `{ "phoneNumber": "...", "code": "482910" }`
**`201 Created`** → [`AuthResponse`](#authresponse).
**Errors:** `400` wrong/expired code · registration expired · already registered.

### POST /auth/register/resend

Resend the registration OTP while a pending signup exists.
**Auth:** public · **Body:** `{ "phoneNumber": "..." }` · **`200 OK`** → `{ "data": "Verification code sent", ... }`.

### POST /auth/login

**Auth:** public · **Body:** `{ "identifier": "<phone or email>", "password": "..." }`

**`200 OK`** → [`AuthResponse`](#authresponse).
**Errors:** `401` invalid credentials · account locked (5 failed attempts) ·
suspended · Google-only account (no password). `429` too many attempts.

### Google sign-in (backend-driven)

Google uses the Authorization Code flow entirely on the backend — the frontend holds
no Google client id or secret. Requires `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`,
`GOOGLE_REDIRECT_URI`.

- **`GET /auth/google/status`** (public) → `{ "enabled": bool, "loginUrl": "..." }`.
  The app shows the button and links it to `loginUrl` only when enabled.
- **`GET /auth/google/login`** (public) → 302 to Google's consent screen (or back to
  the app with `?error=google_unavailable` if unconfigured).
- **`GET /auth/google/callback?code&state`** (public) — Google returns here; the
  backend validates state, exchanges the code with the client secret, resolves/links
  the account, then 302s to the app at `/auth/google/callback?code=<handoff>` with a
  one-time handoff code.
- **`POST /auth/google/exchange`** — Body `{ "code": "<handoff>" }` → [`AuthResponse`](#authresponse).
  The app swaps the handoff for a session (BFF stores the cookies). No token ever
  rides in a URL.

A Google account has no phone; the user must add and verify one
([`POST /users/me/phone`](#post-usersmephone)) before booking or listing.

### GET /auth/verify-email

Opened from the email verification link. Consumes the token, marks the email verified,
and redirects to the app at `/auth/verify-email?status=success|invalid`.

**Auth:** public (token in query) · **Query:** `token` · **Response:** `302` redirect.

### POST /auth/refresh

Exchange a valid refresh token for a fresh pair. The presented refresh token is
invalidated (rotation).

**Auth:** public (refresh token in body) · **Body:** `{ "refreshToken": "..." }`

**`200 OK`** → [`AuthResponse`](#authresponse).
**Errors:** `401` invalid/expired/already-used refresh token · user suspended.

### POST /auth/logout

Revoke the current access token and delete the refresh token.

**Auth:** bearer · **Body:** `{ "refreshToken": "..." }` (optional but recommended)

**`200 OK`** → `{ "data": "Logged out", "error": null, ... }`

> **Dev delivery:** with no SMS/email provider wired, OTP codes and email links are
> logged and mirrored to a Discord webhook (`RENTLE_DISCORD_WEBHOOK`) for testing.

---

## 3. Users & profiles

### GET /users/me

The authenticated user's full private profile.

**Auth:** bearer · **`200 OK`** → [`UserProfile`](#userprofile).

### PUT /users/me

Update name and/or email. Both optional; only present fields change.

**Auth:** bearer · **Body:** `{ "fullName"?: string(2–100), "email"?: string }`
**`200 OK`** → [`UserProfile`](#userprofile). **Errors:** `400` email already in use.

### POST /users/me/photo

Upload a profile photo. `multipart/form-data`, field **`file`**, JPEG/PNG/WebP ≤ 2 MB.

**Auth:** bearer · **`200 OK`** → [`UserProfile`](#userprofile) (with `profilePhotoUrl`).

### POST /users/me/citizenship

Upload a citizenship card for identity verification. `multipart/form-data`, field
**`file`**, ≤ 5 MB. Stored privately; an admin reviews it. Cannot re-upload once verified.

**Auth:** bearer · **`200 OK`** → [`UserProfile`](#userprofile) (`citizenshipUploaded: true`).
**Errors:** `400` already verified.

### POST /users/me/phone

Set (or change) the caller's phone number and dispatch an SMS OTP. Used by Google
users to add a phone. Rejects a number already used by another account.

**Auth:** bearer · **Body:** `{ "phoneNumber": "+9779841000001" }`
**`200 OK`** → `{ "data": "Verification code sent", ... }`. **Errors:** `400` invalid / in use.

### POST /users/me/phone/verify

Verify the OTP for the caller's current phone → `phoneVerified`.
**Auth:** bearer · **Body:** `{ "code": "482910" }` · **`200 OK`** → [`UserProfile`](#userprofile).

### POST /users/me/email/verify/send

(Re)send the email verification link. Verification itself happens by opening the link
([`GET /auth/verify-email`](#get-authverify-email)), not by entering a code.
**Auth:** bearer · **`200 OK`** → `{ "data": "Verification link sent", ... }`. **Errors:** `400` already verified.

The document is stored privately (never under the public `/files` path) and is only
retrievable through the two authenticated endpoints below.

### GET /users/me/citizenship

Stream the caller's own citizenship image.

**Auth:** bearer · **`200 OK`** → the raw image (`image/*`). **Errors:** `404` none on file.

### GET /users/{id}

Public profile of any user — no contact details or identity data.

**Auth:** public · **`200 OK`** → [`PublicProfile`](#publicprofile). **Errors:** `404`.

### GET /users/{id}/listings

That user's `ACTIVE` listings, paginated (newest first, size ≤ 50).

**Auth:** public · **`200 OK`** → page of [`ListingSummary`](#listingsummary).

### GET /users/{id}/reviews

Reviews written **about** this user, paginated (newest first).

**Auth:** public · **`200 OK`** → page of [`Review`](#review).

---

## 4. Categories

### GET /categories

Flat list of active categories, ordered by `sortOrder`.

**Auth:** public · **`200 OK`** → array of [`Category`](#category) (each with empty `children`).

### GET /categories/tree

Two-level tree: top-level categories with their children nested.

**Auth:** public · **`200 OK`** → array of [`Category`](#category) with populated `children`.

Seeded categories: **Cameras & Tech**, **Traditional Clothing** (`PRODUCT`);
**Moving & Transport**, **Event & Photography** (`SERVICE`).

---

## 5. Listings

### POST /listings

Create a listing. The caller must be `VERIFIED`. Created as `DRAFT` — publish by
setting status `ACTIVE` via [`PUT /listings/{id}`](#put-listingsid). Supply exactly
one of `product` / `service` matching `type`.

**Auth:** bearer (VERIFIED) · **Body:**

| Field | Type | Rules |
|-------|------|-------|
| `title` | string | required, 5–120 |
| `description` | string | required, 20–2000 |
| `categoryId` | UUID | required, must accept `type` |
| `type` | `ListingType` | required |
| `pricePerUnit` | money | required, ≥ 1.0 |
| `priceUnit` | `PriceUnit` | required |
| `district` | string | required, ≤ 50 |
| `locationText` | string | optional, ≤ 200 |
| `depositAmount` | money | optional, ≥ 0 (default 0) |
| `product` | object | required if `type=PRODUCT`: `{condition: ItemCondition, brand?, model?, minRentalDays?≥1, maxRentalDays?}` |
| `service` | object | required if `type=SERVICE`: `{serviceAreaKm?, typicalDuration?: ServiceDuration, minNoticeHours?≥0, portfolioUrl?}` |

```http
POST /api/v1/listings
Authorization: Bearer <token>
Content-Type: application/json

{ "title": "Canon EOS R5 with 24-70mm", "description": "Professional mirrorless kit for weddings and events in Kathmandu.",
  "categoryId": "35dc...", "type": "PRODUCT", "pricePerUnit": 2500, "priceUnit": "PER_DAY",
  "district": "Kathmandu", "locationText": "Near Thamel", "depositAmount": 20000,
  "product": { "condition": "GOOD", "brand": "Canon", "model": "EOS R5", "minRentalDays": 1, "maxRentalDays": 30 } }
```

**`201 Created`** → [`Listing`](#listing) (status `DRAFT`, empty `images`).
**Errors:** `403` not verified · daily limit (10) reached (`400`) · `404` category ·
`400` category rejects the type / missing detail block.

### GET /listings (search)

Search/browse `ACTIVE` listings with full-text search and filters.

**Auth:** public · **Query:**

| Param | Type | Notes |
|-------|------|-------|
| `q` | string | PostgreSQL full-text over title/description/district/location |
| `type` | `ListingType` | filter |
| `categoryId` | UUID | filter |
| `district` | string | case-insensitive match |
| `sort` | enum | `newest` (default), `price_asc`, `price_desc`, `rating` |
| `page`, `size` | int | `size` ≤ 50 |

**`200 OK`** → page of [`ListingSummary`](#listingsummary).

### GET /listings/me

Caller's own listings including drafts and inactive (excludes `REMOVED`), newest first.

**Auth:** bearer · **`200 OK`** → page of [`ListingSummary`](#listingsummary).

### GET /listings/{id}

Full listing detail. Non-owners only see `ACTIVE` listings; the owner also sees their
own `DRAFT`/`INACTIVE`.

**Auth:** optional bearer · **`200 OK`** → [`Listing`](#listing). **Errors:** `404`.

### PUT /listings/{id}

Partial update by the owner. Any subset of fields; `status` accepts only `ACTIVE`
(publish) or `INACTIVE`. Product/service detail blocks update in place.

**Auth:** bearer (owner) · **`200 OK`** → [`Listing`](#listing).
**Errors:** `403` not owner · `400` illegal status transition (`DRAFT`/`REMOVED`).

### DELETE /listings/{id}

Soft-delete (status → `REMOVED`). Owner only.

**Auth:** bearer (owner) · **`200 OK`** → `{ "data": "Listing removed", ... }`.

### POST /listings/{id}/images

Upload one or more images. `multipart/form-data`, field **`files`** (repeatable).
Max 5 per listing, ≤ 10 MB each, JPEG/PNG/WebP. First image is the cover.

**Auth:** bearer (owner) · **`201 Created`** → `{ "data": ["<url>", ...], ... }`.
**Errors:** `400` exceeds 5 images / bad type / too large.

### DELETE /listings/{id}/images/{imageId}

Remove an image. Owner only.

**Auth:** bearer (owner) · **`200 OK`** → `{ "data": "Image deleted", ... }`.

### GET /listings/{id}/availability

Blocked ranges — owner blocks and active bookings combined. Use to disable dates in a
calendar.

**Auth:** public · **`200 OK`**:

```json
{ "data": { "listingId": "...", "blocked": [
    { "rangeId": "9f..", "startDate": "2026-08-10", "endDate": "2026-08-12", "source": "OWNER_BLOCKED" },
    { "rangeId": null, "startDate": "2026-08-20", "endDate": "2026-08-22", "source": "BOOKED" } ] },
  "error": null, "timestamp": "..." }
```

`source` is `OWNER_BLOCKED` (deletable via `rangeId`) or `BOOKED` (`rangeId: null`).

### POST /listings/{id}/availability

Owner blocks a date range.

**Auth:** bearer (owner) · **Body:** `{ "startDate", "endDate", "reason"? }`
(`endDate ≥ startDate`). **`201 Created`** → updated [`Availability`](#availability).

### DELETE /listings/{id}/availability/{rangeId}

Owner removes a previously-blocked range.

**Auth:** bearer (owner) · **`200 OK`** → `{ "data": "Blocked range removed", ... }`.

---

## 6. Bookings

### Booking lifecycle

```
REQUESTED ──approve──▶ APPROVED ──(deposit proof)──▶ DEPOSIT_PENDING ──confirm──▶ ACTIVE ──complete──▶ COMPLETED
    │                     │                                  │                        │
    ├──reject──▶ REJECTED │                                  │                        └▶ (30-day review window)
    └──cancel──▶ CANCELLED└──cancel──▶ CANCELLED ◀──cancel───┴──cancel──▶ CANCELLED
```

Transitions are enforced in the service layer **and** by a database trigger.
Overlapping active bookings for one listing are physically impossible (GiST
exclusion constraint). Zero-deposit bookings may go `APPROVED → ACTIVE` directly via
confirm-deposit. Actions are restricted by role:

| Status | Renter may | Owner may |
|--------|-----------|-----------|
| `REQUESTED` | cancel | approve · reject |
| `APPROVED` | upload deposit proof · cancel | cancel (confirm-deposit if deposit = 0) |
| `DEPOSIT_PENDING` | re-upload proof · cancel | confirm deposit · cancel |
| `ACTIVE` | complete · cancel | complete · cancel |
| `COMPLETED` | review (once, ≤ 30 days) | review (once, ≤ 30 days) |
| `CANCELLED` / `REJECTED` | — | — |

### POST /bookings

Request a booking. The renter must be fully verified (`status = VERIFIED`, i.e. phone
+ email + citizenship all done). Renter only (cannot book own listing). Times required
when the listing is `PER_HOUR`.

**Auth:** bearer · **Body:**

| Field | Type | Rules |
|-------|------|-------|
| `listingId` | UUID | required, listing must be `ACTIVE` |
| `startDate` | date | required, not in the past |
| `endDate` | date | required, ≥ `startDate` |
| `startTime` / `endTime` | time | required for `PER_HOUR` |
| `note` | string | optional, ≤ 500 |

**`201 Created`** → [`Booking`](#booking) (status `REQUESTED`, `totalPrice` computed).
**Errors:** `400` own listing / past date / below min or above max rental days /
service notice period / missing times. `409` dates already booked or owner-blocked.
`404` listing.

Pricing: `PER_DAY` = rate × inclusive day count; `PER_HOUR` = rate × whole hours;
`FLAT` = rate.

### GET /bookings/me/as-renter

Caller's bookings as the renter, newest first, paginated.
**Auth:** bearer · **`200 OK`** → page of [`Booking`](#booking).

### GET /bookings/me/as-owner

Bookings on the caller's listings, newest first, paginated.
**Auth:** bearer · **`200 OK`** → page of [`Booking`](#booking).

### GET /bookings/{id}

Booking detail. Participants (renter or listing owner) only.
**Auth:** bearer · **`200 OK`** → [`Booking`](#booking). **Errors:** `403`, `404`.

### POST /bookings/{id}/approve

Owner approves a `REQUESTED` booking → `APPROVED`. Opens the message thread.
**Auth:** bearer (owner) · **`200 OK`** → [`Booking`](#booking). **Errors:** `403`, `409`.

### POST /bookings/{id}/reject

Owner rejects a `REQUESTED` booking → `REJECTED`.
**Auth:** bearer (owner) · **Body:** `{ "reason"? }` · **`200 OK`** → [`Booking`](#booking).

### POST /bookings/{id}/deposit

Renter uploads a deposit-payment screenshot. `multipart/form-data`, field **`file`**,
≤ 5 MB. `APPROVED → DEPOSIT_PENDING` (or re-upload while `DEPOSIT_PENDING`).
**Auth:** bearer (renter) · **`200 OK`** → [`Booking`](#booking). **Errors:** `403`, `400`.

### POST /bookings/{id}/confirm-deposit

Owner confirms the deposit was received → `ACTIVE`. Works directly from `APPROVED`
when `depositAmount` is 0.
**Auth:** bearer (owner) · **`200 OK`** → [`Booking`](#booking). **Errors:** `403`, `409`.

### POST /bookings/{id}/complete

Either participant marks an `ACTIVE` booking `COMPLETED`. Opens the review window and
increments the listing's booking count.
**Auth:** bearer (participant) · **`200 OK`** → [`Booking`](#booking). **Errors:** `403`, `409`.

### POST /bookings/{id}/cancel

Either participant cancels a non-terminal booking → `CANCELLED`.
**Auth:** bearer (participant) · **Body:** `{ "reason"? }` · **`200 OK`** → [`Booking`](#booking).

---

## 7. Messages

### GET /messages/unread-count

Total unread messages across every booking the caller participates in — backs the
navigation badge.

**Auth:** bearer · **`200 OK`** → `{ "data": { "count": 3 }, ... }`.

Messages are scoped to a booking — there are no standalone DMs. The thread opens once
the booking is `APPROVED` and closes to new messages only when terminal-before-approval
(`REQUESTED`/`REJECTED`/`CANCELLED-from-requested`). Poll every ~30 s (no websockets
in Phase 1).

### GET /bookings/{bookingId}/messages

Thread messages, oldest first, paginated (size ≤ 100). Participants only.
**Auth:** bearer · **`200 OK`** → page of [`Message`](#message). **Errors:** `403`, `404`.

### POST /bookings/{bookingId}/messages

Send a message (≤ 2000 chars). Allowed once `APPROVED` or later. The first message of
a booking triggers one SMS to the recipient.
**Auth:** bearer (participant) · **Body:** `{ "content": "..." }` · **`201 Created`** →
[`Message`](#message). **Errors:** `400` thread not open · `403`.

### PUT /bookings/{bookingId}/messages/read

Mark the counterpart's messages in this thread as read.
**Auth:** bearer (participant) · **`200 OK`** → `{ "data": <count marked>, ... }`.

---

## 8. Reviews

### POST /reviews

Leave a review after completion. The caller must be a participant of a `COMPLETED`
booking, within 30 days, once per booking. Rating and aggregates update the subject's
trust score and the listing rating (via DB trigger).

**Auth:** bearer · **Body:** `{ "bookingId": UUID, "rating": 1–5, "comment"?: ≤500 }`
**`201 Created`** → [`Review`](#review).
**Errors:** `400` not completed / window expired / already reviewed · `403` not a
participant · `404`.

### GET /listings/{id}/reviews

Reviews for a listing, newest first, paginated.
**Auth:** public · **`200 OK`** → page of [`Review`](#review).

### GET /users/{id}/reviews

Reviews about a user, newest first, paginated. (Same as
[§3](#get-usersidreviews).)
**Auth:** public · **`200 OK`** → page of [`Review`](#review).

---

## 9. Admin

All `/admin/**` endpoints require role `ADMIN`; others receive `403`.

### GET /admin/users

All users, paginated (newest first, size ≤ 100). Optional `status` filter
(`UserStatus`).
**Auth:** ADMIN · **`200 OK`** → page of [`UserProfile`](#userprofile).

### GET /admin/users/{id}/citizenship

Stream a user's citizenship image for verification review.
**Auth:** ADMIN · **`200 OK`** → the raw image (`image/*`). **Errors:** `404` none on file.

### PUT /admin/users/{id}/verify

Approve a pending citizenship submission → user becomes `VERIFIED`. Requires an
uploaded card and a verified phone.
**Auth:** ADMIN · **`200 OK`** → [`UserProfile`](#userprofile). **Errors:** `400` no card / phone unverified.

### PUT /admin/users/{id}/suspend

Suspend a user → `SUSPENDED`.
**Auth:** ADMIN · **`200 OK`** → [`UserProfile`](#userprofile).

### PUT /admin/users/{id}/unsuspend

Lift suspension → `VERIFIED` if the card was verified, else `PENDING_VERIFICATION`.
**Auth:** ADMIN · **`200 OK`** → [`UserProfile`](#userprofile). **Errors:** `400` not suspended.

### GET /admin/bookings

All bookings, paginated (newest first).
**Auth:** ADMIN · **`200 OK`** → page of [`Booking`](#booking).

### GET /admin/listings

All listings, paginated (newest first).
**Auth:** ADMIN · **`200 OK`** → page of [`ListingSummary`](#listingsummary).

---

## 10. Object schemas

### AuthResponse

```json
{ "accessToken": "string", "refreshToken": "string", "tokenType": "Bearer",
  "expiresIn": 900, "user": { /* UserProfile */ } }
```

### UserProfile

Full private profile (own or admin view).

```json
{ "id": "uuid", "phoneNumber": "string|null", "email": "string", "fullName": "string",
  "profilePhotoUrl": "string|null", "role": "USER|ADMIN",
  "status": "PENDING_VERIFICATION|VERIFIED|SUSPENDED",
  "authProvider": "LOCAL|GOOGLE", "hasPassword": true,
  "phoneVerified": true, "emailVerified": true,
  "citizenshipVerified": false, "citizenshipUploaded": false,
  "trustScore": 4.5, "createdAt": "timestamp" }
```

### PublicProfile

```json
{ "id": "uuid", "fullName": "string", "profilePhotoUrl": "string|null",
  "verified": true, "trustScore": 4.5, "memberSince": "timestamp" }
```

### Category

```json
{ "id": "uuid", "parentId": "uuid|null", "name": "string", "slug": "string",
  "listingType": "PRODUCT|SERVICE|BOTH", "iconName": "string|null",
  "sortOrder": 1, "children": [ /* Category */ ] }
```

### Listing

```json
{ "id": "uuid", "owner": { /* PublicProfile */ }, "categoryId": "uuid",
  "categoryName": "string", "type": "PRODUCT|SERVICE",
  "status": "DRAFT|ACTIVE|INACTIVE|REMOVED", "title": "string",
  "description": "string", "pricePerUnit": 2500.00, "priceUnit": "PER_DAY|PER_HOUR|FLAT",
  "depositAmount": 20000.00, "district": "string", "locationText": "string|null",
  "averageRating": 4.5, "reviewCount": 12, "totalBookings": 8,
  "images": ["url", "..."],
  "product": { "condition": "NEW|GOOD|FAIR", "brand": "string|null",
    "model": "string|null", "minRentalDays": 1, "maxRentalDays": 30 } | null,
  "service": { "serviceAreaKm": 10, "typicalDuration": "HOURLY|HALF_DAY|FULL_DAY|CUSTOM",
    "minNoticeHours": 24, "portfolioUrl": "string|null" } | null,
  "createdAt": "timestamp" }
```

### ListingSummary

Card shape for lists and search.

```json
{ "id": "uuid", "type": "PRODUCT|SERVICE", "status": "ACTIVE",
  "title": "string", "pricePerUnit": 2500.00, "priceUnit": "PER_DAY",
  "depositAmount": 20000.00, "district": "string", "averageRating": 4.5,
  "reviewCount": 12, "coverImage": "url|null", "createdAt": "timestamp" }
```

### Availability

```json
{ "listingId": "uuid", "blocked": [
    { "rangeId": "uuid|null", "startDate": "date", "endDate": "date",
      "source": "OWNER_BLOCKED|BOOKED" } ] }
```

### Booking

```json
{ "id": "uuid", "listingId": "uuid", "listingTitle": "string",
  "listingType": "PRODUCT|SERVICE", "ownerId": "uuid", "ownerName": "string",
  "renterId": "uuid", "renterName": "string", "startDate": "date", "endDate": "date",
  "startTime": "HH:mm|null", "endTime": "HH:mm|null",
  "status": "REQUESTED|APPROVED|DEPOSIT_PENDING|ACTIVE|COMPLETED|CANCELLED|REJECTED",
  "totalPrice": 7500.00, "depositAmount": 20000.00, "depositPaid": false,
  "depositProofUrl": "url|null", "renterNote": "string|null",
  "cancellationReason": "string|null", "createdAt": "timestamp" }
```

### Message

```json
{ "id": "uuid", "bookingId": "uuid", "senderId": "uuid", "senderName": "string",
  "content": "string", "isRead": false, "readAt": "timestamp|null",
  "createdAt": "timestamp" }
```

### Review

```json
{ "id": "uuid", "bookingId": "uuid", "authorId": "uuid", "authorName": "string",
  "subjectId": "uuid", "listingId": "uuid", "rating": 5, "comment": "string|null",
  "createdAt": "timestamp" }
```

---

*Generated for Rentle Phase 1. Endpoint behaviour is enforced by the service layer and
database constraints described in [docs/03_rentle_schema_design.md](03_rentle_schema_design.md).*
