# Voyager — Go-Live Runbook (one step at a time)

Do these **in order, one at a time**. Each step says who does it — **[you]** (needs your Google account or
secret passwords) or **[me]** (I can run it for you). After a **[you]** step, do the single action and tell me
"done" — I'll confirm and hand you the next one. Full copy/answers live in
[`PLAY_STORE_LAUNCH_CHECKLIST.md`](PLAY_STORE_LAUNCH_CHECKLIST.md).

**Status legend:** ☐ not started · ▶ in progress · ✅ done

---

## Track 1 — Signed app bundle

**Step 1 [you] — Create the upload keystore.** In your terminal:
```bash
mkdir -p ~/keys
keytool -genkeypair -v -keystore ~/keys/voyager-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias voyager
```
Answer the prompts (a keystore password, your name/org, a key password — Enter reuses the store password).
**Back up the `.jks` file + both passwords.** → then tell me it's created.

**Step 2 [you] — Create `keystore.properties`** at the repo root (gitignored — never commit it):
```properties
storeFile=/home/anshul/keys/voyager-release.jks
storePassword=YOUR-STORE-PASSWORD
keyAlias=voyager
keyPassword=YOUR-KEY-PASSWORD
```
→ then tell me "keystore.properties ready."

**Step 3 [me] — Build + verify the signed AAB.** I'll run `./gradlew :app:bundlePlayRelease` and confirm the
bundle is signed with *your* key (not the debug key). Output: `app/build/outputs/bundle/playRelease/app-play-release.aab`.

---

## Track 2 — Website (public) + private code

**Step 4 [you] — Make the code repo private.** GitHub → `OkayAnshul/Voyager` → Settings → Danger Zone →
Change visibility → **Private**. (Optional: push your branch first — I can do that.) → tell me "repo is private."

**Step 5 [you] — Create the public site repo.** GitHub → New repository → name **`voyager-site`** → **Public** →
don't add any files → Create. → tell me "voyager-site created."

**Step 6 [me] — Push the site.** I'll run (from the local `voyager-site` folder):
```bash
git branch -M main
git remote add origin git@github.com:OkayAnshul/voyager-site.git
git push -u origin main
```

**Step 7 [you] — Enable GitHub Pages.** `voyager-site` → Settings → Pages → Build and deployment →
**Source: GitHub Actions**. Wait ~1 min. Your site is live at `https://okayanshul.github.io/voyager-site/`.
The **privacy-policy URL for Play** is `https://okayanshul.github.io/voyager-site/privacy.html`. → tell me "site is live."
*(Then open it in Firefox and skim it — landing, engineering, privacy.)*

---

## Track 3 — Play Console (all [you]; I supply every value from the checklist)

**Step 8 — Create the app.** Play Console → Create app → name **Voyager**, type App, **Free**, declarations →
Create.

**Step 9 — Store listing.** Paste the drafted **title / short (80) / full (4000)** description; category
**Maps & Navigation**; contact email; **privacy policy URL** from Step 7. Upload:
- phone screenshots → `docs/marketing/01-today.png … 12-activity-detail.png`
- feature graphic (1024×500) → `docs/marketing/feature-graphic.png`
- app icon (512×512) → `docs/marketing/store-icon-512.png`

**Step 10 — Data safety.** Fill using the drafted answers (location collected, **on-device only, not shared**,
encrypted at rest, user can export/delete).

**Step 11 — Sensitive permissions.** Submit the **background-location declaration** (justification text in the
checklist) + record the short **demo video** (script in the checklist: show the disclosure → grant → timeline/map
filling in, narrate on-device).

**Step 12 — Content rating, audience, ads.** Complete the questionnaire (expect Everyone), target audience
18+/13+ (not children), **ads = No**, **in-app purchases = No** (free).

**Step 13 — Closed testing.** Create a Closed testing track, add your own email as a tester, upload the AAB from
Step 3, roll out, install from the tester link, sanity-check.

**Step 14 — Production.** Promote the tested release to Production and submit for review.

> Heads-up: the **background-location review** is the slowest gate (a few days). Submit Step 11 early.

---

## Quick reference
- Store copy, data-safety answers, permission justifications, demo-video script → `PLAY_STORE_LAUNCH_CHECKLIST.md`
- Screenshots / feature graphic / 512 icon → `docs/marketing/`
- Privacy policy source → `docs/privacy-policy.md` (hosted at `/privacy.html` on the site)
- Updating later (version bumps, DB migrations) → see the "updating the app" notes; bump `versionCode` every
  release, sign with the **same** key, and never ship a schema change without a Room migration once you have users.

**I can do Steps 3 and 6 for you the moment their prerequisites exist. Just say "keystore.properties is ready"
or "voyager-site is created."**
