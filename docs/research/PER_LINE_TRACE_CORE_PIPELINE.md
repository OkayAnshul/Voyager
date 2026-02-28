# Voyager Per-Line Trace (Core Pipeline)

This is the companion to `DEEP_SYSTEM_WORKING_TRACE.md`.

Scope: literal per-line commentary for execution-critical ranges in:
- `LocationTrackingService.kt`
- `SmartDataProcessor.kt`
- `MovementSegmentationUseCase.kt`
- `PlaceDetectionUseCases.kt`

Format:
- `L<line>`: what that exact line does and why it impacts runtime behavior.

---

## 1) `LocationTrackingService.kt` Per-Line Trace

### Range: 376-468 (location ingestion hot path)

- L376: Declares per-sample async processing entry (`saveLocation`).
- L377: Immediate pause gate; prevents writes when user paused tracking.
- L379: Begins sleep-window handling branch.
- L380: Checks if current local time is inside configured sleep window.
- L381: Loads preferences; if missing, exits to avoid undefined behavior.
- L383: Branch comment documents motion-override behavior during sleep.
- L384: Checks if motion detection is enabled and currently true.
- L385: Logs sleep override; continues processing despite sleep.
- L387: Logs suppression when sleep and no motion.
- L388: Returns early; no location persisted.
- L392: Starts activity fallback enrichment from speed.
- L393: Calls motion manager update with derived speed.
- L394: Converts m/s to km/h before update.
- L395: Passes GPS accuracy into activity inference quality.
- L400: Reads current preferences once for activity-recognition gate.
- L401: Ensures activity-recognition gate applies only when enabled.
- L402: Calls moving-state detector with configured confidence threshold.
- L403: Logs moving skip reason with detected activity label.
- L404: Returns early; avoids transit-point persistence.
- L408: Wraps remaining path in standardized error handler.
- L410: Captures start timestamp for perf measurement.
- L413: Calls spatial/temporal save filter.
- L414: Logs filtered sample (non-fatal).
- L415: Exits handler block without persisting.
- L419: Gets current inferred user activity snapshot.
- L420: Captures canonical app timestamp for the sample.
- L422: Creates domain `Location` object.
- L423: Copies latitude from Android sample.
- L424: Copies longitude from Android sample.
- L425: Stores normalized timestamp.
- L426: Stores horizontal accuracy.
- L427: Stores speed only when sensor reports it.
- L428: Stores altitude only when present.
- L429: Stores bearing only when present.
- L431: Stores inferred `userActivity`.
- L432: Stores activity confidence.
- L433: Computes semantic context from activity/time/speed.
- L440: Emits processing log with coordinates and accuracy.
- L442: Switches to IO dispatcher for heavy processing.
- L444: Calls smart processor (DB + visits + stats + state).
- L447: Ensures event emission happens after persistence path.
- L448: Dispatches location event to subscribers.
- L450: Increments in-service location counter.
- L451: Increments detection-trigger counter.
- L452: Updates last-save wall-clock timestamp.
- L453: Stores last saved Android sample for delta calculations.
- L455: Computes processing latency.
- L456: Logs performance metric.
- L459: Stores previous stationary mode state.
- L460: Recomputes stationary mode from movement pattern.
- L463: Checks stationary mode transition.
- L464: Rebuilds location request when mode changed.
- L467: Evaluates whether to trigger place detection.
- L471: Reads notification update frequency.
- L472: Applies modulus-based notification throttling.
- L473: Builds status text with tracked count.
- L474: Updates foreground notification text.
- L477: Returns success from handler block.
- L489: Error callback branch begins.
- L491: Degrades gracefully by showing error notification only.

### Range: 500-569 (`shouldSaveLocation`)

- L500: Declares core sampling filter function.
- L501: If no preferences loaded, fail-open (save sample).
- L504: First sample always saved for baseline.
- L506: Captures current wall-clock for interval checks.
- L507: Computes elapsed since last persisted sample.
- L510: Chooses stricter/lenient accuracy policy based on stationary mode.
- L511: Caps stationary-mode max accuracy at 100m.
- L515: Reject branch starts for poor accuracy.
- L516: Logs rejection reason with actual threshold.
- L517: Drops sample.
- L521: Computes distance delta from last persisted sample.
- L527: Chooses movement threshold based on stationary mode.
- L528: In stationary mode, threshold scales with reported accuracy.
- L530: In moving mode, threshold uses effective min distance vs accuracy.
- L534: Chooses min-time threshold based on stationary mode.
- L535: Doubles time threshold when stationary.
- L540: Begins speed plausibility validation.
- L541: Skips speed validation for sub-second intervals.
- L542: Computes inferred speed in m/s from distance/time.
- L543: Converts inferred speed to km/h.
- L544: Compares to configured max speed.
- L545: Logs impossible-speed rejection details.
- L546: Drops sample.
- L551: Starts final decision `when` ladder.
- L554: Force-save condition checks maximum tracking gap.
- L555: Logs long-gap forced save.
- L556: Accepts sample.
- L559: Significant movement condition branch.
- L560: Logs movement-based acceptance.
- L561: Accepts sample.
- L564: Time+small-movement acceptance branch.
- L565: Logs hybrid acceptance.
- L566: Accepts sample.
- L568: Else branch: likely drift/stationary noise.
- L569: Logs filtered drift case.

