# Permission Catalog (Path B — in-app IAM)

**Status:** for review. This is step 1 of adopting the permission + scope model into
Rentle's backend. Nothing is built until this list is agreed — every later piece
(roles, `@PreAuthorize` gates, admin console, frontend `can()`) is derived from it.

Convention (from spring-iam): a permission key is **`domain.resource.action`**,
lowercase, 3 segments. Immutable once created — you deprecate, never rename.

---

## 1. What IS and ISN'T a permission

- **Permissions gate staff/admin/provider capabilities** — the things a role grants:
  reviewing KYC, suspending users, moderating listings, managing an org's members.
- **Ordinary user actions are NOT in this catalog.** Booking, listing, messaging,
  reviewing, editing your own profile, submitting your own KYC are governed by
  **ownership + verification status** (the existing `VERIFIED` gate), not by
  role-permissions. A plain `USER` holds **no** permissions here. Over-permissioning
  self-service actions is noise.
- **Scope** = where a permission applies. Today everything is at the single **ROOT**
  scope (dormant). When organizations arrive, org-scoped permissions are granted at
  that org's scope subtree — marked **[scoped]** below.

---

## 2. Domains

| Domain | Covers | When |
|--------|--------|------|
| `platform` | The access system itself — roles, permissions, assignments, scopes, audit | now |
| `identity` | User-account administration (list, view, suspend, look-up) | now |
| `kyc` | Identity verification review | now |
| `listing` | Listing moderation + category management | now |
| `booking` | Booking oversight | now |
| `org` | Organizations & memberships (business providers + workers) | org phase |
| `provider` | Per-service provider-credential review | provider phase |

---

## 3. Permissions

> **REVIEW THIS.** Add/remove/rename actions per what your staff and providers should
> be able to do. `.manage` is a coarse create+update+delete; split into
> `.create/.update/.delete` only where you want finer control.

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
| `listing.listing.read_all` | View all listings incl. draft / inactive / removed |
| `listing.listing.moderate` | Deactivate or remove any listing |
| `listing.category.manage` | Create / edit service & product categories |

### 3.5 `booking` — oversight

| Key | Gates |
|-----|-------|
| `booking.booking.read_all` | View all bookings (dispute/support) |
| `booking.booking.moderate` | Intervene / cancel on behalf of support *(optional, later)* |

### 3.6 `org` — organizations & members **[scoped]** (org phase)

Granted at an **org's scope**, so an owner administers only their own org.

| Key | Gates |
|-----|-------|
| `org.org.read` | View the org profile |
| `org.org.manage` | Edit the org profile / settings |
| `org.member.read` | View org members (workers) |
| `org.member.manage` | Add / remove members, set their org-role |
| `org.listing.publish` | Publish a listing under the org |
| `org.booking.assign_worker` | Assign a worker to a service booking |

### 3.7 `provider` — provider-credential review (provider phase)

| Key | Gates |
|-----|-------|
| `provider.verification.read` | View provider (per-service) credential submissions |
| `provider.verification.approve` | Approve a provider for a service category |
| `provider.verification.reject` | Reject with a reason |

---

## 4. Roles (bundles) — the seed

> **REVIEW THIS.** These are the initial named bundles. Admins can create more later
> via `platform.role.manage`.

| Role | Permissions | Scope |
|------|-------------|-------|
| **SUPER_ADMIN** | **everything**, including `platform.*` (manages roles/permissions/assignments) | ROOT |
| **ADMIN** | `identity.*`, `kyc.*`, `listing.*`, `booking.read_all` — day-to-day ops; **no** `platform.*` | ROOT |
| **KYC_REVIEWER** | `kyc.submission.read/approve/reject` only | ROOT |
| **SUPPORT** | `identity.user.read`, `booking.read_all`, `listing.listing.read_all` (read-only) | ROOT |
| **USER** | *(none — marketplace actions via ownership + VERIFIED status)* | — |
| **ORG_OWNER** *(org phase)* | `org.*`, `org.listing.publish`, `org.booking.assign_worker`, `org.member.manage` | that org |
| **ORG_MANAGER** *(org phase)* | `org.org.read`, `org.member.read`, `org.listing.publish`, `org.booking.assign_worker` | that org |
| **ORG_WORKER** *(org phase)* | `org.org.read`, `org.booking.assign_worker` (own jobs) | that org |

`SUPER_ADMIN` and `USER` are **system roles** (can't be deleted). The seeded
super-admin (`admin@gmail.com`) becomes `SUPER_ADMIN`.

---

## 5. Scopes

- **Now:** one auto-seeded **ROOT** scope. Every admin assignment lands there. No
  hierarchy to think about.
- **Org phase:** each organization becomes a scope node under ROOT. Org roles are
  assigned at that node; a grant at an org scope applies to that org (and any
  sub-units) only. This is what makes an owner administer *their* org and nobody
  else's.

---

## 6. How a permission is checked

- **Backend:** `@PreAuthorize("hasAuthority('kyc.submission.approve')")` on the
  endpoint (replaces `hasRole('ADMIN')`). For scoped ones, additionally check the
  resource's scope against the grant.
- **Frontend:** `GET /users/me` (or a `/me/bootstrap`) returns the user's permission
  keys; a `can('kyc.submission.approve')` helper gates nav / buttons / routes.
  UX-only — the server re-checks every call.

**Decision to confirm (shapes the token):** permissions delivered **per-request from
the DB (cached)** — recommended for org-scoped grants an owner can change — vs baked
into the JWT (fast but stale ≤ 15 min). Default: per-request, Redis-cached.

---

## 7. Endpoint → permission map (current admin surface)

| Endpoint | Permission |
|----------|-----------|
| `GET /admin/users` | `identity.user.read` |
| `PUT /admin/users/{id}/suspend` · `/unsuspend` | `identity.user.suspend` |
| `GET /admin/kyc` · `/admin/kyc/{id}` · `/admin/users/{id}/citizenship` | `kyc.submission.read` |
| `PUT /admin/users/{id}/verify` | `kyc.submission.approve` |
| `PUT /admin/users/{id}/reject-kyc` | `kyc.submission.reject` |
| `GET /admin/listings` | `listing.listing.read_all` |
| (listing deactivate/remove by admin — new) | `listing.listing.moderate` |
| `GET /admin/bookings` | `booking.booking.read_all` |
| roles / permissions / assignments / scopes admin (new) | `platform.*` |

Self-service endpoints (`/listings`, `/bookings`, `/users/me/**`, `/reviews`,
`/users/me/kyc`) stay gated by ownership + `VERIFIED` status — **no permission key**.

---

## 8. What to confirm before build

1. **The permission list (§3)** — actions per domain; `.manage` vs split CRUD.
2. **The seed roles (§4)** — names and their bundles.
3. **The token decision (§6)** — per-request lookup (recommended) vs JWT claim.
4. **Naming** — keep `platform.*` for the access system (matches spring-iam) or rename
   to `iam.*`.
5. **Do you want a `SUPPORT` (read-only) role at launch, or just SUPER_ADMIN + ADMIN?**

Mark this up and I'll build the model (permissions + roles + assignments + scopes),
wire `@PreAuthorize`, and move the admin + frontend onto it — incrementally, keeping
today's behavior working throughout.
