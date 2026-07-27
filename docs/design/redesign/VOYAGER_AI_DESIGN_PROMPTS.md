# Voyager — AI-Ready Design Prompts

**Head-of-Design issue.** Copy-paste prompts to generate high-fidelity Voyager mobile screens in **Claude Artifacts · Figma Make · Google Stitch · Lovable · Bolt** — all locked to Voyager's exact design system.

Grounded in the two companion docs (do not contradict them):
[`VOYAGER_FEATURE_CATALOG_AND_UX_SPEC.md`](./VOYAGER_FEATURE_CATALOG_AND_UX_SPEC.md) (screens + tokens) · [`VOYAGER_PRODUCT_DESIGN_BIBLE.md`](./VOYAGER_PRODUCT_DESIGN_BIBLE.md) (UX, motion, a11y).

---

## How to use this (the prompt system)

Each final prompt = **[1] Tool Adapter** + **[2] Global Design-System Preamble** + **[3] Screen Block**.

1. Pick your tool → paste its **Adapter** (§B) once at the top.
2. Paste the **Global Preamble** (§A) once — it encodes the exact tokens, type, motion, and a11y. (In chat tools like Claude Artifacts/Lovable/Bolt you can keep it in the same message; in Stitch/Figma Make, fold the key tokens into the screen description.)
3. Append the **Screen Block** (§C) for the screen you want.

This keeps one source of truth for the design system, so 5 tools × every screen never drift. §D gives **5 fully-assembled, ready-to-run examples** (one per tool, each a different flagship screen) so you can see the finished shape.

> **Frame:** all screens are **390 × 844** (iPhone-13/Pixel-class), **dark only**, portrait, single mobile screen. Status bar + gesture area respected.

---

## §A — Global Design-System Preamble *(paste once)*

```
DESIGN SYSTEM — "Voyager" (privacy-first Android timeline app). Dark-only, OLED-first. No light mode. No dynamic color. Match these tokens EXACTLY.

COLORS (hex):
- Primary #3B82F6 (interactive/selected/highlight) · PrimaryDim #2563EB (pressed) · PrimaryContainer #1E3A5F (raised)
- Background #0F0F1A (screen) · Surface #1A1A2E (cards) · SurfaceVariant #252540 (elevated cards) · SurfaceBright #2E2E4A · SurfaceOverlay #2A2A4A (sheets/dialogs)
- Text: OnSurface #E8E8F0 (body) · OnSurfaceVariant #8888A0 (muted, never <12sp for key text) · OnPrimary #FFFFFF
- Status: Error #EF5350 · Success #66BB6A · Warning #FFA726
- Accents: Blue #42A5F5 · Purple #AB47BC · Green #66BB6A · Orange #FF7043 · Amber #FFA726
- Premium/Pro: Gold #E6B450 (use sparingly, Pro only) · PremiumDim #B8902F
- Transport modes: Walk/Run #66BB6A · Drive #AB47BC · Cycle #42A5F5 · Transit #FF7043 · Gap #616161 (dashed)
- Confidence ramp: <40% #EF5350 · 40–70% #FFA726 · >70% #66BB6A

GRADIENTS:
- screenBackground: radial, core #1C0F42 at top-center (~8% down) fading to #0F0F1A — behind every screen
- heroCard: vertical Primary@13% → Purple@5% → transparent — inside hero cards
- activeCard: vertical Green@10% → transparent — live/recording cards
- primaryGlow: radial Primary@28% → transparent — behind focal data (rings, hero icon)
- topBar: vertical #15102E → #0F0F1A · navBar: vertical #0F0F1A → #141428

TYPOGRAPHY (two families):
- Inter (UI text, Title Case never ALL-CAPS): headlineLarge 32/SemiBold, headlineSmall 24/SemiBold (section heads), titleLarge 22/Medium (card titles), titleMedium 16/Medium, bodyLarge 16, bodyMedium 14, labelLarge 14/Medium (buttons/chips), labelSmall 11
- JetBrains Mono (ALL numbers/stats/timestamps/coordinates): StatLarge 28/Bold ("12.4 km"), StatMedium 20/Bold ("3h 24m"), StatSmall 14/Medium ("92%"), Timestamp 13 ("08:00"), Data 12 (coords)
- Great Vibes (cursive): ONLY the "Voyager" wordmark. Never data.
RULE: every number is mono; UI labels are Inter.

SHAPE & SPACING: cards/sheets/dialogs radius 12 · buttons radius 8 · avatars/badges circle. Spacing scale: xs4 sm8 md12 lg16 xl24 xxl32 on an 8pt grid. Standard card padding 16.

COMPONENTS (rebuild faithfully): VoyagerCard {FLAT | RAISED | HIGHLIGHTED, optional tint}; ConfidenceBar (color-ramp fill + % label); VoyagerBadge (pill); SectionHeader (Title-Case + optional trailing action); ShimmerCard (loading skeleton); EmptyState (icon + headline + subheadline + CTA); PulsingDot (live indicator); DayNavigator (‹ prev | date | next ›); PeriodSelectorBar (date-range filter chips); FeatureGate (Pro lock → gold); ModalBottomSheet (SurfaceOverlay, drag handle); filled/outlined/text buttons with ripple.

MOTION DEFAULTS: micro 100ms · standard 200–300ms · large/shared 350–450ms; easing standard cubic-bezier(0.2,0,0,1), emphasized-decelerate on enters. Data animates (counters roll, rings sweep), chrome doesn't. Live = PulsingDot 1.5s loop. Honor Reduce-Motion: replace slides/scales with ≤100ms cross-fades, snap counters.

ACCESSIBILITY: WCAG AA. Don't use white text on Primary below large/bold (≈3.7:1 — use bold/large or PrimaryDim). Never encode meaning by color alone — pair with icon/label. 48dp min touch targets. Support 200% font scale (wrap, no fixed-height stat tiles). Every swipe action also has a tap/long-press path. All icons/data have text labels for screen readers.

VOICE: calm, factual, trustworthy. Name uncertainty honestly ("about 92% sure"). Never fabricate missing data — show explicit gaps with a reason. Avoid jargon (no "HDBSCAN"/"hysteresis" in UI copy).

PRINCIPLES: Privacy is the product · Honest gaps · Evidence on tap · Numbers are mono · Correctable by design · Calm/legible/dark · One primary action per screen.
```