### Range: 892-961 (auto place detection trigger)

- L892: Declares automatic place-detection gate.
- L893: Loads preferences; if absent exits safely.
- L895: Checks global place-detection enable flag.
- L896: Logs disabled state for diagnostics.
- L897: Exits with no scheduling.
- L900: Captures current time for trigger calculations.
- L901: Computes elapsed time since last detection.
- L902: Converts frequency-hours preference to milliseconds.
- L908: Count-based trigger condition.
- L909: Time-based trigger condition (requires some new points).
- L910: Bootstrap trigger after restart/long idle.
- L912: Composite trigger guard.
- L913: Resolves human-readable trigger reason.
- L919: Logs trigger telemetry values.
- L923: Launches async enqueue work.
- L924: Calls enqueue routine with prefs and timestamp.
- L932: Enqueue routine declaration.
- L933: Logs scheduling intent.
- L935: Delegates scheduling to place detection scheduler abstraction.
- L937: Switches on enqueue result type.
- L939: Success branch begins.
- L940: Resets location counter after accepted scheduling.
- L941: Updates last detection timestamp.
- L942: Logs enqueued work id.
- L944: Failure branch begins.
- L945: Logs scheduler failure/exception.
- L947: Starts fallback manual detection path.
- L950: Executes detection directly in-process.
- L951: Logs fallback result count.
- L954: Resets counters on fallback success.
- L955: Stores last detection timestamp on fallback success.
- L957: Logs fallback failure if both mechanisms fail.

---

## 2) `SmartDataProcessor.kt` Per-Line Trace

### Range: 154-185 (main processing sequence)

- L154: Declares main processing method for one accepted location.
- L155: Wraps all logic with centralized error handling.
- L157: Logs coordinates being processed.
- L160: Restores pending dwell state after process death if needed.
- L163: Ensures singleton current state exists and is valid.
- L166: Runs validation service for location quality/business rules.
- L169: Persists location to repository/DB.
- L172: Updates `lastLocationUpdate` in state singleton.
- L175: Executes place proximity + visit FSM.
- L178: Recomputes and writes daily counters/time.
- L181: Executes auxiliary automatic triggers.
- L183: Logs successful completion.
- L196: Error fallback branch: failure is absorbed/logged gracefully.

### Range: 314-399 (visit FSM)

- L314: Declares place proximity and visit management method.
- L317: Reads places from in-memory cache (with TTL refresh).
- L318: Empty-places guard.
- L320: Exits if no known places exist.
- L324: Finds nearest place under hysteresis logic.
- L325: Reads current state snapshot for active place context.
- L328: Loads dwell preference.
- L331: Starts FSM branching.
- L333: Case 1 condition: near place, no pending, no active place.
- L334: Creates pending visit with detected place id.
- L336: Uses current sample timestamp as first detection time.
- L339: Persists pending visit fields to singleton state.
- L340: Logs pending dwell start.
- L344: Case 2 condition: same place as pending.
- L346: Computes dwell duration in seconds.
- L351: Updates pending last-inside timestamp.
- L354: Confirmation condition: dwell exceeded and not yet confirmed.
- L355: Starts actual visit at pending first-detection time.
- L356: Marks pending as confirmed.
- L357: Persists confirmed pending marker.
- L358: Logs confirmation with dwell seconds.
- L363: Case 3 condition: new nearby place differs from pending place.
- L365: If pending already confirmed, end previous visit first.
- L370: Reinitialize pending for new place.
- L375: Persist new pending state.
- L376: Logs place-switch tracking.
- L380: Case 4 condition: moved away from all places while pending exists.
- L382: If unconfirmed, drop pending without creating visit.
- L384: If confirmed + current place exists, end current visit.
- L387: Clears in-memory pending marker.
- L388: Clears persisted pending fields.
- L392: Case 5: still at same confirmed place (no state change).
- L393: Logs steady-state presence.
- L397: Catch block starts.
- L398: Logs non-fatal proximity/visit FSM errors.

### Range: 405-430 (hysteresis nearest-place resolver)

