# Voyager — Product Design Bible

**A complete product & UX discovery dossier.**
Authored from the perspective of a Principal Product Designer / Staff UX Researcher / Mobile Design Lead, for a product that must outperform Google Timeline, Arc Timeline, MileIQ, Polarsteps, Strava, and Life360.

This is **discovery, not redesign.** It maps what Voyager is, who it's for, where it hurts, and where the design effort should go — before a single pixel is moved.

> ⚠️ **Read this first — research framing.** Voyager is **pre-production with no real users yet.** Every persona, journey, pain point, and 1–10 UX score below is a **rigorous designer hypothesis**, not field data. They are crafted from heuristic analysis of the spec + competitor patterns and are explicitly **"to be validated."** Each research section carries a *Validate via* note (typically N=5 interviews/usability tests per persona). Do not cite these numbers to stakeholders as findings.

**Companion doc:** exact feature inventory, screen specs, and design tokens live in [`VOYAGER_FEATURE_CATALOG_AND_UX_SPEC.md`](./VOYAGER_FEATURE_CATALOG_AND_UX_SPEC.md). This Bible references it rather than duplicating token tables.

**Legend:** ✅ shipped · ◐ parity-polish (built, needs UX work) · ✦ aspirational (not built). Competitors: **GT** Google Timeline · **Arc** Arc Timeline · **Poly** Polarsteps · **MileIQ** · **Strava** · **L360** Life360.

---

## Table of Contents

