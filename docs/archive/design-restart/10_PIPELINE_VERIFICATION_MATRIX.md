# Voyager Pipeline Verification Matrix

Purpose:
- define phase-gate verification checks for runtime correctness, integrity, performance, and UX consistency.

Status legend:
- `not_started`
- `in_progress`
- `passed`
- `failed`

## 1) Baseline Quality Gate

| ID | Check | Method | Pass Criteria | Current |
|---|---|---|---|---|
| Q-01 | Unit test compile | `./gradlew testDebugUnitTest --continue` | compiles and runs tests | failed |
| Q-02 | Smoke suite exists | test inventory | tracking/worker/state smoke tests present | in_progress |
| Q-03 | Critical warning triage | compile logs + review | no untriaged critical runtime warnings | in_progress |

## 2) Lifecycle and State Gates

| ID | Check | Method | Pass Criteria | Current |
|---|---|---|---|---|
| L-01 | Start/stop idempotency | integration test | repeated calls produce stable final state | not_started |
| L-02 | Pause/resume consistency | integration test | state transitions valid and persisted | not_started |
| L-03 | Single-writer state authority | architecture check + tests | core state writes only through gateway | not_started |
| L-04 | Startup reconciliation | cold start/restart scenario tests | deterministic state after startup | not_started |

## 3) Worker and Background Gates

| ID | Check | Method | Pass Criteria | Current |
|---|---|---|---|---|
| W-01 | Unique work policy enforcement | worker tests | no duplicate detection work chains | not_started |
| W-02 | Enqueue/cancel ownership | static review + tests | scheduler boundary is exclusive | not_started |
| W-03 | Boot restore flow | instrumentation scenario | intended tracking state restored safely | not_started |

## 4) Data Integrity and Timeline Gates

| ID | Check | Method | Pass Criteria | Current |
|---|---|---|---|---|
| D-01 | Single active visit invariant | repository/processor tests | max one active visit globally | not_started |
| D-02 | Duration validity | unit/integration tests | no negative durations | not_started |
| D-03 | Duplicate 0s segment prevention | timeline regression tests | no consecutive same-place 0s artifacts | not_started |
| D-04 | Transactional transition safety | DAO/repository tests | end+start transition is atomic | not_started |

## 5) UI Consistency Gates

| ID | Check | Method | Pass Criteria | Current |
|---|---|---|---|---|
| U-01 | Date parity across screens | integration/UI tests | map/timeline/insights date context matches | not_started |
| U-02 | Place context parity | integration/UI tests | selected place syncs correctly across flows | not_started |
| U-03 | Policy persistence | restart tests | category/display policies persist | in_progress |
| U-04 | Address display consistency | snapshot tests | location display policy applied everywhere | not_started |

## 6) Performance Gates

| ID | Check | Method | Pass Criteria | Current |
|---|---|---|---|---|
| P-01 | Timeline render budget | profiling + benchmark dataset | stable frame times under heavy day data | not_started |
| P-02 | Map layer update budget | profiling | marker/route refresh does not stall UI | not_started |
| P-03 | Analytics compute reuse | profiling + logs | no repeated heavy recompute without invalidation | not_started |

## 7) Privacy and Export Gates

| ID | Check | Method | Pass Criteria | Current |
|---|---|---|---|---|
| R-01 | Export anonymization enforcement | unit/integration tests | anonymize flag is honored in outputs | not_started |
| R-02 | Share privacy controls | UI/integration tests | privacy settings respected in all surfaces | not_started |
| R-03 | Permission-path correctness | instrumentation scenarios | permission-denied paths fail gracefully | in_progress |

## 8) Smart Tracking Foundation Gates (future phases)

| ID | Check | Method | Pass Criteria | Current |
|---|---|---|---|---|
| S-01 | Session lifecycle state machine | integration tests | legal transitions only | not_started |
| S-02 | Auto pause/resume correctness | movement simulation tests | state reflects movement transitions | not_started |
| S-03 | Restart recovery for active session | process death tests | session resumes or recovers safely | not_started |

## 9) Gate Usage Rule
- A phase is considered complete only when all gate IDs mapped to that phase in `11_GAP_TO_PHASE_TRACEABILITY.md` are `passed`.
