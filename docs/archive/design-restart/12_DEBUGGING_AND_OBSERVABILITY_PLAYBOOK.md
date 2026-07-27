# Voyager Debugging and Observability Playbook

Purpose:
- standardize debugging for runtime/state/worker/data/UI issues.
- provide repeatable runbooks and minimum instrumentation expectations.

## 1) Debugging Principles
1. Reproduce first, patch second.
2. Capture timeline of events (startup -> state -> worker -> UI).
3. Verify invariants before and after fix.
4. Keep logs structured and correlation-friendly.

## 2) Minimum Observability Contract

## 2.1 Correlation IDs
Add/propagate IDs where possible:
- app session ID
- tracking session ID
- visit transition ID
- worker request ID

## 2.2 Structured log fields
Use consistent fields:
- `component`
- `operation`
- `state_before`
- `state_after`
- `duration_ms`
- `result`
- `error_code`

## 2.3 Key counters/metrics
1. tracking start/stop success rate
2. worker enqueue success/failure rate
3. startup reconciliation mismatch count
4. active visit invariant violations
5. duplicate/0s timeline segment count
6. UI policy load/persist failures

## 3) Runtime Debug Runbooks

## 3.1 Tracking does not start/stop correctly
1. check permission status and service start path
2. inspect orchestrator/service manager logs
3. verify state write path (single authority)
4. verify final state in DB + app state snapshot

## 3.2 State mismatch after restart
1. capture startup sequence logs
2. inspect reconciliation path decisions
3. compare app-state vs DB-state snapshots
4. verify fallback branches and retry behavior

## 3.3 Worker not executing / duplicate workers
1. inspect enqueue caller and unique work name/policy
2. check WorkManager initialization health logs
3. inspect work info transitions and cancellation paths
4. verify only scheduler boundary is used

## 3.4 Timeline duplicate/0s anomalies
1. inspect visit transition logs around anomaly window
2. verify active visit count invariant
3. inspect segment generator grouping decisions
4. classify anomaly root cause: transition, duration, or grouping

## 3.5 Address rendering inconsistency
1. inspect display policy loaded for screen
2. verify formatter inputs (full address/locality/coordinates)
3. compare map/timeline/detail rendering outputs

## 4) Data Integrity Checks (Automatable)
Run periodically and in CI:
1. active visit count <= 1
2. visit duration >= 0
3. no orphan current-place references
4. no consecutive same-place 0s timeline segments
5. no duplicate work requests for same logical worker key

## 5) Incident Severity and Escalation

## Critical
- tracking lifecycle broken
- startup state corruption
- data loss or invariant violations

Action:
- stop rollout
- capture logs + DB snapshot
- hotfix with rollback readiness

## High
- worker mis-scheduling
- timeline integrity regressions
- policy persistence failures

Action:
- patch in current sprint and gate next phase

## Medium/Low
- UX inconsistency, non-blocking performance issues

Action:
- batch into optimization slice with acceptance criteria

## 6) Pre-Merge Debug Checklist
1. Reproduction case documented.
2. Root cause identified and linked to component.
3. Fix includes tests for scenario.
4. Logs/metrics updated where needed.
5. Gate IDs in `10_PIPELINE_VERIFICATION_MATRIX.md` updated.
6. Gap closure updated in `11_GAP_TO_PHASE_TRACEABILITY.md`.

## 7) Post-Release Monitoring Window
For each phase release window:
- Day 0-2: monitor critical/high metrics hourly.
- Day 3-7: monitor daily trend and anomaly counts.
- rollback trigger: critical metric threshold breach or unresolved high-severity regression.
