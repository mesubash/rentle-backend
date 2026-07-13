# Rentle IAM Migration Plan — In-App Permissions + Super-Admin Console

**Status:** implementation plan, backend-first. Written to be handed to an AI agent
(or developer) who will implement it. Read this whole document before writing code.

**Goal:** replace Rentle's binary `USER`/`ADMIN` role enum with a proper
permission-based access system (Path B: the IAM model lives *inside* rentle-backend,
adapted from the `spring-iam` reference project), then build the super-admin
dashboard that manages roles, permissions, and assignments.

---

## 0. Required reading (in this order)

Do not start until you've read these. Paths are absolute on this machine.

**In this repo (`/Users/isubash/Developer/rentle/rentle-backend`):**

1. `docs/08_permission_catalog.md` — **the contract.** The permission keys, seed
   roles, endpoint→permission map, and the decided conventions (3-segment
   `domain.resource.action` keys, per-request DB lookup with Redis cache, ROOT-only
   scope for now). This plan implements that catalog; do not invent different keys.
2. `docs/06_rentle_api_reference.md` — current API surface and envelope.
3. `docs/07_provider_verification_plan.md` — Phase A that follows this work; the
   IAM model must not block it (it adds `provider.*` permissions later).

**In the reference project (`/Users/isubash/Developer/spring-iam`):**

Rentle adopts a *subset* of this system. Study these to copy patterns, naming, and
API shapes — not to copy every feature:

4. `docs/AUTHZ_DESIGN.md`, `docs/ARCHITECTURE.md` — the model and its reasoning.
5. `src/main/resources/db/migration/V1__core.sql` and `V4__platform_seed.sql` —
   table shapes for `permissions`, `roles`, `role_permissions`, `scopes`,
   `assignments`, and how seeding is done (fixed UUIDs, system roles, SuperAdmin
   gets everything).
6. `src/main/java/**/authz/entity/{Permission,Role,RolePermission,Scope,Assignment}.java`
   — entity conventions.
7. `src/main/java/**/authz/controller/{RoleController,PermissionController,AssignmentController}.java`
   and `AuthzMeController` — the management API shapes to mirror.
8. For the frontend phase: `web/src/api/client.ts`, `web/src/api/resources.ts`,
   `web/src/context/AuthzContext.tsx`, `web/src/components/iam/Can.tsx`,
   `web/src/components/iam/AppLayout.tsx`, `web/src/routes/_authenticated.admin.roles.tsx`,
   and `docs/FRONTEND_INTEGRATION.md`.

---

## 1. Current state of rentle-backend (what you are migrating)

| Concern | Today | File |
|---|---|---|
| Role model | `enum UserRole { USER, ADMIN }` column on `users` | `domain/user/model/UserRole.java` |
| Enforcement | `hasRole("ADMIN")` URL rule + `@PreAuthorize("hasRole('ADMIN')")` class-level | `config/SecurityConfig.java`, `domain/admin/controller/AdminController.java` |
| JWT | RS256 resource-server; claims `sub`, `role`, `status`, `jti`; authorities mapped from the `role` claim by a `JwtAuthenticationConverter` | `config/SecurityConfig.java`, `shared/security/JwtTokenService.java` |
| Revocation | `bl:{jti}` blacklist + `susp:{userId}` suspension check inside the `JwtDecoder` wrapper; `TokenRevocationService` | `shared/security/` |
| Admin surface | users list, suspend/unsuspend, KYC queue/detail/approve/reject/document, listings, bookings | `domain/admin/controller/AdminController.java`, `AdminService`, `KycService` |
| Topology | Next.js BFF proxies everything; backend sees one host IP; rate limits keyed by userId/identifier — **not** per-IP | `shared/security/RateLimitInterceptor.java` |
| Envelope | `{data, error, timestamp}` everywhere, including security errors via `JsonErrorWriter` | `shared/api/`, `shared/security/` |
| Migrations | Flyway V001–V011, append-only, `ddl-auto: validate` | `src/main/resources/db/migration/` |

Constraints that must survive the migration untouched: the envelope, the BFF
rate-limit keying, token revocation semantics, `ddl-auto: validate` (schema and
entities must match exactly), and all existing tests.

---

## 2. Target model — what we adopt from spring-iam, and what we deliberately don't

### Adopt (the core four + dormant scopes)

