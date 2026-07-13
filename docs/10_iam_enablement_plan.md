# Rentle IAM — Iteration 2: Enablement (Backend)

**Status:** implementation instructions for an AI agent (or developer). Backend only;
the super-admin frontend console is a separate later document.

**Prerequisite:** Iteration 1 (docs/09) is implemented, verified, and on `main` —
the platform module exists and is dormant behind `rentle.iam.enabled=false` /
`rentle.iam.sync-catalog=false`, tables V015 are empty, and the admin surface still
runs on `hasRole('ADMIN')`.

**What this iteration delivers:** the IAM system switched **on** — catalog and
system roles seeded into the DB by the synchronizer, ROOT scope created, the first
SUPER_ADMIN granted via configuration, all admin endpoints enforced by permission
keys, and the legacy `USER`/`ADMIN` enum removed. After this iteration there is no
`hasRole` left in the codebase.

---

## 0. Required reading (in order)

1. `docs/09_iam_migration_plan.md` — Iteration 1 design; this doc continues its
   numbering and honors its decisions (do not re-litigate them).
2. `docs/08_permission_catalog.md` §7 — the exact endpoint → permission map to
   enforce in Step 4.
3. The implemented platform module — read all of it, it is the foundation:
   - `domain/platform/catalog/` — `PermissionKeys`, `RoleSeeds`, per-domain catalogs
   - `domain/platform/service/IamCatalogSynchronizer.java` — you will extend this
   - `domain/platform/service/PermissionResolverService.java` — cache + invalidation
   - `domain/platform/service/PlatformAdminService.java` — guard rails already built
   - `config/SecurityConfig.java` — flag-gated authorities converter
   - `config/RentleProperties.java` — the `Iam(enabled, syncCatalog)` record
4. `domain/admin/controller/AdminController.java` + `AdminService` + `KycService`
   — the surface being re-gated in Step 4.
5. Existing integration tests in `src/test/java/com/rentle/integration/` —
   especially `PlatformIamIntegrationTest` and `IamDisabledIntegrationTest`; you
   will extend/adjust these.

---

## 1. Steps — in order, one commit each

### Step 0 — Apply the v2 catalog renames (docs/08 §8)

docs/08 was refined to v2 before enablement; the code catalog still holds v1 keys.
Nothing is seeded anywhere, so this is a plain rename — no deprecation dance:

- `listing.listing.read_all` → `listing.listing.read`
  (`PermissionKeys.LISTING_LISTING_READ_ALL` → `LISTING_LISTING_READ`)
- `booking.booking.read_all` → `booking.booking.read`
  (`BOOKING_BOOKING_READ_ALL` → `BOOKING_BOOKING_READ`)

Touch: `PermissionKeys`, `ListingPermissions`, `BookingPermissions`, `RoleSeeds`,
any `@PreAuthorize` references, tests. Org-phase renames in docs/08 §8 are not in
code yet — nothing to do for them.

**Done when:** `grep -rn "read_all" src/` returns nothing; catalog unit test green.

### Step 1 — Synchronizer owns the ROOT scope

Extend `IamCatalogSynchronizer.synchronize()`: before syncing roles, ensure the
ROOT scope exists —

```text
scopeRepository.findFirstByType(ScopeType.ROOT)
    orElse → create Scope(type=ROOT, name="Rentle", parent=null), save
```

Idempotent: second run finds it, creates nothing. This removes the
"ROOT scope not found; IAM enablement has not been completed" error path in
`PlatformAdminService.createAssignment` for any environment that has synced.

**Done when:** a unit/integration test proves double-`synchronize()` yields exactly
one ROOT scope, and `createAssignment` works right after a fresh sync.

### Step 2 — Config-driven SUPER_ADMIN bootstrap

No SQL scripts, no manual grants — bootstrap is configuration, same philosophy as
the catalog:

- Add to `RentleProperties.Iam`: `String bootstrapSuperAdminEmail` (nullable).
- In `application.yml`:
  `bootstrap-super-admin-email: ${RENTLE_IAM_SUPER_ADMIN_EMAIL:}`.
- At the end of `synchronize()`: if the property is non-blank —
  1. look up the user by email (`findByEmailIgnoreCase`); **absent → log a WARN and
     skip** (never fail startup — the account may register later; next boot grants).
  2. if that user already has a live SUPER_ADMIN assignment at ROOT → no-op.
  3. else insert the assignment (`granted_by = null` — system-granted) and call
     `permissionResolverService.invalidate(userId)`.