---

## §B — Tool Adapters *(paste the one for your tool, before the Preamble + Screen Block)*

### B1 · Claude Artifacts
```
You are a senior product designer + front-end engineer. Build a single, self-contained, runnable React + TypeScript artifact of ONE mobile screen for "Voyager".
- Tailwind for styling; use the exact hex tokens as arbitrary values (e.g. bg-[#0F0F1A]). Icons: lucide-react only. Fonts: Inter + JetBrains Mono (assume available; fall back to sans/mono).
- Render inside a fixed 390×844 dark phone frame, centered, with rounded corners + subtle bezel. Include the radial screenBackground.
- Make the interactions listed in the Screen Block actually work (state, taps, sheets, tab switches) using React state — no backend.
- Seed realistic Voyager sample data. Animate per the motion spec with CSS/Framer-Motion-style transitions; respect prefers-reduced-motion.
- Production-quality spacing, hierarchy, and a11y (aria-labels, 44–48px targets). Output ONLY the component.
Then apply the DESIGN SYSTEM and SCREEN below.
```

### B2 · Figma Make
```
Act as a principal product designer. Produce a high-fidelity, dark-mode Figma design of ONE mobile screen (390×844) for "Voyager".
- Use Auto Layout for every section, real components for repeated elements (cards, chips, list rows, nav bar), and Figma Variables/styles bound to the color + type tokens below.
- This is a DESIGN deliverable: describe and render visuals, layout, spacing, states — not code. Include the screenBackground gradient, proper elevation via surface tokens, and the exact type ramp (Inter UI + JetBrains Mono for all numbers).
- Show the default state plus, as separate frames/variants, the loading (ShimmerCard) and empty states noted.
- Annotate interactions and motion as notes; keep 48dp touch targets.
Then apply the DESIGN SYSTEM and SCREEN below.
```

### B3 · Google Stitch
```
Generate a mobile UI screen. Platform: Android, Material 3, DARK theme. Dimensions 390×844. App: "Voyager", a privacy-first location-timeline app.
Describe the screen precisely for UI generation: exact background/surface/text/accent colors (hex below), section-by-section layout top-to-bottom, component styles (rounded 12 cards, pill chips/badges, bottom nav), and the type treatment (clean sans UI text + monospace for all numeric stats). One screen per generation; high fidelity; realistic sample content; generous dark-surface contrast.
Then apply the DESIGN SYSTEM tokens and the SCREEN layout below as the description.
```

### B4 · Lovable
```
You are building "Voyager" as a mobile-first React web app. Create ONE screen as a route/component.
- Stack: React + TypeScript + Tailwind + shadcn/ui, lucide-react icons, Framer Motion for animation. Mobile-first, constrained to a 390px column on a #0F0F1A canvas.
- Map the tokens below into the Tailwind theme (colors, radius, fonts Inter + JetBrains Mono). Build reusable components for VoyagerCard, ConfidenceBar, Badge, SectionHeader, EmptyState, DayNavigator.
- Wire realistic local state + mock data so interactions (tabs, sheets, classify/confirm, day nav) work. Implement the empty/loading/error states. Respect prefers-reduced-motion. Strong a11y (semantic html, aria, focus states, 48px targets).
Then apply the DESIGN SYSTEM and SCREEN below.
```

### B5 · Bolt
```
Scaffold a Vite + React + TypeScript + Tailwind project rendering ONE Voyager mobile screen at 390×844 in a dark phone frame.
- Add framer-motion and lucide-react. Configure Tailwind with the exact color tokens + fonts (Inter, JetBrains Mono) below; radius 12 cards / 8 buttons.
- Implement the screen as a component with working state for the listed interactions and mock Voyager data; include loading (shimmer) + empty states; animate per the motion spec; honor prefers-reduced-motion.
- Keep it self-contained and runnable with `npm run dev`. Clean component structure (Card, StatTile, NavBar, Sheet). Accessible (aria-labels, 48px targets, AA contrast).
Then apply the DESIGN SYSTEM and SCREEN below.
```

