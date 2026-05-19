# Voyager — Competitive Readiness & Improvement Plan

_Date: 2026-05-19_
_Verified against the live codebase (Phases 0–3 complete; Phase 4 partial; Phase 5 built but not transacting)._
_Reads with: `docs/research/competitor-analysis.md`, `core-hardening-audit.md`, `edge-cases-and-hidden-bugs.md`, `hardening-execution-plan.md`._

---

## 1. Verdict

Voyager **out-competes the timeline / proof / mileage field today** and is a credible
**Polarsteps and Arc alternative on Android**. It is **not** a Life360 or Strava
competitor — and was never built to be. Three features (family handshake, duress
mode, OSM contribution loop) plus a visual-polish pass and live billing are all
that stand between the app and "complete" against its own stated vision.

**One-line summary:** the engine and feature breadth are there; what's left is
*polish, monetization plumbing, and three differentiator features* — not core work.

---

## 2. Head-to-head — where Voyager stands

| Competitor | Their edge | Voyager today | Verdict |
|---|---|---|---|
| **Google Timeline** | Auto places/drives, photo correlation, free, default-installed | Parity **+ Evidence Cards, full exports, direct Timeline import** (with onboarding entry) | **Voyager wins** on privacy, portability, explainability. Loses only on default-install reach |
| **Arc (iOS)** | Best-in-class timeline UX | Feature parity (timeline, modes, places) | **Competes as the Android answer** — Arc still wins on pure visual polish |
| **Polarsteps** | Trip stories, printed travel books, social feed | Trip detection + **TripBook PDF** (printable journal) | **Competes on storytelling**; Polarsteps wins on the physical print product + social |
| **Life360** | Family location sharing, safety brand | **Nothing** — family handshake not built | **Cannot compete** in family-safety yet |
| **Strava** | Routes, segments, leaderboards, heatmap, social, fitness | Movement segments, modes, distance, carbon — **no social, no leaderboards, no heatmap** | **Different job — doesn't and shouldn't compete** as a fitness app |
| **MileIQ** | Auto mileage + IRS PDF | Mileage log + IRS/HMRC PDF **+ GPS evidence per row** | **Voyager beats it** — and bundles it as 1 of 10 features |
| **OwnTracks** | Geek-perfect on-device privacy | Same privacy, real UX | **Voyager wins** |
| **Daylio / Day One** | Mood/journal with location pins | Auto **Day Story** (photos↔visits) | Partial overlap; different intent |
| **GeoTracker / OSMTracker** | GPX export + OSM contribution | GPX/GeoJSON/CSV export; **no OSM contribution** | Wins on UX, ties on export, loses the contribution angle |

---

## 3. The 7 differentiators — scorecard

From `competitor-analysis.md` §2 — the whitespace nobody else owns:

| # | Differentiator | Status |
|---|---|---|
| 1 | Three jobs in one app (Memory + Proof + Habits) | ✅ Built — persona system, all 3 surfaces |
| 2 | Genuine on-device + breadth on Android | ✅ Built — encryption always-on, no cloud |
| 3 | Evidence + explainability | ✅ Built — Evidence Cards in detail sheets |
| 4 | Mileage with court/IRS-grade GPS evidence | ✅ Built — incl. raw-sample export |
| 5 | Family one-bit handshake | ❌ Not built |
| 6 | Duress mode for journalists/activists | ❌ Not built |
| 7 | Local-first OSM contribution loop | ❌ Not built (OSM is read-only for names) |

**4 of 7 shipped.** The 3 gaps are the remaining Phase 4 items.

---

## 4. What is already built (verified)

**Core surfaces:** Timeline · Map (MapLibre) · Dashboard · Insights (7 analytics tabs
incl. Carbon) · Places / Visits / Segments · Search (FTS).

**Pro / monetizable:** Trips + TripBook PDF · Photo Day Story · Mileage log + tax PDF ·
Evidence Cards · Carbon footprint · advanced Insights — all behind `FeatureGate`.

**Trust & reliability:** Reliability screen · Battery self-report · Daily Recap ·
encryption always-on · `FLAG_SECURE` toggle.

**Data portability:** Export (GPX / GeoJSON / CSV / VoyagerJSON, date ranges, raw
samples) · Import (`.voyager` restore + Google Timeline, both with onboarding entry).

**Foundation (hardening sprint done):** `LocalCrashHandler` · `backup_rules.xml` +
`data_extraction_rules.xml` + `network_security_config.xml` · soft-delete /
`revision` / `lastModifiedAt` columns · `GeocodingProvider` plugin interface ·
v1→v5 migrations tested · `runBlocking` eliminated from main.

---

## 5. Gaps — what is left or skipped

### 5a. Ship-blockers (gate a public launch — fix first)
- **Play Console products not created.** Billing code exists but there is no live
  purchase path — the Pro tier is built but cannot transact.
- **3 remaining `!!` in screen code** — the hardening gate wanted zero.
- **No verified crash-free dogfood across OEMs** (audit gate item).

