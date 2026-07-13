# Rentle IAM Migration Plan — Backend, Iteration 1 (Code-Only)

**Status:** Iteration 2 implemented.
Read this whole document before writing any code.

**What this iteration delivers:** the complete IAM machinery **built into the
codebase but dormant** — empty tables, entities, the permission catalog as code,
the resolver, the management API — with **zero behavior change**. No data is
seeded, no user is granted anything, no existing endpoint changes its access rule.
Rentle runs after this iteration exactly as it runs today.

**What this iteration deliberately does NOT do** (that is Iteration 2, a separate
future task): sync the catalog into the DB, create the first SUPER_ADMIN
assignment, flip `hasRole('ADMIN')` to permission keys, or drop the legacy
`users.role` column. Do not do any of those now, even though this document
describes where they will happen.

---

## 0. Required reading (in this order)

Paths are absolute on this machine.

**In this repo (`/Users/isubash/Developer/rentle/rentle-backend`):**

1. `docs/08_permission_catalog.md` — **the contract.** Permission keys
   (`domain.resource.action`, 3 segments, lowercase), seed-role bundles, endpoint→
   permission map. This plan implements that catalog verbatim — do not invent
   different keys or roles.
2. `docs/06_rentle_api_reference.md` — current API surface and the
   `{data, error, timestamp}` envelope every response must keep.
3. `docs/07_provider_verification_plan.md` — a later phase that will add
   `provider.*` permissions; nothing here may block it.

**In the reference project (`/Users/isubash/Developer/spring-iam`):**

Rentle adopts a *subset* of this system — copy patterns and naming, not features:

4. `docs/AUTHZ_DESIGN.md` — the model and reasoning.
5. `src/main/resources/db/migration/V1__core.sql` — table shapes for
   `permissions`, `roles`, `role_permissions`, `scopes`, `assignments`.
6. `src/main/java/**/authz/entity/{Permission,Role,RolePermission,Scope,Assignment}.java`
   — entity conventions.
7. `src/main/java/**/authz/controller/{RoleController,PermissionController,AssignmentController}.java`
   and `AuthzMeController` — management-API shapes to mirror.

Do **not** copy from spring-iam: role hierarchy, deny rules, ABAC policies,
resource grants, subject groups, service registry, break-glass, ltree/closure
tables, the `/authorize` PDP, partitioned audit. None of that gets built.

---

## 1. Current state of rentle-backend (context — change nothing here in this iteration)

| Concern | Today | File |
|---|---|---|
| Role model | `enum UserRole { USER, ADMIN }` column on `users` | `domain/user/model/UserRole.java` |
| Enforcement | `hasRole("ADMIN")` URL rule + class-level `@PreAuthorize("hasRole('ADMIN')")` | `config/SecurityConfig.java`, `domain/admin/controller/AdminController.java` |
| JWT | RS256 resource server; claims `sub`, `role`, `status`, `jti`; authorities from the `role` claim via `JwtAuthenticationConverter` | `config/SecurityConfig.java`, `shared/security/JwtTokenService.java` |
| Revocation | `bl:{jti}` + `susp:{userId}` Redis checks inside the `JwtDecoder`; `TokenRevocationService` | `shared/security/` |
| Admin surface | users, suspend/unsuspend, KYC queue/approve/reject/documents, listings, bookings | `domain/admin/` |
| Envelope | `{data, error, timestamp}` everywhere incl. security errors (`JsonErrorWriter`) | `shared/api/`, `shared/security/` |
| Migrations | Flyway V001–V011, append-only, `ddl-auto: validate` | `src/main/resources/db/migration/` |

All of the above keeps working untouched. This iteration only **adds**.

---

## 2. Target model (subset of spring-iam)

Five tables — created empty:

| Table | Purpose |
|---|---|
| `permissions` | Registry of keys `domain.resource.action`. Immutable once present; deprecate, never rename. |
| `roles` | Named bundles. `is_system_role` marks rows the API must refuse to delete. |
| `role_permissions` | role ↔ permission join. |
| `scopes` | Hierarchy for the future org phase. Plain `parent_id` self-reference — no ltree. Stays empty this iteration (ROOT row is created during Iteration 2 enablement). |
| `assignments` | Grant tuple `subject × role × scope` with `granted_by`, soft revoke (`revoked_at`/`revoked_by`), history preserved. |

Decided conventions (from `docs/08` — restated, not up for debate):

- Access-system domain name: **`platform`** (matches spring-iam).
- Plain `USER` holds zero permission keys; marketplace actions stay gated by
  ownership + `VERIFIED` status, never by permissions.
- Permissions are resolved **per-request from the DB with a Redis cache**
  (`perms:{userId}`, explicit invalidation) — never baked into the JWT.

---

## 3. Build instructions — six steps, in order

Each step compiles, passes `./gradlew test`, and is one commit. Suggested commit
messages in §6. Follow existing codebase conventions exactly: constructor
injection, records for DTOs, `ApiResponse.ok(...)` envelope, repository/service/
controller layering identical to `domain/user` or `domain/listing`.

### Step 1 — Flyway `V012__iam_tables.sql` (tables only, **no data**)

Append-only; never touch V001–V011. No INSERT statements anywhere in this file.

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