| Table | Purpose | spring-iam reference |
|---|---|---|
| `permissions` | Immutable registry of keys `domain.resource.action` (CHECK-constrained, lowercase). Deprecate, never rename/delete. | `V1__core.sql`, `Permission.java` |
| `roles` | Named bundles. `is_system_role` protects `SUPER_ADMIN`/`USER` from deletion. | `Role.java` |
| `role_permissions` | role ↔ permission mapping | `RolePermission.java` |
| `scopes` | Hierarchy for the org phase. **Now: a single seeded ROOT row** (fixed UUID). Simple `parent_id` self-reference — no ltree, no closure table until orgs actually arrive. | `Scope.java` (simplified) |
| `assignments` | The grant tuple: `subject_id (user) × role_id × scope_id`, plus `granted_by`, `created_at`, `revoked_at` (revoke = set timestamp, never delete). | `Assignment.java` |

### Explicitly NOT adopted (do not build these)

Role hierarchy DAG, deny rules, ABAC policies/context attributes, resource grants
(ReBAC), subject groups, service registry/API keys, break-glass, ltree +
scope-closure, partitioned authorization-audit, the standalone PDP
(`/authorize`, `/authorize/explain`...). Rentle is one application enforcing its own
permissions with `@PreAuthorize` — it does not need a decision-engine service.
If a future need appears, spring-iam shows the upgrade path; leave it out now.

### Decided conventions (from `docs/08`, restated — do not re-litigate)

- Permission keys: `domain.resource.action`, exactly 3 segments, lowercase.
  The access-system domain keeps the spring-iam name **`platform`**.
- Ordinary marketplace actions (book, list, message, review, own-profile, own-KYC)
  are **not** permissions — they stay gated by ownership + `VERIFIED` status.
  A plain `USER` holds zero permission keys.
- **Permissions are resolved per-request from the DB with a Redis cache** — they are
  NOT baked into the JWT. (spring-iam does the same: roles re-loaded per request via
  `CustomUserDetailsService`; its `iam.claims-mode=roles` claim is informational.)
  Rationale: instant revocation, and org-scoped grants later can change without
  waiting out a 15-minute token.
- Seed roles and their bundles: exactly `docs/08 §4` (`SUPER_ADMIN`, `ADMIN`,
  `KYC_REVIEWER`, `SUPPORT`, `USER`).

---

## 3. Backend migration — phased plan

Work phase by phase; each phase compiles, passes all tests, and is a separate
commit (or small commit series). Behavior stays backward-compatible until Phase 4
flips enforcement.

### Phase 1 — Schema + seed (Flyway `V012`, `V013`)

`V012__iam_tables.sql`:

```sql
CREATE TABLE permissions (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key           VARCHAR(120) NOT NULL,
    domain        VARCHAR(40)  NOT NULL,
    resource      VARCHAR(40)  NOT NULL,
    action        VARCHAR(40)  NOT NULL,
    description   VARCHAR(300),
    is_deprecated BOOLEAN      NOT NULL DEFAULT false,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_permissions_key UNIQUE (key),
    CONSTRAINT chk_permission_key_format
        CHECK (key = domain || '.' || resource || '.' || action
               AND key ~ '^[a-z_]+\.[a-z_]+\.[a-z_]+$')
);

CREATE TABLE scopes (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id  UUID REFERENCES scopes(id),
    type       VARCHAR(20)  NOT NULL,          -- ROOT | ORG (later)
    name       VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE roles (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(60)  NOT NULL,
    display_name   VARCHAR(120) NOT NULL,
    description    VARCHAR(300),
    is_system_role BOOLEAN      NOT NULL DEFAULT false,
    owner_scope_id UUID REFERENCES scopes(id),  -- NULL = global role
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_roles_name UNIQUE (name)
);

CREATE TABLE role_permissions (
    role_id       UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id),
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE assignments (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id UUID NOT NULL REFERENCES users(id),
    role_id    UUID NOT NULL REFERENCES roles(id),
    scope_id   UUID NOT NULL REFERENCES scopes(id),
    granted_by UUID REFERENCES users(id),
    revoked_at TIMESTAMPTZ,
    revoked_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
-- One live assignment per (subject, role, scope); history preserved on revoke
CREATE UNIQUE INDEX uq_assignments_live
    ON assignments(subject_id, role_id, scope_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_assignments_subject ON assignments(subject_id) WHERE revoked_at IS NULL;
```

Add `trg_roles_updated_at` using the existing `fn_update_updated_at()`.

`V013__iam_seed.sql` — mirror the spring-iam `V4__platform_seed.sql` technique
(fixed UUIDs for system rows so later migrations can reference them):