**Part A — Discovery**
- [§1 Product Understanding](#1-product-understanding-phase-1)
- [§2 User Research](#2-user-research-phase-2)
- [§3 User Journeys](#3-user-journeys-phase-3)
- [§4 Information Architecture](#4-information-architecture-phase-4)
- [§5 UX Audit](#5-ux-audit-phase-5)
- [§6 Design System Review](#6-design-system-review-phase-6)
- [§7 Motion System](#7-motion-system-phase-7)
- [§8 Accessibility Audit](#8-accessibility-audit-phase-8)
- [§9 States: Empty / First-use / Loading / Error](#9-states-phase-9)
- [§10 Screen Blueprints](#10-screen-blueprints-phase-10)
- [§11 Design Priorities](#11-design-priorities-phase-11)

**Part B — Packaged Outputs (Phase 12)**
- [B1 Product Design Bible (exec summary)](#b1-product-design-bible)
- [B2 UX Playbook](#b2-ux-playbook)
- [B3 Design Principles](#b3-design-principles)
- [B4 User Journey Maps](#b4-user-journey-maps)
- [B5 Screen Blueprints](#b5-screen-blueprints)
- [B6 Accessibility Guide](#b6-accessibility-guide)
- [B7 Motion Guide](#b7-motion-guide)
- [B8 Design Review Report](#b8-design-review-report)

---
---

# Part A — Discovery

## §1 Product Understanding (Phase 1)

### 1.1 Vision

**The problem.** Modern life leaves a movement trail — where you went, how long you stayed, how you got there — and that trail answers questions people genuinely care about: *Where was I last Tuesday? How many business miles did I drive this quarter? What was that café in Lisbon? Am I actually walking less this month?* Today those answers are split across products that each take something in return: **Google Timeline** harvests your location into the cloud; **MileIQ** locks tax evidence behind a subscription and its servers; **Strava** turns movement into a social feed; **Life360** trades family safety for continuous surveillance. None of them let you keep the whole record, privately, and **trust** what it says.

**Why Voyager exists.** To give a person the **complete, honest, private record of their own movement** — reconstructed on-device, encrypted, owned outright, and **explainable** down to the GPS sample. It collapses three jobs that otherwise need three apps into one:

- **Memory** — *"Where was I, and what was that place?"* (vs GT, Arc)
- **Proof** — *"Prove my mileage / my trip / my presence."* (vs MileIQ, Poly)
- **Habits** — *"Show me my patterns, honestly."* (vs Strava, GT)

**What makes it unique (the moat).**
1. **Local-first + encrypted by default.** No cloud, no account, no API keys, SQLCipher from day one. Privacy isn't a setting — it's the architecture.
2. **Evidence-backed UI.** Every visit, segment, and place carries *why* — supporting metrics, **counter-evidence** (why other labels were rejected), rule versions, human-readable explanations. No competitor exposes this. It converts a black box into something a user (or a tax auditor, or a court) can trust.
3. **Honest gaps.** When data is missing, Voyager says so — explicit GAP rows with reasons — instead of fabricating a smooth line. This is a trust differentiator nobody else offers.
4. **One record, many jobs.** The same on-device timeline powers memory, tax proof, and habit insight — no re-tracking, no per-feature data silos.

**Core value proposition (one line):**
> *Voyager is the private, on-device timeline that remembers everywhere you've been, proves it when you need to, and shows you your patterns — and it can always explain exactly why it knows.*

### 1.2 Positioning vs. competitors

| Product | What it's great at | Where Voyager wins | Where it beats Voyager |
|---|---|---|---|
| **Google Timeline** | Effortless place naming (ML + global POI), zero-config | Privacy, export freedom, evidence/explainability, honest gaps, mileage/proof | First-visit place accuracy, activity classification (ML), polish |
| **Arc Timeline** | Beautiful timeline, on-device, editable | Proof pillar (mileage/trips PDF), evidence depth, analytics breadth, Android-native | Visual polish, motion craft, refined interaction language |
| **Strava** | Workout recording, social, segments/leaderboards | Privacy, passive whole-life record, carbon, no social pressure | Active recording UX, athlete community, live activity screen |
| **MileIQ** | Frictionless drive classification, IRS reports | **GPS-evidence per row (court-grade)**, no subscription lock-in, export | Brand trust with accountants, swipe-classify polish |
| **Polarsteps** | Gorgeous trip stories, printed books, social | Auto trip detection, privacy, evidence, broader feature set | Printed product quality, social discovery, illustration craft |
| **Life360** | Real-time family location, alerts | Privacy-preserving "I'm safe" model (✦), no surveillance | Real-time sharing, crash detection, established family network |

### 1.3 SWOT

| | **Helpful** | **Harmful** |
|---|---|---|
| **Internal** | **Strengths** · Local-first encrypted architecture · Evidence/explainability moat · Three jobs in one record · Mature pipeline (Kalman, adaptive sampling, 9 workers) · Multi-format export, no lock-in · Honest gaps build trust · Court-grade mileage evidence | **Weaknesses** · Place-naming gap (OSM < Google POI) · Heuristic (not ML) activity classification → DRIVE/TRANSIT ambiguity · First-hour emptiness (app feels blank before movement) · Overloaded Settings (4 tabs) & Insights (7 tabs) · No first-class Places browse surface · UI polish/motion behind Arc · Dark-only (no light mode) limits some users |
| **External** | **Opportunities** · Privacy backlash against GT/L360 (regulatory tailwind) · Tax/gig-economy mileage market underserved on privacy · "Honest data" positioning is uncontested · Fitness + family categories addressable via ✦ frontier · Year-in-Review/share as viral loop | **Threats** · Google/Apple bundle timeline free · Arc could ship Android · OSM data coverage stays thin in regions · App Store review friction for location/background · User skepticism that on-device can match cloud accuracy · Monetization: privacy users resist subscriptions |

**Strategic read:** Voyager should **lead with trust** (privacy + evidence + honesty) — the one axis no incumbent can copy without abandoning their business model — while **closing the polish gap** on the 3–4 screens users touch daily, and **neutralizing** the place-naming weakness through better correction UX rather than trying to out-data Google.

---

## §2 User Research (Phase 2)

> *Validate via N=5 moderated interviews + diary study per persona before committing roadmap weight. Frequencies and screen rankings are hypotheses.*

### 2.1 Personas

#### P1 — The Memory Keeper *(Memory)*
- **Demographics:** 28–45, urban/suburban, mixed gender, mid-income, moderate tech comfort, privacy-curious.
- **Motivations:** Recall where they were & what places were called; relive days; settle "when did we last go there?" debates.
- **Frustrations:** GT feels creepy; timelines are wrong and uneditable; can't trust auto-named places.
- **Goals:** A trustworthy, browsable diary of places & days with minimal effort.
- **Workflow:** Opens app in evening → reviews today's timeline → renames a place → occasionally searches a past day.
- **Primary screens:** Timeline, Map, Search, Place Detail, Dashboard.
- **Frequency:** Daily-glance (1–2×/day, <2 min).

#### P2 — The Mileage Pro *(Proof)*
- **Demographics:** 30–55, gig driver / realtor / consultant / field sales, self-employed, tax-aware, pragmatic tech user.
- **Motivations:** Maximize legitimate mileage deductions; survive an audit; stop hand-logging.
- **Frustrations:** MileIQ subscription; fear that logs won't hold up; manual classification tedium.
- **Goals:** Accurate auto-captured drives, fast business/personal classification, defensible IRS/HMRC report.
- **Workflow:** Weekly → opens Mileage → swipe-classifies drives → end of quarter exports PDF with GPS evidence.
- **Primary screens:** Mileage, Segment/Visit Detail (evidence), Export, Dashboard.
- **Frequency:** Weekly active; quarterly heavy.

#### P3 — The Traveler *(Memory + Proof)*
- **Demographics:** 25–40, frequent leisure/business traveler, photo-heavy, shares with friends/family.
- **Motivations:** Auto-capture trips without thinking; relive & share; remember accommodation/places abroad.
- **Frustrations:** Poly is manual; timezone chaos; losing track of where photos were taken.
- **Goals:** Zero-effort trip detection, beautiful trip story, photo↔place correlation, shareable/printable.
- **Workflow:** During trip passive → after trip opens Trips → reviews Trip Detail → builds Day Story → exports TripBook PDF.
- **Primary screens:** Trips, Trip Detail, Day Story, Map, Timeline.
- **Frequency:** Bursty (heavy during/after travel, dormant otherwise).

#### P4 — The Data Nerd *(Habits)*
- **Demographics:** 22–40, technical, quantified-self, spreadsheet-lover, owns the raw data.
- **Motivations:** Analyze their own patterns; export to their own tools; verify accuracy.
- **Frustrations:** Walled-garden exports; black-box metrics; no raw access.
- **Goals:** Rich analytics, anomaly detection, full raw export, transparency into how numbers are computed.
- **Workflow:** Weekly → Insights deep-dive → checks anomalies → exports GeoJSON/CSV → inspects evidence.
- **Primary screens:** Insights (all 7 tabs), Export, Place/Segment evidence, Reliability.
- **Frequency:** Weekly power sessions (10–20 min).

#### P5 — The Privacy Enthusiast *(cross-cutting)*
- **Demographics:** 25–50, security-conscious, may be journalist/activist/professional, high tech literacy, distrusts cloud.
- **Motivations:** A timeline that *cannot* leak; provable on-device; control over every byte.
- **Frustrations:** Every competitor phones home; unclear data handling; no panic option.
- **Goals:** Verify no network egress, encryption, FLAG_SECURE, exclusion zones, (✦) duress wipe.
- **Workflow:** Onboarding scrutiny → Settings ▸ Privacy audit → sets exclusion zones, FLAG_SECURE → periodic trust checks.
- **Primary screens:** Settings (Privacy & Data), Reliability, Onboarding, Export (coordinate stripping).
- **Frequency:** Heavy at setup, low ongoing (trust, then forget).

#### P6 — The Fitness User *(✦ aspirational — Habits)*
- **Demographics:** 20–45, runs/cycles/walks, may also use Strava, goal-oriented.
- **Motivations:** Record activities with live stats; track active habits; own activity data.
- **Frustrations:** Voyager today has **no active recording**; passive-only feels insufficient for workouts.
- **Goals:** Start/stop recording, live pace/distance/splits, per-activity GPX, active-minutes trends.
- **Workflow:** ✦ Pre-run → taps record FAB → live stats → saves activity → reviews splits → exports GPX.
- **Primary screens:** ✦ Workout Recording, Insights (Movement), Map.
- **Frequency:** 3–5×/week (if built).

#### P7 — The Family Safety User *(✦ aspirational)*
- **Demographics:** 30–55, parents/caregivers, want reassurance without surveillance, moderate tech comfort.
- **Motivations:** Know a loved one is safe; be reachable; avoid creepy always-on tracking.
- **Frustrations:** L360 surveils everyone continuously; kids resent it; privacy cost too high.
- **Goals:** ✦ Encrypted one-bit "I'm safe" handshake; no continuous location share.
- **Workflow:** ✦ Settings ▸ adds trusted contact → sends/receives "safe" pings.
- **Primary screens:** ✦ Family Safety (Settings ▸ Privacy), Dashboard.
- **Frequency:** Situational (travel, late nights).

### 2.2 Jobs-To-Be-Done

**Memory**
- When I can't remember where I was, I want to scrub back through an accurate day, so I can recall the place and time without doubt.
- When an auto-named place is wrong, I want to correct it once, so my history stays trustworthy.

**Proof**
- When tax season comes, I want every business drive already captured with evidence, so I can claim deductions without fear of audit.
- When I finish a trip, I want it assembled into a story automatically, so I can relive and share it with no manual logging.

**Habits**
- When a week ends, I want to see how my movement changed, so I can notice patterns and anomalies.
- When I look at a number, I want to know how it was computed, so I can trust it.

**Privacy (cross-cutting)**
- When I install a location app, I want proof it never leaves my device, so I can use it without anxiety.

**Fitness / Family (✦)**
- When I exercise, I want to record it with live feedback. · When I travel, I want to reassure someone without sharing my location.

### 2.3 User Stories (representative)

- **P1:** *As a memory keeper, I want to rename a place once and have it stick everywhere, so my timeline reads like my life.*
- **P1:** *As a memory keeper, I want to search "that bakery in March," so I can find a place by fuzzy memory.*
- **P2:** *As a mileage pro, I want drives auto-detected and one-swipe classified, so I never hand-log.*
- **P2:** *As a mileage pro, I want each mile backed by GPS evidence, so my report survives an audit.*
- **P3:** *As a traveler, I want trips detected without me starting anything, so I capture everything effortlessly.*
- **P4:** *As a data nerd, I want to export raw samples as GeoJSON, so I can analyze in my own tools.*
- **P4:** *As a data nerd, I want to see why a segment was called DRIVE not TRANSIT, so I can trust or correct it.*
- **P5:** *As a privacy enthusiast, I want to confirm zero network calls, so I know my data stays put.*
- **P6 (✦):** *As a fitness user, I want a record button with live pace, so my workouts are first-class.*
- **P7 (✦):** *As a parent, I want to send an "I'm safe" bit, so I reassure without being tracked.*

### 2.4 Consolidated Pain Points

| # | Pain | Who | Severity | Root cause |
|---|------|-----|----------|------------|
| PP1 | First-hour emptiness — app feels blank/broken before movement is detected | All, esp. P1 | High | Pipeline needs ~10 min of movement; Dashboard has no "capturing now" reassurance ◐ |
| PP2 | Auto-named places are wrong/coordinate-only | P1, P3 | High | OSM POI gap; correction flow not surfaced enough |
| PP3 | DRIVE vs TRANSIT misclassification | P2, P4 | Med-High | Heuristic classifier; needs visible easy correction |
| PP4 | Key value (Mileage, Trips, Evidence) buried | P2, P3, P4 | High | No nav entry; hidden behind Dashboard cards / sheets |
| PP5 | Settings & Insights overload (4 + 7 tabs) | All | Med | Feature breadth without progressive disclosure |
| PP6 | Trust uncertainty — "is it even tracking?" | All, esp. P5 | High | Tracking status not always legible; Reliability hidden |
| PP7 | No active recording | P6 | High (✦) | Feature absent |
| PP8 | No first-class place browsing | P1, P3 | Med | No `places` route |
| PP9 | Polish/motion gap vs Arc | All | Med | Motion system not yet systematized |
| PP10 | Onboarding asks for trust + permissions before showing value | All | Med-High | Value shown after, not during, setup |

---

## §3 User Journeys (Phase 3)

Each journey: **entry → goal → screens → actions → emotion → friction → opportunity.** (These are the canonical Journey Maps; [B4](#b4-user-journey-maps) is the quick index.)

### J1 — Daily Timeline Review *(P1)*
| Stage | Detail |
|---|---|
| Entry | Evening, opens app / notification glance |
| Goal | Confirm where they went today; fix anything wrong |
| Screens | Dashboard → Timeline → (Place Detail / Visit sheet) |
| Actions | Scan rings → scroll timeline → tap a visit → rename place |
| Emotion | Curious → satisfied (if accurate) / annoyed (if wrong) |
| Friction | Wrong place names; gaps confusing; correction not obvious |
| Opportunity | Inline rename on timeline; explain gaps in plain language; "looks right?" affirmation tap |

### J2 — Mileage Tracking *(P2)*
| Stage | Detail |
|---|---|
| Entry | Weekly habit / quarter-end |
| Goal | Classify drives; export deductible report |
| Screens | Dashboard → Mileage → Segment Detail (evidence) → Export |
| Actions | Swipe business/personal → add purpose → export IRS PDF |
| Emotion | Dutiful → relieved (evidence present) |
| Friction | Mileage has no nav entry; bulk classify missing; evidence link subtle |
| Opportunity | Top-level Proof entry; batch-classify; "audit-ready ✓" trust badge |

### J3 — Trip Detection *(P3)*
| Stage | Detail |
|---|---|
| Entry | Returns from trip; notification "Trip detected" |
| Goal | Review & keep the auto-built trip |
| Screens | (Notification) → Trips → Trip Detail |
| Actions | Confirm trip → review per-day → title/notes (✦) → TripBook PDF |
| Emotion | Delighted (it just appeared) |
| Friction | Trips buried; can't title/cover yet; PDF gated |
| Opportunity | Proactive "we made you a trip" card; editable cover/title; share |

### J4 — Day Story Creation *(P3)*
| Stage | Detail |
|---|---|
| Entry | Timeline day header / after travel |
| Goal | See the day as photos tied to places |
| Screens | Timeline → Day Story |
| Actions | Browse photo↔visit story; resolve "unplaced" |
| Emotion | Nostalgic, warm |
| Friction | Pro gate mid-delight; unplaced bucket unclear |
| Opportunity | Free teaser (1 story); drag-to-place unplaced photos |

### J5 — Export Data *(P4, P5)*
| Stage | Detail |
|---|---|
| Entry | Wants own data / privacy audit |
| Goal | Get clean export in chosen format |
| Screens | Settings/Dashboard → Export |
| Actions | Pick format → range → toggle raw/strip coords → export → share |
| Emotion | In control, reassured |
| Friction | Format differences unexplained; no preview |
| Opportunity | Format helper ("GPX for maps, CSV for sheets"); size/row preview |

### J6 — Place Review *(P1)*
| Stage | Detail |
|---|---|
| Entry | Bell badge with N candidates |
| Goal | Confirm/clean unconfirmed visits |
| Screens | Place Review |
| Actions | Confirm / rename / reject per card; swipe |
| Emotion | Tidy-satisfaction or chore-fatigue |
| Friction | Can pile up; bulk actions missing; why-flagged unclear |
| Opportunity | Batch confirm; "high-confidence auto-accept" setting; show evidence inline |

### J7 — Place Corrections *(P1, P4)*
| Stage | Detail |
|---|---|
| Entry | Sees wrong place/category/segment |
| Goal | Fix it once, everywhere |
| Screens | Place Detail / Timeline → correction dialog |
| Actions | Rename / set category / reclassify / merge / split |
| Emotion | Empowered (it learns) or frustrated (hard to find) |
| Friction | Correction entry points inconsistent; merge/split advanced |
| Opportunity | Consistent long-press correct menu; "this helps Voyager learn" feedback; undo |

### J8 — Weekly Insights *(P4)*
| Stage | Detail |
|---|---|
| Entry | Sunday review / curiosity |
| Goal | Understand the week's patterns & anomalies |
| Screens | Insights (Overview → Weekly → Movement/Patterns) |
| Actions | Read comparison deltas → drill anomalies |
| Emotion | Reflective, "huh, interesting" |
| Friction | 7 tabs to scan; insight cards generic; no narrative |
| Opportunity | Lead with 1–2 "story" insights; collapse tabs into a feed; tap-to-explain |

### J9 — Carbon Tracking *(P4)*
| Stage | Detail |
|---|---|
| Entry | Insights ▸ Carbon |
| Goal | See driving emissions over time |
| Screens | Insights (Carbon) |
| Actions | Set vehicle → view daily/weekly/monthly |
| Emotion | Conscientious / motivated |
| Friction | Buried as tab 6; vehicle setup unclear; no goal/benchmark |
| Opportunity | Onboard vehicle once; compare to avg; mode-shift nudges |

### J10 — Onboarding *(all)*
| Stage | Detail |
|---|---|
| Entry | First launch |
| Goal | Trust the app, grant permissions, see value |
| Screens | Splash → Restore → GT Import → Permissions → Persona Pick → Walkthrough |
| Actions | Choose persona → grant perms → maybe import |
| Emotion | Hopeful → wary (permissions) → impatient (no data yet) |
| Friction | Asks trust before showing value; long flow; empty after |
| Opportunity | Show a live "capturing now" moment in onboarding; defer non-critical perms; demo data preview |

---

## §4 Information Architecture (Phase 4)

> Reorganization only — **no new features.** Grounded in `presentation/navigation/VoyagerDestination.kt` (4 bottom tabs: home/map/timeline/insights; everything else push/sheet).

### 4.1 Current-state problems

- **Hidden / hard-to-discover features:**
  - **Mileage, Trips, Day Story, Export, Reliability, Search** have *no persistent navigation* — reachable only via Dashboard cards or the top-bar icons. The entire **Proof pillar is invisible** in the nav.
  - **Evidence** (the moat) is buried inside Segment/Visit detail **sheets** — users rarely discover it.
  - **No `places` route** — there's no way to *browse* places; you stumble into Place Detail from a map marker or search.
  - **Categories, Place Review** discoverable only via badge/deep states.
- **Overloaded screens:**
  - **Settings** = 4 tabs × many collapsible groups (≈1000+ lines) — a kitchen sink.
  - **Insights** = 7 horizontally-scrolled tabs — most never seen past tab 2–3.
  - **Dashboard** trends toward a link-farm of quick-action cards compensating for missing nav.
- **Missing nav paths / shortcuts:**
  - No global "jump to today," no quick-add mileage purpose, no fast path from a photo to its place.
  - No home-screen widget / quick settings tile equivalents (shortcuts).

### 4.2 Proposed IA (reorg of existing capabilities)

**Reframe the 4 tabs around the 3 pillars + map, and promote a "Library":**

```
BOTTOM NAV (4) — pillar-aligned
  1. Today      (= Dashboard; the "now/Memory glance")
  2. Map        (spatial)
  3. Timeline   (Memory deep)
  4. Insights   (Habits)

PROMOTE a 5th access pattern — a "Library/Hub" entry (top-bar or Today header):
  Library groups the currently-orphaned destinations under clear headings:
    • Proof:   Mileage · Trips · Export
    • Places:  Places browse (NEW surface using existing Place screens) · Categories · Place Review
    • Memory+: Day Story · Search
    • System:  Reliability · Settings · About/Feedback
```

- **Surface a Places browse list** by composing the *existing* Place Detail/list capabilities behind a `places`-style hub entry (no new feature — just a reachable index of places already in the DB).
- **Promote Evidence:** add a persistent "Why?" affordance on every timeline/visit/segment card that opens the existing evidence sheet — turning a hidden asset into a visible trust signal.
- **Insights: collapse 7 tabs → a scrollable "feed" with a secondary tab bar** (Overview feed first; Movement/Patterns/Places/Weekly/Carbon/Social as filter chips, not 7 equal tabs).
- **Settings: progressive disclosure** — top-level "Tracking / Privacy / Data / Advanced" cards that drill in, instead of 4 dense tabs side-by-side.

### 4.3 Discoverability improvements (no new features)

| Problem | Fix (reorg) |
|---|---|
| Proof pillar invisible | Library hub + Today "Proof" shortcut row |
| Evidence hidden | Persistent "Why?" chip on cards → existing sheet |
| Places un-browsable | Places index entry reusing existing place screens |
| Insights overload | Feed-first + chips instead of 7 tabs |
| Settings overload | Card-based drill-in, search-in-settings |
| "Is it tracking?" | Make tracking status + Reliability reachable from Today header |

---

## §5 UX Audit (Phase 5)

> Heuristic scores (1–10), designer hypotheses — **validate with usability tests.** Criteria: Info Hierarchy (IH), Visual Hierarchy (VH), Cognitive Load (CL — higher = lower load), Discoverability (D), Accessibility (A11y), Trust (T), Perceived Quality (PQ). **Overall** = weighted mean (T and IH weighted ×1.5 given the product's trust thesis).

### 5.1 Scorecard

| Screen | IH | VH | CL | D | A11y | T | PQ | **Overall** |
|---|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| Dashboard | 6 | 6 | 5 | 7 | 6 | 7 | 6 | **6.2** |
| Map | 7 | 7 | 7 | 6 | 6 | 7 | 7 | **6.8** |
| Timeline | 6 | 6 | 6 | 6 | 6 | 8 | 6 | **6.4** |
| Insights | 5 | 5 | 4 | 5 | 6 | 7 | 6 | **5.5** |
| Place Detail | 7 | 6 | 6 | 6 | 6 | 8 | 6 | **6.6** |
| Trips | 6 | 7 | 7 | 4 | 6 | 7 | 7 | **6.2** |
| Mileage | 7 | 7 | 7 | 3 | 6 | 9 | 7 | **6.6** |
| Settings | 5 | 5 | 3 | 6 | 6 | 7 | 6 | **5.3** |
| Export | 7 | 6 | 6 | 5 | 6 | 8 | 6 | **6.4** |
| Search | 6 | 6 | 7 | 6 | 6 | 7 | 6 | **6.2** |
| Place Review | 6 | 6 | 6 | 4 | 6 | 7 | 6 | **5.9** |

**Cohort read:** strongest on **Trust** (the thesis is working) and **Map/Mileage/Place Detail**; weakest on **Insights & Settings** (cognitive overload) and **Discoverability** (Proof features hidden). Average ≈ **6.2/10** — a capable product with a polish-and-findability problem, not a feature problem.

### 5.2 Per-screen recommendations (top issues)

- **Dashboard (6.2):** Kill first-hour emptiness with a live "Capturing now" state; reduce quick-card clutter once nav improves; make rings the hero, not a grid of links.
- **Map (6.8):** Strongest core screen; add legend for transport colors; improve marker tap targets; cluster dense markers.
- **Timeline (6.4):** Inline correction (long-press), plain-language gap explanations, persistent "Why?" chip; tighten card rhythm.
- **Insights (5.5):** Biggest opportunity — collapse 7 tabs to a story-led feed; lead with 1–2 narrative insights; defer Carbon/Social behind chips.
- **Place Detail (6.6):** Promote evidence to a visible (collapsed) section; make rename/category one tap; show map snippet above the fold.
- **Trips (6.2):** Needs a nav home; proactive "trip detected" entry; enable title/cover.
- **Mileage (6.6):** Highest Trust score — but **D=3** (buried). Give it a Proof nav entry; add batch classify; surface the evidence badge as a selling point.
- **Settings (5.3):** Progressive disclosure; add settings search; group by mental model (Tracking/Privacy/Data) not tier.
- **Export (6.4):** Add format guidance + preview; one-tap "privacy export" preset (stripped coords).
- **Search (6.2):** Show recent/suggested when idle; highlight matches; filter chips visible by default.
- **Place Review (5.9):** Batch actions; inline evidence ("flagged because…"); an auto-accept threshold setting.

---

## §6 Design System Review (Phase 6)

> Exact tokens in [catalog Part 4](./VOYAGER_FEATURE_CATALOG_AND_UX_SPEC.md#part-4--design-tokens-honor-these-exactly). Evaluated vs Material 3, Apple HIG, Google Maps, Arc.

### 6.1 Colors
- **Strengths:** Coherent OLED-dark identity (Background `#0F0F1A`, Primary `#3B82F6`); semantic transport-mode palette (a Maps/Arc-grade touch); Premium gold reserved for Pro (good discipline).
- **Weaknesses:**
  - **Dark-only** — no light theme. HIG & M3 both expect light/dark; outdoor map legibility (vs Google Maps) and user preference suffer. *Threat for daytime drivers (P2).*
  - **No dynamic color** (intentional) — fine for brand, but forgoes M3 personalization.
  - **Contrast risks:** white-on-Primary ≈ **3.7:1** (below AA 4.5 for small text); muted `OnSurfaceVariant #8888A0` on Surface ≈ **4.9:1** (passes AA, fails AAA, risky at 12sp). See §8.
- **Improvements:** Add a **light theme** (or at least a high-contrast map mode); darken Primary or use `OnPrimary` text size ≥ large/bold; introduce a slightly lighter muted-text token for small captions.

### 6.2 Spacing
- **Weakness:** **No named spacing scale** — raw dp (4/8/12/16/24) scattered. M3 and Arc both rely on a token grid; ad-hoc spacing is the #1 source of visual inconsistency.
- **Improvement:** Adopt `VoyagerSpacing { xs4, sm8, md12, lg16, xl24, xxl32 }` and refactor screens to it. Define an 8pt baseline grid.

### 6.3 Typography
- **Strengths:** Excellent **dual-font system** (Inter UI + JetBrains Mono for data) — distinctive, data-credible, more deliberate than GT/Arc. Full M3 scale present.
- **Weaknesses:** Mono not consistently applied (catalog notes this); Great Vibes brand font is charming but must never carry data; line-length/letter-spacing untuned for small mono at 12–13sp.
- **Improvements:** Enforce "**numbers = mono**" lint/convention; cap Great Vibes to wordmark; verify mono legibility at min sizes; add Dynamic Type / `fontScale` testing.

### 6.4 Components
- **Strengths:** Mature library (`VoyagerCard` variants, `ConfidenceBar`, `EmptyStateComposable` typed states, `DayNavigator`, `FeatureGate`) — ahead of a typical v1.
- **Weaknesses vs Arc/HIG:** No standardized **list-row** component; sheets/dialogs inconsistent; no skeleton variety beyond `ShimmerCard`; no documented focus/pressed/disabled states per component; tab styling re-specified per screen.
- **Improvements:** Add a canonical `VoyagerListRow`, a sheet template, a component state matrix (default/hover-n/a/pressed/focused/disabled), and centralize tab styling (M3 default indicator + `contentColor`, never custom SecondaryIndicator lambda — per house rule).

### 6.5 Benchmark summary

| Axis | Voyager | M3 | HIG | Google Maps | Arc |
|---|---|---|---|---|---|
| Theming | Dark-only ◐ | light+dark+dynamic | light+dark | adaptive | refined dark+light |
| Spacing tokens | missing ◐ | 8pt grid | 8pt-ish | grid | tight grid |
| Type system | dual-font ✅ strong | M3 scale | SF Dynamic Type | Roboto | custom, crafted |
| Data viz color | semantic ✅ | generic | generic | mode colors | tasteful |
| Component states | partial ◐ | full | full | full | full |

---

## §7 Motion System (Phase 7)

**Principles:** *(1)* motion clarifies causality (where did this come from / go to); *(2)* fast on entry, gentle on exit; *(3)* data earns emphasis (counters animate, chrome doesn't); *(4)* **respect Reduce Motion** — replace movement with cross-fades, keep ≤100ms. Durations follow M3-ish bands: micro 100ms, standard 200–300ms, large/shared 350–450ms. Default easing = standard `cubic-bezier(0.2,0,0,1)` (emphasized-decelerate for enters).

| Interaction | Duration | Easing | Transition | Feedback |
|---|---|---|---|---|
| Tab switch (bottom nav) | 200ms | standard | cross-fade + 8dp slide of content | haptic light; selected icon tint+scale 1.0→1.1→1.0 |
| Push nav (screen) | 300ms enter / 250ms exit | emphasized-decel / accel | shared-axis X slide + fade | predictive-back follows finger |
| Bottom sheet (Segment/Visit) | 300ms in / 250ms out | decel / accel | slide-up from bottom, scrim fade 0→60% | drag handle; rubber-band over-drag |
| Card tap | 120ms | standard | scale 1.0→0.98 press, back on release | haptic `LongPress`; ripple |
| Card enter (list) | 250ms, 30ms stagger | emphasized-decel | fade + 12dp rise | — |
| Map camera (day change) | 450ms | emphasized | fly-to bounds | marker pop-in 150ms stagger |
| Marker tap → sheet | 120ms + 300ms | standard then decel | marker bounce, then sheet up | haptic; selected marker enlarges |
| Activity rings load | 800ms | decel | sweep 0→value | counter tick in `MonoStat` |
| Counter / stat update | 400ms | standard | number roll | subtle color flash on change |
| Loading (skeleton) | 1200ms loop | linear | shimmer sweep L→R | — |
| Pull-to-refresh | follows finger + 300ms snap | decel | spinner + content settle | haptic on trigger |
| Onboarding pager | 300ms | emphasized | shared-axis X + parallax illustration | dot indicator morph |
| Live tracking pulse | 1500ms loop | ease-in-out | `PulsingDot` opacity/scale | — |
| Toast / snackbar | 200ms in, 150ms out | decel / accel | slide-up + fade | — |
| FAB (✦ record) | 200ms | emphasized | scale-in; morph to stop on record | strong haptic on start/stop |
| Empty→data reveal | 350ms | emphasized-decel | cross-fade empty→content | — |

**Reduce Motion:** all slides/scales → 100ms cross-fades; rings/counters snap to value; map jumps without fly-to; pulse becomes static dot.

---

## §8 Accessibility Audit (Phase 8)

> Ratios computed from the real palette (WCAG 2.1). Findings are concrete; **verify on-device with TalkBack + large font.**

### 8.1 Color contrast

| Pair | Ratio | AA normal (4.5) | AA large (3.0) | Verdict |
|---|---|:--:|:--:|---|
| OnSurface `#E8E8F0` on Background `#0F0F1A` | ~15:1 | ✅ | ✅ | Excellent |
| OnSurface on Surface `#1A1A2E` | ~13:1 | ✅ | ✅ | Excellent |
| Primary `#3B82F6` text on Background | ~5.2:1 | ✅ | ✅ | OK |
| **White on Primary** (buttons) | **~3.7:1** | ❌ | ✅ | **Fix: small label fails AA** |
| OnSurfaceVariant `#8888A0` on Surface | ~4.9:1 | ✅ | ✅ | Pass AA, **fails AAA**; risky at ≤12sp |
| OnSurfaceVariant on Background | ~5.5:1 | ✅ | ✅ | OK |
| Warning `#FFA726` text on Surface | ~8:1 | ✅ | ✅ | OK (but amber as *text* on dark only) |
| TransportGap `#616161` on Surface | ~2.0:1 | ❌ | ❌ | OK as line color, **not for text** |

**Fixes:** (1) For filled buttons, use **bold/large** label or darken to `PrimaryDim #2563EB` (raises ratio) or use dark text token; (2) never use `OnSurfaceVariant` below 12sp for essential text — bump to `OnSurface` for captions that matter; (3) never render text in transport/gap greys; pair color with icon/label always.

### 8.2 Screen reader (TalkBack)
- **Good:** icon buttons carry `contentDescription` (per UI audit).
- **Gaps:** custom canvas (Activity rings, heatmap ✦, charts) likely lack semantics; `ConfidenceBar` needs a text alternative ("confidence 92%, high"); map markers need labeled descriptions; timeline cards should expose a single meaningful reading order (place, time, mode, confidence).
- **Fixes:** add `Modifier.semantics{}` to all custom-drawn data; merge card semantics into one node; announce live-tracking state via `liveRegion`; label GAP rows with reason.

### 8.3 Touch targets
- **Good:** M3 buttons ≥48dp.
- **Risks:** map markers, timeline connector dots, small chips, swipe handles may be <48dp.
- **Fixes:** enforce 48dp min hit area (expand touch beyond visual where needed); increase marker tap slop.

### 8.4 Typography scaling / Dynamic Type
- **Risk:** fixed `sp` plus dense mono stats may clip/truncate at `fontScale` 1.3–2.0; mono columns may misalign.
- **Fixes:** test at 200% font scale; allow wrap/scroll; avoid fixed-height stat tiles; cap but don't ignore scaling.

### 8.5 Motor accessibility
- Heavy reliance on **swipe** (classify, confirm, correct) — add **tap/long-press alternatives** for every swipe action; ensure predictive-back doesn't conflict with horizontal swipes.

### 8.6 Cognitive accessibility
- 7-tab Insights & 4-tab Settings raise load (§4/§5). Plain-language gap/evidence copy, progressive disclosure, consistent correction patterns, and "one primary action per screen" reduce it. Avoid jargon ("HDBSCAN," "hysteresis") in user-facing copy.

---

## §9 States (Phase 9)

Per screen: **empty · first-use · loading · error.** Uses the real `EmptyStateComposable` types (NO_TRACKING/NO_PERMISSION/NO_PLACES/NO_INSIGHTS). Illustration concepts share a constellation/cartography motif (Orion's-Belt dots, contour lines) to stay on-brand.

| Screen | State | Headline | Subheadline | CTA | Illustration |
|---|---|---|---|---|---|
| **Dashboard** | First-use | "Voyager is capturing now" | "Move around for a few minutes — your first places will appear here." | View permissions | Pulsing constellation forming |
| | Empty (no track) | "Tracking is off" | "Turn on tracking to start your timeline." | Start tracking | Dim dormant dot |
| | Loading | — | — | — | Shimmer rings |
| | Error (perm) | "Voyager can't see your location" | "Grant location access to rebuild your day." | Fix permissions | Broken contour line |
| **Timeline** | Empty | "No movement recorded" | "Looks like a still day — or tracking was paused." | See why (gaps) | Flat horizon |
| | Loading | — | — | — | Shimmer rows |
| **Map** | Empty | "Nothing to map yet" | "Your routes will draw here as you move." | — | Empty grid with compass |
| **Insights** | Empty (NO_INSIGHTS) | "Insights are warming up" | "A few days of movement unlocks patterns and anomalies." | — | Rising bar sparkline |
| **Places** | Empty (NO_PLACES) | "No places yet" | "Spend time somewhere and Voyager will remember it." | — | Map pin outline |
| **Mileage** | Empty | "No drives logged" | "Drives are detected automatically — take a trip and check back." | How it works | Dotted road |
| | Pro-gated | "Mileage is a Pro feature" | "Auto-classify drives and export audit-ready reports." | Unlock Pro | Gold receipt |
| **Trips** | Empty | "No trips detected" | "Travel 2+ days from home and a trip appears automatically." | — | Suitcase + arc |
| **Day Story** | Empty | "No photos for this day" | "Photos you take will line up with the places you visited." | — | Polaroid stack |
| **Search** | Idle/first | "Search your whole timeline" | "Try a place, a date, or 'café'." | — | Magnifier over map |
| | No results | "Nothing found" | "Try a different place or widen the date range." | Clear filters | Empty constellation |
| **Place Review** | Empty | "All caught up" | "No visits need your review right now." | — | Checkmark constellation |
| **Export** | First | "Export your data, your way" | "GPX for maps, CSV for spreadsheets, JSON for backup." | — | Outbound arrows |
| **Reliability** | Healthy | "Everything's running" | "Tracking, workers, and battery look good." | — | Steady heartbeat |

**Error pattern (global):** every error state names *what broke*, *why*, and *one fix CTA* — never a dead end (honesty principle). Loading uses `ShimmerCard`; never spinners-only on data screens.

---

## §10 Screen Blueprints (Phase 10)

Format: **Purpose · Primary Goal · Secondary Goals · Content Hierarchy · Visual Hierarchy · Layout · Interaction Model · Success Metrics.** Depth = top-10 deep, rest concise (per priorities §11).

### ★ Top-10 (deep)

#### 1. Dashboard / Today
- **Purpose:** the daily "am-I-tracked, what-happened, what's-notable" glance.
- **Primary goal:** reassure tracking is working + show today at a glance.
- **Secondary:** route to Proof shortcuts; surface anomalies/top places.
- **Content hierarchy:** tracking status → today's rings (distance/steps/active) → notable (anomaly/top place) → shortcuts.
- **Visual hierarchy:** rings hero (largest, glow) → status pill → cards descending.
- **Layout:** TopBar · hero card · ring block · section list · shortcut row.
- **Interaction model:** glanceable; tap ring → Insights; tap place → Place Detail; live pulse when active.
- **Success metrics:** time-to-reassurance <3s; % sessions that confirm "tracking on"; first-hour retention.

#### 2. Timeline
- **Purpose:** the honest, editable story of a day.
- **Primary:** let users read & trust the day's segments/visits.
- **Secondary:** correct errors inline; jump to detail/evidence; reach Day Story.
- **Content hierarchy:** day selector → chronological segment/visit/gap cards → per-row mode/time/confidence.
- **Visual hierarchy:** connector rail + mode color as the spine; place names bold; timestamps mono secondary.
- **Layout:** TopBar · DayNavigator · PeriodSelector · vertical timeline.
- **Interaction:** tap → detail sheet; **long-press → correct**; "Why?" chip → evidence; swipe day to change.
- **Success metrics:** correction completion rate; % days reviewed; gap-comprehension (do users understand gaps in tests).

#### 3. Map
- **Purpose:** spatial truth of where you went.
- **Primary:** show today's routes + visits legibly.
- **Secondary:** inspect a segment/visit; navigate days.
- **Content hierarchy:** map canvas → day selector → detail sheet on tap.
- **Visual hierarchy:** routes (mode color) and markers over a muted basemap; selected element pops.
- **Layout:** full-bleed map · floating DayNavigator · bottom sheet.
- **Interaction:** pan/zoom; tap marker/route → sheet; fly-to on day change; legend toggle.
- **Success metrics:** marker tap success rate; map session length; color-legend comprehension.

#### 4. Insights *(redesign target)*
- **Purpose:** make habits legible and trustworthy.
- **Primary:** deliver 1–2 meaningful weekly insights fast.
- **Secondary:** allow drill into movement/patterns/places/carbon; explain numbers.
- **Content hierarchy:** **story feed first** → metric sections (filterable) → evidence-on-tap.
- **Visual hierarchy:** narrative insight card hero → trend deltas → charts.
- **Layout:** TopBar · feed · filter chips (replace 7 equal tabs).
- **Interaction:** scroll feed; chip-filter; tap stat → "how computed."
- **Success metrics:** insight read-through; tab→feed engagement lift; "I trust this number" test score.

#### 5. Mileage *(Proof flagship)*
- **Purpose:** turn drives into defensible deductions.
- **Primary:** classify drives fast; export audit-ready report.
- **Secondary:** attach purpose/notes; inspect GPS evidence.
- **Content hierarchy:** period + deductible summary → drive list → per-row evidence → export.
- **Visual hierarchy:** big money/miles (mono) hero → classifiable rows → gold export CTA.
- **Layout:** TopBar · PeriodSelector · summary · list · export bar.
- **Interaction:** swipe **and** tap to classify; batch select; row → evidence; export PDF.
- **Success metrics:** classify time per drive; % classified; export completion; trust-badge recall.

#### 6. Place Detail
- **Purpose:** the trustworthy profile of a place + correction hub.
- **Primary:** confirm/correct name & category once, everywhere.
- **Secondary:** show visit history/patterns; reveal evidence.
- **Content hierarchy:** name/category header → map snippet → visit history → **evidence (collapsed)** → corrections.
- **Visual hierarchy:** name largest; confidence chip; evidence discoverable but secondary.
- **Layout:** header · map · list · collapsible evidence · action row.
- **Interaction:** one-tap rename/category; merge/split (advanced); expand evidence.
- **Success metrics:** correction success; rename persistence understanding; evidence open rate.

#### 7. Trips *(+ Trip Detail)*
- **Purpose:** auto-assembled travel memory & proof.
- **Primary:** review/keep a detected trip.
- **Secondary:** per-day breakdown; TripBook PDF; (✦) title/cover.
- **Content hierarchy:** trip cards → Trip Detail (hero map, per-day, export).
- **Visual hierarchy:** hero route map; dates/distance mono; PDF gold.
- **Layout:** list → detail with DayNavigator.
- **Interaction:** confirm trip; scrub days; export.
- **Success metrics:** trip confirm rate; PDF export; "delight" rating on auto-detection.

#### 8. Settings *(redesign target)*
- **Purpose:** configure tracking, privacy, data — without overwhelm.
- **Primary:** find & change one setting fast.
- **Secondary:** apply presets; privacy controls; advanced/calibration.
- **Content hierarchy:** **search** → grouped cards (Tracking · Privacy · Data · Advanced) → drill-in.
- **Visual hierarchy:** group cards > rows > controls; destructive (duress ✦) clearly red.
- **Layout:** searchable list of category cards → sub-screens (replace 4 dense tabs).
- **Interaction:** tap card → sub-screen; toggle; preset picker; confirm destructive.
- **Success metrics:** time-to-setting; settings-search usage; mis-toggle rate.

#### 9. Onboarding
- **Purpose:** earn trust, get permissions, show value — in that order, fast.
- **Primary:** convert install → tracking-on with a value moment.
- **Secondary:** persona/preset selection; optional import.
- **Content hierarchy:** value/trust framing → persona pick → **live "capturing" moment** → permissions (just-in-time) → done.
- **Visual hierarchy:** one message per screen; progress dots; honest permission rationale.
- **Layout:** pager + selectable persona cards + permission cards.
- **Interaction:** swipe pager; pick persona; grant per-need.
- **Success metrics:** onboarding completion; permission grant rate; D1 retention.

#### 10. Search
- **Purpose:** retrieve any place/day/visit by fuzzy memory.
- **Primary:** find the right result fast.
- **Secondary:** filter by date/category/mode/dwell.
- **Content hierarchy:** field → (idle: recent/suggested) → ranked grouped results.
- **Visual hierarchy:** matched terms highlighted; type-grouped sections.
- **Layout:** search bar · chips · results.
- **Interaction:** type; chip-filter; tap result → detail.
- **Success metrics:** search success rate; time-to-result; zero-result rate.

### Secondary screens (concise blueprints)

- **Export** — *Purpose:* own/share data. *Primary:* produce a clean export. *Hierarchy:* format → range → privacy toggles → export. *Interaction:* select+export+share. *Metric:* export success, privacy-preset use.
- **Place Review** — *Purpose:* clear visit candidates. *Primary:* confirm/clean in bulk. *Hierarchy:* queue → per-card evidence/actions. *Interaction:* tap/swipe confirm/reject, batch. *Metric:* queue clear rate, time/card.
- **Day Story** — *Purpose:* photo memory of a day. *Primary:* browse photo↔place story. *Hierarchy:* day → photo/visit timeline → unplaced. *Metric:* story views, unplaced resolved.
- **Reliability** — *Purpose:* prove the app is healthy. *Primary:* reassure tracking/battery/workers OK. *Hierarchy:* status → battery → workers → health log. *Metric:* trust rating, return after scare.
- **Carbon (Insights tab)** — *Purpose:* driving emissions over time. *Hierarchy:* vehicle → daily/weekly/monthly. *Metric:* vehicle-setup completion, repeat views.
- **Paywall** — *Purpose:* convert to Pro. *Hierarchy:* value → feature list → price → buy/restore. *Metric:* view→purchase, restore success.
- **Categories** — *Purpose:* manage place categories. *Hierarchy:* list → edit → reassign. *Metric:* edit completion.
- **Developer Profile / Licenses / Feedback** — *Purpose:* trust/credits/support. *Hierarchy:* info list / form. *Metric:* feedback submissions.
- **Debug (Data Insertion / Pipeline)** — *Purpose:* internal QA only. *Hierarchy:* controls/diagnostics. *Metric:* n/a (dev).
- **Segment/Visit Detail sheets** — *Purpose:* inspect + evidence. *Hierarchy:* stats → evidence → correct. *Metric:* evidence open, correction rate.

---

## §11 Design Priorities (Phase 11)

**Ranking logic:** *(pillar centrality × usage frequency × competitive leverage × current-score deficit).* High-frequency, trust-bearing, or currently-weak screens rank highest.

### Top 10 (most design effort)

| Rank | Screen | Why it earns the effort |
|---|---|---|
| 1 | **Timeline** | The Memory heart; daily use (P1); trust thesis lives here; current 6.4 with fixable correction/gap UX. Beating GT/Arc happens on this screen. |
| 2 | **Dashboard / Today** | First thing seen every session; owns the **first-hour emptiness** problem (PP1) — the biggest retention risk. |
| 3 | **Insights** | Lowest score (5.5), highest upside; Habits pillar; 7-tab overload is the clearest redesign win. |
| 4 | **Onboarding** | Decides activation & permission grant; sets the trust tone; current flow shows value too late. |
| 5 | **Mileage** | Proof flagship; highest Trust (9) but lowest Discoverability (3) — promoting it unlocks a paying segment (P2) and beats MileIQ. |
| 6 | **Map** | Strong (6.8) and high-frequency; small polish (legend, markers, clustering) yields outsized perceived quality. |
| 7 | **Place Detail** | Where corrections + evidence converge; fixing the place-naming weakness (PP2) is a correction-UX problem solved here. |
| 8 | **Settings** | Second-lowest (5.3); houses privacy controls central to the brand; progressive disclosure is high-impact. |
| 9 | **Trips** | Differentiated delight (auto-detect) but buried; promoting + enabling titles/cover competes with Poly. |
| 10 | **Search** | Unlocks the whole archive's value (P1/P4); cheap to elevate from 6.2 with idle suggestions + match highlighting. |

**Below the line (maintain, don't over-invest):** Export, Place Review, Day Story, Reliability, Carbon, Paywall, Categories, About/Feedback/Licenses, Debug. These matter but are lower-frequency or already adequate; invest after the Top 10.

---
---

# Part B — Packaged Outputs (Phase 12)

## B1 Product Design Bible

**One-paragraph thesis.** Voyager wins by being the **trustworthy** timeline — the only one that is private by architecture, honest about what it doesn't know, and able to explain everything it does. Design must therefore optimize for **trust and legibility first, polish second, breadth last.** The strategy: *(a)* fix the **first-hour** so the app proves itself immediately (Dashboard, Onboarding); *(b)* make the **daily Memory loop** (Timeline, Map, Search, Place Detail) effortless and correctable; *(c)* **surface the Proof pillar** (Mileage, Trips, Export) that today is hidden; *(d)* turn **Insights** from a tab-farm into a story; *(e)* hold the **trust line** everywhere via visible evidence, honest gaps, and privacy controls. North-star outcome: a user who, after one week, says *"it knows where I was, I trust it, and it never left my phone."*

**Vision → Principles → Priorities chain:** privacy/evidence/honesty (vision) → the 8 principles (B3) → the Top-10 screen investments (§11).

## B2 UX Playbook

**Repeatable patterns**
- **Card + "Why?" chip:** any data claim (visit/segment/place/insight) pairs with an optional evidence affordance opening the existing evidence sheet.
- **Correct-in-place:** long-press any timeline/place row → correction menu (rename/category/reclassify/merge/split) + undo + "this helps Voyager learn."
- **Honest empty/gap:** every absence is explained with a reason + one CTA; never a blank or a spinner-only.
- **Progressive disclosure:** advanced controls (calibration, merge/split, raw export) live behind a clear "Advanced," never on the first surface.
- **Proof shortcuts:** Mileage/Trips/Export reachable from a persistent hub, not only Dashboard cards.

**Do / Don't**
- ✅ Lead screens with one primary action. ❌ Don't present 7 equal tabs.
- ✅ Numbers in mono, UI in Inter. ❌ Don't put data in Great Vibes.
- ✅ Color + icon/label together. ❌ Don't encode meaning in color alone.
- ✅ Explain jargon in plain words. ❌ Don't show "HDBSCAN/hysteresis" to users.
- ✅ Give every swipe a tap alternative. ❌ Don't gate core actions behind gestures.

**Content & voice:** calm, factual, trustworthy; first-person from the app sparingly ("I'm capturing now"); never alarmist; name uncertainty honestly ("about 92% sure — here's why").

## B3 Design Principles

1. **Privacy is the product.** If a choice trades privacy for convenience, the privacy default wins; make on-device-ness *visible*.
2. **Honest gaps.** Never fabricate continuity; show missing data with a reason. Trust is built at the seams.
3. **Evidence on tap.** Every claim can explain itself — including why alternatives were rejected.
4. **Numbers are mono.** Data wears JetBrains Mono; it signals precision and credibility.
5. **One record, many jobs.** Reuse the same timeline for Memory, Proof, Habits — never re-ask the user.
6. **Correctable by design.** Errors are expected; make fixing them a one-tap, learning act.
7. **Calm, legible, dark.** OLED-first clarity; one primary action per screen; restraint over decoration.
8. **Earn the first hour.** The app must prove it's working before it has much to show.

## B4 User Journey Maps

Canonical maps live in [§3](#3-user-journeys-phase-3). Index: J1 Daily Review · J2 Mileage · J3 Trip Detection · J4 Day Story · J5 Export · J6 Place Review · J7 Corrections · J8 Weekly Insights · J9 Carbon · J10 Onboarding. **Highest-friction maps to fix first:** J10 (value-after-trust), J1 (correction/gaps), J2 (discoverability), J8 (tab overload).

## B5 Screen Blueprints

Full blueprints in [§10](#10-screen-blueprints-phase-10). Build order follows [§11](#11-design-priorities-phase-11) Top-10. Each blueprint's **Success Metrics** double as the acceptance criteria for its redesign.

## B6 Accessibility Guide (checklist)

- [ ] **Contrast:** fix white-on-Primary buttons (bold/large label or darker fill); never use `OnSurfaceVariant` <12sp for essential text; never text in gap/transport greys.
- [ ] **Screen reader:** add `semantics{}` to all custom-drawn data (rings, charts, `ConfidenceBar`, heatmap ✦); merge card semantics; `liveRegion` for tracking state; label GAP reasons & map markers.
- [ ] **Targets:** 48dp min hit area everywhere (expand markers, connector dots, chips, handles).
- [ ] **Type scaling:** verify at `fontScale` 200%; allow wrap/scroll; no fixed-height stat tiles.
- [ ] **Motor:** every swipe action has a tap/long-press equivalent; resolve back-swipe vs horizontal-swipe conflicts.
- [ ] **Cognitive:** progressive disclosure (Settings/Insights); plain-language copy; consistent correction pattern; one primary action/screen.
- [ ] **Motion:** honor Reduce Motion (cross-fades ≤100ms; snap counters; static pulse).
- [ ] **Theming (longer-term):** add a light/high-contrast mode for outdoor/daytime use.

## B7 Motion Guide

Quick-reference table in [§7](#7-motion-system-phase-7). **Defaults:** micro 100ms · standard 200–300ms · large/shared 350–450ms; easing standard `(0.2,0,0,1)`, emphasized-decelerate for enters. **Non-negotiables:** predictive-back on push nav; data animates, chrome doesn't; Reduce-Motion path defined for every entry.

## B8 Design Review Report

**Overall product UX (hypothesis): ~6.2/10** — strong trust foundation, real findability & polish debt. Scorecard in [§5](#5-ux-audit-phase-5).

**Prioritized remediation backlog**

| Pri | Item | Screens | Source |
|---|---|---|---|
| **P0** | Kill first-hour emptiness ("Capturing now" live state) | Dashboard, Onboarding | PP1, §11 #2/#4 |
| **P0** | Surface the Proof pillar in navigation | IA, Mileage, Trips, Export | PP4, §4, §11 #5/#9 |
| **P0** | Fix white-on-Primary contrast + custom-draw semantics | global | §8 |
| **P1** | Insights: 7 tabs → story feed + chips | Insights | §5, §11 #3 |
| **P1** | Inline correction + plain-language gaps + "Why?" chip | Timeline, Place Detail | PP2/PP3, §11 #1/#7 |
| **P1** | Settings: progressive disclosure + search | Settings | §5, §11 #8 |
| **P1** | Adopt named spacing scale + component state matrix | design system | §6 |
| **P2** | Map legend/marker/clustering polish | Map | §5, §11 #6 |
| **P2** | Search idle suggestions + match highlight | Search | §11 #10 |
| **P2** | Systematize motion (Reduce-Motion paths) | global | §7 |
| **P2** | Places browse surface (reuse existing screens) | IA | §4 |
| **P3** | Light/high-contrast theme | design system | §6/§8 |

**Definition of done for the discovery phase:** Top-10 screens have agreed blueprints + success metrics; P0/P1 backlog scheduled; accessibility fixes ticketed; motion + spacing tokens specified. **Next phase = redesign** (out of scope here, by request).

---

*End of Product Design Bible. Companion: [`VOYAGER_FEATURE_CATALOG_AND_UX_SPEC.md`](./VOYAGER_FEATURE_CATALOG_AND_UX_SPEC.md). All research artifacts are designer hypotheses pending user validation.*
