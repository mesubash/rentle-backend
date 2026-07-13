# Rentle Frontend — IAM Migration & Admin Console Plan

**Status:** implementation instructions for an AI agent (or developer).
**Workspace:** `/Users/isubash/Developer/rentle/rentle-frontend`, branch **`feat/api-impl`**
(already checked out — verify with `git branch --show-current` before touching anything).
**Backend:** `/Users/isubash/Developer/rentle/rentle-backend` (sibling directory `../rentle-backend`) —
IAM Iterations 1+2 are fully implemented, enabled, and tested there. Read it as needed;
it is the source of truth for every endpoint and permission key named here.
**IAM reference implementation:** `/Users/isubash/Developer/spring-iam/web` — a complete
React admin console built on the same permission model. **Whenever you are unsure how a
permission-driven UI mechanism should work (`can()`, gated nav, role editor), open that
project and copy the pattern, not the stack.**

---

## 0. Why this is a *migration*, not just a feature

The backend removed the `role` field entirely (`users.role` column, `UserRole` enum,
`role` JWT claim — all gone). Authorization is now **permission keys** resolved
per-user: `GET /users/me/permissions` → `string[]` like
`["kyc.submission.read", "platform.role.manage", ...]`.

The frontend still gates on `user.role === "ADMIN"` in four places — **which no longer
exists in the API response**. Until Step 2 lands, admin gating is silently broken.
This plan replaces the role model with a `can()` system where **every button, field,
nav item, and route that triggers a permission-gated API call is wrapped in the same
permission key the backend enforces** — mirroring how `spring-iam/web` does it.

End state:
1. A reusable IAM template: `PermissionsProvider` + `useCan()` + `<Can>` — the standard
   through which **every future component** decides whether to render.
2. The existing `/admin` section upgraded into a full, proper console (shadcn-based),
   including the new role/assignment management screens the backend now serves.
3. Zero references to `role` anywhere in the frontend.

---

## 1. Required reading (in order)

**Backend (`../rentle-backend`):**

1. `docs/08_permission_catalog.md` — the permission keys and what each gates. The
   frontend must use these exact strings; never invent one.
2. `docs/06_rentle_api_reference.md` — full API surface + envelope.
3. `src/main/java/com/rentle/domain/platform/catalog/PermissionKeys.java` — the
   canonical key constants; your TS constants file mirrors this 1:1.
4. `src/main/java/com/rentle/domain/admin/controller/AdminController.java` and
   `src/main/java/com/rentle/domain/platform/controller/*.java` — every admin/platform
   endpoint with its `@PreAuthorize` key. The UI gate for an element is **always the
   same key** the endpoint declares.

**spring-iam reference (`/Users/isubash/Developer/spring-iam/web`):** read these five
before building the equivalent pieces; consult again on any confusion:

5. `src/context/AuthzContext.tsx` — permission context, `can()/canAny()` exposure.
6. `src/components/iam/Can.tsx` + `PermissionGuardedPage.tsx` — declarative gating.
7. `src/components/iam/AppLayout.tsx` — nav items carrying a `permission` key,
   filtered before render.
8. `src/routes/_authenticated.admin.roles.tsx` — the role editor screen (list +
   permission checkboxes grouped by domain + save).
9. `src/api/resources.ts` — how endpoint wrappers are organized per resource.

Copy **patterns** from spring-iam, never stack: it is Vite + TanStack Router + Bun;
Rentle is Next 16 App Router + BFF proxy + plain `apiRequest`.

**Frontend (the workspace itself):** `src/lib/api/client.ts` (envelope + `ApiError`),
`src/lib/api/shared.ts`, `src/components/auth-provider.tsx`, `src/components/admin-shell.tsx`,
`src/app/api/rentle/[...path]/route.ts` (BFF proxy — all backend calls go through it),
`src/app/globals.css` (design tokens).

---

## 2. Current state (verified facts — trust these, re-verify only if diverged)

