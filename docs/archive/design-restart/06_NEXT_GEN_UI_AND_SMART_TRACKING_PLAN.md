# Voyager Next-Gen UI and Smart Tracking Plan

## 1. Goal
Evolve Voyager into a dual-mode product:
- Life tracking (places, visits, timeline intelligence)
- Workout tracking (running/walking, Strava-like live experience, optional social sharing)

This plan defines screen structure, function placement, new capabilities, and rollout strategy.

## 2. Target UI Information Architecture

### 2.1 Primary tabs (5)
1. Home
2. Track
3. Timeline
4. Map
5. Insights

### 2.2 Secondary screens
1. Activity Details
2. Post Composer
3. Challenges and Goals
4. Segments
5. Route Builder
6. Safety Live Share
7. Place Review Queue
8. Categories and Visibility
9. Data, Privacy, Export
10. Advanced Control

### 2.3 Screen count
- Primary screens: 5
- Secondary screens: 10
- Total user-facing (non-debug): 15
- Debug screens remain developer-gated only

## 3. Feature Placement and Ownership

## 3.1 Home
Keep:
- daily summary
- tracking status
- current place/session
- quick actions (start tracking, go to review queue, jump to map)

Do not place:
- deep analytics charts
- advanced settings and technical controls

## 3.2 Track (new)
Owns:
- start/pause/resume/finish workout
- live map and live metrics
- split/lap events
- audio/haptic alerts
- safety share trigger

## 3.3 Timeline
Owns:
- merged life + workout chronology
- filters: visits/workouts/both
- correction entry points

## 3.4 Map
Owns:
- place and route overlays
- route replay
- segment overlays
- full location rendering policy application

## 3.5 Insights
Owns:
- trends and distributions
- training load/readiness analytics
- anomalies
- recommendations

## 3.6 Control menu (top-right profile/menu)
Owns:
- settings
- categories visibility
- data/privacy/export
- advanced settings
- debug/developer tools (gated)

## 4. Advanced and Unique Features (UI-Ready)

## 4.1 Smart workout tracking features
1. Auto workout detection (walk/run suggestion)
2. Auto pause/resume
3. Live pace zones and split alerts
4. Interval/lap mode (manual + automatic)
5. Route adherence and off-route alerts
6. Segment effort detection (PR and comparative context)
7. Effort score and training load trend

## 4.2 Voyager-native differentiators
1. Place-to-performance correlation
- show speed/effort patterns by place and time window
2. Routine-aware run suggestions
- suggest best run windows from historical movement patterns
3. Contextual safety confidence
- route confidence by historical time/place behavior
4. Unified life + fitness timeline
- one narrative across visits and workouts
5. Recovery-aware recommendations
- lower intensity suggestions when overload is detected

## 4.3 Share and social features (opt-in)
1. Activity post card (map, distance, pace, splits, highlights)
2. Privacy levels: private, followers-only, public
3. Hidden start/end zones for privacy
4. Kudos/comments model
5. Challenge and streak systems

## 5. Smart Tracking Mechanism (Core System)

## 5.1 New engine
Introduce `SmartTrackingEngine` with explicit state machine:
- Idle -> Warmup -> Active -> AutoPaused -> Cooldown -> Completed

Input channels:
- fused GPS points
- activity recognition transitions
- speed variance and motion confidence
- optional Health Connect exercise integration

Output channels:
- live metric stream
- split/lap/pause/resume events
- finalized workout session record

## 5.2 Adaptive sampling strategy
- Idle: low frequency
- Warmup/Active: high frequency/high accuracy
- AutoPaused: medium frequency for confirmation
- User-selectable mode: Eco/Balanced/Performance

## 5.3 Integrity and accuracy layer
1. GPS smoothing and outlier rejection
2. Unrealistic speed spike filtering
3. Optional route map matching
4. Session confidence score for integrity-sensitive features

## 5.4 New domain types
- `WorkoutSession`
- `WorkoutPoint`
- `Split`
- `Lap`
- `SegmentEffort`
- `TrainingLoadSnapshot`
- `ActivityPost`
- `SharePrivacyConfig`

## 6. Optimized Side-by-Side Function Operations

## 6.1 Pipeline model
1. Tracking pipeline
- location/motion ingestion -> smart tracking engine -> canonical workout + visit events
2. State pipeline
- single state write authority persists session/visit state changes
3. Aggregate pipeline
- day/place/visit/workout aggregates generated once, reused across screens
4. Presentation pipeline
- policy-driven formatters + screen adapters render data

## 6.2 Cross-screen synchronization rules
- single selected date context for timeline/map/insights
- single selected place context for timeline/map/review
- single location display policy for all UI surfaces
- single category visibility policy

## 6.3 Performance guardrails
- no heavy aggregation in composables
- query-keyed caches for aggregates
- incremental live updates (diff updates, not full rerender)
- background recalculation for expensive analytics

## 7. Public Interface Additions (Planned)
1. `SmartTrackingEngine`
- `startSession(config)`
- `pauseSession()`
- `resumeSession()`
- `finishSession()`
- `observeLiveMetrics()`

2. `WorkoutRepository`
- session CRUD
- points, splits, lap retrieval

3. `SegmentService`
- segment definition
- effort detection
- PR comparisons

4. `PostService`
- post draft creation
- privacy enforcement
- publish/unpublish workflow

5. `TrainingInsightsService`
- load/readiness computation
- recommendation feed

6. `UiPolicyRepository`
- display density
- location display policy
- privacy defaults
- tracking cue preferences

## 8. Rollout Plan

### Phase 7.1 - Navigation and shell
- add Track tab
- move Settings under Control menu
- wire merged timeline filters

### Phase 7.2 - Core smart tracking
- implement workout session flow
- live metrics, auto pause/resume, split/lap

### Phase 7.3 - Insight and recommendation layer
- training load/readiness
- contextual recommendations

### Phase 7.4 - Share and social
- post composer
- privacy controls
- feed interactions

### Phase 7.5 - Competitive/safety enhancements
- segments and PRs
- safety live sharing
- confidence/integrity scoring

## 9. Test Matrix
1. Motion transition correctness (still/walk/run)
2. GPS stress scenarios (drift/tunnel/spikes)
3. Session lifecycle integrity (single active workout, valid split times)
4. Cross-screen parity for totals and dates
5. Privacy controls enforced in all share outputs
6. Restart/process death recovery for active sessions
7. Battery mode behavior matches policy

## 10. Assumptions and Defaults
- Existing stack is retained (Compose/Hilt/Room/WorkManager)
- Scope starts with running/walking first
- Social posting is opt-in and private-by-default
- Advanced competitive features rely on integrity score readiness

## 11. Research and Reference Links
- Strava record UX direction:
- https://press.strava.com/articles/strava-launches-redesigned-record-experience
- Strava subscription/feature framing:
- https://support.strava.com/hc/en-us/articles/216917657-Strava-Subscription-Features
- Android activity transitions and recognition:
- https://developer.android.com/develop/sensors-and-location/location/transitions
- https://developers.google.com/android/reference/com/google/android/gms/location/ActivityRecognitionClient
- Health Connect exercise route/session guidance:
- https://developer.android.com/health-and-fitness/guides/health-connect/develop/exercise-routes
- https://developer.android.com/health-and-fitness/health-connect/features/exercise-routes