1. ROOT scope, fixed UUID `00000000-0000-0000-0000-000000000001`.
2. Every **now-phase** permission from `docs/08 §3` (`platform.*`, `identity.*`,
   `kyc.*`, `listing.*`, `booking.*` — org/provider domains are seeded in their own
   later phases, not now).
3. System roles `SUPER_ADMIN` (all seeded permissions) and `USER` (none);
   non-system roles `ADMIN`, `KYC_REVIEWER`, `SUPPORT` with the exact bundles from
   `docs/08 §4`.
4. **Backfill:** for every existing user with `role = 'ADMIN'`, insert a live
   assignment `(user, SUPER_ADMIN, ROOT)`. (The current lone admin is the
   platform owner — they must come out of this migration able to manage the new
   system. Day-to-day staff get the lesser `ADMIN` role later via the console.)
5. Keep the `users.role` column and enum for now — it is dropped in Phase 6.

### Phase 2 — `domain/platform` module (entities, repos, resolver)

New module `com.rentle.domain.platform` (name matches the permission domain),
standard layout like every other domain: `model/`, `repository/`, `service/`,
`dto/`, `controller/`.

- Entities: `Permission`, `Role`, `RolePermission` (or `@ManyToMany` on Role — prefer
  the explicit join entity, matching spring-iam), `Scope`, `Assignment`. Extend the
  existing `BaseEntity`/`AuditableEntity` correctly per which tables have
  `updated_at` (only `roles` does — see the BaseEntity/AuditableEntity split; wrong
  base class fails `ddl-auto: validate` at boot).
- `PermissionResolverService` — the heart:

```
Set<String> permissionKeysFor(UUID userId)
  1. Redis GET perms:{userId} → hit: deserialize, return.
  2. Miss: one query — live assignments (revoked_at IS NULL) join roles
     join role_permissions join permissions (is_deprecated = false)
     → collect keys. Scope is ignored for now (everything is ROOT);
     when orgs arrive this method grows a scope parameter.
  3. Redis SET perms:{userId} TTL 5m. Return.

void invalidate(UUID userId)        → DEL perms:{userId}
void invalidateRole(UUID roleId)    → DEL perms:{u} for every live assignee of the role
```

  Every mutation in Phase 5's admin API calls the matching invalidate. This is the
  same pattern as the existing `susp:{userId}` revocation — instant effect, no
  waiting for token expiry.

### Phase 3 — Authorities wiring (additive, nothing breaks yet)

Today `SecurityConfig.jwtAuthenticationConverter()` maps the JWT `role` claim to
`ROLE_<role>`. Change the converter to produce **both** kinds of authorities during
the transition:

- `ROLE_<role-claim>` (legacy — keeps every existing `hasRole` working), and
- one `SimpleGrantedAuthority` per permission key from
  `PermissionResolverService.permissionKeysFor(sub)`.

Implementation note: the converter needs the resolver bean — build the
`JwtAuthenticationConverter` with a lambda `jwtGrantedAuthoritiesConverter` that
closes over the service (constructor-inject it into `SecurityConfig`; it is an
ordinary bean, safe there — but do **not** move this wiring into a
`WebMvcConfigurer`, see the `JsonErrorWriter`/ObjectMapper trap in §7).

Cost: one Redis GET per authenticated request (cache hit path). That is in line
with the existing per-request blacklist/suspension checks in the decoder.

### Phase 4 — Flip enforcement

Apply the endpoint→permission map from `docs/08 §7` exactly:

- `AdminController`: drop the class-level `@PreAuthorize("hasRole('ADMIN')")`;
  put per-method `@PreAuthorize("hasAuthority('...'))")` — e.g. KYC queue/detail/
  document → `kyc.submission.read`, verify → `kyc.submission.approve`, reject-kyc →
  `kyc.submission.reject`, users list → `identity.user.read`, suspend/unsuspend →
  `identity.user.suspend`, listings → `listing.listing.read_all`, bookings →
  `booking.booking.read_all`.
- `SecurityConfig`: change `.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")`
  to `.authenticated()` — method-level annotations are now the authority (URL rule
  stays only as a "must be logged in" floor).
- Add the one new moderation capability the catalog defines:
  admin deactivate/remove any listing → `listing.listing.moderate`
  (new `AdminController` or `ListingController` admin endpoint calling
  `ListingService` with an ownership bypass — keep it in AdminService).
- Update integration tests: creating a staff user in tests now means inserting an
  assignment (test helper), not setting the enum.

### Phase 5 — Platform admin API (what the super-admin console consumes)

