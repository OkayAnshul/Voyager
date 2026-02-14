# Voyager Restart Docs Index

This folder is the restart source of truth.

## Core Documents
1. `01_ARCHITECTURE_AND_DESIGN.md`
- As-is architecture, target architecture, phased migration, constraints.

2. `02_CORE_FUNCTIONS_GAPS_AND_STRUCTURE.md`
- Core function analysis, structural issues, severity-ranked gaps.

3. `03_WIRING_AND_FUNCTION_MAPPING.md`
- End-to-end trigger/handler/write wiring map and authority mapping.

## Execution Document
4. `04_IMPLEMENTATION_BACKLOG.md`
- Prioritized actionable engineering backlog with acceptance criteria.

## Optimization Extension
5. `05_FUNCTION_SEPARATION_AND_OPTIMIZATION_RESEARCH.md`
- Deep-dive on restart-critical fixes:
- smart insight system redesign,
- day/place/visit data grain separation,
- shutdown/restart reliability,
- place-name suggestion ranking upgrades,
- timeline duplicate/0s visit fixes,
- full location display consistency,
- additional core optimization opportunities.

## Next-Gen Product Extension
6. `06_NEXT_GEN_UI_AND_SMART_TRACKING_PLAN.md`
- Product and UI expansion plan:
- new screen system (including Track tab),
- smart running/walking tracking engine,
- advanced insight and recommendation features,
- Strava-style optional posting and sharing,
- rollout and test strategy for high-output UX.

## Master Execution Control
7. `07_MASTER_PHASE_EXECUTION_PLAN.md`
- Authoritative, conflict-safe implementation order across `01..06`.
- Defines non-conflict rules, phase gates, dependency order, rollback policy, and ownership model.

## Sprint Breakdown
8. `08_PHASE_WORK_BREAKDOWN.md`
- Sprint-ready slice plan mapped to master phases.
- Includes per-slice tasks, owned modules/files, effort/risk, dependencies, and deliverables.

## Deep Audit and Gate Evidence
9. `09_DEEP_AUDIT_FINDINGS.md`
- Consolidated audit findings across codebase, pipeline, structure, reliability, and UX.

10. `10_PIPELINE_VERIFICATION_MATRIX.md`
- Phase-gate verification matrix with measurable checks and pass criteria.

11. `11_GAP_TO_PHASE_TRACEABILITY.md`
- Maps each identified gap to owning phase and required verification IDs.

12. `12_DEBUGGING_AND_OBSERVABILITY_PLAYBOOK.md`
- Standard debugging runbooks, integrity checks, and observability requirements.

## Recommended Usage Order
1. Read `01` to understand boundaries and target shape.
2. Read `02` to understand why the refactor is needed and priority.
3. Read `03` before touching runtime/state/worker flows.
4. Execute tasks in `04` phase by phase.
5. Use `05` as optimization and reliability implementation guidance while executing `04`.
6. Use `06` when implementing next-gen UX and smart tracking capabilities.
7. Follow `07` as the final execution sequencing authority when conflicts or overlaps appear.
8. Use `08` for sprint planning, ownership assignment, and delivery tracking per phase slice.
9. Use `09` to understand current gaps and baseline health before implementation.
10. Use `10` as the mandatory phase-gate checklist.
11. Use `11` to track gap ownership and closure evidence.
12. Use `12` for debugging standards and post-release monitoring.