---

## §C — Per-Screen Blocks

Each block carries the 8 required facets — **Purpose · Layout · Hierarchy · Components · Interactions · Animations · Accessibility · Visual style.** Append after the Adapter + Preamble. (Tokens referenced by name are defined in §A.)

> Convention: "TopBar" = transparent over `topBar` gradient with Voyager wordmark (Great Vibes) + Search/Bell(badge)/Settings icons; shown only on the 4 core tabs. "NavBar" = 4-tab bottom nav over `navBar` gradient: Today, Map, Timeline, Insights.

### CORE TABS

#### SCREEN: Today / Dashboard
- **Purpose:** Daily glance — confirm tracking is live and show today at a glance.
- **Layout:** TopBar → (conditional permission banner) → tracking-status hero card → activity-rings block → "Notable" section (top place + anomaly) → Proof shortcut row (Mileage·Trips·Export) → NavBar.
- **Hierarchy:** Rings hero (largest, primaryGlow) > live status pill > section cards > shortcut row.
- **Components:** VoyagerCard(HIGHLIGHTED) hero w/ activeCard gradient when live; PulsingDot; 3 activity rings (distance/steps/active) with mono center value; VoyagerBadge; SectionHeader; ConfidenceBar; shortcut chips.
- **Interactions:** tap ring→Insights; tap place→Place Detail; tap shortcut→that screen; pull-to-refresh.
- **Animations:** rings sweep 800ms on load; center counters roll 400ms; PulsingDot 1.5s when tracking; cards stagger-rise 250ms.
- **Accessibility:** rings expose text alt ("8,420 steps, 70% of goal"); live state in an aria-live region; status legible without color.
- **Visual style:** calm hero with glow; mono stats dominate; one clear "you're being captured" reassurance. First-use variant: headline "Voyager is capturing now" + forming-constellation.

#### SCREEN: Map
- **Purpose:** Spatial truth of where you went today.
- **Layout:** full-bleed dark map → floating DayNavigator (top) → optional legend chip → bottom ModalBottomSheet on tap.
- **Hierarchy:** routes + markers over muted basemap; selected element pops; sheet overlays.
- **Components:** map canvas (dark style); transport-colored polylines (Walk green/Drive purple/Cycle blue/Transit orange/Gap grey-dashed); circular visit markers tinted by category; DayNavigator; VisitDetailSheet/SegmentDetailSheet; legend.
- **Interactions:** pan/zoom; tap marker/route→sheet; swipe/tap DayNavigator to change day; fly-to bounds.
- **Animations:** camera fly-to 450ms emphasized; markers pop-in 150ms stagger; marker bounce→sheet slide-up 300ms.
- **Accessibility:** markers/routes have text labels + a list fallback; legend explains colors; 48dp marker hit slop.
- **Visual style:** cartographic, restrained; data is the color, basemap is grey.

#### SCREEN: Timeline ★flagship
- **Purpose:** The honest, editable story of a day (Memory heart).
- **Layout:** TopBar → DayNavigator → PeriodSelectorBar → vertical timeline (left connector rail + stacked segment/visit/gap cards) → NavBar.
- **Hierarchy:** connector rail w/ mode color = spine; place name bold (Inter title); time mono secondary; confidence chip tertiary; GAP cards dashed + muted.
- **Components:** DayNavigator; PeriodSelectorBar chips; VoyagerCard rows (one per segment/visit); mode VoyagerBadge; ConfidenceBar; dashed GAP card w/ plain-language reason; "Why?" evidence chip.
- **Interactions:** tap row→detail sheet; **long-press row→correction menu** (rename/category/reclassify/merge/split) + undo; tap "Why?"→evidence sheet; swipe to change day; tap day header→Day Story.
- **Animations:** connector draws in 250ms; rows stagger-fade-rise 30ms each; correction menu scale-in 120ms; day change cross-fade.
- **Accessibility:** each row one merged semantic node read as "place, time range, mode, confidence"; GAP announces reason; correction available via long-press AND an overflow tap.
- **Visual style:** clean vertical rhythm, mode color as the through-line; gaps look intentional, not broken.

#### SCREEN: Insights *(redesign target — story-feed, not 7 tabs)*
- **Purpose:** Make habits legible and trustworthy.
- **Layout:** TopBar → 1–2 narrative "story insight" hero cards → filter chips (Overview·Movement·Patterns·Places·Weekly·Carbon·Social) → metric sections → NavBar.
- **Hierarchy:** story card hero > trend deltas (up/down arrows colored) > charts > Pro(Social) gated.
- **Components:** insight hero VoyagerCard; trend stat tiles (mono + Success/Error arrow); simple bar/line charts; ConfidenceBar; FeatureGate (Social, gold); chip row replacing equal tabs.
- **Interactions:** scroll feed; chip-filter (not 7 hard tabs); tap stat→"how is this computed" evidence; Pro chip→Paywall.
- **Animations:** charts grow-in 350ms; counters roll; chip selection slide-underline 200ms.
- **Accessibility:** every chart has a text summary; trends not color-only (arrow + sign); chip row keyboard/scroll accessible.
- **Visual style:** editorial, one story leads; data-dense but calm; Carbon uses Green, Pro uses gold.

