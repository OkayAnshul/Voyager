# Voyager vs. The Field — Competitor Analysis & Speed-to-Market Plan

_Date: 2026-05-15_

## 1. Direct competitors (what they already ship)

| App | What they have today | Where they win | Where they bleed |
|-----|---------------------|----------------|------------------|
| **Google Timeline** | Auto-detected places, drives, photos correlation, monthly heatmap. **As of Dec 2024, killed cloud sync — data now lives on-device only.** | Free, accurate, default on most Androids | Killed cloud sync without migration tooling; users lost years of data; no proof exports; ad-funded parent; opaque ML |
| **Arc (iOS)** | Beautiful timeline, mode detection, places, "Arc Mini" widget, **on-device-only** since Arc 3 (2024) | Best-in-class iOS UX; same privacy ethos | iOS only; $35/yr subscription; one solo dev (slow ship cadence) |
| **Polarsteps** | Trip stories, photo correlation, printed travel books (~$80) | Beautiful trip albums; print revenue | Cloud-locked; trip-focused (not daily life); €30/yr |
| **OwnTracks** | Self-hosted MQTT; raw location stream | Geek-perfect privacy | Requires running a broker; no UX |
| **Life360** | Family location sharing | Brand recognition; safety market lock-in | Sold user data to brokers (2022 scandal); ad-funded; surveillance-y |
| **Strava** | Routes, social, segments | Fitness community; gamification | Heatmap data leak (2018); cloud; $80/yr; not a life-timeline |
| **MileIQ** | Auto-mileage classification, IRS PDF | Best-in-class mileage UX | $60/yr; cloud-required; single-use |
| **Hurdlr / Stride / Everlance** | Mileage + expense tracking | Tax integrations | All cloud; subscription fatigue |
| **Daylio / Day One** | Mood/journal apps with location pins | Beautiful journal UX | Manual entry; no auto-capture |
| **GeoTracker / OSMTracker (FOSS)** | GPX export, OSM contribution | F-Droid darling; private | No places, no insights, raw-only |
| **PhotoMap / Memories** | Photo timeline by location | Photo-first storytelling | Doesn't capture movement; passive only |
| **Citymapper / Google Maps Trips** | Per-trip routes | Network effects on transit data | Not a life-timeline |

## 2. What none of them have

This is the **whitespace** Voyager owns:

1. **Three jobs in one app** — Memory + Proof + Habits. Every competitor is single-job: Polarsteps = trips, MileIQ = mileage, Strava = workouts, Life360 = family. Voyager combines them because the underlying engine is the same.
2. **Genuine on-device + breadth.** Arc has on-device + breadth, but iOS-only. OwnTracks has on-device but no UX. Google Timeline has breadth but cloud. **Nobody has all three on Android.**
3. **Evidence + explainability.** No competitor shows "we said you drove because…". Black boxes everywhere.
4. **Mileage log with court/IRS-grade evidence per row.** MileIQ exports a PDF but not the underlying GPS samples; Voyager can.
5. **Family one-bit handshake** (no live stream). Direct Life360 replacement that isn't surveillance.
6. **Duress mode for journalists/activists.** Niche but no competitor offers it.
7. **Local-first OSM contribution loop.** Turns users into commons contributors without forcing it.

## 3. Approachability — how to deliver value fast

"Approachable in less time" splits into two questions: how do *we* ship fast, and how does the *user* feel value fast.

### 3a. Founder speed — ship the smallest convincing thing

The fastest path to a "real" app users tell friends about, ranked by speed of payoff:

| Action | Build time | Why it beats competitors instantly |
|--------|------------|------------------------------------|
| Ship Evidence Card (L1 in plan) | 3 days | Already in `SegmentEvidenceEntity`; just surface the JSON. Voyager becomes the only explainable timeline app. |
| Daily Recap notification | 2 days | Existing notification channel; one WorkManager job. Retention loop active from day 1. |
| FTS "Ask My Timeline" search bar | 4 days | Index exists; UI only. Demo-able moment that goes viral on Reels/TikTok. |
| Battery cost self-report | 1 day | One `BatteryStatsManager` call + a card. **Single best anti-1-star defence.** |
| Reliability check screen | 2 days | Detect OEM, link to dontkillmyapp + 24h gap self-test. Pre-empts the "this app is broken" review. |
| Google Timeline JSON import | 5 days | Existing export scaffolding; parser is straightforward. **Unlocks an entire wave of orphaned-Timeline users.** |
| Real names via Overpass | 1 week | Already in roadmap; closes the "Gym 123" credibility gap. |
| Mileage log + IRS PDF | 1 week | Existing drive segments + Android `PdfDocument`. **The first monetizable feature.** |

