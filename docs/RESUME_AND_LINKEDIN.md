# Voyager — Résumé bullets, LinkedIn post & pitches

All copy below is **truthful and verifiable** (no user/download claims — the app is pre-production). Swap in your
links before posting: repo/site/Play URLs.

---

## Résumé bullets

Pick 3–5. Lead with the architecture + the differentiator; keep the metrics.

**Header line**
> **Voyager — Private, on-device location timeline (Android)** · Solo · Kotlin, Jetpack Compose, Room/SQLCipher
> · 212 commits over 12 months · 660 tests · [site] · [Play Store]

**Strong bullets (impact-first):**
- Architected an **8-stage, single-writer real-time pipeline** (Kotlin coroutines/Flow) that turns a noisy GPS
  stream into an explainable timeline — 4-state Kalman filter with Joseph-form covariance, hysteresis-based visit
  detection, and a watchdog that emits **reason-coded "honest gap" rows** instead of faking missing data.
- Designed a **30-table, SQLCipher-encrypted** Room schema on an evidence-first model: every inferred label
  carries its supporting metrics **and counter-evidence** ("why other labels were rejected"), enabling
  audit-defensible mileage, trips, and presence records.
- Wrote **660 unit/instrumented tests** including a synthetic pipeline-invariant test, a lost-write concurrency
  test, and a **CI-enforced architecture-boundary test** that fails the build if the pipeline layer imports the
  storage layer (a KMP-ready seam).
- Made background tracking survive **Doze and aggressive OEM battery-killers**: adaptive sampling, a
  hardware-significant-motion dormant tier, a self-healing health-check worker, and motion-aware sample-staleness
  — cutting stationary GPS writes ~60% and improving battery ~40% (bench).
- Built a **local-first privacy architecture** — always-on encryption keyed to the Android Keystore
  (device-bound), no cloud, no accounts, no analytics — plus a Google Timeline importer as the migration wedge.
- Shipped a Play-ready release (R8/ProGuard/resource-shrinking, validated at runtime), two product flavors
  (Play + F-Droid), CI on every PR, and a monthly OWASP dependency scan.

**One-line version (for a dense résumé):**
> Solo-built an on-device, privacy-first location timeline for Android (Kotlin/Compose, encrypted 30-table Room
> DB): an 8-stage single-writer inference pipeline (Kalman + hysteresis + honest-gap detection), an evidence-first
> data model with counter-evidence, and 660 tests incl. a CI-enforced architecture-boundary check.

---

## LinkedIn launch post

> **I built Voyager — a private location timeline that lives entirely on your phone. 🛰️**
>
> When Google discontinued Timeline's cloud sync, millions lost their history with no way to bring it home. So I
> spent the last year (solo) building the app I wanted: a timeline that **remembers** everywhere you've been,
> can **prove** it when you need to (audit-ready mileage, trips, presence), and shows you your **patterns** — and
> it can always explain *why* it knows.
>
> No cloud. No account. No tracking by me. It's encrypted on your device from the first launch.
>
> What I'm proud of as an engineer:
> • An **8-stage, single-writer pipeline** that turns noisy GPS into a truthful timeline in real time — Kalman
> filtering, hysteresis visit detection, and **"honest gaps"** (when data is missing it says so, with a reason,
> instead of faking a smooth line).
> • **Evidence on tap** — every inference carries *why*, including why the alternatives were rejected. That turns
> a black box into something you (or an accountant) can trust.
> • The unglamorous stuff: surviving Doze and OEM battery-killers, an encrypted 30-table schema, 660 tests, and a
> CI check that fails the build if the layers leak into each other.
>
> Google Timeline meets a tax-mileage app meets a private fitness tracker — one on-device record, many jobs. And
> it's **100% free**.
>
> 📱 [Play Store link]  ·  🌐 [site link]  ·  built with Kotlin, Jetpack Compose, Room/SQLCipher, MapLibre.
>
> #Android #Kotlin #JetpackCompose #MobileDevelopment #Privacy #SoftwareEngineering

*(Attach 3–4 of the framed screenshots from `docs/marketing/` — e.g. Today, Timeline, Insights, Mileage.)*

**Shorter LinkedIn variant:**
> Spent a year building Voyager: a private, **on-device** location timeline for Android that remembers where
> you've been, *proves* it (audit-ready mileage/trips), and shows your patterns — and always explains why. No
> cloud, no account, encrypted on-device. The engineering I loved: an 8-stage single-writer GPS pipeline with
> "honest gaps," an evidence-first schema (with counter-evidence), and 660 tests incl. a CI-enforced
> architecture-boundary check. 100% free. [links] #Android #Kotlin

---

## 30-second elevator pitch (spoken)

"Voyager is a private, on-device location timeline for Android. It remembers everywhere you've been, can *prove*
it when you need to — think audit-ready mileage and trips — and shows you your patterns, and it can always explain
*why* it inferred what it did. Nothing leaves your phone; it's encrypted from day one. The hard part was turning a
noisy GPS stream into a truthful, explainable timeline in real time, which is an 8-stage single-writer pipeline I
built in Kotlin. It's Google Timeline, a mileage app, and a fitness tracker in one on-device record — and it's
free."

---

## 5-minute deep-dive script (spoken outline)

1. **Problem (30s):** Google killed Timeline cloud sync; location apps monetize your data. Gap for a local-first
   timeline you own *and* can prove things with.
2. **Insight (30s):** one on-device record can do three jobs that are separate apps today — memory, proof, habits.
3. **The hard part (60s):** noisy GPS → truthful, *explainable* timeline, live. That's the whole game.
4. **Architecture (90s):** the 8-stage single-writer pipeline — walk normalize → dedup → anti-spoof → Kalman →
   quality → fuse → visit → segment → commit; single channel, lock-free by construction; honest gaps via the
   watchdog; crash recovery.
5. **The moat (30s):** evidence + counter-evidence on tap; nobody exposes *why the alternatives were rejected*.
6. **Rigor (30s):** 660 tests, CI-enforced layer boundary, per-flavor CI, monthly security scan.
7. **Honesty (30s):** pre-production, heuristic-not-ML on purpose, 4 of 7 differentiators shipped, and what's
   next. (Candor = credibility.)

---

## Talking-point cheat sheet (memorize)

- **Numbers:** 212 commits · 12 months · solo · ~57K LOC · 660 tests · 30 encrypted tables · 20 workers · 28
  screens.
- **The flex:** 8-stage single-writer pipeline · Joseph-form Kalman · honest gaps · counter-evidence · CI-enforced
  architecture boundary.
- **The one-liner:** "One on-device record, many jobs — and it can always explain why."
- **On AI:** "Built with AI pair-programming; the architecture and decisions are mine — ask me about any line."