Mirror spring-iam's controller shapes (`RoleController`, `PermissionController`,
`AssignmentController`, `AuthzMeController`), scaled down, all under `/api/v1`,
returning the standard Rentle envelope:

| Endpoint | Permission | Notes |
|---|---|---|
| `GET /platform/permissions` | `platform.permission.read` | full registry, `?domain=` filter |
| `GET /platform/roles` · `GET /platform/roles/{id}` | `platform.role.read` | include permission keys per role |
| `POST /platform/roles` | `platform.role.manage` | `{name, displayName, description, permissionKeys[]}` |
| `PUT /platform/roles/{id}` | `platform.role.manage` | rename display/description + **replace** permission set; reject edits to `is_system_role` rows' permission set only for SUPER_ADMIN (it always has everything); invalidateRole |
| `DELETE /platform/roles/{id}` | `platform.role.manage` | refuse system roles and roles with live assignments |
| `GET /platform/assignments?userId=&roleId=` | `platform.assignment.read` | live only, with subject email/name for display |
| `POST /platform/assignments` | `platform.assignment.manage` | `{userId, roleId}` (scope implied ROOT); invalidate(userId) |
| `DELETE /platform/assignments/{id}` | `platform.assignment.manage` | soft-revoke (`revoked_at`, `revoked_by`); invalidate(userId) |
| `GET /platform/users/lookup?email=` | `identity.user.read` | assignment picker |
| `GET /users/me/permissions` | authenticated | the caller's permission keys — the frontend `can()` bootstrap |

