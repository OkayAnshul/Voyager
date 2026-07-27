# Voyager Feature Rationalization Plan (Reliability-First)
**Date:** 2026-03-01  
**Scope:** Core feature audit + category/review optimization + multi-name/address place naming strategy

---

## 1. Goal
Prioritize reliability and real user value:
- Keep automatic life logging strong.
- Reduce unnecessary complexity.
- Improve correction flows (category + review + naming) with less friction.

---

## 2. Core Functions/Features (As Implemented)

### 2.1 Core Value Features (Keep and strengthen)
1. Background location tracking and ingestion.
2. Place detection and visit lifecycle creation.
3. Timeline + map + statistics for memory and behavior insights.
4. Place review queue for user correction and trust.
5. Local-first privacy architecture.

### 2.2 Supporting Features (Keep, but simplify)
1. Category visibility and assignment.
2. Advanced settings controls.
3. Worker orchestration and fallback paths.

### 2.3 Present but weakly utilized / not effectively shipped
1. Visit review domain pipeline (not clearly user-facing in active navigation).
2. Secondary/parallel analytics ViewModels not aligned with active nav path.
3. Some insights screens/components present but not part of primary flow.

---

## 3. Real Problem Fit vs Mismatch

### 3.1 Solving real problems well
1. "Where was I?" and "How long did I stay?" are solved by timeline/map/visits.
2. Background logging solves memory and routine tracking use cases.
3. Review queue allows user correction for trust and personalization.

### 3.2 Mismatches reducing user value
1. Multi-authority state writes can cause inconsistent behavior (trust loss).
2. Category visibility controls are partly in-memory and inconsistent across screens.
3. Review system can be over-heavy for straightforward high-confidence decisions.
4. Settings surface is too large for normal users.

---

## 4. Category System Assessment

### 4.1 Current use
- Categories power filtering, analytics, and place labeling.

### 4.2 Current issues
1. Category visibility persistence is incomplete in screen logic.
2. Map/Timeline category filtering loads defaults instead of durable settings in some paths.
3. Messaging/defaults in UI and ViewModel are inconsistent.

### 4.3 Decision
Use a **hybrid category model**:
1. Auto-assign category by system.
2. Ask user only for uncertain/high-impact cases.
3. Persist category visibility and apply uniformly across map/timeline.

---

## 5. Review System Assessment

### 5.1 Current use
- Place review is active and useful.
- Review decisions feed correction learning.

### 5.2 Current issues
1. Overly heavy flow for auto-accepted/auto-rejected cases.
2. Visit review pipeline appears underused in user-facing flow.
3. Review cards can be information-heavy for quick decisions.

### 5.3 Decision
Use **lightweight review-first UX**:
1. High confidence: minimal friction path.
2. Low confidence: explicit review queue.
3. Keep correction learning, reduce unnecessary review-row churn.

---

## 6. Place Naming Improvement (New Requirement)

### 6.1 Requirement
User must be able to choose a better place name from **all possible names/addresses**, not be restricted to one detected name.

### 6.2 Current state
- Multi-source suggestions already exist (OSM/geocoding/nearby/history/category default).
- Dialog supports suggestions + custom name.

### 6.3 Gaps
1. Ranking mostly static; personalization limited.
2. Full-address options are not first-class in suggestion UX.
3. Explanations for "why this suggestion is top" are limited.

### 6.4 Decision (Default)
**Ranked shortlist + user pick**:
1. Show top 3-5 ranked options with source + address preview.
2. Expand to full list on demand.
3. Always allow custom text input.
4. Preserve address as structured data; name is user-facing label.

### 6.5 Data/model upgrades
- Extend suggestion model with candidate type, subtitle, reason codes, and final score.
- Save naming decision metadata (selected source/type/score) for learning.

---

## 7. Underused vs Overused Areas

### 7.1 Underused / candidates for archive or proper wiring
1. Visit review operations if no user-facing route is added.
2. Parallel analytics ViewModels not in active navigation.

### 7.2 Overused / candidates for consolidation
1. State mutation paths across service/manager/repository/synchronizer.
2. Worker scheduling from multiple layers.
3. Excessive user-facing settings for internal tuning knobs.

---

## 8. Phased Execution

### Phase 0: Guardrails
1. Restore test baseline.
2. Add reliability KPIs and smoke checks.

### Phase 1: State and lifecycle authority
1. Single tracking lifecycle orchestrator.
2. Single state write gateway.

### Phase 2: Category consistency
1. Persist category visibility settings.
2. Apply same policy in map/timeline/query paths.

### Phase 3: Review simplification
1. Hybrid review policy.
2. Trim heavy flows for auto decisions.

### Phase 4: Naming upgrade
1. Ranked multi-name/address chooser.
2. Personalization feedback loop from user selections.

### Phase 5: Feature topology cleanup
1. Remove/archive unused feature paths.
2. Keep canonical analytics and review entry points.

---

## 9. Acceptance Criteria

1. Tracking and restart behavior is deterministic and stable.
2. No conflicting state write paths bypass gateway.
3. Category visibility persists and works identically across map/timeline.
4. Review backlog remains actionable and low-noise.
5. Naming UI allows multi-option selection (name/address) plus custom input.
6. Naming quality improves over time from correction/selection learning.

---

## 10. Defaults and Assumptions

1. Priority: reliability over expansion.
2. Category policy default: hybrid auto-assign + selective review.
3. Naming policy default: ranked shortlist + user pick.
4. Visit review remains non-core unless explicitly promoted in UI.
