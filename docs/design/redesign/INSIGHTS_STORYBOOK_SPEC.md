# Insights → "Storybook" — Concept & Spec

**Status:** Concept for review (Phase 1). No Compose code changes made yet.
**Scope:** The Insights tab (`presentation/screen/analytics/StatisticsScreen.kt`).
**Companion:** an HTML mockup Artifact of the flagship pages (Overview · Highlights · a Pro lens).

---

## 1. Why this exists

The brief asks for a **storybook, not a dashboard** — "every statistic becomes a sentence,
every routine becomes a personality trait." The important discovery is that **the analytics
already exist.** "Insights v2" shipped all nine sections the brief lists, each backed by a real,
tested engine:

| Brief section | Lens today | Engine already wired |
|---|---|---|
| Overview | `OVERVIEW` | weekly comparison + movement + top places |
| Weekly | `WEEKLY` | `observeComparisons` → `WeeklyComparisonData` |
| Routines | `PATTERNS` | `DetectRecurringPatternsUseCase`, `PredictNextPlaceUseCase` |
| Highlights | `HIGHLIGHTS` | `DetectNotableEventsUseCase`, `BuildOnThisDayUseCase` |
| Rhythm | `RHYTHM` | `DetectSleepRhythmUseCase`, `ComputeDayRhythmUseCase`, `AnalyzeCommuteUseCase` |
| Movement | `MOVEMENT` | `BuildHeatmapUseCase`, `YearInReview` |
| Balance | `BALANCE` | `ComputeTimeBudgetUseCase` → `TimeBudget` |
| Carbon | `CARBON` | `BuildCarbonFootprintUseCase` |
| Anomalies | `ANOMALIES` | `observeAnomalies`, `DetectRoutineBreaksUseCase` |

So this is a **presentation transformation**, not a data project. What's missing is the
*storytelling layer*: sentences are generated ad-hoc inside composables, `SynthesisHero` is the
only narrative primitive, every other lens is raw stat cards, and the loading skeleton
(`VoyagerShimmer`) exists but is never wired.

## 2. Locked decisions & verified constraints

- **Deliverable:** concept/spec first; taste is signed off before any Compose is written.
- **Free tier widened:** **Overview + Weekly + Highlights are free**; the other six lenses stay
  Pro. (The free trio is the "look back and relive it" hook — the most shareable pages.)
- **Look (revised after mockup review):** **flat OLED, no gradients at all** — drop the nebula
  `screenBackground` and the aurora washes/fills on this surface. Hierarchy comes from
  **typography**: the app's two faces (Inter for prose, JetBrains Mono for data), size contrast,
  and **one accent colour per chapter used only on text** (the chapter label + the single number
  that matters). Cards stay flat; frosted glass still not honored. **Compact/dense** — smaller
  type and tighter spacing than the first pass ("not so big").
- **Swipeable:** lenses become a **HorizontalPager** — one chapter per swipe (a flip-book),
  with a mono position indicator (e.g. `01 / 09`); the chip row remains as a tap shortcut.
- **Stack (verified on `fix-timeline-map-correctness`):** Compose BOM `2024.09.00` → Material3
  1.3.0. **No Material 3 Expressive, no BOM bump.** Everything the brief's motion needs
  (collapsing header, pull-to-refresh via material3 1.3; shared-element via Compose animation 1.7)
  is already available.
- **RouteBackdrop deferred:** the "map IS the app" atmosphere joins Insights when the
  cartographic track reaches it, not in this work.

## 3. Design thesis

> **Every statistic becomes a sentence. Every lens becomes a page you swipe.**
> A flip-book of your life — flat OLED, no gradients, dense and precise like a field almanac.
> The design is carried entirely by *type*: two faces, dramatic size contrast, and one colour
> per chapter. Nothing new to look at as a surface; everything new to *read*.

## 4. Voice & tone

The narrator is a thoughtful companion, never a robot, never a salesman, never creepy.

| Instead of… | Say… |
|---|---|
| "Distance: 35 km" | "You wandered farther than usual this week — 35 km across seven places." |
| "Routine detected." | "Tuesday evenings have quietly become gym time." |
| "Home: 62%" | "Most of your week was spent where you recharge — home stayed the center." |
| "Prediction: Gym 18:00" | "Looks like you'll head to the gym this evening." |
| "Anomaly: +2.1σ" | "You explored a new neighborhood — the farthest you've gone in a month." |

**Rules that keep it honest (already established in `SynthesisHero`):**
- Direction is carried by the **word** ("more / less / farther / calmer"), never by colour
  alone — accessible and unambiguous.
- The **period label is never hardcoded** ("this week"); it flows from the selected range.
- **Never over-claim.** Keep the existing caveats: Rhythm is "a proxy, not sleep tracking";
  Carbon is "a guide, not an audit." Predictions are gentle ("looks like…"), never certain.
- **Declines are framed kindly**, never as failure ("a calmer week — a little more time at home").

## 5. The architectural idea: a narrative layer