| Concern | State | Where |
|---|---|---|
| Stack | Next 16.2 App Router, React 19, TypeScript, **no Tailwind, no shadcn**, no query lib, lucide-react icons | `package.json` |
| API access | Everything through same-origin BFF `/api/rentle/*`; httpOnly cookies `rentle_access_token`/`rentle_refresh_token`; refresh handled inside the proxy; client never calls `:8080` directly | `src/app/api/rentle/[...path]/route.ts`, `src/lib/api/client.ts` |
| Envelope | `{data, error, timestamp}`; `apiRequest<T>` unwraps, throws `ApiError(message, status)`; `PageResponse<T>` for lists | `src/lib/api/shared.ts` |
| Auth state | Client-only `AuthProvider` (`useAuth()` → `{user, loading, setUser, reload, logout}`), fetches `usersApi.me()` on mount | `src/components/auth-provider.tsx` |
| **Broken role checks** | `user.role === "ADMIN"` at `admin-shell.tsx:27,34`, `site-header.tsx:32`, `auth-form.tsx:46`, `notifications-view.tsx:18`; `role` typed in `UserProfile` (`src/lib/api/users.ts`) — **backend no longer sends it** | grep `role ===` |
| Existing admin | `/admin` fully built: dashboard, verifications (KYC queue + workspace), users (suspend), listings, bookings, profile; `AdminShell` sidebar; `.admin-*` CSS classes | `src/app/admin/**`, `src/components/admin-*.tsx` |
| Route guard | `src/proxy.ts` (Next 16 middleware) — cookie-presence only. That stays; real enforcement is backend 403s + client gating | `src/proxy.ts` |
| Design tokens | CSS custom props: `--paper --pine --marigold --brick --ink --stone --pine-wash --marigold-wash --brick-wash --border --radius:7px`; fonts Fraunces (`--font-display`) + Instrument Sans (`--font-body`) | `src/app/globals.css` |

Backend endpoints the console consumes (all through the proxy, so client paths are
relative like `apiRequest("/platform/roles")`):

| Endpoint | Returns | Gated by |
|---|---|---|
| `GET /users/me/permissions` | `string[]` sorted keys | authenticated |
| `GET /platform/permissions?domain=` | `[{id,key,domain,resource,action,description,deprecated}]` | `platform.permission.read` |
| `GET /platform/roles` · `/platform/roles/{id}` | `{id,name,displayName,description,systemRole,permissionKeys[]}` | `platform.role.read` |
| `POST /platform/roles` · `PUT /platform/roles/{id}` · `DELETE` | same shape; body `{name?, displayName, description, permissionKeys[]}` | `platform.role.manage` |
| `GET /platform/assignments?userId=&roleId=` | `[{id,userId,email,fullName,roleId,roleName,scopeId,scopeName,grantedBy,createdAt}]` | `platform.assignment.read` |
| `POST /platform/assignments` `{userId, roleId}` · `DELETE /platform/assignments/{id}` | — | `platform.assignment.manage` |
| `GET /platform/users/lookup?email=` | `{id,email,fullName,status}` | `identity.user.read` |
| `GET /admin/users` · suspend/unsuspend | existing | `identity.user.read` / `identity.user.suspend` |
| `GET /admin/kyc` · detail · citizenship image · verify · reject-kyc | existing | `kyc.submission.read/approve/reject` |
| `GET /admin/listings` · `PUT /admin/listings/{id}/deactivate` · `/remove` | existing + new moderation | `listing.listing.read` / `listing.listing.moderate` |
| `GET /admin/bookings` | existing | `booking.booking.read` |

Server guard rails you must surface, not fight: system roles can't be deleted;
SUPER_ADMIN's permission set can't be edited; the last live SUPER_ADMIN assignment
can't be revoked (including self). Expect 400s with a clear `error` string — show it.

---

## 3. The IAM template (Step 1–2) — the part every future component uses

### 3.1 Permission constants — `src/lib/iam/permission-keys.ts`

Mirror the backend's `PermissionKeys.java` exactly, as `as const`:

```ts
export const P = {
  PLATFORM_ROLE_READ: "platform.role.read",
  PLATFORM_ROLE_MANAGE: "platform.role.manage",
  PLATFORM_PERMISSION_READ: "platform.permission.read",
  PLATFORM_PERMISSION_MANAGE: "platform.permission.manage",
  PLATFORM_ASSIGNMENT_READ: "platform.assignment.read",
  PLATFORM_ASSIGNMENT_MANAGE: "platform.assignment.manage",
  PLATFORM_SCOPE_READ: "platform.scope.read",
  PLATFORM_SCOPE_MANAGE: "platform.scope.manage",
  IDENTITY_USER_READ: "identity.user.read",
  IDENTITY_USER_SUSPEND: "identity.user.suspend",
  KYC_SUBMISSION_READ: "kyc.submission.read",
  KYC_SUBMISSION_APPROVE: "kyc.submission.approve",
  KYC_SUBMISSION_REJECT: "kyc.submission.reject",
  LISTING_LISTING_READ: "listing.listing.read",
  LISTING_LISTING_MODERATE: "listing.listing.moderate",
  LISTING_CATEGORY_MANAGE: "listing.category.manage",
  BOOKING_BOOKING_READ: "booking.booking.read",
} as const;
export type PermissionKey = (typeof P)[keyof typeof P];
```

