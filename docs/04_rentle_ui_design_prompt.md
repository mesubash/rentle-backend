# Rentle — UI Design Prompt

> Paste everything below this line into Claude Design / Google Stitch as one prompt.
> Design one screen at a time in the order listed under "Screens", pasting this
> full context each session so the system stays consistent.

---

## 1. What you are designing

You are designing the **Rentle web application** — a desktop-browser product that is
fully responsive down to phone width. Phase 1 is web only (no native app); many users
will still open it on their phone's browser, so every screen must degrade gracefully,
but the primary canvas is the desktop web experience.

**Rentle** is a peer-to-peer marketplace for Nepal where people rent physical items
(cameras, tech gear, sarees, traditional costumes) and book local services (movers,
photographers, event help) from other verified people nearby. Think "Airbnb's trust
model applied to everyday things and skills" — built for Kathmandu Valley and Pokhara.

The core problem it solves: today these transactions happen through WhatsApp groups
and Facebook posts — no verification, no record of what was agreed, no recourse when
a camera comes back damaged or a mover doesn't show up. Rentle's entire value is
**trust between strangers**: citizenship-card verification, phone OTP, deposits,
dual-sided reviews unlocked only after a completed booking, and messaging that is
always scoped to a specific booking so everything stays on record.

**Who uses it:**

- **Renters/Clients** — ages 20–35, students and young professionals in Kathmandu and
  Pokhara. Mobile-first, comfortable with eSewa/Khalti digital wallets, active on
  Instagram. They need a camera for a wedding, a saree for Dashain, a mover for a
  house shift. They are wary: 60%+ of young Nepali consumers report trust issues with
  online platforms. The UI must constantly, quietly reassure.
- **Owners/Providers** — ages 22–45, individuals monetising an idle camera or offering
  photography/moving services. They want a professional presence and a portable
  reputation. For many this is their first "storefront".
- **Admins** — small internal team reviewing citizenship cards and watching activity.

**How a transaction works (this drives most screens):**

1. Renter finds a listing (search/browse by category and district) and requests dates.
2. Owner approves or rejects.
3. On approval, a message thread opens. Renter sends the deposit directly via
   eSewa/Khalti (outside the app — no in-app payment in Phase 1), then uploads a
   screenshot as proof.
4. Owner confirms deposit received → booking becomes **Active**.
5. After the rental/service, a party marks it **Complete**.
6. Both sides get a 30-day window to review each other. Ratings build the trust
   scores shown everywhere.

Booking states the UI must express clearly: `Requested → Approved → Deposit pending →
Active → Completed`, with `Cancelled` and `Declined` as exits. Every listing is either
a **Product** (priced per day, has condition/brand, rented with a deposit) or a
**Service** (priced per hour or flat, has service area and notice period, booked for
a date/time). One unified layout family must handle both gracefully.

---

## 2. Design direction

### Personality

Neighborly, capable, calm. This is a tool people use to hand a NPR 300,000 camera to
a stranger — the design should feel like a well-run local institution: warm but
precise, friendly but never cute. Not a Silicon Valley SaaS, not a crypto app, not a
toy. Reference feeling: a well-designed banking app crossed with a neighborhood
notice board. Every screen should answer the user's silent question: *"can I trust
this person, and what happens next?"*

### Color

Ground the palette in Nepal's physical world — brick, marigold, evergreen hills —
not in tech-industry defaults.

- **Paper** `#FAF7F2` — warm off-white app background. Never pure white, never grey-blue.
- **Ink** `#1F2421` — near-black with a green undertone for all primary text.
- **Pine** `#1E5748` — deep evergreen. Primary actions, active states, links, the logo.
- **Marigold** `#E8A13A` — warm accent from festival garlands. Ratings, highlights,
  "verified" warmth, small moments of celebration. Use sparingly — it should feel
  earned. When used as text on paper, darken to `#B07515` for WCAG AA contrast.
- **Brick** `#B4552D` — terracotta from Kathmandu's old city. Destructive/cancel
  actions, urgent notices, unread badges.
- **Stone** `#8A8578` — secondary text and placeholders. Borders use the warmer
  `#DDD6C8` / `#E7E1D4`.
- Status tints: soft 8–16% alpha washes of pine/marigold for chips and tags — e.g.
  Product tag `rgba(30,87,72,.09)`, Service tag `rgba(232,161,58,.16)`; each state
  must also differ by label and icon, never by color alone.

Solid colors only. **No gradients anywhere.** High contrast, generous warmth. Dominant
neutral (paper) + one confident primary (pine) + accents that appear only when they
mean something.

