# Flagship Device Test — the one run that proves Voyager

Every card in `FEATURE_TEST_CATALOG.md` is verified at code level and locked with unit tests,
but each carries the same caveat: **device-verify pending**. Proof you can't trust on a real
trip is the one thing that sinks this product. This is the single end-to-end journey that
validates the flagship promise — *capture → timeline → trip → exportable proof* — on a real
device, and doubles as the regression script for the correctness/safety bugs fixed in the
`w4.6-feedback-calibration` and `wave-6-proof-hub` branches.

Run it once, start to finish, on a real phone with a real trip. If this feels like magic, the
product is real.

## Setup
- Fresh install (pre-production — a clean DB is fine; see memory `project_preproduction`).
- A second phone or a known route for the live legs.
- For the import leg: a Google Takeout *Semantic Location History* or on-device `Timeline.json`.
- Optional, for the spoof check (T15): a mock-location app from Developer Options.

## The journey

### 1 · First run & persona (W8.3 / W8.2 / F3)
- Pick a persona on first launch.
- **Expect:** tracking starts; the persistent notification reads warm/human (not "tracking
  your journey") and is tappable; the chosen persona's behaviour is in effect (e.g. Battery
  Saver vs Precision Max change cadence). Switching persona later must **not** wipe your home
  timezone / geocoding providers.

### 2 · Bring your history home (W7.3)
- Import the Takeout / `Timeline.json`.
- **Expect:** places, visits and activity segments populate; the summary count is honest;
  a raw `Records.json` is rejected with guidance. **Regression (W7.3):** any motorcycle trip
  shows as **Drive**, not Cycle.

### 3 · Live capture across a real day (W0.1, W1.1–W1.6)
Drive a leg, walk a leg, take transit, sit somewhere ≥ your min-dwell, with the screen off.
- **Expect:** samples land continuously through screen-off + Doze (W0.1); a 20–30 min
  continuous walk is **one** segment, not six (T1); slow/stop-go traffic reads as **Drive**,
  not Cycle (T4); a brisk walk is **not** called running (T7); the route is smooth, not a
  GPS-jitter zigzag (W1.1).
- **Regression (T15):** with a mock-location app active, spoofed/teleporting fixes are dropped
  (the route doesn't jump across the map).

### 4 · Visit truth (W1.4 — T6, T10)
At a stay, step just outside briefly and return; separately, walk to a shop ~400 m away and back.
- **Expect:** the brief step-outside stays **one** visit; the 400 m round trip becomes **two**
  visits (T6). Dwell time matches reality — it doesn't include the walk-out (T10).

### 5 · Names & review (W2.2, W4.4)
- **Expect:** places get sensible names with the "near to" phrasing; low-confidence places land
  in the review queue, and confirming/renaming one drops it out and keeps it out (W4.6 trust).

### 6 · The trip detects itself (W6.2)
Be away from home ≥ 2 days (or simulate via the route).
- **Expect:** a trip auto-appears with correct start/end and destination. A phone-off day
  *mid-trip* doesn't split it. **Regression (W6.2):** a long blackout does **not** fabricate
  one continuous mega-trip.

### 7 · The proof artifacts — the hero moment (W6.3, W6.4, W6.6)
- Open the trip → export the **trip-book PDF**. **Expect:** day-by-day, places in arrival
  order, reads like a story; deleted visits absent; gap days omitted (W6.3).
- Classify a few drives Business/Personal/Medical → export the **mileage PDF**. **Expect:**
  per-purpose miles + an IRS deduction total that's arithmetically right; personal shows no
  deduction; the disclaimer to verify rates is present (W6.6).

### 8 · Day Story (W6.7)
Take a few photos during the day, then open Day Story.
- **Expect:** photos map to the right place/time; geotagged photos disambiguate overlapping
  visits by nearest place; out-of-window photos sit in "unplaced".

### 9 · Workout (W6.8)
Record a short run.
- **Expect:** live distance/pace look right; the **saved** distance matches what you watched
  live (no GPS-glitch inflation — the W6.8 fix).

### 10 · Portability round-trip (W7.1, W7.2)
Export VoyagerJSON and GPX.
- **Expect:** the GPX opens in an external tool with the route intact (PolylineEncoder is
  standard — W7.1). Re-import the VoyagerJSON. **Regression (W7.2):** the import summary's
  visit/segment/duplicate counts are accurate.

### 11 · It survives the real world (W0.6, W0.7, W7.4)
Force-stop the app and reboot mid-day.
- **Expect:** tracking recovers; the open visit closes at the last-known-alive time, not the
  gap (T3); the reliability screen shows health honestly and surfaces OEM-kill guidance.
  Multi-year rollups are **not** deleted (W7.4 — rollups are kept forever by default).

## One-line regression checklist (this session's fixes)
- [ ] T6 — short step-out keeps one visit; 400 m round trip = two
- [ ] T7 — desk shuffle / brisk walk not mislabelled running
- [ ] T15 — spoofed GPS dropped
- [ ] W6.2 — no fabricated trip across a data blackout
- [ ] W6.8 — saved workout distance == live distance
- [ ] W7.2 — import summary counts accurate
- [ ] W7.3 — motorcycle imports as Drive
- [ ] W7.4 — rollups survive past 365 days; nothing wiped
- [ ] W9.3 — settings export/import doesn't crash on edge values

## Pass bar
The run passes when the **proof artifacts in step 7 are something you'd hand to an accountant or
print as a keepsake** — accurate, explainable, and yours. That's the flagship.