### PLACES & CORRECTIONS

#### SCREEN: Place Detail ★flagship
- **Purpose:** Trustworthy place profile + one-tap correction hub.
- **Layout:** header (name + category + Home/Work badge + confidence) → map snippet → visit-history list → collapsible Evidence section → correction action row.
- **Hierarchy:** name largest > category/confidence chips > map > history > evidence (secondary, discoverable) > destructive (merge/split) lowest.
- **Components:** RenamePlaceDialog; category selector; VoyagerCard map snippet; visit list rows (mono dwell/time); VoyagerCollapsibleSection (evidence: cluster density, naming candidates ranked, category reasoning); ConfidenceBar; buttons.
- **Interactions:** one-tap rename/category; expand evidence; merge/split (confirm); "improve in OSM" (✦ overflow).
- **Animations:** evidence expand 250ms; rename dialog scale-in; saved-confirmation toast.
- **Accessibility:** confidence as text + bar; candidate list readable; rename reachable in ≤2 taps.
- **Visual style:** profile-like, trust-forward; evidence visible but not shouting.

#### SCREEN: Place Review
- **Purpose:** Clear the queue of unconfirmed visit candidates.
- **Layout:** TopBar/back → batch action bar → queue of candidate cards (where/when/dwell/confidence + "flagged because…") → per-card actions.
- **Hierarchy:** candidate place + time > confidence + reason > actions.
- **Components:** VoyagerCard; ConfidenceBar; inline evidence line; Confirm/Rename/Reject buttons; batch "Confirm all high-confidence"; EmptyState "All caught up".
- **Interactions:** tap **or** swipe confirm/reject; batch confirm; tap→Place Detail.
- **Animations:** card dismiss slide-out 250ms; badge count tick.
- **Accessibility:** swipe mirrored by buttons; reason stated in words; running count announced.
- **Visual style:** tidy inbox; satisfying clear-down.

#### SCREEN: Search
- **Purpose:** Retrieve any place/day/visit by fuzzy memory.
- **Layout:** search field (top) → filter chips (date/category/mode/dwell) → idle: recent/suggested → results grouped by type.
- **Hierarchy:** field > chips > grouped results (matched terms highlighted Primary).
- **Components:** search field; PeriodSelectorBar; FilterChips; result VoyagerCards; EmptyState (idle + no-result variants).
- **Interactions:** type-ahead; chip filter; tap result→detail; clear filters.
- **Animations:** results fade-in 200ms; chip toggle 150ms.
- **Accessibility:** result count announced; highlighted matches also bold (not color-only); clear-filters reachable.
- **Visual style:** fast, focused, archival.

#### SCREEN: Categories *(concise)*
Purpose: manage place categories. Layout: list/grid of category cards → edit → reassign places. Components: VoyagerCard, category chips, SectionHeader. Interactions: tap→edit, reassign. Animations: standard list fade. A11y: labeled categories. Visual: simple management list.

### PROOF

#### SCREEN: Mileage ★flagship (Pro)
- **Purpose:** Turn drives into defensible, audit-ready deductions.
- **Layout:** TopBar/back → PeriodSelectorBar → summary card (total miles + deductible $) → drive list (classifiable rows) → export bar.
- **Hierarchy:** big mono money/miles hero > drive rows > per-row evidence link > gold export CTA.
- **Components:** FeatureGate (Pro); summary VoyagerCard(HIGHLIGHTED) mono StatLarge; drive rows w/ Business/Personal toggle + purpose; "GPS evidence ✓" badge per row; ConfidenceBar; export button (gold); batch-select bar.
- **Interactions:** swipe **and** tap to classify Business/Personal; add purpose/notes; multi-select batch classify; row→Segment evidence; export IRS/HMRC PDF.
- **Animations:** classify chip flip 120ms + haptic; summary recount roll on change; export progress.
- **Accessibility:** classification via tap not just swipe; "audit-ready" trust badge has text; money/miles labeled.
- **Visual style:** ledger-credible; mono numbers lead; gold = Pro/export only; evidence is the hero differentiator.

#### SCREEN: Trips (list) ★flagship + Trip Detail
- **Purpose:** Auto-assembled travel memory & proof.
- **Layout (list):** back → filter → trip cards (date span, distance, primary mode icon, place count). **(Detail):** hero route map → per-day breakdown (DayNavigator within trip) → segments/visits → TripBook PDF (gold).
- **Hierarchy:** hero route map > dates/distance (mono) > per-day list > export.
- **Components:** trip VoyagerCards; hero map; DayNavigator; FeatureGate(PDF); mode badges; (✦) editable title/cover field.
- **Interactions:** tap trip→detail; confirm detected trip; scrub days; export PDF; (✦) edit title/cover.
- **Animations:** "Trip detected" card reveal; hero map fly-to; per-day stagger.
- **Accessibility:** trip summarized in text; map has list fallback; PDF action labeled.
- **Visual style:** wanderlust but restrained; the auto-detection delight is the moment.