CREATE TRIGGER trg_roles_updated_at
BEFORE UPDATE ON roles FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at();

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
CREATE UNIQUE INDEX uq_assignments_live
    ON assignments(subject_id, role_id, scope_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_assignments_subject ON assignments(subject_id) WHERE revoked_at IS NULL;
```

**Done when:** app boots (`ddl-auto: validate` passes once Step 2's entities exist;
until then this migration alone must not break boot — Flyway creates tables,
validate only checks mapped entities).

### Step 2 — `domain/platform` module: entities + repositories

New package `com.rentle.domain.platform` with the standard layout:

```text
domain/platform/
├── model/        Permission, Role, RolePermission, Scope, Assignment, ScopeType (enum: ROOT, ORG)
├── repository/   PermissionRepository, RoleRepository, RolePermissionRepository,
│                 ScopeRepository, AssignmentRepository
├── catalog/      (Step 3)
├── service/      (Steps 4–5)
├── dto/          (Step 5)
└── controller/   (Step 5)
```

Entity rules (violating these fails boot under `ddl-auto: validate`):

- `Role` extends **`AuditableEntity`** (its table has `updated_at`).
- `Permission`, `Scope`, `Assignment` extend **`BaseEntity`** (created_at only).
- `RolePermission`: explicit join entity with a composite `@EmbeddedId`
  (`roleId`, `permissionId`) — mirror spring-iam's `RolePermission.java`; do not
  use `@ManyToMany`.
- Lombok `@Getter @Setter @NoArgsConstructor`, explicit `@Column(name=...)` for
  every snake_case column, `FetchType.LAZY` on all associations — exactly like
  `domain/user/model/User.java`.

Key repository methods:

```java
// AssignmentRepository — the resolver's single query
@Query("""
    SELECT p.key FROM Assignment a
    JOIN RolePermission rp ON rp.id.roleId = a.role.id
    JOIN Permission p ON p.id = rp.id.permissionId
    WHERE a.subject.id = :userId AND a.revokedAt IS NULL AND p.isDeprecated = false
""")
Set<String> findLivePermissionKeys(@Param("userId") UUID userId);

List<Assignment> findBySubjectIdAndRevokedAtIsNull(UUID subjectId);
List<Assignment> findByRoleIdAndRevokedAtIsNull(UUID roleId);
boolean existsByRoleIdAndRevokedAtIsNull(UUID roleId);
```

**Done when:** `./gradlew test` green (boot validates new entities against V012).

### Step 3 — Catalog as code (modular, config-style — **no DB writes**)

The permission catalog and role bundles are **declared in code**, one file per
domain, so adding a domain later (provider, org) is a new file, not an edit spray.
Nothing in this step touches the database.

```text
domain/platform/catalog/
├── PermissionDefinition.java   // record: key, domain, resource, action, description
├── PermissionCatalog.java      // interface: List<PermissionDefinition> permissions()
├── PlatformPermissions.java    // implements PermissionCatalog — platform.role.read, platform.role.manage,
│                               //   platform.permission.read, platform.permission.manage,
│                               //   platform.assignment.read, platform.assignment.manage,
│                               //   platform.scope.read, platform.scope.manage
├── IdentityPermissions.java    // identity.user.read, identity.user.suspend
├── KycPermissions.java         // kyc.submission.read / approve / reject
├── ListingPermissions.java     // listing.listing.read, listing.listing.moderate, listing.category.manage
├── BookingPermissions.java     // booking.booking.read
├── PermissionKeys.java         // public static final String constants for every key —
│                               //   the ONLY strings ever used in @PreAuthorize later
└── RoleSeeds.java              // static definitions of the seed bundles from docs/08 §4:
                                //   SUPER_ADMIN (system, all keys), ADMIN, KYC_REVIEWER,
                                //   SUPPORT, USER (system, empty)
```

Each `*Permissions` class is a `@Component` implementing `PermissionCatalog`;
Spring collects them via `List<PermissionCatalog>` injection. `RoleSeeds` maps
role name → (displayName, isSystemRole, set of `PermissionKeys` constants).

Also add `IamCatalogSynchronizer` in `service/`:

- Injects `List<PermissionCatalog>` + `RoleSeeds` + repositories.
- Method `synchronize()`: idempotent upsert — insert missing permissions, insert
  missing roles, reconcile each **system** role's permission set to the code
  definition; never deletes, never touches assignments, never modifies non-system
  roles (those belong to admins at runtime).
- Runs at startup **only** when `rentle.iam.sync-catalog=true`.
  **Default `false`** — add both `rentle.iam.enabled: false` and
  `rentle.iam.sync-catalog: false` under the existing `rentle:` block in
  `application.yml` and to `RentleProperties` (nested record or flat fields —
  follow the existing style). In this iteration the flags stay false; flipping
  them is an Iteration 2 action.

**Done when:** unit test asserts every catalog key matches
`^[a-z_]+\.[a-z_]+\.[a-z_]+$`, keys are unique across all catalog components, and
every key referenced by `RoleSeeds` exists in the catalog.

### Step 4 — `PermissionResolverService` + Redis cache (code complete, unused)

`domain/platform/service/PermissionResolverService.java`:

```text
Set<String> permissionKeysFor(UUID userId)
  1. Redis GET perms:{userId} → hit: split CSV, return.
  2. Miss: assignmentRepository.findLivePermissionKeys(userId)
  3. Redis SET perms:{userId} = String.join(",", keys), TTL 5 minutes.
     (Empty set cached too — as "" — most requests are plain users with no perms.)

void invalidate(UUID userId)      → DEL perms:{userId}
void invalidateRole(UUID roleId)  → DEL perms:{u} for each live assignee (findByRoleIdAndRevokedAtIsNull)
```

Use the existing `StringRedisTemplate` (same one as `bl:*`/`susp:*`/`otp:*`).
CSV in a plain string key — no JSON serializer config needed.

Wire authorities **behind the flag**: in `SecurityConfig`, extend the existing
`jwtAuthenticationConverter()` so that when `rentle.iam.enabled=true` it appends
one `SimpleGrantedAuthority` per key from `permissionKeysFor(sub)` alongside the
legacy `ROLE_<role>` authority; when `false` (default) behavior is byte-for-byte
today's. Constructor-inject `PermissionResolverService` and `RentleProperties`
into `SecurityConfig` — that is safe; what is NOT safe is constructing this inside
a `WebMvcConfigurer` (see §7 gotchas).

**Done when:** unit tests cover cache miss → DB → cache hit, empty-set caching,
`invalidate`, `invalidateRole`; an integration test proves that with the flag
**off** (default) authorities are unchanged from today.

### Step 5 — Platform management API (guarded, effectively inert)

Mirror spring-iam's `RoleController` / `AssignmentController` / `AuthzMeController`
shapes, scaled down, under `/api/v1`, standard envelope. All endpoints guarded by
`@PreAuthorize("hasAuthority('<key>')")` using `PermissionKeys` constants — since
no one holds any authority while the flag is off and no data exists, **every
endpoint 403s for everyone**: shipped, but inert. That is intended.

| Endpoint | Permission constant | Behavior |
|---|---|---|
| `GET /platform/permissions?domain=` | `PLATFORM_PERMISSION_READ` | list registry |
| `GET /platform/roles` · `GET /platform/roles/{id}` | `PLATFORM_ROLE_READ` | roles incl. their permission keys |
| `POST /platform/roles` | `PLATFORM_ROLE_MANAGE` | `{name, displayName, description, permissionKeys[]}` — validate every key exists and is not deprecated |
| `PUT /platform/roles/{id}` | `PLATFORM_ROLE_MANAGE` | update display/description, **replace** permission set; refuse permission-set edits on `SUPER_ADMIN`; call `invalidateRole` |
| `DELETE /platform/roles/{id}` | `PLATFORM_ROLE_MANAGE` | refuse `is_system_role`; refuse roles with live assignments |
| `GET /platform/assignments?userId=&roleId=` | `PLATFORM_ASSIGNMENT_READ` | live only, joined with subject email/fullName |
| `POST /platform/assignments` | `PLATFORM_ASSIGNMENT_MANAGE` | `{userId, roleId}`; scope = the ROOT scope row (404 with clear message if scopes table is still empty — enablement not done); `granted_by` = current user; `invalidate(userId)` |
| `DELETE /platform/assignments/{id}` | `PLATFORM_ASSIGNMENT_MANAGE` | soft revoke (`revoked_at`, `revoked_by`); `invalidate(subjectId)` |
| `GET /platform/users/lookup?email=` | `IDENTITY_USER_READ` | assignment picker (id, email, fullName, status) |
| `GET /users/me/permissions` | authenticated only | the caller's keys from the resolver — returns `[]` for everyone today; the future frontend `can()` bootstrap. Add to `UserController`. |

Service-level guard rails (in `PlatformAdminService` or split
`RoleAdminService`/`AssignmentAdminService` — implementer's choice, keep it in the
platform module):

- Never allow revoking the **last live SUPER_ADMIN assignment in the system**, and
  never allow a user to revoke their own last SUPER_ADMIN assignment.
- Role `name` immutable after creation (display name editable).
- All mutations set `granted_by`/`revoked_by` from `SecurityUtils.currentUserId()`.

**Existing endpoints are NOT touched.** `AdminController` keeps
`@PreAuthorize("hasRole('ADMIN')")`; `SecurityConfig` keeps its `/admin/**` rule.
The enforcement flip is Iteration 2.

**Done when:** integration tests (Testcontainers, existing pattern in
`src/test/java/com/rentle/integration/` + `TestcontainersConfig`) build their own
fixture data **inside the test** (insert permissions/roles/scope/assignment via
repositories — remember: no seed exists), then verify: role CRUD round-trip,
assignment grant/revoke with cache invalidation, system-role deletion refused,
last-SUPER_ADMIN revocation refused, a user with zero assignments gets `[]` from
`/users/me/permissions`, and every `/platform/**` endpoint returns 403 for a
user without the matching key.

### Step 6 — Documentation touch-up

- Append a "Platform (IAM) — dormant" section to `docs/06_rentle_api_reference.md`
  listing the new endpoints and stating the flags are off.
- Update this file's status line to "Iteration 1 implemented".

---

## 4. Iteration 2 preview (DO NOT IMPLEMENT NOW — recorded so Iteration 1 leaves the right seams)

Listed only so the agent understands what the seams are for:

1. Flip `rentle.iam.sync-catalog=true` (catalog + system roles upsert into DB) and
   create the ROOT scope row (the synchronizer will own this too).
2. Grant the first SUPER_ADMIN assignment to the platform owner (one-off, decided
   then — via SQL or a bootstrap admin command).
3. Flip `rentle.iam.enabled=true` (permission authorities start flowing).
4. Migrate `AdminController`/`SecurityConfig` from `hasRole('ADMIN')` to the
   endpoint→permission map in `docs/08 §7`; add `listing.listing.moderate`
   endpoints.
5. Drop the `role` JWT-claim authority, migration `V0xx` drops `users.role`,
   delete `UserRole`.
6. Super-admin frontend console (separate document; patterns from
   `spring-iam/web`).

Iteration 1 must leave every one of these as a config flip or an isolated change —
if implementing a step forces edits across many Iteration-1 files, the modularity
requirement was violated.

---

## 5. Acceptance criteria (Iteration 1)

1. `./gradlew test` green; **all pre-existing tests untouched and passing**.
2. App boots with default config; `ddl-auto: validate` passes; V012 applied.
3. **Zero behavior change:** every existing endpoint responds exactly as before
   (same status codes, same envelope); admin surface still gated by
   `hasRole('ADMIN')`; JWT contents unchanged.
4. Both flags default `false`; grepping config confirms no environment enables them.
5. No migration or startup code inserts rows into the five new tables under
   default config.
6. `PermissionKeys` constants exist for every key in `docs/08 §3`
   (now-phase domains only); catalog unit test enforces format/uniqueness/
   role-bundle integrity.
7. New integration tests from Step 5 pass, creating all their own fixture data.
8. Committed in the §6 sequence, conventional messages, no AI attribution.

---

## 6. Commit sequence

1. `feat(platform): add IAM tables migration V012 (no data)`
2. `feat(platform): add platform module entities and repositories`
3. `feat(platform): define permission catalog and role seeds in code`
4. `feat(platform): add permission resolver with Redis cache behind iam flag`
5. `feat(platform): add role and assignment management API (dormant)`
6. `docs: record dormant platform endpoints in API reference`

---

## 7. Gotchas (learned in this codebase — respect these)

- **`ddl-auto: validate` is on.** Every entity column must match V012 exactly.
  Two base classes: `BaseEntity` (id + created_at) vs `AuditableEntity`
  (+ updated_at) — only `Role` gets `AuditableEntity` here.
- **Migrations are append-only.** V001–V011 are immutable; fixes = new version.
- **Never inject late beans (e.g. `ObjectMapper`) into objects constructed during
  `WebMvcConfigurer` setup** — context breaks. `JsonErrorWriter` exists for
  security-layer JSON; reuse it. Constructor-injecting services into
  `SecurityConfig` beans is fine.
- **BFF topology:** all traffic arrives from one IP. Never key caches/limits by
  client IP; key by userId. (`RateLimitInterceptor` shows the pattern.)
- **`AuthService.login` uses `@Transactional(noRollbackFor = UnauthorizedException.class)`**
  on purpose — don't "fix" it while wiring `SecurityConfig`.
- **Local ports:** Postgres `5433`, Redis `6380` (5432/6379 belong to the
  spring-iam containers). `docker compose up -d` in this repo starts the right ones.
- Redis key namespaces already in use: `bl:*`, `susp:*`, `refresh:*`, `otp:*`,
  `rl:*`. Add `perms:*` alongside via the same `StringRedisTemplate`.
- Tests: integration tests use `@SpringBootTest` +
  `@Import(TestcontainersConfig.class)`; no test may depend on seed data existing.
- Commit style: conventional commits; never mention AI in messages.
