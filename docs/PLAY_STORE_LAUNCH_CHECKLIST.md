# Voyager — Play Store Launch Checklist

Everything needed to publish Voyager on Google Play, with drafted copy you can paste. Items are
tagged **[you]** (needs your Google account / secrets — I can't do it for you) or **[done/ready]**
(prepared in this repo).

> **Monetization:** Voyager launches **100% free**. There are **no in-app products to create** in the
> Play Console for launch. The Play Billing code stays dormant (`ProEntitlementManager.FREE_EVERYTHING`)
> so subscriptions can be switched on later without a rebuild.

---

## 0. Readiness at a glance

| Area | State |
|---|---|
| App code / build (flavors, R8, ProGuard, signing config) | ✅ Ready |
| `versionCode 1` / `versionName 1.0.0` | ✅ Ready |
| Privacy policy (public URL) | ⛳ Hosted by the website (`/privacy.html`) — linked in-app (Settings ▸ Privacy Policy); **add the URL to the listing** |
| Store listing copy | ✅ Drafted below |
| Screenshots / feature graphic / 512 icon | ✅ Screenshots + 1024×500 feature graphic + 512 icon all done |
| Release **keystore** | ⛳ **[you]** must create it |
| Data Safety form | ✅ Answers drafted below · **[you]** submit |
| Background-location declaration + review video | ⛳ Draft below · **[you]** record + submit |
| Content rating | ✅ Guidance below · **[you]** submit |

---

## 1. Create the upload keystore  **[you]**

The repo signs release builds from a **gitignored** `keystore.properties` (see `keystore.properties.example`).
No keystore is committed (correct). Create one:

```bash
keytool -genkeypair -v \
  -keystore voyager-upload.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias voyager
# then create keystore.properties (next to app/) — do NOT commit it:
#   storeFile=/absolute/path/voyager-upload.jks
#   storePassword=********
#   keyAlias=voyager
#   keyPassword=********
```

Back up `voyager-upload.jks` and the passwords somewhere safe — losing them means you can't update the
app (unless enrolled in Play App Signing, which is recommended: let Google hold the app-signing key and
you keep only the upload key).

## 2. Build the release AAB  **[you]**

```bash
./gradlew :app:bundlePlayRelease
# output: app/build/outputs/bundle/playRelease/app-play-release.aab
```

Verify it is **not** debug-signed:
```bash
jarsigner -verify -verbose -certs app/build/outputs/bundle/playRelease/app-play-release.aab | head
```
(If `keystore.properties` is missing, the build silently falls back to the debug key — the AAB would be
rejected by Play. Make sure the file exists.)

## 3. Store listing copy  *(drafts — edit to taste)*

- **App name:** `Voyager — Private Location Timeline`
- **Short description (≤80 chars):**
  `Your private, on-device timeline. Remember, prove, and understand where you go.`
- **Full description (≤4000 chars):**

```
Voyager is a private, on-device timeline that remembers everywhere you've been — and can prove it
when you need to. No cloud. No account. No tracking by us. Your history is stored encrypted on your
phone and never leaves it.

Three apps in one:

MEMORY — An honest, automatic timeline of your visits and journeys, with real place names ("Near
Civil Lines" instead of raw coordinates). See any day on a dark, beautiful map.

PROOF — Turn your drives into an audit-ready mileage log with IRS/HMRC-style deductions, where every
row carries its GPS evidence. Auto-detected multi-day trips you can share. Export to GPX, GeoJSON,
CSV or JSON — your data is always yours.

HABITS — Nine insight "lenses" read your history back to you: weekly comparisons, routines, sleep
rhythm, a time budget, an estimated carbon footprint, personal records, and anomalies.

Plus a private, on-device fitness tracker: record runs, walks, rides and hikes with live maps,
per-kilometre splits, elevation, auto-pause, personal records, and race-yourself segments.

Why Voyager is different:
• Local-first and encrypted by default (SQLCipher) — privacy is the architecture, not a setting.
• Evidence-backed — every visit, drive and place can explain exactly why it was inferred.
• Honest gaps — when tracking is interrupted, you see a clear gap, never a faked line.
• Coming from Google Timeline? Import your Timeline JSON and bring your history home.

Every feature is free.

Voyager needs background location to build your timeline while the app is closed; a persistent
notification shows when tracking is active. You are always in control — start, pause, or stop
tracking anytime, and export or delete everything whenever you want.
```

- **Category:** Maps & Navigation (alt: Health & Fitness) · **Tags:** location, timeline, mileage, privacy
- **Contact email / website:** your email · the GitHub Pages site URL
- **Privacy policy URL:** `https://<your-site>/privacy` (from the website deliverable)

## 4. Graphic assets

| Asset | Spec | Status |
|---|---|---|
| Phone screenshots (2–8) | ≥1080 px, PNG/JPG | ✅ Raw + framed set in `docs/screenshots/` & `docs/marketing/` |
| Feature graphic | 1024 × 500 PNG | ✅ `docs/marketing/feature-graphic.png` |
| App icon | 512 × 512 PNG | ✅ `docs/marketing/store-icon-512.png` (launcher icon also rebranded to match) |

## 5. Data Safety form  *(answers to submit — **[you]**)*

- **Does the app collect or share user data?** Collects: **yes**. Shares: **no**.
- **Location (approximate & precise):** Collected. Purpose: **App functionality** (the core timeline).
  **Processed on-device**, **not shared**, **encrypted in transit** (geocoding uses HTTPS) and
  **encrypted at rest** (SQLCipher). Users can **request deletion** (in-app export/delete).
- **Photos/media:** Optional (Day Story) — read on-device, not uploaded.
- **App activity / device IDs / personal info:** None collected by us.
- **Is all data encrypted in transit?** Yes. **Can users request deletion?** Yes.
- **Independent security review:** No.

## 6. Sensitive permissions — declarations  *(**[you]** submit in Console)*

Voyager requests permissions that trigger Play review. Prepared justifications:

- **`ACCESS_BACKGROUND_LOCATION`** — *Core purpose:* "Voyager builds an automatic location timeline
  and mileage log, which requires recording location while the app is in the background/closed.
  Location is processed and stored **on-device only** and never sent to a server." You must:
  1. Show a **prominent in-app disclosure** before requesting it (the onboarding permission screen).
  2. Record a **short screen-capture video** demonstrating the disclosure + the feature, and link it.
- **`FOREGROUND_SERVICE_LOCATION` / `foregroundServiceType=location`** — declared; justification is the
  same always-on timeline; a persistent notification is shown while tracking.
- **`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`** — justification: "Continuous location logging is killed by
  OEM battery optimization; the app offers (not forces) an exemption so the timeline has no gaps." Keep
  it **opt-in** (the Reliability screen).
- Also declared: `ACTIVITY_RECOGNITION` (transport-mode detection), `POST_NOTIFICATIONS`,
  `READ_MEDIA_IMAGES`/`ACCESS_MEDIA_LOCATION` (Day Story).

## 7. Content rating, audience, ads  **[you]**
- **Content rating questionnaire:** no objectionable content → expect **Everyone**. No ads, no
  user-generated content, no gambling.
- **Target audience:** 18+ (or 13+); **not** designed for children (avoids Families policy).
- **Ads:** **No**. **In-app purchases:** **No** (free launch).
- **Government/COVID/financial-features:** No.

## 8. App content & policy pages  **[you]**
- Complete the **App content** section: privacy policy URL, ads declaration, data safety, content
  rating, target audience, news/health declarations (No), and a **background-location** access form.
- Set up **Play App Signing** (recommended).
- Add the AAB to a **Closed testing** track first (a small tester list), then promote to Production.

## 9. Final pre-submit checklist
- [ ] `keystore.properties` present; release AAB verified not debug-signed
- [ ] Privacy policy URL live (website) and linked in the listing **and** in-app Settings
- [ ] Screenshots + feature graphic + 512 icon uploaded
- [ ] Data Safety, content rating, target audience, ads = none submitted
- [ ] Background-location declaration + demonstration video submitted
- [ ] Short + full description pasted; contact email set
- [ ] App set to **Free**; no in-app products
- [ ] Closed test → then Production rollout

---

*Prepared for Anshul (Cosmic Laboratory). Drafts above are starting points — review before submitting.*
