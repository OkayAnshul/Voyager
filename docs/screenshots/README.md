# Screenshots & Media

Place all screenshots and demo media here. The main README references these files.

## Required Screenshots

| Filename | Screen | Notes |
|----------|--------|-------|
| `dashboard.png` | Home/Dashboard | Show active tracking, hero ring, streak |
| `timeline.png` | Timeline | Show a day with mixed segments (walk, drive, visit) |
| `map.png` | Map | Show route polylines + place markers |
| `insights.png` | Insights/Statistics | Show weekly comparison chart |
| `settings.png` | Settings | Show the 4-tab settings panel |
| `place_detail.png` | Place Detail Sheet | Show a confirmed place with visit history |

## Optional Screenshots

| Filename | Screen |
|----------|--------|
| `onboarding_1.png` | Feature walkthrough page 1 |
| `onboarding_2.png` | Feature walkthrough page 2 |
| `export.png` | Export format picker |
| `segment_detail.png` | Segment detail bottom sheet |
| `search.png` | Search screen |

## Video Demo

| Filename | Description |
|----------|-------------|
| `demo_thumb.png` | Thumbnail for the YouTube/Loom demo link |

## Capture Tips

- Use portrait orientation, 1080×2340 resolution
- Enable "Do Not Disturb" before capturing — hide notification bar clutter
- Use Android Studio's Device Mirror or `adb shell screencap` for clean captures:
  ```bash
  adb shell screencap -p /sdcard/screen.png && adb pull /sdcard/screen.png docs/screenshots/dashboard.png
  ```
- Compress PNG files before committing: `pngcrush` or `optipng`