Today each sentence is hand-built inside a composable. Lift narrative generation into a small,
**testable** domain/VM layer so all nine pages speak in one voice and the copy can be unit-tested.

```
InsightNarrative(
    eyebrow: String,                 // uppercase micro-label ("WEEKLY SYNTHESIS")
    headline: NarrativeText,         // prose with highlighted spans (value + direction word)
    body: String? = null,            // optional supporting sentence
    metricChips: List<MetricChip>,   // e.g. "35 km", "7 places" (JetBrains Mono)
    tone: Tone                       // CELEBRATE / NEUTRAL / GENTLE / CURIOUS
)
```

- `NarrativeText` is a token list the UI turns into an `AnnotatedString` (so the highlight
  logic lives in one place, not per-composable).
- Reuse the precedent that already exists: `DashboardInsight(title, description, metricValue,
  trend)` in `domain/model/AnalyticsModels.kt`, and the `SynthesisHero` honesty conventions.
- Each lens gets one thin "narrator" (pure function over its already-computed model → a list of
  `InsightNarrative`), which is trivial to test with `*UseCaseTest`-style coverage.

**This narrator is the primitive the whole storybook is assembled from.**

## 6. The page system (reuse first)

Reusable "page" scaffolding, all reduce-motion-aware via `LocalReduceMotion`:

- **ChapterPager** — a `HorizontalPager` over the nine lenses (one chapter per swipe). A mono
  position indicator (`01 / 09`) and the existing chip row (tap shortcut) both stay in sync with
  the pager. Each page is a `LazyColumn` whose items rise in with `Modifier.staggeredEntrance`;
  pull-to-refresh re-runs `loadAllStatistics()`.
- **StoryHero** — generalization of today's `SynthesisHero`, but **flat** (no aurora tint):
  a mono chapter label + an Inter narrative sentence in which the key numbers switch to
  **JetBrains Mono + the chapter accent colour** (font-switch + colour = the emphasis, replacing
  gradients).
- **DataLedger** — replaces big stat tiles: dense typeset rows (`Inter` muted key · dotted
  leader · `mono` ink value · `mono` green/amber delta), hairline rules between. Compact.
- **Typographic metaphors** — where the old design used a coloured chart, use type instead. E.g.
  **Balance** = each part of the week as a **word sized by its share** (Home largest → Untracked
  tiny), the size *is* the chart; no pie, no bar.
- Chapter accent colour appears **only on text** (chapter label + the one key number), never as a
  fill. `FREE`/`PRO` tiers are small mono labels (green/gold text), not coloured chips.

**Reuse, do not re-create:** `VoyagerCard(FLAT)`, `VoyagerEyebrow`, `SectionHeader`,
`SectionDivider`, `AnimatedStat`/`animatedCount`, `VoyagerActivityRings`,
`SegmentedProgressBar`/`DayArcBar`, `ActivityHeatmapCard` + `YearInReviewCard`,
`SparklineChart`, `RouteSparkline`. Finally **wire `VoyagerShimmer`** (`isLoading` is tracked
but nothing renders today), and swap the local `EmptyStateMessage` for the richer shared
`EmptyStateComposable(NO_INSIGHTS)` / `ErrorStateComposable`.

## 7. The nine pages

