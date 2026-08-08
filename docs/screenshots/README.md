# Screenshots

The main [README](../../README.md) references every file here.

| File | Screen |
|---|---|
| `00-splash.png` | Splash |
| `01-today.png` | Today — live tracking, stats, top places, anomalies |
| `02-timeline.png` | Timeline — visits and movements in order, gaps included |
| `03-map.png` | Map — routes and visit markers on OpenStreetMap |
| `04-insights-overview.png` | Insights → Overview |
| `05-insights-weekly.png` | Insights → Weekly, with the tracking streak |
| `06-insights-routines.png` | Insights → Patterns / routines |
| `07-insights-carbon.png` | Insights → Carbon |
| `08-proof.png` | Proof — the evidence hub |
| `09-mileage.png` | Mileage — classified drives and deductible value |
| `10-trips.png` | Trips — auto-detected multi-day journeys |
| `11-activities.png` | Activities — rings, workout feed, segments |
| `12-activity-detail.png` | Activity detail — splits and elevation profile |

All captures are portrait, 1080 × 2400.

## Capturing more

Put Do Not Disturb on first, so the status bar is clean.

```bash
adb exec-out screencap -p > docs/screenshots/13-whatever.png
```

Compress before committing — `optipng` or `pngcrush`. Then add the file to the table above and
reference it from the main README, so this directory never drifts out of sync with what the
README claims exists.