Guard rails (service-level, mirroring spring-iam's invariants):

- A user cannot revoke their own last live `SUPER_ADMIN` assignment, and the last
  live SUPER_ADMIN assignment in the system can never be revoked.
- Permission registry is read-only via API for now (`platform.permission.manage`
  exists in the catalog but registering new keys happens via Flyway seed
  migrations; wire the POST later if ever needed).
- Every mutating endpoint records `granted_by`/`revoked_by` from
  `SecurityUtils.currentUserId()`.

### Phase 6 — Cleanup

- Add `GET /users/me/permissions` keys into the login/refresh `AuthResponse.user`
  payload (or keep separate — frontend's choice; separate endpoint is enough).
- Drop the `role` JWT claim's authority mapping (legacy `ROLE_*` no longer used
  anywhere), then migration `V014` drops `users.role`; delete `UserRole` enum and
  all references. Keep `status` exactly as is — verification is orthogonal.
- Update `docs/06_rentle_api_reference.md` with the new endpoints and a short
  "authorization model" section.

### Testing (every phase, but Phase 4–5 especially)

- Unit: `PermissionResolverService` (cache hit/miss/invalidation),
  role guard rails (last-superadmin, system-role deletion).
- Integration (Testcontainers, existing setup in
  `src/test/java/com/rentle/integration/` + `config/TestcontainersConfig.java`):
  - seeded SUPER_ADMIN backfill works (existing admin user keeps full access),
  - `KYC_REVIEWER` can approve KYC but gets 403 on `GET /platform/roles` and on
    suspend,
  - revoking an assignment takes effect on the next request (cache invalidated),
  - creating a custom role with a permission subset and assigning it works
    end-to-end.
- Smoke (curl, backend + compose up): login as seeded super-admin → list roles →
  create `SUPPORT`-like custom role → assign to a fresh user → verify that user
  sees exactly those permissions on `/users/me/permissions` and 403s elsewhere.

---

## 4. Frontend — super-admin console (after backend Phases 1–5 land)

Lives in the existing **`../rentle-frontend`** app (Next.js 16 App Router,
integration branch `feat/api-impl`, BFF proxy at `/api/rentle/*`, httpOnly cookie
tokens). Do NOT copy spring-iam's stack (Vite/TanStack Router/Bun) — copy its
**patterns** into the Next app:

| Pattern | spring-iam reference | Rentle adaptation |
|---|---|---|
| Typed API resource layer | `web/src/api/resources.ts` | extend the existing BFF client with `platformApi` (roles/permissions/assignments) |
| Permission context + `can()` | `web/src/context/AuthzContext.tsx` | provider that fetches `/users/me/permissions` once after login, exposes `can(key)`, `canAny(...)` |
| Declarative gating | `web/src/components/iam/Can.tsx`, `PermissionGuardedPage.tsx` | same two components; server components check via the BFF session |
| Nav filtered by permission | `web/src/components/iam/AppLayout.tsx` (nav items carry a `permission` key) | admin sidebar items declare their key; items the user lacks don't render |
| Role editor screen | `web/src/routes/_authenticated.admin.roles.tsx` | role list + detail with grouped permission checkboxes (group by `domain`), save = `PUT /platform/roles/{id}` |

Console pages (all under `/admin`, each gated by its key):

1. **Dashboard** — counts (pending KYC, users, listings, bookings) — existing data.
2. **Users** (`identity.user.read`) — list/filter, suspend/unsuspend
   (`identity.user.suspend`), and a "roles" panel per user showing live
   assignments with grant/revoke (`platform.assignment.manage`).
3. **KYC queue** (`kyc.submission.read/approve/reject`) — exists conceptually;
   re-gate onto permission keys.
4. **Roles** (`platform.role.read/manage`) — list, create, edit permission sets,
   delete; system roles visibly locked.
5. **Assignments** (`platform.assignment.read/manage`) — flat table with user
   lookup by email.
6. **Listings / Bookings oversight** (`listing.listing.read_all` + `moderate`,
   `booking.booking.read_all`).

Design language: the Rentle system (`docs/04_rentle_ui_design_prompt.md`) at admin
density — pine/paper/ink palette, flat cards, no new visual language.

---

## 5. What is explicitly out of scope for this migration

- Org scopes beyond the dormant ROOT row (comes with the organizations phase;
  the `assignments.scope_id` column is already in place for it).
- `provider.*` and `org.*` permission domains (seeded by the provider/org phases —
  see `docs/07`).
- Authorization audit table (catalog marks `platform.audit.read` as *later*;
  `granted_by`/`revoked_by`/`revoked_at` on assignments is the interim trail).
- Any spring-iam feature listed in §2 "NOT adopted".

---

## 6. Acceptance criteria

1. All existing tests pass; `./gradlew test` green throughout every phase.
2. The pre-migration admin user retains full access with zero manual steps
   (backfill assignment).
3. A `KYC_REVIEWER`-assigned user can work the KYC queue and nothing else;
   403s carry the standard envelope.
4. Assignment revocation takes effect on the next request (≤ one request lag,
   no token-expiry wait).
5. Super-admin console can: create a role, edit its permissions, assign it,
   revoke it — all reflected live in `can()`-gated UI.
6. `users.role` column and `UserRole` enum removed at the end (Phase 6),
   with no `hasRole` left in the codebase.
7. `docs/06_rentle_api_reference.md` updated.

---

## 7. Gotchas for the implementing agent (learned the hard way — respect these)

- **`ddl-auto: validate` is on.** Every entity column must match the migration
  exactly. Note the two base classes: `BaseEntity` (id + created_at) vs
  `AuditableEntity` (+ updated_at) — pick per table (§3 Phase 2).
- **Migrations are append-only.** Never edit V001–V013 once merged; fixes go in a
  new version.
- **Do not inject `ObjectMapper` (or other late beans) into anything constructed
  during `WebMvcConfigurer` setup** — it isn't ready and breaks context startup.
  That's why `JsonErrorWriter` exists; reuse it for any new security-layer JSON.
- **BFF topology:** all requests arrive from one IP. Never key anything by client
  IP; key by userId (authed) or identifier (login). The `RateLimitInterceptor`
  shows the pattern.
- **`AuthService.login` uses `@Transactional(noRollbackFor = UnauthorizedException.class)`**
  deliberately (failed-login counter must survive the throw). Don't "fix" it.
- **Local ports:** Postgres `5433`, Redis `6380` (5432/6379 belong to another
  project's containers — which are, ironically, spring-iam's).
- Redis is already the home for `bl:{jti}`, `susp:{userId}`, `refresh:*`,
  `otp:*`, `rl:*` — add `perms:{userId}` alongside, same StringRedisTemplate.
- Frontend dev server: never delete `.next` while `npm run dev` is alive
  (Turbopack cache corruption); kill node first.
- Commit style: conventional commits, no AI attribution in messages.

---

## 8. Suggested commit sequence

1. `feat(platform): add IAM schema and seed (permissions, roles, assignments, ROOT scope)` — Phases 1
2. `feat(platform): add platform module with permission resolver and Redis cache` — Phase 2
3. `feat(security): issue permission authorities alongside legacy role` — Phase 3
4. `refactor(admin): enforce endpoints with permission keys instead of hasRole` — Phase 4
5. `feat(platform): add role/assignment management API and /users/me/permissions` — Phase 5
6. `refactor(user): drop legacy role column and enum` — Phase 6
7. frontend commits in `rentle-frontend` (`feat/api-impl` branch), one per console page group.
