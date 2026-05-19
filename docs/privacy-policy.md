# Voyager — Privacy Policy

_Last updated: 2026-05-19_

Voyager is a private, on-device location-timeline app. This policy explains —
plainly — what Voyager does and does not do with your data. The short version:
**your data stays on your phone. We never see it.**

## 1. Who this is from

Voyager is developed by Cosmic Laboratory. This policy covers the Voyager Android
app distributed via Google Play and F-Droid.

## 2. What Voyager collects — and where it goes

Voyager records, **only on your device**:

- **Location** — GPS/network fixes, used to build your timeline of places and
  trips.
- **Activity & motion** — walking/driving/cycling inference and step counts.
- **Photo metadata** — for the optional Day Story feature, capture time and EXIF
  location are read from photos you already have; photos are never copied or
  uploaded.
- **Derived data** — places, visits, trips, routes, mileage, insights computed
  from the above.

**All of this is stored only in an encrypted database on your device.** It is
**not** sent to us, to any server, or to any third party. Voyager has no user
accounts, no cloud sync, no analytics SDKs, and no advertising.

## 3. The only time Voyager uses the network

To turn coordinates into readable place names, Voyager may send a **coordinate**
to public geocoding services — OpenStreetMap (Overpass/Nominatim), Photon, and
the Android system geocoder. These requests contain only a location to look up,
never your identity or your timeline. You can disable network geocoding entirely
in Settings (and the Privacy-first mode disables it for you).

Voyager makes **no other network connections**.

## 4. What we receive

**Nothing.** We operate no servers and have no ability to access your data. The
developer cannot see your location, your places, or anything else in the app.

If you install via Google Play, Google may collect standard install/crash
telemetry under Google's own policies — this is outside Voyager's control and
applies to every Play app. The F-Droid build avoids even that.

## 5. Your data, your control

- **Export** — export your full timeline at any time (Voyager JSON, GPX, GeoJSON,
  CSV). This satisfies your right to data portability (e.g. GDPR Article 20).
- **Delete** — "Clear all data" performs an immediate, permanent erasure of the
  database (your right to erasure, e.g. GDPR Article 17).
- **Retention** — you choose how long raw samples and derived data are kept;
  Voyager auto-cleans on your schedule.
- **Backups** — Voyager's database is excluded from Android cloud auto-backup, so
  it is never copied to Google Drive. Migrate devices with Voyager's own
  encrypted export instead.

## 6. Security

The on-device database is encrypted with SQLCipher; the key is held in the
Android Keystore. Voyager protects against passive on-device threats. A device
that is rooted and actively targeted by malware is outside the threat model — as
it is for any app.

## 7. Children

Voyager is not directed at children under 13. Onboarding includes an age
acknowledgement.

## 8. Changes

If this policy changes, the updated version will be published with the app and
dated above.

## 9. Contact

Questions about privacy: open an issue on the Voyager repository, or contact the
developer through the listing on Google Play / F-Droid.

---

_Voyager collects sensitive data because a location timeline requires it — but it
keeps every byte on your device. "Told by you, kept by you" is the whole point._