Rule for the whole codebase, forever: **UI code never contains a raw permission
string** — always `P.X`. When the backend adds a domain, this file gets the new keys
in the same commit as the first UI using them.

### 3.2 API module — `src/lib/api/platform.ts`

Same style as the existing `admin.ts`: a `platformApi` object with typed functions
for every `/platform/*` endpoint plus `myPermissions(): Promise<string[]>`
(`GET /users/me/permissions`). Types for `RoleResponse`, `AssignmentResponse`,
`PermissionResponse`, `UserLookupResponse` per the table in §2. Add the two new
moderation calls (`deactivateListing`, `removeListing`) to the existing `admin.ts`.

### 3.3 `PermissionsProvider` — `src/components/permissions-provider.tsx`

Model on `spring-iam/web/src/context/AuthzContext.tsx`, adapted to this app's
client-context pattern:

- `"use client"` provider mounted in `src/app/layout.tsx` **inside** `AuthProvider`
  (it depends on the user).
- On `user` change (login/logout/reload): user present → fetch
  `platformApi.myPermissions()`, store as a `Set<string>`; user null → empty set.
  Expose `{ permissions, can, canAny, ready }`:

```ts
can(key: PermissionKey): boolean          // set.has(key)
canAny(...keys: PermissionKey[]): boolean
ready: boolean   // false while the fetch is in flight — gate admin UI on it to avoid flicker
```

- Failure tolerance: if the fetch fails (expired session mid-flight), treat as empty
  set — UI hides, server still enforces. Never crash the shell on this call.
- Wire `AuthProvider.reload()` so permissions refetch together with the profile
  (one place: the provider reacts to the `user` object identity anyway).

### 3.4 `useCan()` + `<Can>` — `src/components/can.tsx`

Model on `spring-iam/web/src/components/iam/Can.tsx`:

```tsx
const { can, canAny, ready } = usePermissions();   // context hook
useCan(key)                                        // convenience: boolean

<Can perm={P.KYC_SUBMISSION_APPROVE}>
  <button className="button" onClick={approve}>Approve</button>
</Can>

<Can any={[P.PLATFORM_ROLE_READ, P.PLATFORM_ASSIGNMENT_READ]} fallback={null}>…</Can>
```

Renders `children` when allowed, `fallback` (default `null`) otherwise. Also export
`PermissionGuardedPage({ perm, children })` — full-page variant that renders a
paper-toned "You don't have access to this section" card (with a link home) when
denied, used at the top of every admin page component.