Idempotent across restarts; removing the env var later grants nothing new and
revokes nothing (revocation stays a console action, protected by the existing
last-SUPER_ADMIN guard rails).

**Done when:** integration test — sync with the property pointing at a created
test user → user's `/users/me/permissions` (or resolver call) returns the full key
set; second sync adds no duplicate assignment.

### Step 3 — Flip the defaults

In `application.yml` set the new production posture:

```yaml
rentle:
  iam:
    enabled: true
    sync-catalog: true
    bootstrap-super-admin-email: ${RENTLE_IAM_SUPER_ADMIN_EMAIL:}
```

- Sync at every startup is safe (idempotent, small). Env overrides remain possible.
- Keep the flag *machinery* — do not delete the `enabled` check from
  `SecurityConfig`; it stays the kill switch.
- Update `IamDisabledIntegrationTest`: it now must set
  `rentle.iam.enabled=false` explicitly via `@TestPropertySource` (or
  `@SpringBootTest(properties=...)`) to keep proving the kill switch works.
  `.env.example` gains `RENTLE_IAM_SUPER_ADMIN_EMAIL=`.

**Done when:** full suite green; a fresh-DB boot (Testcontainers) seeds catalog +
roles + ROOT with zero manual action.

### Step 4 — Flip admin enforcement to permission keys

Apply `docs/08 §7` exactly on `AdminController`:

1. Remove the class-level `@PreAuthorize("hasRole('ADMIN')")`.
2. Per-method `@PreAuthorize("hasAuthority('" + PermissionKeys.X + "')")` —
   compile-time constants, same style as the platform controllers:

| Method | Key |
|---|---|
| `users` list | `IDENTITY_USER_READ` |
| `suspend` / `unsuspend` | `IDENTITY_USER_SUSPEND` |
| `kycQueue`, `kycDetail`, `citizenship` document | `KYC_SUBMISSION_READ` |
| `approveKyc` | `KYC_SUBMISSION_APPROVE` |
| `rejectKyc` | `KYC_SUBMISSION_REJECT` |
| listings list | `LISTING_LISTING_READ` |
| bookings list | `BOOKING_BOOKING_READ` |

3. `SecurityConfig`: change `.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")`
   to `.authenticated()` — method annotations are now the authority; the URL rule is
   only the logged-in floor.
4. Update every admin-flow integration test: staff users are now created by a test
   helper that (a) ensures sync ran, (b) inserts a live assignment for the wanted
   role, (c) calls `permissionResolverService.invalidate(userId)`. Put the helper
   where all integration tests can reach it (small `IamTestSupport` class in the
   test tree) — no test may set `user.setRole(ADMIN)` anymore.

**Done when:** integration tests prove — SUPER_ADMIN does everything;
KYC_REVIEWER works the KYC queue but 403s on suspend and `/platform/roles`;
SUPPORT reads users/listings/bookings but 403s on approve/suspend; a plain user
403s on all of `/admin/**`; all 403s carry the standard envelope.

### Step 5 — Listing moderation endpoints (the one new capability)

The catalog defines `listing.listing.moderate`; give it a surface:

- `PUT /api/v1/admin/listings/{id}/deactivate` — sets status `INACTIVE`
- `PUT /api/v1/admin/listings/{id}/remove` — sets status `REMOVED`
- Both `@PreAuthorize` `LISTING_LISTING_MODERATE`, body optional
  `{ "reason": "..." }` (reuse the existing `ReasonRequest` DTO).
- Implement in `AdminService` (ownership bypass belongs there, not in
  `ListingService`): load listing, set status, save. Removal of an already-REMOVED
  listing → `RentleException`. Log/store nothing beyond the status change for now
  (no moderation-audit table this iteration; the reason goes into the log).

**Done when:** integration test — moderator deactivates and removes another
user's listing; owner of the listing cannot call these endpoints without the key.

### Step 6 — Remove the legacy role system (last, separate commit)

Only after Steps 1–5 are merged and green:

1. `SecurityConfig` converter: delete the `ROLE_ + role-claim` authority block —
   authorities are permission keys only. Keep the `iam.enabled` guard around the
   permission lookup (kill switch semantics: disabled now means *no* authorities,
   which is correct — enforcement endpoints then simply 403).