- L405: Declares nearest-place resolver.
- L406: Pulls current state for current-place awareness.
- L407: Initializes nearest candidate variable.
- L410: Iterates all known places.
- L411: Computes distance to place centroid.
- L417: Chooses threshold branch by current/pending membership.
- L419: Exit threshold branch uses larger tolerance.
- L420: Includes place radius and accuracy-scaled tolerance.
- L422: Entry threshold branch uses tighter criteria.
- L425: Candidate accept condition: inside threshold and closer than prior.
- L426: Updates nearest candidate.
- L427: Stores nearest distance.
- L430: Ends loop; nearest candidate returned below.

### Range: 445-517 (start/end visit + prune)

- L445: Declares visit-start helper.
- L448: Logs visit start place name.
- L451: Creates active visit via repository.
- L452: Logs created visit id.
- L455: Writes current place/visit/entry through gateway.
- L462: Success path logs gateway acceptance.
- L464: Failure path logs gateway rejection.
- L473: Declares visit-end helper.
- L476: Logs visit end timestamp.
- L478: Fetches current active visit.
- L482: Logs active visit id to end.
- L486: Reads user min-visit-duration preference.
- L489: Converts min duration minutes to milliseconds.
- L491: Completes visit with provided timestamp.
- L492: Prune condition if below min duration threshold.
- L493: Deletes short visit.
- L494: Logs short-visit discard.
- L496: Otherwise updates completed visit.
- L497: Logs persisted duration.
- L504: Clears current place through gateway.
- L507: Logs gateway clear success.
- L509: Logs clear rejection path.

### Range: 526-567 (daily stats recompute)

- L526: Declares daily stats updater.
- L529: Builds start of today boundary.
- L530: Builds end of today boundary.
- L533: Counts today’s locations from repository.
- L538: Fetches today visits flow snapshot.
- L539: Computes distinct places visited today.
- L542: Starts total tracked time calculation.
- L543: Iterates visits and sums durations.
- L544: Uses stored/completed duration when exit exists.
- L548: Uses live duration for active visits.
- L554: Logs daily stats telemetry.
- L557: Writes stats to state gateway.
- L564: Logs write success.
- L566: Logs write rejection.

---

## 3) `MovementSegmentationUseCase.kt` Per-Line Trace

### Range: 53-150 (segment assembly)

- L53: Declares segmentation entry for `[rangeStart, rangeEnd)`.
- L57: Loads preferences for thresholds.
- L58: Loads current state for tracking-session context.
- L61: Fetches and sorts locations in range.
- L63: Fetches and sorts visits in range.
- L67: Collects distinct place ids from visits.
- L68: Resolves place metadata map for visits.
- L73: Reads `trackingStartTime` fallback to session start.
- L79: Begins pre-tracking classification.
- L80: If session starts after range start, create `NOT_TRACKING` pre-segment.
- L87: If no visits and no locations, classify whole range as `NOT_TRACKING`.
- L97: Builds high-confidence confirmed visit segments.
- L100: Uses `rangeEnd` for active/open visit exit.
- L106: Assigns address label from place metadata.
- L111: Begins gap classification between visits.
- L112: Sets cursor after any pre-tracking segment.
- L117: Iterates each confirmed visit segment.
- L118: If gap before this visit, classify with raw points.
- L120: Calls `classifyGap` for the interval.
- L128: Appends inferred gap segments.
- L130: Appends confirmed visit segment.
- L131: Moves cursor to visit end.
- L135: Classifies trailing gap after last visit.
- L139: Calls `classifyGap` for remaining window.
- L148: Logs segment count.
- L149: Returns segments sorted by start time.

### Range: 156-239 (`classifyGap`)

- L156: Declares gap classifier.
- L162: Computes gap duration.
- L163: Drops trivially tiny gaps (<10s).
- L165: If no points exist, emits `UNTRACKED_WHILE_TRACKING`.
- L176: Computes stationary threshold from preferences with floor.
- L180: Handles leading no-data before first point.
- L182: If leading no-data > gap threshold, emits untracked segment.
- L192: Initializes contiguous-window scanner.
- L196: Checks inter-point temporal gaps.
- L200: If inter-point gap > threshold, flushes prior window and emits untracked segment.
- L214: Resets window start after untracked split.
- L220: Flushes final contiguous window.
- L227: Handles trailing no-data after last point.
- L229: Emits trailing untracked if threshold exceeded.
- L238: Returns inferred segments for this gap.

### Range: 245-342 (`classifyLocationWindow`)