**Total: ~5 weeks of focused work for an app that looks 10x better than today.** Notice none of this is greenfield — it's all surfacing what's already in the engine. Speed-to-market advantage = the audit showed 14 features 70–95% built.

### 3b. User speed — value in under 60 seconds

A privacy app dies if onboarding is heavy. Compress to:

1. **First 10 seconds — value claim, not permissions.** Show: "Your private life-timeline. Nothing leaves this phone." One screen, one toggle ("Start").
2. **Persona pick (10 sec)** — three tiles: Memory / Proof / Habits. Pre-configures presets, default Dashboard order, default notification cadence. Reduces all settings questions to one.
3. **Permissions just-in-time, not bulk.** Foreground location first; ask background only when user opens a multi-hour day timeline that has gaps. Photo permission only when they tap "see photos here". Background request becomes self-explanatory because the user *sees the gap they want filled*.
4. **First aha within 1 hour, not 7 days.** Live-tracking card on the dashboard that updates as the user walks. Even before places exist, the user sees "Walking for 4 min, 320m, 47 steps." Immediate sensory feedback.
5. **First *retention* aha within 24 hours.** Daily recap notification at 22:00: "You moved 8km, visited 3 places. Tap to see your day." This is the hook that gets them back.

The current onboarding has a clean permission flow, but the first-hour experience can feel empty (the place-detection state machine needs ~10 min of walking to trigger). **Adding a live "I'm capturing right now" pulse on the dashboard is a 1-day fix with outsized retention impact.**

### 3c. Differentiation in one sentence

Against each competitor, here is the one-line positioning to use:

- vs. **Google Timeline**: *"They killed your cloud sync. We never had one."*
- vs. **Polarsteps**: *"They sell you back your own memories. We let you own them."*
- vs. **Life360**: *"They sold your family's location to brokers. We don't even see it."*
- vs. **Strava**: *"They leaked military base locations from your heatmap. Ours stays on your phone."*
- vs. **MileIQ**: *"They charge $60/yr for one feature. We give you mileage as one of ten — for $50/yr."*
- vs. **Arc**: *"Same privacy ethos, on the 70%+ of phones that aren't iPhones."*
- vs. **OwnTracks**: *"All the privacy. None of the MQTT broker."*

## 4. Recommended sequencing (less-time priority)

Given the engine is solid and the surface needs polish, do this 4-week sprint *before* any major new feature:

1. **Week 1** — Stability: force-unwraps gone, encryption non-optional, mock filter, reliability check screen. App stops crashing.
2. **Week 2** — Trust surface: Evidence Card, Voyager's Health dashboard, battery self-report card. App becomes the *only* explainable timeline.
3. **Week 3** — Retention surface: daily recap notification, FTS search bar, Place Discovery moments. App gives users a reason to open daily.
4. **Week 4** — Migration trigger: Google Timeline JSON import + landing page with the headline "Lost your Timeline? Bring it home." Public soft-launch on r/privacy.

That's 28 days from today to a beta that has zero direct equivalent on Android. Real place names, mileage log, photo correlation, and the rest follow in weeks 5–12 from the existing 90-day plan.

The compounding advantage: every feature shipped is *closer to free* than competitors, because (a) no cloud bill, (b) no analytics SDKs, (c) most of it is already 70%+ built. Their unit economics are stuck at $20–80/yr to cover infra; Voyager can profit at $5/mo because marginal cost per user is ~$0.

---

_Companion strategy doc: `~/.claude/plans/debug-test-and-bright-deer.md` (full 10-part masterpiece plan: vision, 90-day sprint, hidden engineering leverage, personas, GTM, distribution playbook, KPIs, and what-not-to-build)._