2. `JwtTokenService.createAccessToken`: drop the `role` claim (keep `status`).
   Update its callers (`AuthService` issue/refresh paths).
3. `UserProfileResponse`: remove the `role` field. **Coordination note:** the
   frontend (`../rentle-frontend`, branch `feat/api-impl`) currently reads
   `user.role` for admin gating — it must switch to `GET /users/me/permissions`
   (`can()` model). Land this backend commit together with that frontend change.
4. Migration `V016__drop_user_role.sql` (or next free number):
   `ALTER TABLE users DROP COLUMN role;`
5. Delete `UserRole` enum; remove the field from `User`; purge every remaining
   reference (`grep -rn "UserRole\|hasRole" src/` must return nothing).
6. In-flight tokens: old access tokens still carry a `role` claim — harmless, the
   converter ignores unknown claims; nobody loses marketplace access because plain
   endpoints never depended on `ROLE_USER`.

**Done when:** grep clean; suite green; fresh boot + bootstrap email → full
console capability with no enum anywhere.

### Step 7 — Docs

- Update `docs/06_rentle_api_reference.md`: platform endpoints no longer "dormant";
  admin endpoints list their permission keys; document
  `RENTLE_IAM_SUPER_ADMIN_EMAIL`; add the moderation endpoints.
- Update `docs/09` status line: "Iteration 2 implemented".

---

## 2. Acceptance criteria

1. Fresh empty DB + `RENTLE_IAM_SUPER_ADMIN_EMAIL` set → one boot seeds catalog,
   roles, ROOT scope, and the super-admin assignment. Second boot changes nothing.
2. Full role matrix enforced (Step 4 tests): SUPER_ADMIN / ADMIN / KYC_REVIEWER /
   SUPPORT / plain user each see exactly their slice; 403s use the envelope.
3. Assignment grant/revoke reflects on the **next request** (cache invalidation),
   and suspension still revokes live tokens (`susp:{userId}` path untouched).
4. Kill switch: `rentle.iam.enabled=false` → no authorities → all permission-gated
   endpoints 403 (proven by the adjusted `IamDisabledIntegrationTest`).
5. No `hasRole`, no `UserRole`, no `users.role` column anywhere after Step 6.
6. All pre-existing marketplace behavior (auth, listings, bookings, messaging,
   reviews, KYC self-service) byte-for-byte unchanged.
7. `./gradlew test` green at every commit boundary.

---

## 3. Gotchas (inherited + new)

- **`ddl-auto: validate`** — dropping `users.role` (migration) and the entity field
  must land in the same commit or boot fails.
- **Migrations append-only** — next free V number (V016 if unused; check
  `src/main/resources/db/migration/` first, KYC/social work has been taking numbers).
- **Synchronizer runs at `ApplicationReadyEvent`** — integration tests that need
  seeded data must either boot with `sync-catalog=true` (now the default) or call
  `synchronize()` explicitly in the fixture. Don't double-seed in helpers.
- **Bootstrap must never fail startup** — missing user, blank email, duplicate
  assignment: WARN and continue. Startup crash on a config typo is unacceptable.
- **BFF topology** — one client IP; keep keying by userId. Never per-IP.
- **`noRollbackFor` on `AuthService.login`** — leave it alone while touching
  `JwtTokenService` callers.
- **Frontend coordination (Step 6 only)** — `user.role` disappears from the profile
  payload; `feat/api-impl` must gate by `/users/me/permissions` in the same deploy.
- Ports 5433/6380 locally; Docker Desktop must be running for the test suite
  (Testcontainers) — 27 phantom failures on 2026-07-13 were exactly that.
- Commit style: conventional commits, no AI attribution.

---

## 4. Commit sequence

0. `refactor(platform): apply v2 permission catalog renames`
1. `feat(platform): create ROOT scope during catalog sync`
2. `feat(platform): bootstrap super admin from configuration`
3. `feat(platform): enable IAM sync and permission authorities by default`
4. `refactor(admin): enforce admin endpoints with permission keys`
5. `feat(admin): add listing moderation endpoints`
6. `refactor(user): remove legacy role enum, column and JWT claim`
7. `docs: update API reference for enabled IAM`