Each page = a `StoryHero` at the top (the lens's one-sentence lead) followed by supporting cards.

### Free tier — "look back and relive it"

**① Overview** *(free)*
- Hero: "You wandered **18% farther** this week — across **7 places**." (empty: "Keep moving —
  a few days of data unlock your trends, patterns and anomalies.")
- Below: today's summary line, a compact 2×2 trend grid (Distance / Places / Time Away /
  Anomalies), one "Notable" featured moment, Top Places.
- Reuses `weeklyComparison`, `movementStats`, top places.

**② Weekly** *(free — newly free)*
- Hero: "A busier week — **three more places** and a little less time at home."
- Below: comparison rendered as prose + `ComparisonDetailCard`s with deltas (direction by word).
- Reuses `observeComparisons` → `WeeklyComparisonData`.

**③ Highlights** *(free — newly free)*
- Hero: "Some moments worth keeping." Then **collectible** memory cards: firsts, longest walk,
  biggest day, and **"On This Day — 1 year ago."**
- Cards feel like keepsakes (portrait-ish, one moment each), built for screenshots.
- Reuses `DetectNotableEventsUseCase`, `BuildOnThisDayUseCase`.

### Pro tier — "patterns invisible to the naked eye"

**④ Routines** *(Pro)*
- Hero: "Tuesday evenings have quietly become gym time." + "Coming up: looks like you'll head to
  the gym this evening."
- Below: routine cards ("Mon/Wed/Fri · around 6 PM"), top place patterns.
- Reuses `DetectRecurringPatternsUseCase`, `PredictNextPlaceUseCase`.

**⑤ Rhythm** *(Pro)*
- Hero: "Your days keep a steady beat." + Home-Overnight window, typical-day 24h band
  (weekday/weekend), commute legs. **Caveat preserved:** "a proxy, not sleep tracking."
- Reuses `DetectSleepRhythmUseCase`, `ComputeDayRhythmUseCase`, `AnalyzeCommuteUseCase`.

**⑥ Movement** *(Pro)*
- Hero: "Your year in motion." + GitHub-style heatmap, an **exploration score** (new), and the
  year-in-review as a page rather than a card.
- Reuses `BuildHeatmapUseCase`, `YearInReview`; adds exploration score (see §10).

**⑦ Balance** *(Pro)*
- Hero: "Most of your time was spent where you recharge — home stayed the center of your week."
- Below: an organic segmented metaphor (Home / Work / Out / Moving / Untracked), places by
  category. (Brief: "not a pie chart" — a single horizontal life-bar with labels reads more human.)
- Reuses `ComputeTimeBudgetUseCase` → `TimeBudget`.

**⑧ Carbon** *(Pro)*
- Hero framed **positively**: "Half your trips this week were on foot or by bike." Celebrate
  walking/cycling; totals as a guide. **Caveat preserved:** "a guide, not an audit." Never guilt.
- Reuses `BuildCarbonFootprintUseCase`.

**⑨ Anomalies** *(Pro)*
- Hero framed as **curiosity, not warning**: "Something new: you explored a neighborhood you'd
  never visited." + routine watch ("you usually visit Work on Mondays — not yet today").
- Reuses `observeAnomalies`, `DetectRoutineBreaksUseCase`.

## 8. Pro model

- **Free:** Overview, Weekly, Highlights (full).
- **Pro:** Routines, Rhythm, Movement, Balance, Carbon, Anomalies — keep `FeatureGate`, but
  restyle the lock to the brief's aspirational tone. The gate is a single tasteful card:
  > "There's a deeper story waiting here — routines, rhythm and the patterns shaping your week."
  with a gold `ProBadge` and a calm "Unlock" `VoyagerPrimaryButton`. No aggressive interstitials.
- Per the "widen the free tier" choice, we do **not** partial-reveal inside locked lenses; the
  three free lenses are the teaser.

## 9. Motion (achievable now; reduce-motion aware)

| Interaction | Mechanism | Availability |
|---|---|---|
| Items rise + fade on scroll-in | `Modifier.staggeredEntrance` | exists |
| Numbers roll up | `animatedCount` / `AnimatedStat` | exists |
| Header collapses on scroll | `TopAppBarScrollBehavior` | material3 1.3 |
| Pull-to-refresh | `PullToRefreshBox` → `refresh()` | material3 1.3 |
| Lens switch | **`HorizontalPager`** swipe (chip row + pager kept in sync) | foundation (Compose 1.7) |
| Chip select feedback | haptics (already in `VoyagerFilterChipRow`) | exists |

Everything collapses to opacity/snap under `LocalReduceMotion`. **No M3 Expressive; no BOM bump.**

## 10. Net-new work (BUILD phase, not this concept phase)

Reused: all engines + models + every design token/component above. Genuinely new later:
1. **Narrative layer** — the `InsightNarrative` model + one thin narrator per lens (+ tests).
2. **Page primitives** — `StoryPage`/`StoryHero`/`StatSentence`; wire `VoyagerShimmer`; adopt the
   shared empty/error components; collapsing header; pull-to-refresh.
3. **Two small data gaps the brief implies:**
   - **Tracking-day streak** — no place/tracking-day streak is computed yet (only a workout
     streak exists in `PersonalRecordsUseCase.longestStreak`). A pure use-case over
     `daily_rollups` active days.
   - **Exploration score** — currently only ad-hoc proxies (a `DashboardInsight` when
     uniquePlaces > 3; a `varietyScore` in the VM). A first-class score for Movement.

## 11. Accessibility

- Direction by word, not colour (already the rule); keep it everywhere.
- `semantics{}` / merged nodes on all canvas charts (heatmap, rings, segmented bar) — the design
  bible flags canvas charts as currently unlabelled.
- Watch contrast: the design bible notes white-on-Primary ≈ 3.7:1 fails AA for small text — use
  `OnSurface` on dark cards for body copy, reserve Primary fills for large/non-text.
- Reduce-motion honored via `LocalReduceMotion` on every animation.

## 12. Build roadmap (after this concept is approved)

Staged, one focus per session, device-gated on `Medium_Phone_API_36.1` (per the visual-polish rule):

1. **Foundation** — `InsightNarrative` + page primitives + shimmer/empty/error wiring (no visible
   lens rewrite yet; unit-test the narrator).
2. **Overview lens** rebuilt as the flagship storybook page → device review.
3. **Weekly + Highlights** (the rest of the free tier) → device review.
4. **Pro lenses** in impact order: Routines → Rhythm → Movement → Balance → Carbon → Anomalies,
   with the exploration score + streak added when Movement is built.
5. Restyle the `FeatureGate` to the aspirational Pro card.

## 13. Verification of THIS phase

- Read this spec; open the Artifact mockup in dark mode on phone/desktop.
- Sign off on **voice + layout + the free/Pro split**. Then we start the build roadmap at step 1.
