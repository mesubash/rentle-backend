# Provider Verification — Design & Plan (Phase A)

**Status:** proposal for review. Nothing here is built yet — customize the templates,
fields, and gating rules below, then it gets implemented.

The goal: verify **service providers** in a way that differs per service (a mover
needs a driving licence + vehicle papers; a photographer needs a portfolio; an
electrician needs a trade licence) **without hardcoding each service in code**. New
service types should be added as data/config, not a deploy.

---

## 1. Two separate verification layers

| Layer | What it proves | Varies per service? | Status |
|-------|----------------|---------------------|--------|
| **Identity KYC** | This is a real, known person | No — same for everyone | Built |
| **Provider credentials** | This person/business is qualified to offer *this* service | **Yes** | This plan |

A service listing goes live only when **both** are satisfied: identity `VERIFIED`
**and** provider-verified for that service category.

---

## 2. Account type

Add an account type, because a business's trust story is different from a person's.

- **INDIVIDUAL** — identity KYC (done) + category credentials for themselves. The
  person booked is the person who shows up.
- **BUSINESS** — business KYC (registration / PAN / VAT) + an authorized
  representative who passes identity KYC + category credentials + a **worker
  registry** (Phase B). A staff member, not the account holder, may attend.

Listings by a business show a "Registered business" badge (vs "Verified individual"),
so a client knows a staff member may attend.

---

## 3. The core idea: template-driven credentials (data, not code)

Each service category has a **requirement template** — a list of what a provider must
submit. The submission form, storage, and admin review all render *from* the template,
so adding a service = editing config, not shipping code.

### 3.1 Requirement definition (one row per required item)

```
key            e.g. "driving_license", "portfolio_url", "trade_license_no"
label          human text shown on the form
type           TEXT | NUMBER | DATE | SELECT | DOCUMENT | DOCUMENT_LIST
required       true / false
options        for SELECT (e.g. van, truck, pickup)
help           optional hint / validation note
```

### 3.2 Example templates (customize these)

> **REVIEW THESE — this is the part you tune.** Adjust fields, add categories, mark
> required vs optional.

**Moving & Transport**
- `driving_license` — DOCUMENT — required
- `driving_license_no` — TEXT — required
- `vehicle_type` — SELECT (motorbike, van, pickup, truck) — required
- `vehicle_registration` — DOCUMENT — required
- `capacity_kg` — NUMBER — optional

**Event & Photography**
- `portfolio_url` — TEXT — required
- `sample_work` — DOCUMENT_LIST — optional
- `years_experience` — NUMBER — optional

**(Future) Electrical / Plumbing**
- `trade_license_no` — TEXT — required
- `trade_license_doc` — DOCUMENT — required

**(Future) Tutoring**
- `highest_qualification` — SELECT — required
- `qualification_doc` — DOCUMENT — required
- `subjects` — TEXT — required

Product categories (cameras, clothing) need **no** provider credentials — identity KYC
is enough. Templates only exist for service categories that require them.

---

## 4. Data model (pragmatic — JSONB, not full EAV)

Keep it simple for a handful of categories. Two small tables plus a config source for
templates.

```
provider_verification
  id                uuid
  user_id           uuid  (the provider or the business account)
  category_id       uuid  (which service category this qualifies them for)
  status            SUBMITTED | APPROVED | REJECTED
  fields            jsonb  ({ "driving_license_no": "12-34", "vehicle_type": "van", ... })
  template_version  int    (which template revision they answered — for grandfathering)
  rejection_reason  varchar
  reviewed_by       uuid
  reviewed_at       timestamptz
  created_at / updated_at

provider_verification_document
  id                uuid
  verification_id   uuid
  key               varchar  (which requirement, e.g. "driving_license")
  ref               varchar  (private storage reference, like KYC images)
```

Templates themselves can live as:
- **Config** (a YAML/JSON file or a `@ConfigurationProperties` map) — simplest, edited
  by developers; or
- a `verification_requirement` table — editable by admins at runtime.

Recommendation: **start with config** (fast, versioned in git), move to a table only if
you want non-developers editing requirements live.

**Why JSONB, not normalized attribute tables:** for ~4–8 categories, a normalized
attribute-value model is heavy to build and query for little gain. JSONB + template
validation in the service gives the flexibility at a fraction of the cost. Revisit only
if a category needs relational querying over individual answers.

---

## 5. Flows

### Provider onboarding (individual)
1. Finish identity KYC (Tier 1). 
2. Choose a service category to offer → the app reads that category's template and
   renders a dynamic form (only that category's fields/documents).
3. Submit → `provider_verification` row (SUBMITTED) + documents stored privately.
4. Admin reviews the rendered fields + documents → approve / reject (with reason).
5. On approval, the user may publish listings in that category.

### Admin review
- A queue of `SUBMITTED` provider verifications.
- The review screen renders whatever the template defined (labels + values +
  documents) — no per-category admin code.
- Approve, or reject with a reason (user corrects and resubmits, same as KYC).

### Listing gate (updated)
Publishing a **service** listing in category X requires:
`identity VERIFIED` **and** an `APPROVED provider_verification` for category X.
Publishing a **product** listing requires only `identity VERIFIED`.

---

## 6. Endpoints (proposed)

```
GET  /api/v1/categories/{id}/requirements     public — the template to render the form
GET  /api/v1/users/me/provider-verifications  own submissions + statuses
POST /api/v1/users/me/provider-verifications  multipart: categoryId + field values + docs
GET  /api/v1/admin/provider-verifications     queue (SUBMITTED)
GET  /api/v1/admin/provider-verifications/{id}
GET  /api/v1/admin/provider-verifications/{id}/documents/{key}
PUT  /api/v1/admin/provider-verifications/{id}/approve
PUT  /api/v1/admin/provider-verifications/{id}/reject   { reason }
```

---

## 7. Two cautions

- **Version templates.** Store `template_version` on each submission. When you change a
  category's requirements, already-approved providers stay valid (grandfathered) until
  you explicitly re-request; new submissions use the new version.
- **Don't over-engineer.** No generic rules engine, no per-field workflow states.
  Config + JSONB + one validating service is enough until a real category proves it
  isn't.

---

## 8. Phasing

- **Phase A (this doc):** account type + business KYC + template-driven per-category
  provider verification + updated listing gate + dynamic admin review.
- **Phase B:** worker registry for businesses; per-booking worker assignment so the
  client sees who will actually attend, with that worker's credentials.
- **Phase C:** licence expiry tracking, insurance, re-verification cadence, background
  checks, worker-level reviews rolling up to the business.

---

## 9. What to customize before build

1. **Templates in §3.2** — the exact fields per service, required vs optional.
2. **Account types in §2** — do you need BUSINESS now, or individuals only first?
3. **Template source in §4** — config file (dev-edited) vs admin-editable table.
4. **Listing gate in §5** — confirm product listings need identity only.
5. **Which categories** get a provider template at launch (moving + photography?).

Mark this up and I'll build Phase A to match.