### 5b. Competitive-parity gaps (visual & UX — where rivals still win)
- **No visual redesign / motion layer** (Phase 2) — Arc out-polishes Voyager.
- **No heatmap / year-in-review visualization** — a Google Timeline & Strava staple
  and a strong shareable-moment.
- **First-hour emptiness** — place detection needs ~10 min of movement; the app can
  feel dead on first launch.

### 5c. Differentiator features not built (Phase 4)
- Family one-bit handshake → unlocks the Life360 segment (needs security review).
- Duress mode / panic-wipe → the journalist/activist niche (needs security review;
  destructive — handle with care).
- OSM contribution loop → the "commons contributor" angle (needs an OSM OAuth
  write subsystem).

### 5d. Future-proofing deliberately deferred (not user-visible)
- `ExportFormatPlugin` / `PipelineGateway` interfaces, typed-ID value classes,
  `userId` / multi-user column. Fine to defer until cloud / iOS / B2B is real.

---

## 6. Improvement plan — compete on every axis

Ordered by leverage. Each tier states *what*, *why*, and *which competitor gap it closes*.

### P0 — Make the product shippable & earning (days, not weeks)
| Item | Why | Closes |
|---|---|---|
| Create `pro_monthly` / `pro_yearly` / `pro_lifetime` in Play Console; pricing; license testers; data-safety form | The paid tier cannot earn a cent until this exists | Monetization vs MileIQ/Polarsteps |
| End-to-end billing test on a real device | Proves purchase → entitlement → unlock | — |
| Remove the last 3 `!!` in screens; run a 7-day crash-free dogfood across Pixel + Xiaomi + Samsung + OnePlus | Hardening gate; 1-star defence | Reliability vs all |

### P1 — Visual & UX polish (the biggest perceived-quality gap)
| Item | Why | Closes |
|---|---|---|
| Dashboard / Timeline / Map / onboarding **visual redesign** with a coherent design language | Arc wins on polish today; first impression decides retention | **Arc**, Google Timeline |
| **Motion layer** — predictive-back, animated counters, activity-ring draw-on, shared-element transitions | Makes the app feel "alive" and premium; justifies the Pro price | **Arc**, Polarsteps |
| **First-hour "I'm capturing now" pulse** on the dashboard (live distance/steps before places exist) | Kills the empty-first-launch feeling; 1-day fix, outsized retention | Google Timeline |
| **Heatmap / Year-in-Review** visual (per-year movement heatmap + shareable card) | A staple rivals have and a viral shareable moment | Google Timeline, **Strava** |
| Design-system finish — kill inline literals, spacing tokens, `derivedStateOf` audit on hot paths | Consistency + recomposition performance | — |
| Accessibility pass — font-scale 130/180/200%, TalkBack on map (list fallback), color-blind-safe mode chips | Broadens the addressable market; review-score insurance | — |

### P2 — Complete the differentiators (own the whitespace)
| Item | Why | Closes |
|---|---|---|
| **Family one-bit handshake** (after a security design review) — no live stream, just an encrypted "I'm safe" bit | Opens the entire Life360 segment without being surveillance | **Life360** |
| **Duress mode / panic-wipe** (after a security design review) — cryptographically complete SQLCipher key destruction | The journalist/activist niche no competitor serves | unique |
| **OSM contribution loop** — let a place rename / missing-POI flow optionally write back to OSM (OAuth + changeset API) | Turns users into commons contributors; FOSS goodwill | **OSMTracker**, FOSS audience |

### P3 — Future-proofing (do before cloud / iOS / B2B, not before launch)
| Item | Why |
|---|---|
| `ExportFormatPlugin` + `PipelineGateway` interfaces; typed-ID value classes | Plugin marketplace & KMP/iOS seams |
| `userId` / `installationId` columns | Multi-user, family-pairing, B2B fleet mode |
| `SyncManager` interface (NoOp default) | Makes optional cloud an opt-in plugin, not a refactor |

---

## 7. Recommended sequence

1. **P0 first** — 1–2 weeks. Without billing the product cannot make money; without
   crash-free dogfood it cannot survive reviews. Nothing else matters more.
2. **P1 next** — 3–4 weeks. This is the single largest *perceived-quality* lever and
   the one thing standing between Voyager and Arc-grade polish. Ship it before any
   marketing push.
3. **P2** — per-feature, each on its own plan. Family + duress are gated behind a
   security review; OSM loop is a self-contained subsystem. Pick based on which
   market segment to enter next (family = mass market; duress = niche credibility;
   OSM = FOSS goodwill).
4. **P3** — only when cloud, iOS, or B2B becomes a real, dated commitment.

---

## 8. The honest bottom line

Voyager already beats Google Timeline, MileIQ, and OwnTracks, and competes with
Polarsteps and Arc on Android. The work that remains is **not** core engineering —
it is **(a) turning billing on, (b) a visual/motion polish pass to match Arc, and
(c) three differentiator features to enter the family and activist markets.**

Do P0 to earn, P1 to impress, P2 to dominate the whitespace. The foundation is
done; everything left compounds on it.