#### SCREEN: Export
- **Purpose:** Own/share your data, your way.
- **Layout:** back → format selector (GPX·GeoJSON·CSV·VoyagerJSON) → date range → privacy toggles (include raw samples / strip coordinates) → export CTA + Import entry.
- **Hierarchy:** format choice > range > privacy toggles (emphasized) > export.
- **Components:** segmented format chips w/ helper ("GPX for maps, CSV for sheets"); PeriodSelectorBar; switches; primary button; size/row preview.
- **Interactions:** select format (shows helper)→range→toggles→export→system share sheet; "Privacy export" one-tap preset (stripped).
- **Animations:** export progress bar; success→share slide-up.
- **Accessibility:** toggles labeled with consequence; format helper text; preview announced.
- **Visual style:** controlled, reassuring, privacy-forward.

### SYSTEM & ONBOARDING

#### SCREEN: Settings *(redesign target — progressive disclosure)*
- **Purpose:** Configure tracking/privacy/data without overwhelm.
- **Layout:** back → settings search → category cards (Tracking · Privacy & Data · Data/Export · Advanced) → drill-in sub-screens (replace 4 dense tabs).
- **Hierarchy:** search > category cards > rows/controls; destructive (✦ duress) clearly Error-red + double-confirm.
- **Components:** search field; category VoyagerCards w/ icon + summary; sub-screen toggles/sliders/preset pickers; VoyagerCollapsibleSection; preset cards (5 general + 6 traveler).
- **Interactions:** search settings; tap card→sub-screen; toggle; apply preset (confirmation); confirm destructive.
- **Animations:** drill-in shared-axis 300ms; toggle thumb 150ms.
- **Accessibility:** every control labeled + state spoken; search lowers cognitive load; preset effects described in words.
- **Visual style:** organized, scannable, not a wall of switches.

#### SCREEN: Reliability *(concise)*
Purpose: prove the app is healthy → trust. Layout: tracking-state hero → battery self-report → worker heartbeat rows → health-log timeline. Components: VoyagerCard, PulsingDot, status rows, VoyagerBadge, ConfidenceBar(coverage). Interactions: tap a worker→detail; fix-it CTAs on degraded rows. Animations: heartbeat pulse; healthy=green settle. A11y: status as text + color; latency mono labeled. Visual: dashboard-of-trust, Success/Warning/Error semantics.

#### SCREEN: Paywall *(concise)*
Purpose: convert to Pro. Layout: value hero → Pro feature list (Mileage, Day Story, advanced insights, extended export) → pricing → Buy + Restore. Components: feature rows w/ gold accents, price card, primary gold button. Interactions: buy/restore. Animations: feature rows stagger; gold sheen on CTA (subtle). A11y: price/terms legible; restore reachable. Visual: this is the ONE screen where Gold #E6B450 leads — confident, not spammy.

#### SCREEN: Onboarding (flow: Splash → Restore → Google-Timeline Import → Permissions → Persona Pick → Walkthrough)
- **Purpose:** Earn trust, grant permissions, show value — fast, in that order.
- **Layout:** full-screen pager; one message per screen; progress dots; persona = selectable cards (Memory/Proof/Habits + traveler preset); permissions = just-in-time rationale cards; a live "capturing now" value moment before/with permissions.
- **Hierarchy:** single headline + illustration > one primary action > skip/secondary.
- **Components:** pager; persona VoyagerCards (heroCard on select); PermissionRequestCard w/ honest "why"; animated splash (Great Vibes wordmark).
- **Interactions:** swipe pager; pick persona; grant permission per-need; optional import.
- **Animations:** splash color/scale 2s; pager shared-axis X + parallax illustration; persona select lift.
- **Accessibility:** each step focus-managed; rationale in plain words; skip always available.
- **Visual style:** trust-building, spacious, brand-forward; never asks for everything at once.

#### Detail sheets & secondary *(concise)*
- **Segment Detail / Visit Detail sheet:** ModalBottomSheet (SurfaceOverlay, handle). Purpose: inspect + evidence + correct. Layout: stats (mono) → ConfidenceBar → collapsible evidence (speeds, votes, counter-evidence, human explanation) → correct/confirm buttons. Animation: slide-up 300ms. A11y: handle + scrim labeled; evidence readable.
- **Day Story (Pro):** photo↔visit timeline + "unplaced" bucket; FeatureGate; photo-forward minimal chrome; mono timestamps. Empty: "No photos for this day".
- **Developer Profile / Open-source Licenses / Feedback:** info lists / form; standard surfaces; SectionHeaders; Feedback = category + text + submit.
- **Debug (Data Insertion / Pipeline):** DEBUG-only utility screens; dense controls/diagnostics; not design-polished.

### ✦ ASPIRATIONAL (not yet built)

