# Voyager Gap-to-Phase Traceability

Purpose:
- map observed gaps to master phases and verification gates.
- prevent ambiguous ownership and cross-phase conflicts.

## 1) Traceability Table

| Gap ID | Gap Description | Severity | Primary Phase | Secondary Phase | Verification IDs |
|---|---|---|---|---|---|
| G-01 | Unit test baseline broken (dependency + fixture drift) | Critical | Phase 0 | - | Q-01, Q-02 |
| G-02 | Multi-writer state mutation paths | Critical | Phase 1 | Phase 2 | L-03, L-04 |
| G-03 | Lifecycle orchestration duplication | Critical | Phase 2 | Phase 1 | L-01, L-02 |
| G-04 | Worker scheduling/control duplicated | High | Phase 2 | Phase 6 | W-01, W-02 |
| G-05 | Duplicate/0s timeline segment anomalies | High | Phase 3 | Phase 4 | D-03, D-04 |
| G-06 | Visit transition non-atomic behavior risk | High | Phase 3 | - | D-01, D-04 |
| G-07 | UI policy persistence TODOs | Medium | Phase 4 | Phase 7 | U-03 |
| G-08 | Location display inconsistency | Medium | Phase 4 | Phase 7 | U-04 |
| G-09 | Insights schema/explainability fragmentation | Medium | Phase 5 | - | U-01, P-03 |
| G-10 | Hotspot class maintainability risk | High | Phase 6 | - | P-01, P-02 |
| G-11 | Preferences architecture sprawl | High | Phase 6 | Phase 4 | Q-03, P-03 |
| G-12 | Navigation semantics drift | Medium | Phase 7 | - | U-01, U-02 |
| G-13 | Cross-screen context fragility | Medium | Phase 7 | Phase 4 | U-01, U-02 |
| G-14 | Smart tracking foundation missing | Future | Phase 8 | - | S-01, S-02, S-03 |
| G-15 | Social/privacy share controls missing | Future | Phase 9 | - | R-02 |
| G-16 | Export anonymization enforcement gap | Medium | Phase 6 | Phase 9 | R-01 |

## 2) Ownership Policy
- Each gap has one primary phase owner.
- Secondary phase may optimize or extend but not redefine completion criteria.
- Gate IDs are mandatory evidence for closure.

## 3) Conflict Resolution Rule
If multiple docs suggest different phase placement:
1. follow primary phase in this file,
2. follow sequencing from `07_MASTER_PHASE_EXECUTION_PLAN.md`,
3. use slice assignment from `08_PHASE_WORK_BREAKDOWN.md`.

## 4) Closure Template (Per Gap)
- Gap ID:
- Phase owner:
- Changes landed:
- Verification IDs passed:
- Residual risk:
- Follow-up phase (if any):
