# Permission Catalog (Path B — in-app IAM)

**Status:** v2 — refined before enablement. Key format rules formalized, redundant
suffixes removed, action verbs standardized into a closed vocabulary. Because the
IAM flags are still off and nothing is seeded to any database, these renames are
free; the code catalog (`domain/platform/catalog/`) must be updated to match this
document before Iteration 2 enablement.

---

## 1. What IS and ISN'T a permission

- **Permissions gate staff/admin/provider capabilities** — the things a role grants:
  reviewing KYC, suspending users, moderating listings, managing an org's members.
- **Ordinary user actions are NOT in this catalog.** Booking, listing, messaging,
  reviewing, editing your own profile, submitting your own KYC are governed by
  **ownership + verification status** (the existing `VERIFIED` gate), not by
  role-permissions. A plain `USER` holds **no** permissions here.
- **Scope** = where a permission applies. Today everything is at the single **ROOT**
  scope. When organizations arrive, org-scoped permissions are granted at that org's
  scope subtree — marked **[scoped]** below.

---

## 2. Key construction rules (the extensibility contract)

Every key is exactly **`domain.resource.action`** — 3 segments, lowercase,
`[a-z_]+` each. Immutable once seeded: deprecate, never rename.

### 2.1 Domain

A domain is a **backend module** (`com.rentle.domain.<x>`), plus `platform` for the
access system itself. Deterministic rule: **new module ⇒ new domain, same name**.
No judgment calls. Registered domains:

| Domain | Module | Status |
|--------|--------|--------|
| `platform` | the access system (roles, permissions, assignments, scopes, audit) | now |
| `identity` | user-account administration | now |
| `kyc` | identity-verification review | now |
| `listing` | listing + category moderation | now |
| `booking` | booking oversight | now |
| `org` | organizations & memberships | org phase |
| `provider` | provider-credential review | provider phase |

### 2.2 Resource

The entity being acted on, named after the module's primary entity/table
(singular). When a module's primary resource shares the module's name, the key
stutters (`listing.listing.read`) — **accepted deliberately**: a predictable
mechanical rule beats a pretty ad-hoc one. Secondary resources use their own names
(`listing.category.*`, `org.member.*`).

### 2.3 Action — closed vocabulary

Actions come **only** from this table. A new verb requires amending this table
first, with justification — that keeps keys uniform forever.

| Action | Meaning | HTTP shape it typically gates |
|--------|---------|------------------------------|
| `read` | View list + detail. For staff this always includes **all states** (draft, inactive, removed, suspended) — a separate `read_all` never exists. | GET |
| `create` | Create a resource | POST |
| `update` | Modify a resource | PUT / PATCH |
| `delete` | Remove a resource | DELETE |
| `manage` | Coarse `create` + `update` + `delete` in one key. Use when splitting adds no real control; split later by deprecating. | POST/PUT/DELETE |
| `approve` | Accept a submission in a review workflow | PUT/POST |
| `reject` | Decline a submission (with reason) | PUT/POST |
| `suspend` | Suspend / unsuspend an account or resource | PUT |
| `moderate` | Override the state of someone else's resource (deactivate / remove) | PUT |
| `assign` | Attach a subject to something (role → user, worker → booking) | POST/DELETE |

**Splitting rule:** actions are separate exactly when you would ever grant one
without the other (a reviewer who approves but escalates rejections; support that
suspends but never edits). Merging two actions later is easy; splitting a seeded
key is a deprecate-and-re-grant exercise — so when in doubt, keep the boundary.

### 2.4 Adding a future domain — the procedure

1. Domain name = the new module's name; add it to §2.1.
2. Name its resources (§2.2 rule).
3. Pick actions from §2.3 only; if a genuinely new verb is unavoidable, add it to
   §2.3 with its meaning first.
4. Add one catalog class in `domain/platform/catalog/` (e.g. `OrgPermissions`)
   implementing `PermissionCatalog`, plus constants in `PermissionKeys`.
5. Extend role bundles in `RoleSeeds` (or create new roles) — next boot syncs it.
   No migration, no SQL.

---

## 3. Permissions

### 3.1 `platform` — access administration (super-admin territory)

| Key | Gates |
|-----|-------|
| `platform.role.read` | View roles and their permissions |
| `platform.role.manage` | Create / edit / delete roles, set their permissions |
| `platform.permission.read` | View the permission catalog |
| `platform.permission.manage` | Register / deprecate permissions |
| `platform.assignment.read` | View who has which role where |
| `platform.assignment.manage` | Grant / revoke roles to subjects (at a scope) |
| `platform.scope.read` | View the scope tree |
| `platform.scope.manage` | Create / edit / move scopes (orgs) |
| `platform.audit.read` | View authorization/audit records *(later)* |

### 3.2 `identity` — user accounts

| Key | Gates |
|-----|-------|
| `identity.user.read` | List and view user accounts; look up by email (assignment picker) |
| `identity.user.suspend` | Suspend / unsuspend an account (revokes live tokens) |

### 3.3 `kyc` — identity verification

| Key | Gates |
|-----|-------|
| `kyc.submission.read` | View the KYC queue, a submission's details, and its documents |
| `kyc.submission.approve` | Approve a submission (verifies the user, locks the name) |
| `kyc.submission.reject` | Reject with a reason |

### 3.4 `listing` — catalog moderation

| Key | Gates |
|-----|-------|
| `listing.listing.read` | View all listings incl. draft / inactive / removed (staff `read` always means all states — §2.3) |
| `listing.listing.moderate` | Deactivate or remove any listing |
| `listing.category.manage` | Create / edit service & product categories |

