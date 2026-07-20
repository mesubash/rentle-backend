# Rentle — API Reference

Base URL: `/api/v1`. Authentication is a Bearer JWT (the frontend BFF proxy stores it as an httpOnly cookie and attaches it automatically). Every JSON response uses the envelope `{ "data": <payload|null>, "error": <string|null>, "timestamp": <ISO-8601> }`; the tables below describe the `data` contents. Paged endpoints return `{ content: [...], page, size, totalElements, last }`.

> Related: [Features](./FEATURES.md) · [System Design](./SYSTEM_DESIGN.md)

## Contents

- [Authentication & Google OAuth](#authentication-google-oauth)
- [Users, Profile & KYC](#users-profile-kyc)
- [Listings, Categories & Templates](#listings-categories-templates)
- [Bookings](#bookings)
- [Messaging, Reviews, Favorites & Notifications](#messaging-reviews-favorites-notifications)
- [Reports, Workers & Pricing Policy](#reports-workers-pricing-policy)
- [Admin Console](#admin-console)
- [Platform IAM (Roles, Permissions, Assignments)](#platform-iam-roles-permissions-assignments)

## Authentication & Google OAuth

All endpoints are rooted at `/api/v1`. Endpoints in this group are public unless noted — they establish the session that other groups require. Successful JSON responses use the standard envelope `{ "data": <payload>, "error": null, "timestamp": <ISO-8601> }`; the tables below describe the `data` contents. Endpoints returning `RedirectView` (browser redirects) issue a 302 and carry no envelope.

The `AuthResponse` payload is shared by several endpoints:

| Field | Type | Description |
| --- | --- | --- |
| `accessToken` | string | Bearer JWT. The BFF stores this as an httpOnly cookie and attaches it as `Authorization: Bearer <token>`. |
| `refreshToken` | string | Opaque token used to obtain a new access token. |
| `tokenType` | string | Always `"Bearer"`. |
| `expiresIn` | number (long) | Access-token lifetime in seconds. |
| `user` | object | The caller's full profile (`UserProfileResponse`, below). |

The nested `user` object (`UserProfileResponse`):

| Field | Type | Description |
| --- | --- | --- |
| `id` | UUID | User ID. |
| `phoneNumber` | string \| null | Phone number; null until added post-signup. |
| `email` | string | Email address. |
| `fullName` | string | Display name. |
| `profilePhotoUrl` | string \| null | Avatar URL. |
| `status` | string | Account status (enum name). |
| `authProvider` | string | `LOCAL` or `GOOGLE`. |
| `hasPassword` | boolean | Whether a password is set (false for Google-only accounts). |
| `phoneVerified` | boolean | Phone verification state. |
| `emailVerified` | boolean | Email verification state. |
| `citizenshipVerified` | boolean | Citizenship/ID verification state. |
| `kycStatus` | string \| null | `null`, `SUBMITTED`, `APPROVED`, or `REJECTED`. |
| `trustScore` | number (decimal) | Computed trust score. |
| `paymentWallet` | string \| null | Payout wallet identifier. |
| `accountType` | string | Account type. |
| `businessName` | string \| null | Business name for business accounts. |
| `createdAt` | string (ISO-8601 instant) | Account creation timestamp. |

---

### POST /api/v1/auth/register

Email-first signup: creates the account and starts a session immediately. Returns `201 Created`. Auth: public.

**Request body**

| Field | Type | Required | Constraints | Description |
| --- | --- | --- | --- | --- |
| `email` | string | Yes | valid email, max 100 chars | Account email. |
| `password` | string | Yes | 8–72 chars | Account password. |
| `fullName` | string | Yes | 2–100 chars | Display name. |

**Response (data)** — `AuthResponse` (see above).

Request:
```json
{
  "email": "aarati@example.com",
  "password": "hunter2pass",
  "fullName": "Aarati Sharma"
}
```
Response data:
```json
{
  "accessToken": "eyJhbGciOiJI...",
  "refreshToken": "b1f3c8e2-...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": {
    "id": "8d2f...",
    "phoneNumber": null,
    "email": "aarati@example.com",
    "fullName": "Aarati Sharma",
    "profilePhotoUrl": null,
    "status": "ACTIVE",
    "authProvider": "LOCAL",
    "hasPassword": true,
    "phoneVerified": false,
    "emailVerified": false,
    "citizenshipVerified": false,
    "kycStatus": null,
    "trustScore": 0.0,
    "paymentWallet": null,
    "accountType": "PERSONAL",
    "businessName": null,
    "createdAt": "2026-07-18T09:12:44Z"
  }
}
```

---

### POST /api/v1/auth/login

Authenticate with email or phone plus password and start a session. Auth: public.

**Request body**

| Field | Type | Required | Constraints | Description |
| --- | --- | --- | --- | --- |
| `identifier` | string | Yes | non-blank | Email or phone number. |
| `password` | string | Yes | non-blank | Account password. |

**Response (data)** — `AuthResponse` (see above).

Request:
```json
{ "identifier": "aarati@example.com", "password": "hunter2pass" }
```
Response data: same shape as `register` above.

---

### POST /api/v1/auth/refresh

Exchange a refresh token for a fresh access token (and rotated session). Auth: public (refresh token is the credential).

**Request body**

| Field | Type | Required | Constraints | Description |
| --- | --- | --- | --- | --- |
| `refreshToken` | string | Yes | non-blank | Refresh token from a prior auth response. |

**Response (data)** — `AuthResponse` (see above).

Request:
```json
{ "refreshToken": "b1f3c8e2-4a90-4f2e-9c1a-77d0e5b8f321" }
```

---

### POST /api/v1/auth/logout

Invalidate the current session and (if provided) the refresh token. Auth: authenticated (uses the bearer JWT if present); the body is optional.

**Request body** (optional)

| Field | Type | Required | Constraints | Description |
| --- | --- | --- | --- | --- |
| `refreshToken` | string | No | non-blank if present | Refresh token to revoke. Whole body may be omitted. |

**Response (data)** — string message, e.g. `"Logged out"`.

Request:
```json
{ "refreshToken": "b1f3c8e2-4a90-4f2e-9c1a-77d0e5b8f321" }
```
Response data:
```json
"Logged out"
```

---

### POST /api/v1/auth/forgot-password

Request a password-reset link. Always returns OK — never reveals whether the email has an account. Auth: public.

**Request body**

| Field | Type | Required | Constraints | Description |
| --- | --- | --- | --- | --- |
| `email` | string | Yes | valid email | Account email to send the reset link to. |

**Response (data)** — string message.

Request:
```json
{ "email": "aarati@example.com" }
```
Response data:
```json
"If that email has an account, a reset link is on its way."
```

---

### POST /api/v1/auth/reset-password

Complete a password reset using the emailed token. Auth: public (token is the credential).

**Request body**

| Field | Type | Required | Constraints | Description |
| --- | --- | --- | --- | --- |
| `token` | string | Yes | non-blank | Reset token from the email link. |
| `password` | string | Yes | 8–72 chars | New password. |

**Response (data)** — string message.

Request:
```json
{ "token": "reset_9f2a...", "password": "newpass123" }
```
Response data:
```json
"Password updated. You can now log in."
```

---

### GET /api/v1/auth/verify-email

Opened from the email verification link; verifies the token then redirects into the app. Returns a 302 redirect (no JSON envelope). Auth: public.

**Query params**

| Name | In | Type | Required | Description |
| --- | --- | --- | --- | --- |
| `token` | query | string | Yes | Email-verification token. |

**Response** — 302 redirect to `{appUrl}/auth/verify-email?status=success` on success, or `?status=invalid` if the token is bad/expired.

---

### GET /api/v1/auth/google/status

Report whether Google sign-in is enabled and where the sign-in button should point. Auth: public.

**Response (data)**

| Field | Type | Description |
| --- | --- | --- |
| `enabled` | boolean | Whether Google OAuth is configured/available. |
| `loginUrl` | string | Full-page login entry URL (`{apiBaseUrl}/api/v1/auth/google/login`). |

Response data:
```json
{
  "enabled": true,
  "loginUrl": "https://api.rentle.example/api/v1/auth/google/login"
}
```

---

### GET /api/v1/auth/google/login

Full-page entry point: redirects the browser to Google's consent screen. Returns a 302 redirect (no JSON envelope). Auth: public.

**Response** — 302 redirect to Google's consent URL, or to `{appUrl}/auth/login?error=google_unavailable` if OAuth is not configured.

---

### GET /api/v1/auth/google/callback

Google's redirect target. On success, redirects to the app with a one-time handoff code. Returns a 302 redirect (no JSON envelope). Auth: public.

**Query params** (supplied by Google)

| Name | In | Type | Required | Description |
| --- | --- | --- | --- | --- |
| `code` | query | string | No | Authorization code from Google. |
| `state` | query | string | No | CSRF/state value echoed back. |
| `error` | query | string | No | Present when the user denied consent or Google errored. |

**Response** — 302 redirect to the app with a handoff code on success; to `{appUrl}/auth/login?error=google_denied` when `error` is set or `code` is missing; to `?error=google_failed` on processing failure.

---

### POST /api/v1/auth/google/exchange

The app swaps the one-time handoff code for a session (the BFF stores the resulting cookies). Auth: public (handoff code is the credential).

**Request body**

| Field | Type | Required | Constraints | Description |
| --- | --- | --- | --- | --- |
| `code` | string | Yes | non-blank | One-time handoff code from the callback redirect. |

**Response (data)** — `AuthResponse` (see above).

Request:
```json
{ "code": "handoff_7c1e2a90..." }
```
Response data: same shape as `register` above, with `"authProvider": "GOOGLE"` and `"hasPassword": false` for Google-only accounts.

---

## Users, Profile & KYC

All endpoints below live under the base URL `/api/v1` and return the standard envelope `{ "data": …, "error": null, "timestamp": "…" }`. Only the `data` contents are described.

### GET /api/v1/users/me

Returns the authenticated user's full profile. Auth: authenticated.

**Response (data)** — `UserProfileResponse`

| Field | Type | Description |
| --- | --- | --- |
| id | UUID | User id |
| phoneNumber | string \| null | Phone number (may be unset) |
| email | string \| null | Email address |
| fullName | string | Display name |
| profilePhotoUrl | string \| null | Profile photo URL |
| status | string | Account status enum name |
| authProvider | string | `LOCAL` or `GOOGLE` |
| hasPassword | boolean | Whether a local password is set |
| phoneVerified | boolean | Phone verified |
| emailVerified | boolean | Email verified |
| citizenshipVerified | boolean | KYC/citizenship verified |
| kycStatus | string \| null | `null` \| `SUBMITTED` \| `APPROVED` \| `REJECTED` |
| trustScore | number | Trust score |
| paymentWallet | string \| null | Payout wallet identifier |
| accountType | string | `INDIVIDUAL` or `BUSINESS` |
| businessName | string \| null | Business name (business accounts) |
| createdAt | string (ISO-8601) | Account creation time |

```json
{
  "id": "b3f1c2a4-5e6d-47f8-9a0b-1c2d3e4f5a6b",
  "phoneNumber": "+9779812345678",
  "email": "aarav@example.com",
  "fullName": "Aarav Sharma",
  "profilePhotoUrl": "https://cdn.rentle.app/u/aarav.jpg",
  "status": "ACTIVE",
  "authProvider": "LOCAL",
  "hasPassword": true,
  "phoneVerified": true,
  "emailVerified": false,
  "citizenshipVerified": true,
  "kycStatus": "APPROVED",
  "trustScore": 82.5,
  "paymentWallet": "esewa:9812345678",
  "accountType": "INDIVIDUAL",
  "businessName": null,
  "createdAt": "2026-01-04T09:39:00Z"
}
```

### GET /api/v1/users/me/permissions

Returns the sorted list of permission keys resolved for the authenticated user. Auth: authenticated.

**Response (data)** — array of strings

```json
["listing.create", "listing.read", "rental.manage"]
```

### PUT /api/v1/users/me

Updates the authenticated user's profile. All fields optional; only provided fields change. Auth: authenticated.

**Request body** — `UpdateProfileRequest`

| Field | Type | Required | Constraints | Description |
| --- | --- | --- | --- | --- |
| fullName | string | No | size 2–100 | Display name |
| email | string | No | valid email, max 100 | Email address |
| paymentWallet | string | No | max 100 | Payout wallet identifier |
| accountType | string | No | — | `INDIVIDUAL` or `BUSINESS` |
| businessName | string | No | max 120 | Business name |

```json
{
  "fullName": "Aarav Sharma",
  "email": "aarav@example.com",
  "accountType": "BUSINESS",
  "businessName": "Sharma Rentals"
}
```

**Response (data)** — `UserProfileResponse` (see GET /users/me).

### POST /api/v1/users/me/photo

Uploads a new profile photo. Auth: authenticated.

**Request body** — `multipart/form-data`

| Field | Type | Required | Constraints | Description |
| --- | --- | --- | --- | --- |
| file | file | Yes | — | Image file part |

**Response (data)** — `UserProfileResponse` (see GET /users/me).

### POST /api/v1/users/me/phone

Sets the user's phone number and sends an OTP verification code to it. Auth: authenticated.

**Request body** — `SetPhoneRequest`

| Field | Type | Required | Constraints | Description |
| --- | --- | --- | --- | --- |
| phoneNumber | string | Yes | matches `^\+?[0-9]{7,15}$` | Phone number (optional leading +) |

```json
{ "phoneNumber": "+9779812345678" }
```

**Response (data)** — string

```json
"Verification code sent"
```

### POST /api/v1/users/me/phone/verify

Verifies the user's phone with the OTP code. Auth: authenticated.

**Request body** — `CodeRequest`

| Field | Type | Required | Constraints | Description |
| --- | --- | --- | --- | --- |
| code | string | Yes | matches `^[0-9]{6}$` (6 digits) | OTP code |

```json
{ "code": "482913" }
```

**Response (data)** — `UserProfileResponse` (see GET /users/me).

### POST /api/v1/users/me/email/verify/send

Re-sends the email verification link to the authenticated user (verification completes by opening the link). Auth: authenticated.

**Response (data)** — string

```json
"Verification link sent"
```

### GET /api/v1/users/{id}

Returns a user's public profile (no phone, email, or citizenship data). Auth: authenticated.

**Path/query params**

| Name | In | Type | Required | Description |
| --- | --- | --- | --- | --- |
| id | path | UUID | Yes | Target user id |

**Response (data)** — `PublicProfileResponse`

| Field | Type | Description |
| --- | --- | --- |
| id | UUID | User id |
| fullName | string | Display name |
| profilePhotoUrl | string \| null | Profile photo URL |
| verified | boolean | Citizenship/KYC verified |
| trustScore | number | Trust score |
| accountType | string | `INDIVIDUAL` or `BUSINESS` |
| businessName | string \| null | Business name |
| memberSince | string (ISO-8601) | Account creation time |

```json
{
  "id": "b3f1c2a4-5e6d-47f8-9a0b-1c2d3e4f5a6b",
  "fullName": "Aarav Sharma",
  "profilePhotoUrl": "https://cdn.rentle.app/u/aarav.jpg",
  "verified": true,
  "trustScore": 82.5,
  "accountType": "INDIVIDUAL",
  "businessName": null,
  "memberSince": "2026-01-04T09:39:00Z"
}
```

### GET /api/v1/users/me/kyc

Returns the authenticated user's KYC record, or `null` if never submitted. Auth: authenticated.

**Response (data)** — `KycResponse` (or `null`)

| Field | Type | Description |
| --- | --- | --- |
| status | string | `SUBMITTED` \| `APPROVED` \| `REJECTED` |
| realName | string | Legal name |
| fatherName | string | Father's name |
| grandfatherName | string | Grandfather's name |
| dateOfBirth | string (date) | Date of birth |
| gender | string \| null | Gender |
| citizenshipNumber | string | Citizenship number |
| citizenshipIssueDistrict | string | Issuing district |
| occupation | string | Occupation |
| permanentAddress | object \| null | Permanent address (see AddressDto) |
| temporaryAddress | object \| null | Temporary address (see AddressDto) |
| rejectionReason | string \| null | Reason if rejected |
| reviewedAt | string (ISO-8601) \| null | Review time |
| submittedAt | string (ISO-8601) | Submission time |

**AddressDto**

| Field | Type | Description |
| --- | --- | --- |
| district | string | District |
| municipality | string | Municipality |
| ward | integer | Ward number |
| tole | string \| null | Tole/street |

```json
{
  "status": "APPROVED",
  "realName": "Aarav Sharma",
  "fatherName": "Bikash Sharma",
  "grandfatherName": "Hari Sharma",
  "dateOfBirth": "1998-05-12",
  "gender": "MALE",
  "citizenshipNumber": "12-01-70-01234",
  "citizenshipIssueDistrict": "Kathmandu",
  "occupation": "Engineer",
  "permanentAddress": { "district": "Kathmandu", "municipality": "Kathmandu Metropolitan", "ward": 10, "tole": "Baneshwor" },
  "temporaryAddress": { "district": "Lalitpur", "municipality": "Lalitpur Metropolitan", "ward": 5, "tole": "Kupondole" },
  "rejectionReason": null,
  "reviewedAt": "2026-01-06T11:20:00Z",
  "submittedAt": "2026-01-05T14:00:00Z"
}
```

### POST /api/v1/users/me/kyc

Submits (or resubmits after rejection) identity details with front/back citizenship images. Returns `201 Created`. Auth: authenticated.

**Request body** — `multipart/form-data` (`KycSubmitRequest` fields + image parts)

| Field | Type | Required | Constraints | Description |
| --- | --- | --- | --- | --- |
| realName | string | Yes | not blank, max 120 | Legal name |
| fatherName | string | Yes | not blank, max 120 | Father's name |
| grandfatherName | string | Yes | not blank, max 120 | Grandfather's name |
| dateOfBirth | string (date) | Yes | past date, ISO `yyyy-MM-dd` | Date of birth |
| gender | string | No | max 10 | Gender |
| citizenshipNumber | string | Yes | not blank, max 40 | Citizenship number |
| citizenshipIssueDistrict | string | Yes | not blank, max 60 | Issuing district |
| occupation | string | Yes | not blank, max 80 | Occupation |
| permDistrict | string | Yes | not blank, max 60 | Permanent address district |
| permMunicipality | string | Yes | not blank, max 80 | Permanent address municipality |
| permWard | integer | Yes | 1–35 | Permanent address ward |
| permTole | string | No | max 120 | Permanent address tole |
| tempDistrict | string | Yes | not blank, max 60 | Temporary address district |
| tempMunicipality | string | Yes | not blank, max 80 | Temporary address municipality |
| tempWard | integer | Yes | 1–35 | Temporary address ward |
| tempTole | string | No | max 120 | Temporary address tole |
| front | file | Yes | — | Citizenship front image part |
| back | file | Yes | — | Citizenship back image part |

Example (form fields):

```
realName=Aarav Sharma
fatherName=Bikash Sharma
grandfatherName=Hari Sharma
dateOfBirth=1998-05-12
gender=MALE
citizenshipNumber=12-01-70-01234
citizenshipIssueDistrict=Kathmandu
occupation=Engineer
permDistrict=Kathmandu
permMunicipality=Kathmandu Metropolitan
permWard=10
permTole=Baneshwor
tempDistrict=Lalitpur
tempMunicipality=Lalitpur Metropolitan
tempWard=5
tempTole=Kupondole
front=@citizenship-front.jpg
back=@citizenship-back.jpg
```

**Response (data)** — `KycResponse` (see GET /users/me/kyc), with `status` typically `SUBMITTED`.

---

I have everything. Producing the markdown.

## Listings, Categories & Templates

Base URL: `/api/v1`. Auth is a Bearer JWT (the BFF proxy attaches it as an httpOnly cookie). All responses use the envelope `{ "data": <payload|null>, "error": <string|null>, "timestamp": <ISO-8601> }`; the tables below describe the `data` contents. Paginated endpoints return a `PageResponse` object as `data` with fields `content[]`, `page`, `size`, `totalElements`, `totalPages`, `last`.

Shared enums used across these endpoints:
- `ListingType`: `PRODUCT`, `SERVICE`
- `PriceUnit`: `PER_DAY`, `PER_HOUR`, `FLAT`
- `ListingStatus`: `DRAFT`, `ACTIVE`, `INACTIVE`, `REMOVED`
- `ItemCondition`: `NEW`, `GOOD`, `FAIR`
- `ServiceDuration`: `HOURLY`, `HALF_DAY`, `FULL_DAY`, `CUSTOM`
- `TemplateScope`: `VERIFICATION`, `LISTING`, `BOOKING`
- `FieldType`: `TEXT`, `NUMBER`, `DATE`, `SELECT`, `MULTISELECT`, `BOOLEAN`, `DOCUMENT`, `DOCUMENT_LIST`

---

### POST /api/v1/listings

Create a new listing owned by the current user (returns `201 Created`). Auth: authenticated.

**Request body** — `CreateListingRequest`

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| title | string | Yes | `@NotBlank`, length 5–120 | Listing title |
| description | string | Yes | `@NotBlank`, length 20–2000 | Full description |
| categoryId | UUID | Yes | `@NotNull` | Target category |
| type | ListingType | Yes | `@NotNull` | `PRODUCT` or `SERVICE` |
| pricePerUnit | BigDecimal | Yes | `@NotNull`, `>= 1.0` | Price per unit |
| priceUnit | PriceUnit | Yes | `@NotNull` | Pricing unit |
| district | string | Yes | `@NotBlank`, max 50 | District/location |
| locationText | string | No | max 200 | Free-text location detail |
| depositAmount | BigDecimal | No | `>= 0.0` | Security deposit |
| rentalTerms | string | No | max 2000 | Terms/conditions text |
| attributes | object (map) | No | — | Category-template field values (key→value) |
| product | ProductDetailDto | No | valid when present | Product-only details (see below) |
| service | ServiceDetailDto | No | valid when present | Service-only details (see below) |

`ProductDetailDto`

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| condition | ItemCondition | Yes | `@NotNull` | Item condition |
| brand | string | No | max 100 | Brand |
| model | string | No | max 100 | Model |
| minRentalDays | integer | No | `>= 1` | Minimum rental length |
| maxRentalDays | integer | No | `>= 1` | Maximum rental length |

`ServiceDetailDto`

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| serviceAreaKm | integer | No | `>= 1` | Coverage radius in km |
| typicalDuration | ServiceDuration | No | — | Typical service duration |
| minNoticeHours | integer | No | `>= 0` | Minimum advance notice |
| portfolioUrl | string | No | max 500 | Link to portfolio |

**Response (data)** — `ListingResponse` (see [GET /api/v1/listings/{id}](#get-apiv1listingsid)).

Request example:

```json
{
  "title": "Canon EOS R6 Mirrorless Camera",
  "description": "Full-frame mirrorless body with 24-105mm kit lens, ideal for events.",
  "categoryId": "8f3b2c10-1a2b-4c3d-9e5f-a1b2c3d4e5f6",
  "type": "PRODUCT",
  "pricePerUnit": 2500.00,
  "priceUnit": "PER_DAY",
  "district": "Kathmandu",
  "locationText": "Near Baneshwor Chowk",
  "depositAmount": 20000.00,
  "rentalTerms": "Return with full battery. Renter covers damage.",
  "attributes": { "sensor": "Full-frame", "megapixels": 20 },
  "product": { "condition": "GOOD", "brand": "Canon", "model": "EOS R6", "minRentalDays": 1, "maxRentalDays": 14 }
}
```

Response example:

```json
{
  "id": "d1e2f3a4-5b6c-7d8e-9f0a-1b2c3d4e5f60",
  "owner": { "id": "aa11bb22-...", "fullName": "Sita Rai", "verified": true, "trustScore": 4.8, "accountType": "INDIVIDUAL", "businessName": null, "memberSince": "2025-03-01T09:00:00Z" },
  "categoryId": "8f3b2c10-1a2b-4c3d-9e5f-a1b2c3d4e5f6",
  "categoryName": "Cameras",
  "type": "PRODUCT",
  "status": "DRAFT",
  "title": "Canon EOS R6 Mirrorless Camera",
  "description": "Full-frame mirrorless body...",
  "pricePerUnit": 2500.00,
  "priceUnit": "PER_DAY",
  "depositAmount": 20000.00,
  "district": "Kathmandu",
  "locationText": "Near Baneshwor Chowk",
  "rentalTerms": "Return with full battery...",
  "attributes": { "sensor": "Full-frame", "megapixels": 20 },
  "averageRating": null,
  "reviewCount": 0,
  "totalBookings": 0,
  "images": [],
  "product": { "condition": "GOOD", "brand": "Canon", "model": "EOS R6", "minRentalDays": 1, "maxRentalDays": 14 },
  "service": null,
  "createdAt": "2026-07-17T10:15:00Z"
}
```

---

### GET /api/v1/listings

Search/browse active listings with filters, sorting and pagination. Auth: public.

**Query params**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| q | query | string | No | Full-text search term |
| type | query | ListingType | No | Filter by `PRODUCT`/`SERVICE` |
| categoryId | query | UUID | No | Filter by category |
| district | query | string | No | Filter by district |
| minPrice | query | BigDecimal | No | Minimum price |
| maxPrice | query | BigDecimal | No | Maximum price |
| sort | query | string | No | Sort key (default `newest`) |
| page | query | int | No | Page index (default `0`) |
| size | query | int | No | Page size (default `20`, capped at `50`) |

**Response (data)** — `PageResponse<ListingSummaryResponse>`

| Field | Type | Description |
|---|---|---|
| content | ListingSummaryResponse[] | Page of listing cards (see below) |
| page | int | Zero-based page index |
| size | int | Page size |
| totalElements | long | Total matching listings |
| totalPages | int | Total pages |
| last | boolean | True on the final page |

`ListingSummaryResponse`

| Field | Type | Description |
|---|---|---|
| id | UUID | Listing id |
| type | string (ListingType) | Listing type |
| status | string (ListingStatus) | Listing status |
| title | string | Title |
| pricePerUnit | BigDecimal | Price per unit |
| priceUnit | string (PriceUnit) | Pricing unit |
| depositAmount | BigDecimal | Security deposit |
| district | string | District |
| averageRating | BigDecimal | Average rating (null if unrated) |
| reviewCount | int | Number of reviews |
| coverImage | string | Cover image URL (null if none) |
| createdAt | Instant | Creation timestamp |

Response example:

```json
{
  "content": [
    {
      "id": "d1e2f3a4-5b6c-7d8e-9f0a-1b2c3d4e5f60",
      "type": "PRODUCT",
      "status": "ACTIVE",
      "title": "Canon EOS R6 Mirrorless Camera",
      "pricePerUnit": 2500.00,
      "priceUnit": "PER_DAY",
      "depositAmount": 20000.00,
      "district": "Kathmandu",
      "averageRating": 4.7,
      "reviewCount": 12,
      "coverImage": "https://cdn.rentle.app/listings/d1e2.../cover.jpg",
      "createdAt": "2026-07-17T10:15:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "last": true
}
```

---

### GET /api/v1/listings/me

List the current user's own listings, newest first, paginated. Auth: authenticated.

**Query params**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| page | query | int | No | Page index (default `0`) |
| size | query | int | No | Page size (default `20`, capped at `50`) |

**Response (data)** — `PageResponse<ListingSummaryResponse>` (fields identical to [GET /api/v1/listings](#get-apiv1listings)).

---

### GET /api/v1/listings/{id}

Get full listing detail. Auth: public (owner-only fields resolved when authenticated).

**Path params**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| id | path | UUID | Yes | Listing id |

**Response (data)** — `ListingResponse`

| Field | Type | Description |
|---|---|---|
| id | UUID | Listing id |
| owner | PublicProfileResponse | Owner public profile |
| categoryId | UUID | Category id |
| categoryName | string | Category name |
| type | string (ListingType) | Listing type |
| status | string (ListingStatus) | Listing status |
| title | string | Title |
| description | string | Full description |
| pricePerUnit | BigDecimal | Price per unit |
| priceUnit | string (PriceUnit) | Pricing unit |
| depositAmount | BigDecimal | Security deposit |
| district | string | District |
| locationText | string | Free-text location |
| rentalTerms | string | Terms text |
| attributes | object (map) | Category-template field values |
| averageRating | BigDecimal | Average rating (null if unrated) |
| reviewCount | int | Number of reviews |
| totalBookings | int | Completed bookings count |
| images | string[] | Image URLs |
| product | ProductDetailDto | Product details (null for services) |
| service | ServiceDetailDto | Service details (null for products) |
| createdAt | Instant | Creation timestamp |

`PublicProfileResponse` (owner)

| Field | Type | Description |
|---|---|---|
| id | UUID | User id |
| fullName | string | Display name |
| profilePhotoUrl | string | Avatar URL |
| verified | boolean | Citizenship-verified flag |
| trustScore | BigDecimal | Trust score |
| accountType | string | Account type |
| businessName | string | Business name (null for individuals) |
| memberSince | Instant | Registration date |

---

### PUT /api/v1/listings/{id}

Update a listing owned by the current user; all body fields optional, only non-null values are applied. Auth: authenticated (owner).

**Path params**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| id | path | UUID | Yes | Listing id |

**Request body** — `UpdateListingRequest`

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| title | string | No | length 5–120 | New title |
| description | string | No | length 20–2000 | New description |
| pricePerUnit | BigDecimal | No | `>= 1.0` | New price |
| priceUnit | PriceUnit | No | — | New pricing unit |
| district | string | No | max 50 | New district |
| locationText | string | No | max 200 | New location text |
| depositAmount | BigDecimal | No | `>= 0.0` | New deposit |
| rentalTerms | string | No | max 2000 | New terms |
| status | ListingStatus | No | — | New status |
| product | ProductDetailDto | No | valid when present | Product details |
| service | ServiceDetailDto | No | valid when present | Service details |

**Response (data)** — `ListingResponse` (see [GET /api/v1/listings/{id}](#get-apiv1listingsid)).

Request example:

```json
{ "pricePerUnit": 2200.00, "status": "ACTIVE" }
```

---

### DELETE /api/v1/listings/{id}

Soft-delete (mark `REMOVED`) a listing owned by the current user. Auth: authenticated (owner).

**Path params**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| id | path | UUID | Yes | Listing id |

**Response (data)** — string.

```json
"Listing removed"
```

---

### POST /api/v1/listings/{id}/images

Upload one or more images to a listing (multipart, returns `201 Created`). Auth: authenticated (owner).

**Path params**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| id | path | UUID | Yes | Listing id |

**Request body** — `multipart/form-data`

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| files | file[] | Yes | — | Image files (form part `files`) |

**Response (data)** — string[] (URLs of the newly stored images).

```json
["https://cdn.rentle.app/listings/d1e2.../1.jpg", "https://cdn.rentle.app/listings/d1e2.../2.jpg"]
```

---

### DELETE /api/v1/listings/{id}/images/{imageId}

Delete a single image from a listing. Auth: authenticated (owner).

**Path params**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| id | path | UUID | Yes | Listing id |
| imageId | path | UUID | Yes | Image id |

**Response (data)** — string.

```json
"Image deleted"
```

---

### GET /api/v1/listings/{id}/availability

Get a listing's blocked/booked date ranges. Auth: public.

**Path params**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| id | path | UUID | Yes | Listing id |

**Response (data)** — `AvailabilityResponse`

| Field | Type | Description |
|---|---|---|
| listingId | UUID | Listing id |
| blocked | BlockedRange[] | Unavailable ranges |

`BlockedRange`

| Field | Type | Description |
|---|---|---|
| rangeId | UUID | Range id; null for booking-derived blocks (only owner blocks are deletable) |
| startDate | LocalDate | Range start (inclusive) |
| endDate | LocalDate | Range end (inclusive) |
| source | string | `OWNER_BLOCKED` or `BOOKED` |

Response example:

```json
{
  "listingId": "d1e2f3a4-5b6c-7d8e-9f0a-1b2c3d4e5f60",
  "blocked": [
    { "rangeId": "c0ffee00-...", "startDate": "2026-08-01", "endDate": "2026-08-05", "source": "OWNER_BLOCKED" },
    { "rangeId": null, "startDate": "2026-08-10", "endDate": "2026-08-12", "source": "BOOKED" }
  ]
}
```

---

### POST /api/v1/listings/{id}/availability

Block a date range on a listing owned by the current user (returns `201 Created`). Auth: authenticated (owner).

**Path params**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| id | path | UUID | Yes | Listing id |

**Request body** — `BlockDatesRequest`

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| startDate | LocalDate | Yes | `@NotNull` | Range start (inclusive) |
| endDate | LocalDate | Yes | `@NotNull` | Range end (inclusive) |
| reason | string | No | max 200 | Optional note |

**Response (data)** — `AvailabilityResponse` (see [GET /api/v1/listings/{id}/availability](#get-apiv1listingsidavailability)).

Request example:

```json
{ "startDate": "2026-08-01", "endDate": "2026-08-05", "reason": "Personal use" }
```

---

### DELETE /api/v1/listings/{id}/availability/{rangeId}

Remove an owner-blocked date range. Auth: authenticated (owner).

**Path params**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| id | path | UUID | Yes | Listing id |
| rangeId | path | UUID | Yes | Blocked range id (owner blocks only) |

**Response (data)** — string.

```json
"Blocked range removed"
```

---

### GET /api/v1/users/{id}/listings

List a given user's public listings, newest first, paginated. Auth: public.

**Path/query params**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| id | path | UUID | Yes | Owner user id |
| page | query | int | No | Page index (default `0`) |
| size | query | int | No | Page size (default `20`, capped at `50`) |

**Response (data)** — `PageResponse<ListingSummaryResponse>` (fields identical to [GET /api/v1/listings](#get-apiv1listings)).

---

### GET /api/v1/categories

List all categories (flat). Auth: public.

**Response (data)** — `CategoryResponse[]`

| Field | Type | Description |
|---|---|---|
| id | UUID | Category id |
| parentId | UUID | Parent category id (null for roots) |
| name | string | Display name |
| slug | string | URL slug |
| listingType | string (ListingType) | `PRODUCT` or `SERVICE` |
| iconName | string | Icon identifier |
| sortOrder | int | Display order |
| children | CategoryResponse[] | Nested children (empty in flat listing) |

Response example:

```json
[
  { "id": "8f3b2c10-...", "parentId": null, "name": "Cameras", "slug": "cameras", "listingType": "PRODUCT", "iconName": "camera", "sortOrder": 1, "children": [] }
]
```

---

### GET /api/v1/categories/tree

List categories as a nested tree (roots with populated `children`). Auth: public.

**Response (data)** — `CategoryResponse[]` (same fields as above; `children` populated recursively).

Response example:

```json
[
  {
    "id": "11111111-...", "parentId": null, "name": "Electronics", "slug": "electronics",
    "listingType": "PRODUCT", "iconName": "chip", "sortOrder": 1,
    "children": [
      { "id": "8f3b2c10-...", "parentId": "11111111-...", "name": "Cameras", "slug": "cameras", "listingType": "PRODUCT", "iconName": "camera", "sortOrder": 1, "children": [] }
    ]
  }
]
```

---

### GET /api/v1/categories/{id}/templates/{scope}

Get the current field template for a category and scope, to render its dynamic form. Auth: public.

**Path params**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| id | path | UUID | Yes | Category id |
| scope | path | TemplateScope | Yes | `VERIFICATION`, `LISTING`, or `BOOKING` |

**Response (data)** — `TemplateResponse` (null if no template configured)

| Field | Type | Description |
|---|---|---|
| id | UUID | Template id |
| categoryId | UUID | Category id |
| scope | string (TemplateScope) | Template scope |
| version | int | Current version number |
| fields | FieldDefinition[] | Ordered field definitions |

`FieldDefinition`

| Field | Type | Description |
|---|---|---|
| key | string | Field key (used in listing `attributes`) |
| label | string | Display label |
| type | string (FieldType) | Field type |
| required | boolean | Whether the field is mandatory |
| options | string[] | Choices for `SELECT`/`MULTISELECT` (null otherwise) |
| help | string | Help/hint text |

Response example:

```json
{
  "id": "77777777-...",
  "categoryId": "8f3b2c10-...",
  "scope": "LISTING",
  "version": 3,
  "fields": [
    { "key": "sensor", "label": "Sensor Type", "type": "SELECT", "required": true, "options": ["Full-frame", "APS-C"], "help": "Pick the camera sensor size" },
    { "key": "megapixels", "label": "Megapixels", "type": "NUMBER", "required": false, "options": null, "help": null }
  ]
}
```

---

### GET /api/v1/admin/categories/{id}/templates

List all templates configured on a category across every scope (current versions and history). Auth: permission `listing.category.manage`.

**Path params**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| id | path | UUID | Yes | Category id |

**Response (data)** — `TemplateResponse[]` (fields as in [GET /api/v1/categories/{id}/templates/{scope}](#get-apiv1categoriesidtemplatesscope)).

---

### PUT /api/v1/admin/categories/{id}/templates/{scope}

Save a new version of a category+scope template. Auth: permission `listing.category.manage`.

**Path params**

| Name | In | Type | Required | Description |
|---|---|---|---|---|
| id | path | UUID | Yes | Category id |
| scope | path | TemplateScope | Yes | `VERIFICATION`, `LISTING`, or `BOOKING` |

**Request body** — `SaveTemplateRequest`

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| fields | FieldDefinition[] | Yes | `@NotNull` | Full field set for the new version |

`FieldDefinition` object fields: `key` (string), `label` (string), `type` (FieldType), `required` (boolean), `options` (string[], for `SELECT`/`MULTISELECT`), `help` (string).

**Response (data)** — `TemplateResponse` (the newly created version).

Request example:

```json
{
  "fields": [
    { "key": "sensor", "label": "Sensor Type", "type": "SELECT", "required": true, "options": ["Full-frame", "APS-C"], "help": "Pick the camera sensor size" },
    { "key": "megapixels", "label": "Megapixels", "type": "NUMBER", "required": false, "options": null, "help": null }
  ]
}
```

Response example:

```json
{
  "id": "88888888-...",
  "categoryId": "8f3b2c10-...",
  "scope": "LISTING",
  "version": 4,
  "fields": [
    { "key": "sensor", "label": "Sensor Type", "type": "SELECT", "required": true, "options": ["Full-frame", "APS-C"], "help": "Pick the camera sensor size" },
    { "key": "megapixels", "label": "Megapixels", "type": "NUMBER", "required": false, "options": null, "help": null }
  ]
}
```

---

## Bookings

Manage the booking lifecycle: create requests, list bookings as renter/owner, approve/reject/cancel, adjust price, upload deposit and condition proofs, and complete. All endpoints require a valid Bearer JWT.

The `BookingResponse` payload is shared by every endpoint that returns a single booking:

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Booking identifier |
| listingId | UUID | Booked listing |
| listingTitle | string | Listing title |
| listingType | string | Listing type enum name |
| ownerId | UUID | Listing owner user id |
| ownerName | string | Owner full name |
| renterId | UUID | Renter user id |
| renterName | string | Renter full name |
| startDate | date | Rental start date (ISO-8601) |
| endDate | date | Rental end date (ISO-8601) |
| startTime | time | Start time of day, nullable |
| endTime | time | End time of day, nullable |
| status | string | Booking status enum name |
| totalPrice | decimal | Agreed total price |
| depositAmount | decimal | Required deposit amount |
| depositPaid | boolean | Whether deposit is paid |
| depositProofUrl | string | URL/ref of deposit proof, nullable |
| ownerPaymentWallet | string | Owner payout wallet, nullable |
| renterNote | string | Note supplied by renter, nullable |
| agreedTerms | string | Snapshot of agreed terms, nullable |
| attributes | object | Free-form key/value attributes |
| hasCheckoutCondition | boolean | Checkout condition photo recorded |
| checkoutNote | string | Checkout condition note, nullable |
| hasReturnCondition | boolean | Return condition photo recorded |
| returnNote | string | Return condition note, nullable |
| platformFeeAmount | decimal | Platform fee, nullable |
| feeInvoiced | boolean | Whether platform fee was invoiced |
| cancellationSchedule | CancellationTier[] | Cancellation forfeit tiers (`hoursBefore`: int, `withholdPct`: decimal) |
| assignedWorkerId | UUID | Assigned worker user id, nullable |
| assignedWorkerName | string | Assigned worker name, nullable |
| cancellationReason | string | Reason if cancelled/rejected, nullable |
| createdAt | datetime | Creation timestamp (ISO-8601) |

List endpoints return a `PageResponse` envelope: `content[]` (array of `BookingResponse`), `page` (int), `size` (int), `totalElements` (long), `last` (boolean).

---

### POST /api/v1/bookings

Create a booking request for a listing. Auth: authenticated. Returns HTTP 201.

**Request body**

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| listingId | UUID | Yes | @NotNull | Listing to book |
| startDate | date | Yes | @NotNull | Rental start date |
| endDate | date | Yes | @NotNull | Rental end date |
| startTime | time | No | — | Optional start time of day |
| endTime | time | No | — | Optional end time of day |
| note | string | No | max 500 chars | Note to owner |
| attributes | object | No | — | Free-form key/value attributes |

**Response (data)** — `BookingResponse` (see shared table above).

Request example:

```json
{
  "listingId": "8f3a1c2e-4b5d-6e7f-8a9b-0c1d2e3f4a5b",
  "startDate": "2026-08-01",
  "endDate": "2026-08-05",
  "startTime": "10:00:00",
  "note": "Will pick up in the morning.",
  "attributes": { "deliveryRequested": true }
}
```

Response data example:

```json
{
  "id": "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
  "listingId": "8f3a1c2e-4b5d-6e7f-8a9b-0c1d2e3f4a5b",
  "listingTitle": "DSLR Camera Kit",
  "listingType": "GEAR",
  "ownerId": "aa11bb22-cc33-dd44-ee55-ff6677889900",
  "ownerName": "Priya Sharma",
  "renterId": "bb22cc33-dd44-ee55-ff66-778899001122",
  "renterName": "Ravi Thapa",
  "startDate": "2026-08-01",
  "endDate": "2026-08-05",
  "startTime": "10:00:00",
  "endTime": null,
  "status": "PENDING",
  "totalPrice": 4000.00,
  "depositAmount": 2000.00,
  "depositPaid": false,
  "depositProofUrl": null,
  "ownerPaymentWallet": null,
  "renterNote": "Will pick up in the morning.",
  "agreedTerms": null,
  "attributes": { "deliveryRequested": true },
  "hasCheckoutCondition": false,
  "checkoutNote": null,
  "hasReturnCondition": false,
  "returnNote": null,
  "platformFeeAmount": null,
  "feeInvoiced": false,
  "cancellationSchedule": [
    { "hoursBefore": 48, "withholdPct": 0.0 },
    { "hoursBefore": 24, "withholdPct": 50.0 }
  ],
  "assignedWorkerId": null,
  "assignedWorkerName": null,
  "cancellationReason": null,
  "createdAt": "2026-07-18T09:30:00Z"
}
```

---

### GET /api/v1/bookings/me/as-renter

List the current user's bookings where they are the renter. Auth: authenticated.

**Query params**

| Name | In | Type | Required | Description |
|------|----|------|----------|-------------|
| page | query | int | No | Zero-based page index (default 0) |
| size | query | int | No | Page size (default 20, capped at 50) |

**Response (data)** — `PageResponse<BookingResponse>`: `content[]` of `BookingResponse`, plus `page`, `size`, `totalElements`, `last`.

Response data example:

```json
{
  "content": [ { "id": "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d", "status": "PENDING" } ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "last": true
}
```

---

### GET /api/v1/bookings/me/as-owner

List the current user's bookings where they own the listing. Auth: authenticated.

**Query params**

| Name | In | Type | Required | Description |
|------|----|------|----------|-------------|
| page | query | int | No | Zero-based page index (default 0) |
| size | query | int | No | Page size (default 20, capped at 50) |

**Response (data)** — `PageResponse<BookingResponse>` (same shape as `/me/as-renter`).

---

### GET /api/v1/bookings/{id}

Get a single booking's detail. Auth: authenticated (must be a party to the booking).

**Path params**

| Name | In | Type | Required | Description |
|------|----|------|----------|-------------|
| id | path | UUID | Yes | Booking id |

**Response (data)** — `BookingResponse`.

---

### POST /api/v1/bookings/{id}/approve

Owner approves a pending booking request. Auth: authenticated (owner).

**Path params**

| Name | In | Type | Required | Description |
|------|----|------|----------|-------------|
| id | path | UUID | Yes | Booking id |

**Response (data)** — `BookingResponse` (status transitions to approved).

---

### POST /api/v1/bookings/{id}/assign-worker

Assign (or clear) a worker to fulfill the booking. Auth: authenticated (owner). Omit `workerId` to unassign.

**Path/query params**

| Name | In | Type | Required | Description |
|------|----|------|----------|-------------|
| id | path | UUID | Yes | Booking id |
| workerId | query | UUID | No | Worker to assign; omit to clear assignment |

**Response (data)** — `BookingResponse` (with `assignedWorkerId`/`assignedWorkerName` set or cleared).

---

### POST /api/v1/bookings/{id}/price

Owner adjusts the booking's total price. Auth: authenticated (owner).

**Path params**

| Name | In | Type | Required | Description |
|------|----|------|----------|-------------|
| id | path | UUID | Yes | Booking id |

**Request body**

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| totalPrice | decimal | Yes | @NotNull, @DecimalMin("0.0") | New agreed total price |

**Response (data)** — `BookingResponse` (with updated `totalPrice`).

Request example:

```json
{ "totalPrice": 3500.00 }
```

---

### POST /api/v1/bookings/{id}/reject

Owner rejects a pending booking. Auth: authenticated (owner). Body optional.

**Path params**

| Name | In | Type | Required | Description |
|------|----|------|----------|-------------|
| id | path | UUID | Yes | Booking id |

**Request body** (optional)

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| reason | string | No | max 500 chars | Rejection reason |

**Response (data)** — `BookingResponse` (status rejected, `cancellationReason` set if provided).

Request example:

```json
{ "reason": "Item unavailable for those dates." }
```

---

### POST /api/v1/bookings/{id}/deposit

Renter uploads a deposit payment proof (multipart). Auth: authenticated (renter).

**Path params**

| Name | In | Type | Required | Description |
|------|----|------|----------|-------------|
| id | path | UUID | Yes | Booking id |

**Request body** — `multipart/form-data`

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| file | file | Yes | — | Deposit proof image/document |

**Response (data)** — `BookingResponse` (with `depositProofUrl` set).

---

### POST /api/v1/bookings/{id}/confirm-deposit

Owner confirms the uploaded deposit. Auth: authenticated (owner).

**Path params**

| Name | In | Type | Required | Description |
|------|----|------|----------|-------------|
| id | path | UUID | Yes | Booking id |

**Response (data)** — `BookingResponse` (with `depositPaid` true).

---

### GET /api/v1/bookings/{id}/deposit-proof

Download the raw deposit proof file. Auth: authenticated (party to the booking).

**Path params**

| Name | In | Type | Required | Description |
|------|----|------|----------|-------------|
| id | path | UUID | Yes | Booking id |

**Response** — Binary file stream. Not the standard envelope: returns the raw resource with its stored `Content-Type` (e.g. `image/jpeg`).

---

### POST /api/v1/bookings/{id}/condition

Record a condition photo for a phase (e.g. checkout or return), multipart. Auth: authenticated (party to the booking).

**Path/query params**

| Name | In | Type | Required | Description |
|------|----|------|----------|-------------|
| id | path | UUID | Yes | Booking id |
| phase | query | string | Yes | Condition phase (e.g. `checkout`, `return`) |
| note | query | string | No | Optional condition note |

**Request body** — `multipart/form-data`

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| file | file | Yes | — | Condition photo |

**Response (data)** — `BookingResponse` (with `hasCheckoutCondition`/`hasReturnCondition` and note updated for the phase).

---

### GET /api/v1/bookings/{id}/condition/{phase}

Download the raw condition photo for a phase. Auth: authenticated (party to the booking).

**Path params**

| Name | In | Type | Required | Description |
|------|----|------|----------|-------------|
| id | path | UUID | Yes | Booking id |
| phase | path | string | Yes | Condition phase |

**Response** — Binary image stream with its stored `Content-Type`. Not the standard envelope.

---

### POST /api/v1/bookings/{id}/complete

Mark the booking complete after return. Auth: authenticated (owner).

**Path params**

| Name | In | Type | Required | Description |
|------|----|------|----------|-------------|
| id | path | UUID | Yes | Booking id |

**Response (data)** — `BookingResponse` (status completed).

---

### POST /api/v1/bookings/{id}/cancel

Cancel a booking. Auth: authenticated (party to the booking). Body optional. A withheld amount may apply per `cancellationSchedule`.

**Path params**

| Name | In | Type | Required | Description |
|------|----|------|----------|-------------|
| id | path | UUID | Yes | Booking id |

**Request body** (optional)

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| reason | string | No | max 500 chars | Cancellation reason |

**Response (data)** — `BookingResponse` (status cancelled, `cancellationReason` set if provided).

Request example:

```json
{ "reason": "Plans changed." }
```

---

## Messaging, Reviews, Favorites & Notifications

All endpoints in this section require an authenticated user; the acting user is resolved from the Bearer JWT. Responses are wrapped in the standard envelope `{ "data": …, "error": null, "timestamp": … }`; the tables below describe the `data` contents. Paginated endpoints return a `PageResponse` object: `content[]`, `page`, `size`, `totalElements`, `last`.

### GET /api/v1/bookings/{bookingId}/messages

List messages in a booking thread (paginated). Auth: authenticated.

**Path/query params**

| Name | In | Type | Required | Description |
| --- | --- | --- | --- | --- |
| bookingId | path | UUID | Yes | Booking whose thread is fetched |
| page | query | int | No | Page index, default `0` |
| size | query | int | No | Page size, default `50`, capped at `100` |

**Response (data)** — `PageResponse<MessageResponse>`; each `content[]` item:

| Field | Type | Description |
| --- | --- | --- |
| id | UUID | Message id |
| bookingId | UUID | Owning booking |
| senderId | UUID | Sender user id |
| senderName | string | Sender full name |
| content | string | Message body |
| isRead | boolean | Whether the message has been read |
| readAt | ISO-8601 \| null | When it was read |
| createdAt | ISO-8601 | Sent timestamp |

```json
{
  "content": [
    {
      "id": "8f3c1e2a-4b5d-6e7f-8a9b-0c1d2e3f4a5b",
      "bookingId": "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
      "senderId": "aa11bb22-cc33-dd44-ee55-ff6677889900",
      "senderName": "Anisha Rai",
      "content": "Is the drill still available for the weekend?",
      "isRead": true,
      "readAt": "2026-07-17T09:15:00Z",
      "createdAt": "2026-07-17T09:10:00Z"
    }
  ],
  "page": 0,
  "size": 50,
  "totalElements": 1,
  "last": true
}
```

### POST /api/v1/bookings/{bookingId}/messages

Send a message in a booking thread. Returns `201 Created`. Auth: authenticated.

**Path/query params**

| Name | In | Type | Required | Description |
| --- | --- | --- | --- | --- |
| bookingId | path | UUID | Yes | Booking to post into |

**Request body** — `SendMessageRequest`

| Field | Type | Required | Constraints | Description |
| --- | --- | --- | --- | --- |
| content | string | Yes | `@NotBlank`, `@Size(max=2000)` | Message body |

**Response (data)** — `MessageResponse` (see fields above).

```json
{ "content": "Yes, it's free from Saturday morning." }
```

```json
{
  "id": "9a8b7c6d-5e4f-3a2b-1c0d-9e8f7a6b5c4d",
  "bookingId": "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
  "senderId": "bb22cc33-dd44-ee55-ff66-778899001122",
  "senderName": "Bikash Thapa",
  "content": "Yes, it's free from Saturday morning.",
  "isRead": false,
  "readAt": null,
  "createdAt": "2026-07-17T09:20:00Z"
}
```

### PUT /api/v1/bookings/{bookingId}/messages/read

Mark all messages in a thread as read for the current user. Auth: authenticated.

**Path/query params**

| Name | In | Type | Required | Description |
| --- | --- | --- | --- | --- |
| bookingId | path | UUID | Yes | Booking thread to mark read |

**Response (data)**

| Field | Type | Description |
| --- | --- | --- |
| (data) | int | Number of messages marked read |

```json
3
```

### GET /api/v1/messages/unread-count

Total unread messages across all threads for the current user (nav badge). Auth: authenticated.

**Response (data)**

| Field | Type | Description |
| --- | --- | --- |
| count | long | Total unread message count |

```json
{ "count": 5 }
```

### GET /api/v1/messages/threads

Per-thread inbox summaries (last activity + unread count) for the current user. Auth: authenticated.

**Response (data)** — array of `ThreadSummary`

| Field | Type | Description |
| --- | --- | --- |
| bookingId | UUID | Booking the thread belongs to |
| lastMessageAt | ISO-8601 | Timestamp of latest activity |
| unreadCount | long | Unread messages for the viewer |

```json
[
  {
    "bookingId": "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
    "lastMessageAt": "2026-07-17T09:20:00Z",
    "unreadCount": 2
  }
]
```

### POST /api/v1/reviews

Create a review for a completed booking. Returns `201 Created`. Auth: authenticated.

**Request body** — `CreateReviewRequest`

| Field | Type | Required | Constraints | Description |
| --- | --- | --- | --- | --- |
| bookingId | UUID | Yes | `@NotNull` | Booking being reviewed |
| rating | integer | Yes | `@NotNull`, `@Min(1)`, `@Max(5)` | Star rating, 1–5 |
| comment | string | No | `@Size(max=500)` | Optional written review |

**Response (data)** — `ReviewResponse`

| Field | Type | Description |
| --- | --- | --- |
| id | UUID | Review id |
| bookingId | UUID | Reviewed booking |
| authorId | UUID | Reviewer user id |
| authorName | string | Reviewer full name |
| subjectId | UUID | User being reviewed |
| listingId | UUID | Listing the booking was for |
| rating | int | Star rating 1–5 |
| comment | string \| null | Written review |
| createdAt | ISO-8601 | Creation timestamp |

```json
{
  "bookingId": "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
  "rating": 5,
  "comment": "Great tool, smooth handover."
}
```

```json
{
  "id": "c1d2e3f4-a5b6-7c8d-9e0f-1a2b3c4d5e6f",
  "bookingId": "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
  "authorId": "aa11bb22-cc33-dd44-ee55-ff6677889900",
  "authorName": "Anisha Rai",
  "subjectId": "bb22cc33-dd44-ee55-ff66-778899001122",
  "listingId": "dd44ee55-ff66-7788-9900-112233445566",
  "rating": 5,
  "comment": "Great tool, smooth handover.",
  "createdAt": "2026-07-18T08:00:00Z"
}
```

### GET /api/v1/bookings/{id}/my-review-status

Whether the current user has already reviewed the given booking. Auth: authenticated.

**Path/query params**

| Name | In | Type | Required | Description |
| --- | --- | --- | --- | --- |
| id | path | UUID | Yes | Booking id |

**Response (data)**

| Field | Type | Description |
| --- | --- | --- |
| (data) | boolean | `true` if the user has already reviewed |

```json
true
```

### GET /api/v1/listings/{id}/reviews

List reviews for a listing (paginated). Auth: authenticated.

**Path/query params**

| Name | In | Type | Required | Description |
| --- | --- | --- | --- | --- |
| id | path | UUID | Yes | Listing id |
| page | query | int | No | Page index, default `0` |
| size | query | int | No | Page size, default `20`, capped at `50` |

**Response (data)** — `PageResponse<ReviewResponse>` (item fields as in POST /reviews).

```json
{
  "content": [
    {
      "id": "c1d2e3f4-a5b6-7c8d-9e0f-1a2b3c4d5e6f",
      "bookingId": "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
      "authorId": "aa11bb22-cc33-dd44-ee55-ff6677889900",
      "authorName": "Anisha Rai",
      "subjectId": "bb22cc33-dd44-ee55-ff66-778899001122",
      "listingId": "dd44ee55-ff66-7788-9900-112233445566",
      "rating": 5,
      "comment": "Great tool, smooth handover.",
      "createdAt": "2026-07-18T08:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "last": true
}
```

### GET /api/v1/users/{id}/reviews

List reviews written about a user (paginated). Auth: authenticated.

**Path/query params**

| Name | In | Type | Required | Description |
| --- | --- | --- | --- | --- |
| id | path | UUID | Yes | Subject user id |
| page | query | int | No | Page index, default `0` |
| size | query | int | No | Page size, default `20`, capped at `50` |

**Response (data)** — `PageResponse<ReviewResponse>` (item fields as in POST /reviews).

### POST /api/v1/listings/{id}/favorite

Toggle a listing in the current user's saved list. Auth: authenticated.

**Path/query params**

| Name | In | Type | Required | Description |
| --- | --- | --- | --- | --- |
| id | path | UUID | Yes | Listing to toggle |

**Response (data)**

| Field | Type | Description |
| --- | --- | --- |
| saved | boolean | `true` if now saved, `false` if removed |

```json
{ "saved": true }
```

### GET /api/v1/users/me/favorite-ids

The current user's saved listing ids (for filling card hearts). Auth: authenticated.

**Response (data)**

| Field | Type | Description |
| --- | --- | --- |
| (data) | UUID[] | Saved listing ids |

```json
["dd44ee55-ff66-7788-9900-112233445566", "ee55ff66-7788-9900-1122-334455667788"]
```

### GET /api/v1/users/me/favorites

The current user's saved listings. Auth: authenticated.

**Response (data)** — array of `ListingSummaryResponse`

| Field | Type | Description |
| --- | --- | --- |
| id | UUID | Listing id |
| type | string | Listing type enum name |
| status | string | Listing status enum name |
| title | string | Listing title |
| pricePerUnit | number | Price per unit |
| priceUnit | string | Price unit enum name |
| depositAmount | number | Required deposit |
| district | string | District |
| averageRating | number | Average rating |
| reviewCount | int | Number of reviews |
| coverImage | string \| null | Cover image URL |
| createdAt | ISO-8601 | Listing creation timestamp |

```json
[
  {
    "id": "dd44ee55-ff66-7788-9900-112233445566",
    "type": "TOOL",
    "status": "ACTIVE",
    "title": "Bosch Hammer Drill",
    "pricePerUnit": 350.00,
    "priceUnit": "DAY",
    "depositAmount": 2000.00,
    "district": "Lalitpur",
    "averageRating": 4.8,
    "reviewCount": 12,
    "coverImage": "https://cdn.rentle.app/listings/drill.jpg",
    "createdAt": "2026-06-01T10:00:00Z"
  }
]
```

### GET /api/v1/notifications

List the current user's notifications (paginated). Auth: authenticated.

**Path/query params**

| Name | In | Type | Required | Description |
| --- | --- | --- | --- | --- |
| page | query | int | No | Page index, default `0` |
| size | query | int | No | Page size, default `30`, capped at `100` |

**Response (data)** — `PageResponse<NotificationResponse>`; each `content[]` item:

| Field | Type | Description |
| --- | --- | --- |
| id | UUID | Notification id |
| type | string | Notification type code |
| message | string | Display text |
| link | string \| null | Deep link target |
| read | boolean | Read state |
| createdAt | ISO-8601 | Creation timestamp |

```json
{
  "content": [
    {
      "id": "f1e2d3c4-b5a6-9788-6f5e-4d3c2b1a0f9e",
      "type": "BOOKING_REQUEST",
      "message": "Anisha requested to book your Bosch Hammer Drill.",
      "link": "/bookings/1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
      "read": false,
      "createdAt": "2026-07-17T09:00:00Z"
    }
  ],
  "page": 0,
  "size": 30,
  "totalElements": 1,
  "last": true
}
```

### GET /api/v1/notifications/unread-count

Count of unread notifications for the current user. Auth: authenticated.

**Response (data)**

| Field | Type | Description |
| --- | --- | --- |
| (data) | long | Unread notification count |

```json
4
```

### PUT /api/v1/notifications/{id}/read

Mark a single notification as read. Auth: authenticated.

**Path/query params**

| Name | In | Type | Required | Description |
| --- | --- | --- | --- | --- |
| id | path | UUID | Yes | Notification id |

**Response (data)**

| Field | Type | Description |
| --- | --- | --- |
| (data) | string | Confirmation message (`"Marked read"`) |

```json
"Marked read"
```

### PUT /api/v1/notifications/read-all

Mark all of the current user's notifications as read. Auth: authenticated.

**Response (data)**

| Field | Type | Description |
| --- | --- | --- |
| (data) | string | Confirmation message (`"All marked read"`) |

```json
"All marked read"
```

---

## Reports, Workers & Pricing Policy

### POST /api/v1/reports

File a trust-and-safety report against a listing, user, or booking. Auth: authenticated.

**Request body** — | Field | Type | Required | Constraints | Description |
| --- | --- | --- | --- | --- |
| `targetType` | string enum | Yes | `@NotNull`; one of `LISTING`, `USER`, `BOOKING` | Kind of entity being reported |
| `targetId` | UUID | Yes | `@NotNull` | ID of the reported entity |
| `reason` | string | Yes | `@NotBlank`, max 1000 chars | Why the report is being filed |

**Response (data)** — `ReportResponse` | Field | Type | Description |
| --- | --- | --- |
| `id` | UUID | Report ID |
| `reporterId` | UUID | ID of the user who filed it |
| `reporterName` | string | Reporter's full name |
| `targetType` | string | Reported entity kind (`LISTING`/`USER`/`BOOKING`) |
| `targetId` | UUID | Reported entity ID |
| `reason` | string | Report reason |
| `status` | string | `OPEN`, `RESOLVED`, or `DISMISSED` (new reports are `OPEN`) |
| `resolutionNote` | string \| null | Admin note added on resolution |
| `handledBy` | UUID \| null | Admin who resolved it |
| `handledAt` | ISO-8601 \| null | When it was resolved |
| `createdAt` | ISO-8601 | When it was filed |

Returns `201 Created`.

Request:
```json
{
  "targetType": "LISTING",
  "targetId": "8f2b1c34-0a11-4e77-9d2e-4c3b6a1f9e01",
  "reason": "Listing photos don't match the actual item; suspected scam."
}
```

Response `data`:
```json
{
  "id": "1a9c7e20-3f4d-4b8a-b1c2-9e0f5a6d7c88",
  "reporterId": "c4d5e6f7-8a9b-40c1-92d3-e4f5a6b7c8d9",
  "reporterName": "Asha Gurung",
  "targetType": "LISTING",
  "targetId": "8f2b1c34-0a11-4e77-9d2e-4c3b6a1f9e01",
  "reason": "Listing photos don't match the actual item; suspected scam.",
  "status": "OPEN",
  "resolutionNote": null,
  "handledBy": null,
  "handledAt": null,
  "createdAt": "2026-07-18T09:14:22Z"
}
```

### GET /api/v1/admin/reports

Admin report queue, optionally filtered by status. Auth: permission `trust.report.read`.

**Query params** — | Name | In | Type | Required | Description |
| --- | --- | --- | --- | --- |
| `status` | query | string enum | No | Filter by `OPEN`, `RESOLVED`, or `DISMISSED`; omit for all |
| `page` | query | int | No | Zero-based page index (default `0`) |
| `size` | query | int | No | Page size (default `20`, capped at `100`) |

**Response (data)** — `PageResponse<ReportResponse>` | Field | Type | Description |
| --- | --- | --- |
| `content` | ReportResponse[] | Reports on this page (see fields above) |
| `page` | int | Current zero-based page index |
| `size` | int | Page size |
| `totalElements` | long | Total matching reports |
| `last` | boolean | Whether this is the final page |

Response `data`:
```json
{
  "content": [
    {
      "id": "1a9c7e20-3f4d-4b8a-b1c2-9e0f5a6d7c88",
      "reporterId": "c4d5e6f7-8a9b-40c1-92d3-e4f5a6b7c8d9",
      "reporterName": "Asha Gurung",
      "targetType": "LISTING",
      "targetId": "8f2b1c34-0a11-4e77-9d2e-4c3b6a1f9e01",
      "reason": "Listing photos don't match the actual item; suspected scam.",
      "status": "OPEN",
      "resolutionNote": null,
      "handledBy": null,
      "handledAt": null,
      "createdAt": "2026-07-18T09:14:22Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "last": true
}
```

### PUT /api/v1/admin/reports/{id}

Resolve or dismiss a report. Auth: permission `trust.report.resolve`.

**Path params** — | Name | In | Type | Required | Description |
| --- | --- | --- | --- | --- |
| `id` | path | UUID | Yes | Report ID |

**Request body** — | Field | Type | Required | Constraints | Description |
| --- | --- | --- | --- | --- |
| `status` | string enum | Yes | `@NotNull`; use `RESOLVED` or `DISMISSED` | New report status |
| `note` | string | No | max 1000 chars | Optional resolution note |

**Response (data)** — `ReportResponse` (see fields under `POST /reports`), reflecting the new `status`, `resolutionNote`, `handledBy`, and `handledAt`.

Request:
```json
{
  "status": "RESOLVED",
  "note": "Listing removed and owner warned."
}
```

Response `data`:
```json
{
  "id": "1a9c7e20-3f4d-4b8a-b1c2-9e0f5a6d7c88",
  "reporterId": "c4d5e6f7-8a9b-40c1-92d3-e4f5a6b7c8d9",
  "reporterName": "Asha Gurung",
  "targetType": "LISTING",
  "targetId": "8f2b1c34-0a11-4e77-9d2e-4c3b6a1f9e01",
  "reason": "Listing photos don't match the actual item; suspected scam.",
  "status": "RESOLVED",
  "resolutionNote": "Listing removed and owner warned.",
  "handledBy": "0b1a2c3d-4e5f-4061-8273-9a8b7c6d5e4f",
  "handledAt": "2026-07-18T11:02:47Z",
  "createdAt": "2026-07-18T09:14:22Z"
}
```

### GET /api/v1/users/me/workers

List the current business owner's workers. Auth: authenticated.

**Response (data)** — `WorkerResponse[]` | Field | Type | Description |
| --- | --- | --- |
| `id` | UUID | Worker ID |
| `name` | string | Worker name |
| `phone` | string \| null | Contact phone |
| `role` | string \| null | Worker role/title |
| `active` | boolean | Whether the worker is active |

Response `data`:
```json
[
  {
    "id": "aa11bb22-cc33-4d44-8e55-ff6600112233",
    "name": "Bikash Tamang",
    "phone": "+9779812345678",
    "role": "Delivery",
    "active": true
  }
]
```

### POST /api/v1/users/me/workers

Add a worker to the current owner's registry. Auth: authenticated.

**Request body** — `WorkerRequest` | Field | Type | Required | Constraints | Description |
| --- | --- | --- | --- | --- |
| `name` | string | Yes | `@NotBlank`, max 120 chars | Worker name |
| `phone` | string | No | max 20 chars | Contact phone |
| `role` | string | No | max 80 chars | Worker role/title |

**Response (data)** — `WorkerResponse` (see fields under `GET .../workers`).

Returns `201 Created`.

Request:
```json
{
  "name": "Bikash Tamang",
  "phone": "+9779812345678",
  "role": "Delivery"
}
```

Response `data`:
```json
{
  "id": "aa11bb22-cc33-4d44-8e55-ff6600112233",
  "name": "Bikash Tamang",
  "phone": "+9779812345678",
  "role": "Delivery",
  "active": true
}
```

### PUT /api/v1/users/me/workers/{id}

Update one of the current owner's workers. Auth: authenticated.

**Path params** — | Name | In | Type | Required | Description |
| --- | --- | --- | --- | --- |
| `id` | path | UUID | Yes | Worker ID |

**Request body** — `WorkerRequest` | Field | Type | Required | Constraints | Description |
| --- | --- | --- | --- | --- |
| `name` | string | Yes | `@NotBlank`, max 120 chars | Worker name |
| `phone` | string | No | max 20 chars | Contact phone |
| `role` | string | No | max 80 chars | Worker role/title |

**Response (data)** — `WorkerResponse` (see fields under `GET .../workers`).

Request:
```json
{
  "name": "Bikash Tamang",
  "phone": "+9779800000000",
  "role": "Logistics Lead"
}
```

Response `data`:
```json
{
  "id": "aa11bb22-cc33-4d44-8e55-ff6600112233",
  "name": "Bikash Tamang",
  "phone": "+9779800000000",
  "role": "Logistics Lead",
  "active": true
}
```

### DELETE /api/v1/users/me/workers/{id}

Remove a worker from the current owner's registry. Auth: authenticated.

**Path params** — | Name | In | Type | Required | Description |
| --- | --- | --- | --- | --- |
| `id` | path | UUID | Yes | Worker ID |

**Response (data)** — a confirmation string.

Response `data`:
```json
"Worker removed"
```

### GET /api/v1/categories/{id}/pricing-policy

Public pricing policy for a category — deposit guidance and cancellation schedule for display. Auth: public. Returns empty band/tier lists if no policy is set.

**Path params** — | Name | In | Type | Required | Description |
| --- | --- | --- | --- | --- |
| `id` | path | UUID | Yes | Category ID |

**Response (data)** — `PricingPolicyResponse` | Field | Type | Description |
| --- | --- | --- |
| `categoryId` | UUID | Category ID |
| `depositBands` | DepositBand[] | Deposit guidance bands (may be empty) |
| `cancellationTiers` | CancellationTier[] | Cancellation schedule (may be empty) |

`DepositBand` — | Field | Type | Description |
| --- | --- | --- |
| `minValue` | decimal | Lower bound of the declared item-value range |
| `maxValue` | decimal | Upper bound of the declared item-value range |
| `depositMin` | decimal | Minimum suggested deposit for the range |
| `depositMax` | decimal | Maximum suggested deposit for the range |
| `damageCap` | decimal | Maximum damage liability for the range |

`CancellationTier` — | Field | Type | Description |
| --- | --- | --- |
| `hoursBefore` | int | Cancelling within this many hours of start... |
| `withholdPct` | decimal | ...forfeits this percentage of the rental charge |

Response `data`:
```json
{
  "categoryId": "5c6d7e8f-9a0b-41c2-83d4-e5f6a7b8c9d0",
  "depositBands": [
    {
      "minValue": 0,
      "maxValue": 50000,
      "depositMin": 2000,
      "depositMax": 5000,
      "damageCap": 50000
    }
  ],
  "cancellationTiers": [
    { "hoursBefore": 24, "withholdPct": 50 },
    { "hoursBefore": 6, "withholdPct": 100 }
  ]
}
```

### PUT /api/v1/admin/categories/{id}/pricing-policy

Set (upsert) a category's pricing policy. Auth: permission `listing.category.manage`.

**Path params** — | Name | In | Type | Required | Description |
| --- | --- | --- | --- | --- |
| `id` | path | UUID | Yes | Category ID |

**Request body** — `PricingPolicyRequest` | Field | Type | Required | Constraints | Description |
| --- | --- | --- | --- | --- |
| `depositBands` | DepositBand[] | No | — | Deposit guidance bands (see field table above) |
| `cancellationTiers` | CancellationTier[] | No | — | Cancellation schedule (see field table above) |

**Response (data)** — `PricingPolicyResponse` (see fields above).

Request:
```json
{
  "depositBands": [
    {
      "minValue": 0,
      "maxValue": 50000,
      "depositMin": 2000,
      "depositMax": 5000,
      "damageCap": 50000
    }
  ],
  "cancellationTiers": [
    { "hoursBefore": 24, "withholdPct": 50 },
    { "hoursBefore": 6, "withholdPct": 100 }
  ]
}
```

Response `data`:
```json
{
  "categoryId": "5c6d7e8f-9a0b-41c2-83d4-e5f6a7b8c9d0",
  "depositBands": [
    {
      "minValue": 0,
      "maxValue": 50000,
      "depositMin": 2000,
      "depositMax": 5000,
      "damageCap": 50000
    }
  ],
  "cancellationTiers": [
    { "hoursBefore": 24, "withholdPct": 50 },
    { "hoursBefore": 6, "withholdPct": 100 }
  ]
}
```

---

## Admin Console

All endpoints are prefixed with `/api/v1/admin` and require a Bearer JWT (attached by the BFF as an httpOnly cookie). Every endpoint is guarded by a specific permission. Responses use the standard envelope `{ "data": ..., "error": null, "timestamp": ... }`; the tables below describe the `data` contents. List endpoints wrap `data` in a `PageResponse` (`content[]`, `page`, `size`, `totalElements`, `last`).

### GET /api/v1/admin/users

List user profiles, optionally filtered by status. Auth: permission `identity.user.read`.

**Query params**

| Name | In | Type | Required | Description |
|------|-----|------|----------|-------------|
| status | query | enum (`PENDING_VERIFICATION`, `VERIFIED`, `SUSPENDED`) | No | Filter by account status |
| page | query | int | No | Page index, default `0` |
| size | query | int | No | Page size, default `20`, capped at `100` |

**Response (data)** — `PageResponse<UserProfileResponse>`; each `content[]` item:

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | User ID |
| phoneNumber | string | Phone number |
| email | string | Email |
| fullName | string | Display name |
| profilePhotoUrl | string | Avatar URL |
| status | string | `PENDING_VERIFICATION` \| `VERIFIED` \| `SUSPENDED` |
| authProvider | string | `LOCAL` \| `GOOGLE` |
| hasPassword | boolean | Whether a local password is set |
| phoneVerified | boolean | Phone verified |
| emailVerified | boolean | Email verified |
| citizenshipVerified | boolean | Citizenship/KYC verified |
| kycStatus | string | null \| `SUBMITTED` \| `APPROVED` \| `REJECTED` |
| trustScore | number | Trust score |
| paymentWallet | string | Payout wallet identifier |
| accountType | string | Account type |
| businessName | string | Business name (if any) |
| createdAt | ISO-8601 | Registration timestamp |

```json
{
  "content": [
    {
      "id": "9c3b1e2a-7f4d-4a11-8b2e-1d6f0a9c2e10",
      "phoneNumber": "+9779800000000",
      "email": "asha@example.com",
      "fullName": "Asha Rai",
      "profilePhotoUrl": "https://cdn.rentle.app/u/asha.jpg",
      "status": "VERIFIED",
      "authProvider": "LOCAL",
      "hasPassword": true,
      "phoneVerified": true,
      "emailVerified": true,
      "citizenshipVerified": true,
      "kycStatus": "APPROVED",
      "trustScore": 92.5,
      "paymentWallet": "esewa:98xxxxxxxx",
      "accountType": "INDIVIDUAL",
      "businessName": null,
      "createdAt": "2026-05-01T08:30:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 340,
  "last": false
}
```

### GET /api/v1/admin/kyc

Paginated queue of pending KYC submissions. Auth: permission `kyc.submission.read`.

**Query params**

| Name | In | Type | Required | Description |
|------|-----|------|----------|-------------|
| page | query | int | No | Page index, default `0` |
| size | query | int | No | Page size, default `20`, capped at `100` |

**Response (data)** — `PageResponse<KycAdminRow>`; each `content[]` item:

| Field | Type | Description |
|-------|------|-------------|
| userId | UUID | Submitting user's ID |
| currentName | string | Name on the account |
| realName | string | Name declared in the KYC form |
| email | string | User email |
| status | string | KYC status |
| submittedAt | ISO-8601 | Submission timestamp |

```json
{
  "content": [
    {
      "userId": "9c3b1e2a-7f4d-4a11-8b2e-1d6f0a9c2e10",
      "currentName": "Asha R.",
      "realName": "Asha Rai",
      "email": "asha@example.com",
      "status": "SUBMITTED",
      "submittedAt": "2026-07-10T04:12:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 12,
  "last": true
}
```

### GET /api/v1/admin/kyc/{userId}

Full KYC detail for one user. Auth: permission `kyc.submission.read`.

**Path params**

| Name | In | Type | Required | Description |
|------|-----|------|----------|-------------|
| userId | path | UUID | Yes | User whose KYC to fetch |

**Response (data)** — `KycResponse`:

| Field | Type | Description |
|-------|------|-------------|
| status | string | `SUBMITTED` \| `APPROVED` \| `REJECTED` |
| realName | string | Legal name |
| fatherName | string | Father's name |
| grandfatherName | string | Grandfather's name |
| dateOfBirth | date (YYYY-MM-DD) | Date of birth |
| gender | string | Gender |
| citizenshipNumber | string | Citizenship number |
| citizenshipIssueDistrict | string | Issuing district |
| occupation | string | Occupation |
| permanentAddress | object | `{ district, municipality, ward (int), tole }`, nullable |
| temporaryAddress | object | `{ district, municipality, ward (int), tole }`, nullable |
| rejectionReason | string | Set when rejected |
| reviewedAt | ISO-8601 | Review timestamp |
| submittedAt | ISO-8601 | Submission timestamp |

```json
{
  "status": "SUBMITTED",
  "realName": "Asha Rai",
  "fatherName": "Bikash Rai",
  "grandfatherName": "Krishna Rai",
  "dateOfBirth": "1996-03-22",
  "gender": "FEMALE",
  "citizenshipNumber": "12-01-70-01234",
  "citizenshipIssueDistrict": "Kathmandu",
  "occupation": "Engineer",
  "permanentAddress": { "district": "Kathmandu", "municipality": "KMC", "ward": 10, "tole": "Baneshwor" },
  "temporaryAddress": null,
  "rejectionReason": null,
  "reviewedAt": null,
  "submittedAt": "2026-07-10T04:12:00Z"
}
```

### GET /api/v1/admin/users/{id}/citizenship

Stream a citizenship document image (binary). Auth: permission `kyc.submission.read`.

**Path/query params**

| Name | In | Type | Required | Description |
|------|-----|------|----------|-------------|
| id | path | UUID | Yes | User whose document to load |
| side | query | string | No | `front` (default) or `back` |

**Response** — raw image bytes (not the JSON envelope); `Content-Type` matches the stored image (e.g. `image/jpeg`).

### PUT /api/v1/admin/users/{id}/verify

Approve a user's KYC submission. Auth: permission `kyc.submission.approve`.

**Path params**

| Name | In | Type | Required | Description |
|------|-----|------|----------|-------------|
| id | path | UUID | Yes | User to approve |

**Response (data)** — `KycResponse` (now `status: APPROVED`; see schema above).

### PUT /api/v1/admin/users/{id}/reject-kyc

Reject a user's KYC submission with an optional reason. Auth: permission `kyc.submission.reject`.

**Path params**

| Name | In | Type | Required | Description |
|------|-----|------|----------|-------------|
| id | path | UUID | Yes | User to reject |

**Request body** — `ReasonRequest` (optional; the whole body may be omitted):

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| reason | string | No | max 400 chars | Rejection reason shown to the user |

**Response (data)** — `KycResponse` (now `status: REJECTED`, `rejectionReason` set).

```json
{ "reason": "Citizenship photo is blurry; please resubmit." }
```

### PUT /api/v1/admin/users/{id}/suspend

Suspend a user account. Auth: permission `identity.user.suspend`.

**Path params**

| Name | In | Type | Required | Description |
|------|-----|------|----------|-------------|
| id | path | UUID | Yes | User to suspend |

**Response (data)** — `UserProfileResponse` (now `status: SUSPENDED`; see `GET /users` schema).

### PUT /api/v1/admin/users/{id}/unsuspend

Reinstate a suspended user. Auth: permission `identity.user.suspend`.

**Path params**

| Name | In | Type | Required | Description |
|------|-----|------|----------|-------------|
| id | path | UUID | Yes | User to unsuspend |

**Response (data)** — `UserProfileResponse` (see `GET /users` schema).

### PUT /api/v1/admin/users/{id}/password

Administratively reset a user's password. Auth: permission `identity.user.reset_password`.

**Path params**

| Name | In | Type | Required | Description |
|------|-----|------|----------|-------------|
| id | path | UUID | Yes | User whose password to reset |

**Request body** — `ResetPasswordRequest`:

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| password | string | Yes | not blank, 8–72 chars | New password |

**Response (data)** — string `"Password updated"`.

```json
{ "password": "N3wStr0ngPass!" }
```

### GET /api/v1/admin/bookings

List all bookings platform-wide. Auth: permission `booking.booking.read`.

**Query params**

| Name | In | Type | Required | Description |
|------|-----|------|----------|-------------|
| page | query | int | No | Page index, default `0` |
| size | query | int | No | Page size, default `20`, capped at `100` |

**Response (data)** — `PageResponse<BookingResponse>`; each `content[]` item is a `BookingResponse` (see schema in the next endpoint).

### GET /api/v1/admin/bookings/{id}

Fetch one booking by ID. Auth: permission `booking.booking.read`.

**Path params**

| Name | In | Type | Required | Description |
|------|-----|------|----------|-------------|
| id | path | UUID | Yes | Booking ID |

**Response (data)** — `BookingResponse`:

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Booking ID |
| listingId | UUID | Listing ID |
| listingTitle | string | Listing title |
| listingType | string | Listing type |
| ownerId | UUID | Owner user ID |
| ownerName | string | Owner name |
| renterId | UUID | Renter user ID |
| renterName | string | Renter name |
| startDate | date | Rental start date |
| endDate | date | Rental end date |
| startTime | time (HH:mm:ss) | Start time |
| endTime | time (HH:mm:ss) | End time |
| status | string | Booking status |
| totalPrice | number | Total price |
| depositAmount | number | Deposit amount |
| depositPaid | boolean | Deposit paid flag |
| depositProofUrl | string | Deposit proof URL |
| ownerPaymentWallet | string | Owner payout wallet |
| renterNote | string | Renter's note |
| agreedTerms | string | Agreed terms snapshot |
| attributes | object (map) | Arbitrary key/value attributes |
| hasCheckoutCondition | boolean | Checkout condition recorded |
| checkoutNote | string | Checkout note |
| hasReturnCondition | boolean | Return condition recorded |
| returnNote | string | Return note |
| platformFeeAmount | number | Platform fee |
| feeInvoiced | boolean | Whether the fee has been invoiced |
| cancellationSchedule | array of `CancellationTier` | Cancellation-refund tiers |
| assignedWorkerId | UUID | Assigned worker ID (services) |
| assignedWorkerName | string | Assigned worker name |
| cancellationReason | string | Reason if cancelled |
| createdAt | ISO-8601 | Booking creation timestamp |

```json
{
  "id": "b1a2c3d4-0000-4000-8000-000000000001",
  "listingId": "11111111-2222-3333-4444-555555555555",
  "listingTitle": "Canon EOS R6 Kit",
  "listingType": "GEAR",
  "ownerId": "9c3b1e2a-7f4d-4a11-8b2e-1d6f0a9c2e10",
  "ownerName": "Asha Rai",
  "renterId": "77777777-8888-9999-aaaa-bbbbbbbbbbbb",
  "renterName": "Prakash Thapa",
  "startDate": "2026-07-20",
  "endDate": "2026-07-22",
  "startTime": "09:00:00",
  "endTime": "18:00:00",
  "status": "CONFIRMED",
  "totalPrice": 4500.00,
  "depositAmount": 10000.00,
  "depositPaid": true,
  "depositProofUrl": "https://cdn.rentle.app/proofs/xyz.jpg",
  "ownerPaymentWallet": "esewa:98xxxxxxxx",
  "renterNote": "Will pick up at 9am.",
  "agreedTerms": "Standard rental terms v3",
  "attributes": { "insurance": "basic" },
  "hasCheckoutCondition": true,
  "checkoutNote": "No scratches.",
  "hasReturnCondition": false,
  "returnNote": null,
  "platformFeeAmount": 225.00,
  "feeInvoiced": false,
  "cancellationSchedule": [ { "hoursBefore": 24, "refundPercent": 50 } ],
  "assignedWorkerId": null,
  "assignedWorkerName": null,
  "cancellationReason": null,
  "createdAt": "2026-07-15T11:00:00Z"
}
```

### GET /api/v1/admin/listings

List all listings platform-wide. Auth: permission `listing.listing.read`.

**Query params**

| Name | In | Type | Required | Description |
|------|-----|------|----------|-------------|
| page | query | int | No | Page index, default `0` |
| size | query | int | No | Page size, default `20`, capped at `100` |

**Response (data)** — `PageResponse<ListingSummaryResponse>`; each `content[]` item:

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Listing ID |
| type | string | Listing type |
| status | string | Listing status |
| title | string | Title |
| pricePerUnit | number | Price per unit |
| priceUnit | string | Pricing unit (e.g. `DAY`, `HOUR`) |
| depositAmount | number | Required deposit |
| district | string | District |
| averageRating | number | Average rating |
| reviewCount | int | Number of reviews |
| coverImage | string | Cover image URL |
| createdAt | ISO-8601 | Creation timestamp |

### PUT /api/v1/admin/listings/{id}/deactivate

Moderator action: deactivate a listing (hide from search) with an optional reason. Auth: permission `listing.listing.moderate`.

**Path params**

| Name | In | Type | Required | Description |
|------|-----|------|----------|-------------|
| id | path | UUID | Yes | Listing to deactivate |

**Request body** — `ReasonRequest` (optional; body may be omitted):

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| reason | string | No | max 400 chars | Moderation reason |

**Response (data)** — `ListingSummaryResponse` (see `GET /listings` schema).

```json
{ "reason": "Prohibited item category." }
```

### PUT /api/v1/admin/listings/{id}/remove

Moderator action: remove a listing with an optional reason. Auth: permission `listing.listing.moderate`.

**Path params**

| Name | In | Type | Required | Description |
|------|-----|------|----------|-------------|
| id | path | UUID | Yes | Listing to remove |

**Request body** — `ReasonRequest` (optional; body may be omitted):

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| reason | string | No | max 400 chars | Moderation reason |

**Response (data)** — `ListingSummaryResponse` (see `GET /listings` schema).

### GET /api/v1/admin/categories

List all categories including inactive ones, with listing counts. Auth: permission `listing.category.manage`.

**Response (data)** — array of `AdminCategoryRow`:

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Category ID |
| name | string | Display name |
| slug | string | URL slug |
| listingType | string | Listing type this category belongs to |
| iconName | string | Icon identifier |
| sortOrder | int | Display order |
| active | boolean | Whether the category is active |
| listingCount | int (long) | Number of listings in this category |

```json
[
  { "id": "c1111111-0000-4000-8000-000000000001", "name": "Cameras", "slug": "cameras", "listingType": "GEAR", "iconName": "camera", "sortOrder": 1, "active": true, "listingCount": 128 }
]
```

### GET /api/v1/admin/settings

Fetch all platform settings as a key/value map. Auth: permission `platform.settings.manage`.

**Response (data)** — object: a map of `{ "<key>": "<value>" }` (all string values).

```json
{ "platform.fee.percent": "5", "booking.min_hours": "2" }
```

### PUT /api/v1/admin/settings/{key}

Update a single platform setting; returns the full settings map. Auth: permission `platform.settings.manage`.

**Path params**

| Name | In | Type | Required | Description |
|------|-----|------|----------|-------------|
| key | path | string | Yes | Setting key to update |

**Request body** — `SettingValueRequest`:

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| value | string | Yes | not blank, max 200 chars | New setting value |

**Response (data)** — object: the complete updated settings map (as in `GET /settings`).

```json
{ "value": "7" }
```

### GET /api/v1/admin/fees

List bookings by platform-fee invoicing status. Auth: permission `booking.fee.manage`.

**Query params**

| Name | In | Type | Required | Description |
|------|-----|------|----------|-------------|
| invoiced | query | boolean | No | `false` (default) = fees not yet invoiced; `true` = already invoiced |
| page | query | int | No | Page index, default `0` |
| size | query | int | No | Page size, default `20`, capped at `100` |

**Response (data)** — `PageResponse<BookingResponse>`; each `content[]` item is a `BookingResponse` (see `GET /bookings/{id}` schema).

### PUT /api/v1/admin/bookings/{id}/fee-invoiced

Mark a booking's platform fee as invoiced. Auth: permission `booking.fee.manage`.

**Path params**

| Name | In | Type | Required | Description |
|------|-----|------|----------|-------------|
| id | path | UUID | Yes | Booking whose fee to mark invoiced |

**Response (data)** — `BookingResponse` (now `feeInvoiced: true`; see `GET /bookings/{id}` schema).

### PUT /api/v1/admin/categories/{id}/status

Activate or deactivate a category. Auth: permission `listing.category.manage`.

**Path params**

| Name | In | Type | Required | Description |
|------|-----|------|----------|-------------|
| id | path | UUID | Yes | Category to update |

**Request body** — `CategoryStatusRequest`:

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| active | boolean | Yes | not null | New active state |

**Response (data)** — `AdminCategoryRow` (see `GET /categories` schema).

```json
{ "active": false }
```

---

## Platform IAM (Roles, Permissions, Assignments)

### GET /api/v1/platform/roles
List all platform roles. Auth: permission `platform.role.read`.

**Response (data)** — array of role objects:

| Field | Type | Description |
| --- | --- | --- |
| id | UUID | Role identifier |
| name | string | Machine name (uppercase, e.g. `PLATFORM_ADMIN`) |
| displayName | string | Human-readable label |
| description | string \| null | Optional description |
| systemRole | boolean | True if a built-in, non-deletable role |
| permissionKeys | string[] | Permission keys granted by this role |

```json
{
  "data": [
    {
      "id": "6f1c2a90-1c2d-4b3e-9a0f-11223344aabb",
      "name": "SUPPORT_AGENT",
      "displayName": "Support Agent",
      "description": "Read-only support access",
      "systemRole": false,
      "permissionKeys": ["identity.user.read", "platform.assignment.read"]
    }
  ],
  "error": null,
  "timestamp": "2026-07-18T10:15:30Z"
}
```

### GET /api/v1/platform/roles/{id}
Fetch a single role by id. Auth: permission `platform.role.read`.

**Path/query params**

| Name | In | Type | Required | Description |
| --- | --- | --- | --- | --- |
| id | path | UUID | Yes | Role identifier |

**Response (data)** — single role object (same fields as GET /platform/roles element above).

```json
{
  "data": {
    "id": "6f1c2a90-1c2d-4b3e-9a0f-11223344aabb",
    "name": "SUPPORT_AGENT",
    "displayName": "Support Agent",
    "description": "Read-only support access",
    "systemRole": false,
    "permissionKeys": ["identity.user.read"]
  },
  "error": null,
  "timestamp": "2026-07-18T10:15:30Z"
}
```

### POST /api/v1/platform/roles
Create a new role. Returns HTTP 201. Auth: permission `platform.role.manage`.

**Request body**

| Field | Type | Required | Constraints | Description |
| --- | --- | --- | --- | --- |
| name | string | Yes | not blank, max 60, `^[A-Z][A-Z0-9_]*$` | Machine name |
| displayName | string | Yes | not blank, max 120 | Human-readable label |
| description | string | No | max 300 | Optional description |
| permissionKeys | string[] | Yes | not null; each element not blank | Permission keys to grant |

**Response (data)** — created role object (see RoleResponse fields above).

```json
{
  "name": "SUPPORT_AGENT",
  "displayName": "Support Agent",
  "description": "Read-only support access",
  "permissionKeys": ["identity.user.read", "platform.assignment.read"]
}
```
```json
{
  "data": {
    "id": "6f1c2a90-1c2d-4b3e-9a0f-11223344aabb",
    "name": "SUPPORT_AGENT",
    "displayName": "Support Agent",
    "description": "Read-only support access",
    "systemRole": false,
    "permissionKeys": ["identity.user.read", "platform.assignment.read"]
  },
  "error": null,
  "timestamp": "2026-07-18T10:15:30Z"
}
```

### PUT /api/v1/platform/roles/{id}
Update a role's display name, description, and permission set. Auth: permission `platform.role.manage`.

**Path/query params**

| Name | In | Type | Required | Description |
| --- | --- | --- | --- | --- |
| id | path | UUID | Yes | Role identifier |

**Request body**

| Field | Type | Required | Constraints | Description |
| --- | --- | --- | --- | --- |
| displayName | string | Yes | not blank, max 120 | Human-readable label |
| description | string | No | max 300 | Optional description |
| permissionKeys | string[] | Yes | not null; each element not blank | Replacement set of permission keys |

Note: `name` is immutable and cannot be changed via update.

**Response (data)** — updated role object (see RoleResponse fields above).

```json
{
  "displayName": "Support Agent (Tier 2)",
  "description": "Extended support access",
  "permissionKeys": ["identity.user.read", "platform.assignment.read", "platform.role.read"]
}
```
```json
{
  "data": {
    "id": "6f1c2a90-1c2d-4b3e-9a0f-11223344aabb",
    "name": "SUPPORT_AGENT",
    "displayName": "Support Agent (Tier 2)",
    "description": "Extended support access",
    "systemRole": false,
    "permissionKeys": ["identity.user.read", "platform.assignment.read", "platform.role.read"]
  },
  "error": null,
  "timestamp": "2026-07-18T10:15:30Z"
}
```

### DELETE /api/v1/platform/roles/{id}
Delete a role. Auth: permission `platform.role.manage`.

**Path/query params**

| Name | In | Type | Required | Description |
| --- | --- | --- | --- | --- |
| id | path | UUID | Yes | Role identifier |

**Response (data)** — confirmation string.

```json
{ "data": "Role deleted", "error": null, "timestamp": "2026-07-18T10:15:30Z" }
```

### GET /api/v1/platform/permissions
List the permission catalog, optionally filtered by domain. Auth: permission `platform.permission.read`.

**Path/query params**

| Name | In | Type | Required | Description |
| --- | --- | --- | --- | --- |
| domain | query | string | No | Filter permissions by domain (e.g. `platform`, `identity`) |

**Response (data)** — array of permission objects:

| Field | Type | Description |
| --- | --- | --- |
| id | UUID | Permission identifier |
| key | string | Full permission key (e.g. `platform.role.manage`) |
| domain | string | Domain segment |
| resource | string | Resource segment |
| action | string | Action segment |
| description | string \| null | Optional description |
| deprecated | boolean | True if the permission is deprecated |

```json
{
  "data": [
    {
      "id": "aa11bb22-cc33-dd44-ee55-ff6677889900",
      "key": "platform.role.manage",
      "domain": "platform",
      "resource": "role",
      "action": "manage",
      "description": "Create, update, and delete platform roles",
      "deprecated": false
    }
  ],
  "error": null,
  "timestamp": "2026-07-18T10:15:30Z"
}
```

### GET /api/v1/platform/assignments
List role assignments, optionally filtered by user or role. Auth: permission `platform.assignment.read`.

**Path/query params**

| Name | In | Type | Required | Description |
| --- | --- | --- | --- | --- |
| userId | query | UUID | No | Filter by assigned user |
| roleId | query | UUID | No | Filter by role |

**Response (data)** — array of assignment objects:

| Field | Type | Description |
| --- | --- | --- |
| id | UUID | Assignment identifier |
| userId | UUID | Assigned user's id |
| email | string | Assigned user's email |
| fullName | string | Assigned user's full name |
| roleId | UUID | Role id |
| roleName | string | Role machine name |
| scopeId | UUID | Scope id the assignment applies to |
| scopeName | string | Scope name |
| grantedBy | UUID \| null | User id that granted the assignment (null if system-granted) |
| createdAt | string (ISO-8601) | When the assignment was created |

```json
{
  "data": [
    {
      "id": "12340000-0000-0000-0000-000000000001",
      "userId": "99990000-0000-0000-0000-000000000002",
      "email": "agent@rentle.io",
      "fullName": "Ada Agent",
      "roleId": "6f1c2a90-1c2d-4b3e-9a0f-11223344aabb",
      "roleName": "SUPPORT_AGENT",
      "scopeId": "00000000-0000-0000-0000-000000000000",
      "scopeName": "PLATFORM",
      "grantedBy": "77770000-0000-0000-0000-000000000003",
      "createdAt": "2026-07-18T10:15:30Z"
    }
  ],
  "error": null,
  "timestamp": "2026-07-18T10:15:30Z"
}
```

### POST /api/v1/platform/assignments
Assign a role to a user. Returns HTTP 201. Auth: permission `platform.assignment.manage`.

**Request body**

| Field | Type | Required | Constraints | Description |
| --- | --- | --- | --- | --- |
| userId | UUID | Yes | not null | User to assign the role to |
| roleId | UUID | Yes | not null | Role to assign |

**Response (data)** — created assignment object (see GET /platform/assignments fields above).

```json
{
  "userId": "99990000-0000-0000-0000-000000000002",
  "roleId": "6f1c2a90-1c2d-4b3e-9a0f-11223344aabb"
}
```
```json
{
  "data": {
    "id": "12340000-0000-0000-0000-000000000001",
    "userId": "99990000-0000-0000-0000-000000000002",
    "email": "agent@rentle.io",
    "fullName": "Ada Agent",
    "roleId": "6f1c2a90-1c2d-4b3e-9a0f-11223344aabb",
    "roleName": "SUPPORT_AGENT",
    "scopeId": "00000000-0000-0000-0000-000000000000",
    "scopeName": "PLATFORM",
    "grantedBy": "77770000-0000-0000-0000-000000000003",
    "createdAt": "2026-07-18T10:15:30Z"
  },
  "error": null,
  "timestamp": "2026-07-18T10:15:30Z"
}
```

### DELETE /api/v1/platform/assignments/{id}
Revoke a role assignment. Auth: permission `platform.assignment.manage`.

**Path/query params**

| Name | In | Type | Required | Description |
| --- | --- | --- | --- | --- |
| id | path | UUID | Yes | Assignment identifier |

**Response (data)** — confirmation string.

```json
{ "data": "Assignment revoked", "error": null, "timestamp": "2026-07-18T10:15:30Z" }
```

### GET /api/v1/platform/users/lookup
Look up a user by email address. Auth: permission `identity.user.read`.

**Path/query params**

| Name | In | Type | Required | Description |
| --- | --- | --- | --- | --- |
| email | query | string | Yes | Email address to look up |

**Response (data)** — user lookup object:

| Field | Type | Description |
| --- | --- | --- |
| id | UUID | User identifier |
| email | string | User email |
| fullName | string | User full name |
| status | string | User status (enum name, e.g. `ACTIVE`) |

```json
{
  "data": {
    "id": "99990000-0000-0000-0000-000000000002",
    "email": "agent@rentle.io",
    "fullName": "Ada Agent",
    "status": "ACTIVE"
  },
  "error": null,
  "timestamp": "2026-07-18T10:15:30Z"
}
```

---