#### SCREEN: Workout Recording + Live Stats ✦
- **Purpose:** Record an activity with live feedback (Strava-gap).
- **Layout:** large circular Start/Stop control (center-bottom) → live stat grid (duration·distance·pace·splits, mono) → mini live map → save sheet (type/title) → post-save summary + GPX export.
- **Hierarchy:** record button + live stats dominate; map secondary.
- **Components:** big record FAB (morphs Start→Stop); StatTiles (mono StatLarge); live map; PulsingDot; save ModalBottomSheet.
- **Interactions:** tap record→active (activeCard + Green); pause/resume; stop→save→summary; export GPX.
- **Animations:** FAB scale + morph 200ms + strong haptic; stats roll live; recording pulse.
- **Accessibility:** record state announced; stats labeled; controls 48dp+.
- **Visual style:** energetic but on-brand dark; Green active state; numbers are the hero. Entry = FAB on Today/Map.

#### SCREEN: Year-in-Review / Heatmap ✦
Purpose: shareable annual recap + activity heatmap. Layout: heatmap calendar (intensity ramp Surface→Primary) → superlatives (most-visited place, longest trip, total distance, mono) → shareable cards. Components: heatmap grid, stat cards, share sheet. Interactions: tap cell→day; share card. Animations: heatmap cells stagger-fill; counters roll. A11y: heatmap has text summary + per-cell labels. Visual: celebratory yet dark/calm; mono stats; intensity = Primary ramp.

#### SCREEN: Family Safety ✦ / Duress Setup ✦
Family: trusted-contact list + one-bit "I'm safe" send/receive (NO continuous location); privacy-forward copy; lives in Settings▸Privacy. Duress: opt-in panic-wipe with strong double-confirm, irreversible Error-red warning, decoy-PIN concept. Components: contact rows, big "Send I'm safe" button, destructive confirm dialog. A11y: destructive flows extra-clear, not color-only. Visual: reassurance over surveillance; duress = sober, unmistakable.

#### SCREEN: OSM Contribution ✦ *(concise)*
Purpose: push place rename / missing POI back to OpenStreetMap. Layout: "Improve this place" entry (Place Detail overflow) → edit form → OAuth → changeset confirm. Components: form fields, auth card, success confirm. Visual: community give-back framing; standard surfaces.

---

## §D — Fully-Assembled Examples *(one per tool, copy-paste ready)*

Each below = Adapter + Preamble (compressed inline) + one Screen Block. Swap the screen block to reuse for any other screen.

### D1 · CLAUDE ARTIFACTS → Timeline
```
You are a senior product designer + front-end engineer. Build a single, self-contained, runnable React + TypeScript artifact of ONE mobile screen for "Voyager" (privacy-first Android timeline app). Tailwind via arbitrary hex values; lucide-react icons; Inter + JetBrains Mono fonts. Render in a fixed 390×844 dark phone frame (rounded, subtle bezel) with a radial screen background (core #1C0F42 top-center → #0F0F1A). Make listed interactions actually work with React state. Seed realistic data. Animate per spec; respect prefers-reduced-motion. Output ONLY the component.

DESIGN SYSTEM (match exactly): Dark-only. Background #0F0F1A; Surface #1A1A2E; SurfaceVariant #252540; sheet #2A2A4A. Text OnSurface #E8E8F0, muted #8888A0. Primary #3B82F6 (pressed #2563EB). Transport colors: Walk #66BB6A, Drive #AB47BC, Cycle #42A5F5, Transit #FF7043, Gap #616161(dashed). Confidence ramp <40% #EF5350 / 40–70% #FFA726 / >70% #66BB6A. Cards radius 12, padding 16; 8pt spacing. Inter for UI (Title Case), JetBrains Mono for ALL numbers/times. Wordmark "Voyager" in a cursive font only. Motion: 200–300ms standard, easing cubic-bezier(0.2,0,0,1); data animates, chrome doesn't. AA contrast; 48px targets; no color-only meaning; aria-labels on everything.

SCREEN — Timeline (the day's honest, editable story):
- Layout: transparent TopBar (Voyager wordmark + Search/Bell/Settings icons) → DayNavigator (‹ "Wed, Jun 3" ›) → horizontal PeriodSelectorBar chips (Day/Week/Custom) → vertical timeline with a left connector rail + stacked cards (visits and movement segments, plus one dashed GAP card with a plain-language reason like "Tracking paused — battery saver"). Bottom NavBar: Today/Map/Timeline/Insights (Timeline active).
- Each card: mode/category icon + colored rail dot; place name (Inter title, bold); time range + dwell/distance in mono; a small confidence pill (color from ramp); a subtle "Why?" chip.
- Hierarchy: rail+mode color is the spine; place name primary; mono time secondary; confidence tertiary; gaps muted+dashed.
- Interactions: tap card → bottom sheet with details + evidence; long-press card → correction menu (Rename, Set category, Reclassify, Merge, Split) with undo; tap "Why?" → evidence sheet (speeds, activity votes, counter-evidence, human explanation); ‹ › changes day with cross-fade.
- Animations: connector draws in (250ms); cards stagger-fade-rise (30ms each); correction menu scale-in (120ms).
- Accessibility: each card is one semantic node read as "place, time range, mode, confidence %"; GAP announces its reason; correction reachable by long-press AND an overflow button.
- Visual: clean vertical rhythm, calm dark, mode color as the through-line, gaps look intentional.
Seed ~6 rows for today (Home → Drive → Café visit → Walk → Office visit → GAP → Drive home).
```