### 3.5 `booking` — oversight

| Key | Gates |
|-----|-------|
| `booking.booking.read` | View all bookings (dispute/support) |
| `booking.booking.moderate` | Intervene / cancel on behalf of support *(optional, later)* |

### 3.6 `org` — organizations & members **[scoped]** (org phase — DRAFT)

**Not seeded until the org phase is designed.** These keys are a provisional
sketch so the model is proven extensible; expect revision (through §2.4, not
ad-hoc) when organizations become real. Granted at an **org's scope**, so an
owner administers only their own org.

| Key | Gates |
|-----|-------|
| `org.profile.read` | View the org profile |
| `org.profile.manage` | Edit the org profile / settings |
| `org.member.read` | View org members (workers) |
| `org.member.manage` | Add / remove members, set their org-role |
| `org.listing.manage` | Create / publish / edit listings under the org |
| `org.booking.assign` | Assign a worker to a service booking |

*(v1 had `org.org.read/manage` — stutter with no primary-entity justification,
renamed to `org.profile.*`; `org.listing.publish` and `org.booking.assign_worker`
used off-vocabulary verbs, now `manage` / `assign`.)*

### 3.7 `provider` — provider-credential review (provider phase)

| Key | Gates |
|-----|-------|
| `provider.verification.read` | View provider (per-service) credential submissions |
| `provider.verification.approve` | Approve a provider for a service category |
| `provider.verification.reject` | Reject with a reason |

---

## 4. Roles (bundles) — the seed

| Role | Permissions | Scope |
|------|-------------|-------|
| **SUPER_ADMIN** | **everything**, including `platform.*` | ROOT |
| **ADMIN** | `identity.*`, `kyc.*`, `listing.*`, `booking.booking.read` — day-to-day ops; **no** `platform.*` | ROOT |
| **KYC_REVIEWER** | `kyc.submission.read/approve/reject` only | ROOT |
| **SUPPORT** | `identity.user.read`, `booking.booking.read`, `listing.listing.read` (read-only) | ROOT |
| **USER** | *(none — marketplace actions via ownership + VERIFIED status)* | — |
| **ORG_OWNER** *(org phase)* | `org.*` | that org |
| **ORG_MANAGER** *(org phase)* | `org.profile.read`, `org.member.read`, `org.listing.manage`, `org.booking.assign` | that org |
| **ORG_WORKER** *(org phase)* | `org.profile.read`, `org.booking.assign` (own jobs) | that org |

`SUPER_ADMIN` and `USER` are **system roles** (can't be deleted).

---

## 5. Scopes

- **Now:** one ROOT scope (created by the catalog synchronizer at enablement).
  Every admin assignment lands there.
- **Org phase:** each organization becomes a scope node under ROOT. Org roles are
  assigned at that node; a grant applies to that org (and sub-units) only.

---

## 6. How a permission is checked

- **Backend:** `@PreAuthorize("hasAuthority('kyc.submission.approve')")` on the
  endpoint, using `PermissionKeys` constants — never string literals. For scoped
  permissions (org phase), additionally check the resource's scope against the grant.
- **Frontend:** `GET /users/me/permissions` returns the caller's keys; a
  `can('kyc.submission.approve')` helper gates nav / buttons / routes. UX-only —
  the server re-checks every call.
- **Delivery:** per-request from the DB with a Redis cache (`perms:{userId}`,
  explicit invalidation) — decided; not baked into the JWT.

---

## 7. Endpoint → permission map (current admin surface)

| Endpoint | Permission |
|----------|-----------|
| `GET /admin/users` | `identity.user.read` |
| `PUT /admin/users/{id}/suspend` · `/unsuspend` | `identity.user.suspend` |
| `GET /admin/kyc` · `/admin/kyc/{id}` · `/admin/users/{id}/citizenship` | `kyc.submission.read` |
| `PUT /admin/users/{id}/verify` | `kyc.submission.approve` |
| `PUT /admin/users/{id}/reject-kyc` | `kyc.submission.reject` |
| `GET /admin/listings` | `listing.listing.read` |
| `PUT /admin/listings/{id}/deactivate` · `/remove` (new) | `listing.listing.moderate` |
| `GET /admin/bookings` | `booking.booking.read` |
| `/platform/**` (roles / permissions / assignments) | `platform.*` per §3.1 |
| `GET /users/me/permissions` | authenticated (no key) |

Self-service endpoints (`/listings`, `/bookings`, `/users/me/**`, `/reviews`) stay
gated by ownership + `VERIFIED` status — **no permission key**.

---

## 8. v1 → v2 rename map (apply to code before enablement)

The code catalog currently implements v1 keys. Nothing is seeded anywhere, so this
is a plain rename in `PermissionKeys`, the per-domain catalog classes, `RoleSeeds`,
the platform controllers' `@PreAuthorize` constants, and tests:

| v1 (in code now) | v2 (this document) |
|------------------|--------------------|
| `listing.listing.read_all` | `listing.listing.read` |
| `booking.booking.read_all` | `booking.booking.read` |
| *(org phase, not in code)* `org.org.read` / `org.org.manage` | `org.profile.read` / `org.profile.manage` |
| *(org phase, not in code)* `org.listing.publish` | `org.listing.manage` |
| *(org phase, not in code)* `org.booking.assign_worker` | `org.booking.assign` |

All other keys are unchanged. After enablement this table becomes historical —
future changes go through deprecation, never rename.