- L245: Declares contiguous-window classifier.
- L249: If fewer than 2 points, cannot classify motion; returns empty.
- L253: Defines internal run model (`moving` vs `stationary`).
- L262: Iterates consecutive point pairs.
- L265: Computes pairwise distance.
- L269: Computes time delta with 1s floor.
- L273: Uses sensor speed if present, else derived speed.
- L276: Applies transit speed threshold.
- L278: Starts new run when movement state toggles.
- L282: Appends current point to active run.
- L286: Converts each run into a segment.
- L292: Moving run => build `TRANSIT` segment.
- L294: Initializes transit distance accumulator.
- L299: Sums pairwise route distance.
- L303: Collects sensor speeds for avg/max stats.
- L305: Computes average speed from sensor speeds if available.
- L308: Computes max speed.
- L309: Captures run coordinates for map plotting.
- L311: Derives activity hint from confident activity samples.
- L317: Emits `TRANSIT` segment with distance/speed/coords/confidence.
- L327: Stationary run long enough => emit `TRANSIENT_STOP`.
- L329: Computes stationary center point.
- L331: Emits stop segment with moderate confidence.
- L338: Short stationary runs intentionally absorbed into transit narrative.
- L341: Returns run-classified segments.

---

## 4) `PlaceDetectionUseCases.kt` Per-Line Trace

### Range: 118-190 (input prep + clustering)

- L118: Declares internal detection routine.
- L121: Caps max locations processed for memory safety.
- L122: Fetches recent locations snapshot.
- L123: Logs retrieval count.
- L125: Empty guard begins.
- L126: Logs hard failure reason for no-input case.
- L127: Returns empty result.
- L131: Chooses sample size for debug logs.
- L132: Logs representative location samples.
- L138: Computes batch size in safe bounds.
- L139: Initializes filtered location accumulator.
- L141: Logs batch filtering start.
- L142: Iterates batches.
- L143: Applies quality filter to each batch.
- L144: Appends filtered results.
- L145: Logs per-batch keep/drop counts.
- L148: Calculates removed count.
- L149: Logs aggregate filtering result.
- L151: Guard for all-filtered-out case.
- L152-L155: Logs actionable filtering diagnostics.
- L156: Returns empty if no quality points.
- L160: Starts optional clustering input cap logic.
- L161: Logs cap application.
- L162: Keeps most recent points when capping.
- L167: Converts locations to lat/lng pairs for DBSCAN.
- L168: Logs clustering start count.
- L169: Logs clustering params from preferences.
- L171: Executes DBSCAN helper.
- L172: Logs cluster count result.
- L174: Empty-cluster guard starts.
- L175-L179: Logs cluster-miss diagnostics.
- L180: Returns empty on no clusters.
- L184-L188: Logs centroid and size for each cluster.
- L190: Initializes `newPlaces` output list.

### Range: 195-371 (cluster-to-place pipeline)

- L195: Processes clusters in batches of 10 for DB/memory stability.
- L198-L199: Computes cluster centroid.
- L201-L203: Retrieves nearby existing places within detection radius.
- L206-L211: Computes min centroid distance to existing places.
- L214-L216: Checks geometric overlap ratio with existing place footprints.
- L219: Reads configured minimum inter-place distance.
- L220: Combines distance+overlap into create/skip decision.
- L223-L231: Logs create/skip reasoning.
- L235-L242: Re-maps cluster points back to original filtered locations.
- L244: Ensures cluster has enough supporting points.
- L246-L254: Computes activity distribution and dominant activity.
- L259-L268: Computes semantic-context distribution and dominant context.
- L272-L274: Sets category to `UNKNOWN` by policy.
- L285: Generates initial fallback name.
- L287-L301: Builds new `Place` aggregate with radius/confidence/activity/context fields.
- L304-L307: Attempts geocoding enrichment (best effort).
- L314: Inserts place.
- L315: Creates returned place with DB id.
- L316: Adds created place to output collection.
- L320-L325: Attempts initial visit backfill for historical cluster sessions.
- L331-L337: Creates review/auto-accept workflow entry.
- L342-L359: Fallback simplified place insert if normal insert fails.
- L362-L365: Per-cluster exception isolation; continue remaining clusters.
- L369: Logs final created place count.
- L370: Returns detected/created places.

---

## 5) Interpretation Guidance

- If a line is a guard (`if (...) return`), its significance is control-flow truncation: it determines whether downstream pipeline even runs.
- If a line writes via repository or gateway, significance is state mutation durability and cross-layer consistency.
- If a line computes thresholds or durations, significance is model sensitivity: it changes false-positive/false-negative balance.
- If a line logs diagnostics, significance is operational visibility: not user-facing behavior, but critical for debugging pipeline correctness.

---

## 6) Next Expansion Option

If needed, a second volume can be generated with per-line traces for:
- `MapViewModel.kt`
- `OpenStreetMapView.kt`
- `GenerateTimelineSegmentsUseCase.kt`
- `GeocodingRepositoryImpl.kt`
- `NominatimGeocodingService.kt`
- `OverpassApiService.kt`