### D2 · FIGMA MAKE → Today / Dashboard
```
Act as a principal product designer. Produce a high-fidelity, dark-mode Figma design of ONE mobile screen (390×844) for "Voyager", a privacy-first location-timeline app. Use Auto Layout everywhere, components for repeated elements, and Variables bound to the tokens. Render the default state plus separate frames for the first-use and loading states. Annotate interactions/motion as notes. This is a design deliverable (no code).

TOKENS: Background #0F0F1A with a radial indigo glow (#1C0F42) at top-center. Surfaces #1A1A2E / #252540; sheet #2A2A4A. Text #E8E8F0 / muted #8888A0. Primary #3B82F6; Success/active #66BB6A; Pro gold #E6B450 (sparingly). Cards radius 12, padding 16; 8pt grid. Type: Inter for UI (Title Case), JetBrains Mono for ALL numbers/stats (StatLarge 28/Bold). Wordmark "Voyager" cursive.

SCREEN — Today / Dashboard:
- Layout top→bottom: transparent TopBar (Voyager wordmark + Search/Bell-with-badge/Settings) → HIGHLIGHTED hero card "Voyager is capturing now" with a green-tinted active gradient + a pulsing live dot and today's distance/steps in mono → a row of three activity rings (Distance, Steps, Active minutes) each with a mono center value and a soft primary glow → "Notable today" section (top place card + one anomaly card) → a Proof shortcut row of 3 chips (Mileage · Trips · Export) → bottom NavBar (Today active).
- Hierarchy: rings + live hero dominate; section cards below; shortcuts smallest.
- Components: VoyagerCard (HIGHLIGHTED hero, RAISED section cards), PulsingDot, ActivityRing component (variant per metric), Badge, SectionHeader, shortcut Chip, bottom NavBar.
- Interactions (note): tap ring→Insights; tap place→Place Detail; tap shortcut→screen; pull-to-refresh.
- Animations (note): rings sweep 800ms; center counters roll 400ms; live dot pulses 1.5s; cards stagger-rise 250ms.
- Accessibility: rings carry text alt ("8,420 steps, 70%"); live status legible without color; 48dp targets.
- Visual: calm, trustworthy, glow-accented; mono stats lead; the "you're being captured" reassurance is the emotional hero.
First-use frame: same layout, hero reads "Move around for a few minutes — your first places will appear here," empty ring placeholders, forming-constellation illustration. Loading frame: ShimmerCard skeletons for hero + rings.
```

### D3 · GOOGLE STITCH → Map
```
Generate a mobile UI screen. Platform: Android, Material 3, DARK theme, 390×844. App: "Voyager", a privacy-first location-timeline app.

Screen: Map — today's routes and place visits on a dark map.
Colors: map canvas and chrome on near-black #0F0F1A; cards/sheets #1A1A2E and #2A2A4A; text #E8E8F0 with muted #8888A0; primary accent #3B82F6. Route lines colored by travel mode: walking #66BB6A, driving #AB47BC, cycling #42A5F5, transit #FF7043, and missing-data gaps as a dashed grey #616161. Visit markers are circular pins tinted by place category.
Layout top→bottom: a full-bleed dark map fills the screen; a floating pill "day navigator" near the top center shows ‹ "Wed, Jun 3" › with left/right chevrons; a small legend chip sits top-right; when a marker or route is tapped, a rounded bottom sheet (radius 12) slides up showing the place/route name, time range and distance in a monospace font, a colored confidence pill, and a "Why?" link to evidence.
Components: dark map, colored polylines, circular category markers, floating day-navigator pill, legend chip, rounded bottom sheet with a drag handle.
Type: clean sans for labels (Title Case), monospace for all numbers, times, and coordinates.
Style: cartographic and restrained — the basemap is muted grey, the data (routes + markers) provides all the color; generous contrast; high fidelity; realistic sample routes for a single day. Touch targets large and comfortable. One screen.
```