**The template rule (put this in the frontend's CLAUDE.md too):** any element whose
click/submit hits a permission-gated endpoint is wrapped in `<Can>` with the *same*
`P.` key the backend controller declares. Nav items declare their key. Whole pages
declare theirs via `PermissionGuardedPage`. No exceptions, no role checks, ever.

### 3.5 Kill the role model (same commit)

- `src/lib/api/users.ts`: delete `role` from `UserProfile`.
- `admin-shell.tsx`: replace `isAdmin` with
  `canAny(...ALL_ADMIN_ENTRY_KEYS)` (define `ADMIN_ENTRY_KEYS` in `lib/iam/` — the
  read keys: `IDENTITY_USER_READ, KYC_SUBMISSION_READ, LISTING_LISTING_READ,
  BOOKING_BOOKING_READ, PLATFORM_ROLE_READ, PLATFORM_ASSIGNMENT_READ`). Gate on
  `ready` first (skeleton while loading).
- `site-header.tsx`: admin chrome shown when `canAny(...ADMIN_ENTRY_KEYS)`.
- `auth-form.tsx`: post-login redirect to `/admin` when `canAny(...)` (after
  permissions load — redirect from a `useEffect` watching `ready`).
- `notifications-view.tsx`: hide KYC nudges when user holds any admin entry key
  (staff accounts don't do marketplace KYC).
- `grep -rn '\brole\b' src/` afterwards: only CSS/aria uses may remain.

`src/proxy.ts` stays cookie-presence-only — it cannot read permissions (httpOnly
opaque token) and doesn't need to: page-level `PermissionGuardedPage` + backend 403s
are the enforcement. Do not try to decode the JWT in middleware.

---

## 4. shadcn admin section (Step 3) — scoped install, do not break the marketplace

Decision (owner's): the admin console uses **shadcn/ui** for proper dashboard
components (tables, dialogs, selects, command palette). The marketplace keeps its
hand-written CSS. These must coexist:

1. Install Tailwind v4 (CSS-first) + shadcn/ui per current shadcn Next.js docs.
2. **Do not let preflight touch the marketplace.** Import Tailwind in a dedicated
   `src/app/admin/admin.css` loaded only by `src/app/admin/layout.tsx` — not in
   `globals.css`. With Tailwind v4 use `@import "tailwindcss"` there and scope
   resets: wrap all admin pages in a `.admin-scope` root div (in the admin layout)
   and confine shadcn CSS variables to it.
3. Map shadcn theme tokens to Rentle tokens on `.admin-scope`:
   `--background: var(--paper); --foreground: var(--ink); --primary: var(--pine);
   --destructive: var(--brick); --accent: var(--marigold-wash); --radius: 7px;
   --border: var(--border);` fonts stay Fraunces/Instrument Sans. Result: shadcn
   components, Rentle skin — one brand, two densities.
4. Generate components into `src/components/ui/` (shadcn default). Marketplace code
   must never import from `components/ui/` — lint-comment the rule at the top of the
   folder's index if needed.
5. **Verify the marketplace after install**: run the app, click through explore /
   listing detail / bookings — if global styles shifted, the Tailwind import leaked;
   fix the scoping before proceeding.
6. Migrate the existing admin pages' chrome to shadcn incrementally as you touch
   them in Step 4/5 (`.admin-table` → shadcn `Table`, native selects → `Select`,
   confirm flows → `AlertDialog`, toasts stay app-wide). Don't do a big-bang restyle
   commit; restyle per page you're already editing.

If Tailwind-v4-alongside-legacy-CSS fights back on some detail, check how
`spring-iam/web` composes its shadcn theme (`web/src/index.css`, `components.json`)
— same token-mapping idea.

---

## 5. Console build-out (Steps 4–6)

### Step 4 — Re-gate the existing admin section per-element

`AdminShell` sidebar: each nav item declares its key and is filtered like
`spring-iam/web AppLayout.tsx`:

| Nav item | Key |
|---|---|
| Dashboard | any admin entry key |
| Verifications | `P.KYC_SUBMISSION_READ` |
| Users | `P.IDENTITY_USER_READ` |
| Listings | `P.LISTING_LISTING_READ` |
| Bookings | `P.BOOKING_BOOKING_READ` |
| **Roles** (new) | `P.PLATFORM_ROLE_READ` |
| **Staff** (new) | `P.PLATFORM_ASSIGNMENT_READ` |

Inside pages, per-element gating (the same key the endpoint declares):

- Verification workspace: Approve button → `KYC_SUBMISSION_APPROVE`; Reject →
  `KYC_SUBMISSION_REJECT`; a reviewer with only `read` sees the submission and
  documents but no action buttons.
- Users view: Suspend/Unsuspend buttons → `IDENTITY_USER_SUSPEND`; the user-roles
  side panel (new, see Step 5) → assignment keys.
- Listings view: add Deactivate / Remove buttons (with reason `AlertDialog`) →
  `LISTING_LISTING_MODERATE`, calling the new moderation endpoints.
- Dashboard cards render only the stats whose list-key the user holds.

Every admin `page.tsx`'s view component starts with
`<PermissionGuardedPage perm={...}>`.

### Step 5 — New pages (model on `spring-iam/web` screens)

**`/admin/roles`** (`P.PLATFORM_ROLE_READ`; mutations `P.PLATFORM_ROLE_MANAGE`) —
model: `_authenticated.admin.roles.tsx`:

- Left: role list (name, display name, `system` badge, permission count).
- Right/detail: role editor — display name + description fields, and the permission
  matrix: checkboxes **grouped by domain** (group order: platform, identity, kyc,
  listing, booking), each row showing `key` + its description from
  `GET /platform/permissions`. SUPER_ADMIN's matrix renders disabled with a note
  ("system role — always holds every permission"). Save = `PUT` with the full
  selected key set. Create-role dialog (name immutable after create — say so in the
  form). Delete guarded by `AlertDialog`; surface server refusals (system role /
  live assignments) verbatim from `ApiError.message`.

**`/admin/staff`** (`P.PLATFORM_ASSIGNMENT_READ`; mutations
`P.PLATFORM_ASSIGNMENT_MANAGE`) — assignments table: person (name + email), role,
granted date, revoke button (AlertDialog; server blocks the last SUPER_ADMIN —
surface its error). "Grant role" dialog: email lookup via
`GET /platform/users/lookup?email=` (shows the matched account before confirming) +
role select from `GET /platform/roles`. After any mutation, refetch the list;
remind the operator changes apply on the target's next request.

**Users page addition:** per-user drawer/panel showing that user's live assignments
(`GET /platform/assignments?userId=`) with grant/revoke inline — same components as
`/admin/staff`, filtered.

### Step 6 — Polish + docs

- Loading: `ready === false` → skeletons in admin shell, never a flash of "no
  access". 403 `ApiError` (race between UI and a just-revoked key) → toast the
  server message and refetch permissions.
- Empty states in Rentle voice (plain, calm, one action).
- Update the frontend `CLAUDE.md` (or create one) with the template rule from §3.4
  and the `components/ui/` import boundary from §4.
- `README`/docs note: staff bootstrap = backend `RENTLE_IAM_SUPER_ADMIN_EMAIL` env
  (see `../rentle-backend/docs/10_iam_enablement_plan.md`).

---

## 6. Step order & commits (each compiles, app runs)

1. `feat(iam): add permission constants, platform api module and permissions provider`
2. `refactor(iam): replace role checks with can() gating` ← app un-breaks here
3. `feat(admin): add scoped tailwind + shadcn for the admin section`
4. `refactor(admin): gate admin nav and actions by permission keys`
5. `feat(admin): add roles management page`
6. `feat(admin): add staff assignments page and user roles panel`
7. `feat(admin): add listing moderation actions`
8. `docs: record iam frontend conventions`

---

## 7. Verification (manual smoke — no test harness in this repo)

Backend up (`../rentle-backend`: `docker compose up -d && ./gradlew bootRun`,
`RENTLE_IAM_SUPER_ADMIN_EMAIL=admin@gmail.com`; dev super-admin account
`admin@gmail.com` / `Admin@123` is seeded by migration V014). Frontend:
`npm run dev`.

1. Login as super-admin → header shows admin chrome; `/admin` fully navigable;
   `/users/me/permissions` (network tab) returns all 17 keys.
2. Create role `KYC_HELPER` with only the three `kyc.*` keys; register a fresh user;
   grant them the role from `/admin/staff`.
3. Login as that user → admin chrome appears; sidebar shows **only** Verifications;
   KYC workspace shows approve/reject; Users/Roles/Staff absent; deep-linking
   `/admin/users` shows the no-access page; the API 403s (envelope intact).
4. Revoke the role → on next navigation the admin chrome disappears.
5. As super-admin: deactivate + remove a listing with a reason; verify status flips
   in `/admin/listings` and the listing vanishes from public explore.
6. Marketplace regression: explore, listing detail, booking flow, messages, profile —
   pixel-identical to pre-migration (Tailwind must not have leaked).
7. Try revoking your own SUPER_ADMIN assignment → server refuses, UI shows the
   message.

---

## 8. Gotchas (this workspace specifically)

- **All API calls go through `/api/rentle/*`** (BFF). Never call `:8080` directly —
  cookies and refresh live in the proxy. New endpoints need zero proxy changes (it
  forwards any path).
- **Turbopack:** never delete `.next` while `npm run dev` runs — kill node first,
  or the cache corrupts.
- **Client-only auth:** there is no SSR user; providers are client components.
  Don't attempt server-component permission checks this iteration.
- **Duplicate auth routes** exist (`/login` + `/auth/login`); `auth-form.tsx` is
  shared — change redirect logic once, verify both entries.
- **Envelope always:** `ApiError.message` is the backend's `error` string — show it
  verbatim in toasts; don't invent copy for server refusals.
- **Two design systems, one boundary:** shadcn/Tailwind exists only under
  `.admin-scope`; `components/ui/` is admin-only. Marketplace visual regressions =
  scoping leak = fix before continuing.
- Backend repo is the styleguide for keys: if a key you need isn't in
  `PermissionKeys.java`, stop — the backend must add it first (see
  `../rentle-backend/docs/08_permission_catalog.md` §2.4 procedure). Never ship a
  UI-only permission string.
- Commit style: conventional commits, no AI attribution.