### Typography

- **Display / headings:** Fraunces (or a comparable warm high-contrast serif). This is
  the personality carrier — use it big and confident on screen titles, listing titles,
  empty states, and prices on detail pages. Weights 600–700, tight line-height,
  letter-spacing `-0.01em`.
- **Body / UI:** Instrument Sans (or comparable humanist grotesque), weights
  400/500/600/700. Never Inter, never a system-font-only stack.
- **Numbers:** tabular figures for all prices and dates. Prices formatted as
  `NPR 2,500 / day` — the currency and unit are always visible, never a bare number.
- Real typographic scale with strong jumps (e.g. 32/24/18/15/13), not a timid
  even ramp. Line-height generous in body text; tight in display.

### Layout & components

- **Web-first, mobile-responsive.** Design the desktop layout first at ~1280px
  (max content width ~1200px), then show how it adapts at 390px. Desktop navigation:
  slim top bar — logo, search, Explore, Bookings, Messages (badge), profile menu, and
  a persistent "List an item" button in pine. On phone widths the top bar collapses
  and a bottom tab bar takes over: Explore, Bookings, Messages, Profile.
- Signature masthead: solid pine top bar containing a paper-colored search field
  and the desktop nav; outlined category pills below (active pill = marigold fill,
  pine text); a marigold-tinted trust strip directly beneath ("Every owner is
  citizenship-verified…").
- One strong layout primitive, repeated: a flat card on paper background with a 1px
  warm border (`#E7E1D4`), small radius (6–7px), no drop shadows — hover state is a
  pine border. Density over airiness — listings should feel like a busy, healthy
  market, not a sparse portfolio. Photo placeholders are striped paper-tone blocks
  with small monospace labels, never grey boxes.
- **Listing card:** photo (4:3), title in serif, price with unit, district with a
  simple pin glyph, rating star in marigold with count, owner's verified badge. A
  small "Product"/"Service" tag distinguishes the two types.
- **Trust surfaces are first-class components, not footnotes:** verified badge
  (pine shield + "Verified" label), trust score, review count, "member since",
  deposit amount clearly stated before any request is sent.
- **Booking status timeline:** a horizontal step indicator showing the five states,
  used on every booking detail screen so both parties always know where they are and
  what happens next. Pair each state with the single action it unlocks ("Upload
  deposit proof", "Confirm deposit received", "Mark complete").
- Forms: one question group per screen section, plain labels above fields, inline
  validation in brick, never placeholder-as-label. The listing-creation flow is a
  short wizard (type → details → photos → price & deposit → review & publish).
- Empty states do real work: explain the trust flow, show a worked example, give one
  clear action. Serif headline + one sentence + one button.

### Imagery & iconography

- Photography only — real items, real rooms, real streets of Kathmandu/Pokhara.
  No 3D illustrations, no abstract blob characters, no stock "diverse team laughing
  at laptop".
- Icons: one consistent stroke set (e.g. Lucide/Phosphor at one weight), functional
  sizes only. No emoji in UI chrome, no sparkle/magic-wand icons anywhere.

### Voice

Plain, concrete, calm. "Your booking request was sent. Sunita usually responds within
a few hours." — not "🎉 Awesome! You're one step closer!". Write amounts, dates and
next steps explicitly. Keep sentences short so future Nepali translation stays clean.
Use real Nepali names, places, items and NPR prices in ALL mock content (Canon EOS R5,
Bhaktapur, daura suruwal, NPR 1,500/day, Dashain week) — never "John Doe", "Product
Name", or "Lorem ipsum".

---

### UX craft (non-negotiable quality bar)

- **Hierarchy before decoration.** Each screen has exactly one primary action, styled
  in pine; everything else is visibly secondary. A user should know the next step
  within two seconds of landing on any screen.
- **Readable by default:** body text ≥ 15px mobile / 16px desktop, line length
  45–75 characters, spacing on a 4px grid with an 8/16/24/32 rhythm. Serif display
  sizes tuned so long Nepali item titles wrap to two lines gracefully, never truncate
  mid-word.
- **Input ergonomics:** comfortable pointer targets on desktop with clear hover
  states; at phone widths all touch targets ≥ 44px with primary actions reachable in
  the bottom third. Destructive actions never adjacent to primary ones at any width.
- **Every action answers back.** Optimistic or explicit feedback for each tap:
  pressed states, button spinners on submit, toast/inline confirmation with what
  happened and what's next. Skeleton placeholders (paper-tone, no shimmer circus) for
  loading lists; never a blank screen or a lone full-page spinner.
- **Errors are recoverable instructions,** not blame: say what failed, keep the
  user's input, offer the retry. Date conflicts on booking show *which* dates clash
  and the nearest free range.
- **Momentum in forms:** auto-advance the OTP boxes, auto-format phone numbers,
  numeric keypad for numeric fields, sensible defaults (today+1 for start date,
  deposit prefilled from listing), progress indicator on the wizard, drafts survive
  back-navigation.
- **State clarity over motion.** Animation only where it explains change (status chip
  transition, sheet slide-in), 150–250ms, ease-out. Nothing bounces, nothing floats.
- **Accessibility floor:** WCAG AA contrast on all text (check marigold on paper —
  darken for text use), visible focus states, labels tied to inputs, status conveyed
  by icon + label + color, alt text patterns for listing photos.

## 3. Hard avoid-list

These are the recognizable fingerprints of AI-generated design. None may appear:

- Purple, violet, indigo, or any purple-to-blue gradient. No gradients at all.
- Inter / default system font stacks as the only typography.
- Glassmorphism, frosted panels, floating glowing orbs, mesh backgrounds.
- Uniform 16px-radius white cards floating on grey, with soft shadows everywhere.
- Oversized vague hero sections ("Rent the future", "Unlock possibilities").
- Colored 3–4px left-border accent strips on cards or alerts.
- Cyan-on-dark "dashboard" aesthetic; unnecessary dark mode as the default.
- Sparkles, magic wands, rockets, or emoji as design elements.
- Evenly-distributed timid pastel palettes where no color dominates.
- Centered-everything landing-page symmetry on every screen.

---

## 4. Screens to design (in order)

1. **Explore / Home** — search bar, category row (Cameras & Tech, Traditional
   Clothing, Moving & Transport, Event & Photography), district filter chips
   (Kathmandu, Lalitpur, Bhaktapur, Pokhara), listing grid, sort control.
2. **Search results** — active filters visible and removable, result count, same cards.
3. **Listing detail (Product)** — photo gallery, serif title, price + deposit block,
   condition/brand/model specs, availability calendar with blocked dates, owner trust
   card (photo, verified badge, trust score, member since, response expectation),
   reviews with per-reviewer ratings, sticky "Request booking" bar with date picker.
4. **Listing detail (Service)** — same family; time-slot selection, service area,
   minimum notice, portfolio link instead of condition specs.
5. **Booking request sheet** — dates/times, price breakdown (rate × duration, deposit
   listed separately with plain-language explanation of how deposit handover works),
   optional note, confirm.
6. **My bookings** — two tabs ("Renting" / "My listings' bookings"), each booking as
   a card with status chip, dates, counterpart, and the one action it currently needs.
7. **Booking detail** — status timeline, all booking facts, deposit-proof upload
   (renter) / confirm-received (owner), message thread entry, contextual actions
   (approve/decline/cancel/complete), review prompt after completion.
8. **Messages** — thread list (newest first, unread badge), thread view clearly headed
   by its booking (item photo + dates), simple composer. Messages exist only inside
   bookings — the header should make that obvious.
9. **Auth** — register (name, phone, email, password), OTP verify (6-digit), login.
   Warm and brief; explain *why* phone verification exists in one line.
10. **Profile (public)** — person-first: photo, name, verified badge, trust score,
    review count, member since, their active listings, reviews about them.
11. **Profile (own) & verification** — profile editing plus citizenship-card upload
    flow with a clear pending/approved state and a one-line privacy reassurance.
12. **Create listing wizard** — type choice (Product/Service) then the short steps;
    finish screen shows the listing exactly as renters will see it.
13. **Admin — verification queue** (desktop) — table of pending citizenship
    submissions, image viewer, approve/reject; plus simple read-only lists of users,
    listings, bookings. Same design language, denser.

For each screen, produce the desktop web layout first, then the 390px responsive
variant. Screens where the two differ most — Explore (grid vs. single column),
Listing detail (two-column with sticky booking panel vs. stacked with sticky bottom
bar), and Booking detail (side-by-side facts + thread vs. stacked) — deserve both
treatments in full; simpler screens can show desktop plus a brief note on how they
collapse.

---

## 5. Latitude

Everything in section 2 sets direction, not handcuffs. You are the senior product
designer: if a better pattern serves trust, clarity, or the two-sided marketplace
dynamic, take it — as long as the palette stays out of tech-default territory, the
typography stays intentional, nothing from the avoid-list appears, and every screen
keeps answering "can I trust this, and what happens next?". Surprise me with craft
in the details: the status timeline, the deposit hand-off explanation, and the
review moment are where this product wins or loses.