### D4 · LOVABLE → Mileage (Pro)
```
You are building "Voyager" as a mobile-first React web app. Create ONE screen — the Mileage log (a Pro feature). Stack: React + TypeScript + Tailwind + shadcn/ui + lucide-react + Framer Motion. Constrain to a 390px column centered on a #0F0F1A canvas with a faint radial indigo glow at top. Map tokens into the Tailwind theme; build reusable VoyagerCard, ConfidenceBar, Badge, SectionHeader, StatTile, DriveRow components. Wire local state + mock data so classification and export feel real. Include loading (shimmer) + empty states. Respect prefers-reduced-motion. Strong a11y.

TOKENS: bg #0F0F1A; surfaces #1A1A2E / #252540; sheet #2A2A4A; text #E8E8F0 / muted #8888A0; Primary #3B82F6; Pro gold #E6B450 / #B8902F; Success #66BB6A; Error #EF5350; Drive accent #AB47BC. Cards radius 12 / buttons radius 8; 8pt spacing; padding 16. Inter for UI (Title Case); JetBrains Mono for ALL numbers/money/miles.

SCREEN — Mileage:
- Layout: back-row header "Mileage log" → PeriodSelectorBar (This week / Month / Quarter / Custom) → HIGHLIGHTED summary card showing total miles and deductible $ in big mono (StatLarge), with an "Audit-ready ✓" gold badge → a list of detected drive rows → a sticky bottom bar with a gold "Export IRS PDF" button and a "Select" (batch) toggle.
- DriveRow: route ("Home → Office"), date/time + miles in mono, a Business/Personal segmented toggle, an editable purpose chip, and a small "GPS evidence ✓" badge that opens an evidence sheet (speeds, sample count, raw-sample note, human explanation, counter-evidence).
- Hierarchy: mono money/miles hero > drive rows > per-row evidence > gold export CTA.
- Interactions: tap or swipe a row to classify Business/Personal (haptic + chip flip); edit purpose; multi-select to batch-classify; tap evidence badge → bottom sheet; export → progress → success share. The summary recounts (rolling number) when classifications change.
- Animations: classify chip flip 120ms; summary number roll 400ms; sheet slide-up 300ms; respect reduced-motion.
- Accessibility: classification reachable by tap (not swipe-only); money/miles and the audit badge have text labels; 48px targets; AA contrast.
- Visual: ledger-credible; mono numbers lead; gold used ONLY for Pro/export; the per-row GPS evidence is the headline differentiator vs MileIQ.
Seed ~7 drives across the week, mixed business/personal, a couple unclassified.
```

### D5 · BOLT → Insights
```
Scaffold a Vite + React + TypeScript + Tailwind project rendering ONE Voyager mobile screen — Insights — at 390×844 inside a dark phone frame. Add framer-motion + lucide-react. Configure Tailwind with the exact tokens + fonts (Inter, JetBrains Mono); cards radius 12, buttons 8. Implement working state, mock data, loading (shimmer) + empty states; animate per spec; honor prefers-reduced-motion. Self-contained, runnable with npm run dev. Accessible (aria-labels, 48px targets, AA contrast).

TOKENS: bg #0F0F1A (radial #1C0F42 glow top-center); surfaces #1A1A2E / #252540; sheet #2A2A4A; text #E8E8F0 / muted #8888A0; Primary #3B82F6; Success #66BB6A; Error #EF5350; Carbon green #66BB6A; Pro gold #E6B450. Inter for UI (Title Case); JetBrains Mono for ALL numbers.

SCREEN — Insights (story-feed, NOT seven equal tabs):
- Layout: transparent TopBar (Voyager wordmark + Search/Bell/Settings) → one or two narrative "story insight" hero cards (e.g. "You walked 23% more this week — your most active week this month") → a horizontal filter-chip row (Overview · Movement · Patterns · Places · Weekly · Carbon · Social) where Social shows a gold Pro lock → metric sections below that update with the selected chip (trend stat tiles with mono values + colored up/down arrows; a simple weekly bar chart; an anomaly card; a carbon card in green) → bottom NavBar (Insights active).
- Hierarchy: story hero card > trend deltas > charts > Pro-gated Social.
- Components: insight hero VoyagerCard, trend StatTile (mono + Success/Error arrow), simple bar/line chart, ConfidenceBar, FeatureGate (Social → gold), chip row with sliding underline.
- Interactions: scroll the feed; tap a chip to filter sections (sliding underline, content cross-fade); tap any stat → a "How is this computed?" evidence sheet; tap the Social Pro chip → a paywall sheet.
- Animations: charts grow-in 350ms; counters roll; chip underline slides 200ms; reduced-motion → cross-fades.
- Accessibility: every chart has a visible text summary; trends shown by arrow + sign (not color alone); chips scrollable and labeled; sheets focus-managed.
- Visual: editorial and calm — one story leads, data-dense but legible; Carbon uses green, Pro uses gold; numbers are mono.
Seed a week of realistic data (distance, steps, top places, one anomaly, carbon kg).
```

---

## Coverage map

| Screen | Block §C | Worked example §D |
|---|---|---|
| Today/Dashboard | ✓ | D2 (Figma Make) |
| Map | ✓ | D3 (Stitch) |
| Timeline | ✓ | D1 (Claude Artifacts) |
| Insights | ✓ | D5 (Bolt) |
| Place Detail | ✓ | — |
| Place Review | ✓ | — |
| Search | ✓ | — |
| Categories | ✓ | — |
| Mileage | ✓ | D4 (Lovable) |
| Trips + Trip Detail | ✓ | — |
| Export | ✓ | — |
| Settings | ✓ | — |
| Reliability | ✓ | — |
| Paywall | ✓ | — |
| Onboarding (6 steps) | ✓ | — |
| Detail sheets / Day Story / Dev / Debug | ✓ | — |
| ✦ Workout / Year-in-Review / Family / Duress / OSM | ✓ | — |

**To generate any non-worked screen:** take its §C block, wrap with your tool's §B adapter + the §A preamble — identical pattern to the §D examples.

*Honor the design system exactly. Companion docs: [feature catalog](./VOYAGER_FEATURE_CATALOG_AND_UX_SPEC.md) · [design bible](./VOYAGER_PRODUCT_DESIGN_BIBLE.md).*
