# Complete BPM Backend — Full Implementation Plan

## Summary

This plan completes the backend to fully support: admin dashboard, seguimiento agrupado, client tracking, policies with real departments, parallel workflow engine (FORK/JOIN), decision nodes, and a clean frontend-ready API. All changes are additive or surgical modifications to existing files — no breaking changes to existing endpoints, Mongo collections, analytics aggregations, or status enums.

---

## Current State Analysis

After reviewing all ~45 source files, the codebase is **already well-structured**. Most of what was requested is partially or fully implemented:

| Feature | Status | Gap |
|---|---|---|
| Workflow engine (sequential) | ✅ Done | — |
| FORK/JOIN (parallel) | ✅ Done | — |
| DECISION nodes | ✅ Done | — |
| Overview endpoint | ✅ Done (`OverviewService`) | Missing controller endpoint wiring |
| My-requests (client) | ✅ Done | Needs grouped-task enrichment |
| Dashboard | ✅ Done | Department load uses `laneId` not `departmentId` |
| Policy with departments | ✅ Done | — |
| Start-form endpoint | ✅ Done | — |
| User filtering | ✅ Done | — |
| Departments CRUD | ✅ Done | — |
| UserResponse (no password) | ✅ Done | — |
| Swagger annotations | ⚠️ Partial | Missing on several endpoints |
| ProcessInstance.processTypeId | ❌ Missing | Not stored, not returned in overview |
| Client my-requests grouped | ❌ Missing | Returns raw `ProcessInstance`, not grouped |
| Overview endpoint wiring | ❌ Missing | `OverviewService` exists but no controller route |
| Department load by departmentId | ⚠️ Incorrect | Aggregates by `laneId` instead of `departmentId` |
| Security cleanup | ⚠️ Needed | `BpmApplication` test insert, unused imports |

---

## Proposed Changes

### Component 1: ProcessInstance Document

#### [MODIFY] [ProcessInstance.java](file:///c:/Users/Toshiba/Desktop/parcial1_SW1/sw_p1/bpm/src/main/java/com/workflow/bpm/workflow/document/ProcessInstance.java)

- Add `processTypeId` field — stores the ProcessType ID from the policy at instance creation time

---

### Component 2: Workflow Engine — processTypeId propagation

#### [MODIFY] [WorkflowEngine.java](file:///c:/Users/Toshiba/Desktop/parcial1_SW1/sw_p1/bpm/src/main/java/com/workflow/bpm/workflow/engine/WorkflowEngine.java)

- In `startProcess()`, read `def.getProcessTypeId()` and stamp it on the new `ProcessInstance`

---

### Component 3: Overview Endpoint Wiring + Client Grouped View

#### [MODIFY] [WorkflowController.java](file:///c:/Users/Toshiba/Desktop/parcial1_SW1/sw_p1/bpm/src/main/java/com/workflow/bpm/workflow/WorkflowController.java)

- Add `GET /api/workflow/instances/overview` endpoint wired to `OverviewService.getOverview()`
- Add `GET /api/workflow/instances/my-requests/overview` endpoint for client grouped view
- Add full Swagger `@Operation` / `@Parameter` annotations on new endpoints
- Inject `OverviewService`

#### [MODIFY] [OverviewService.java](file:///c:/Users/Toshiba/Desktop/parcial1_SW1/sw_p1/bpm/src/main/java/com/workflow/bpm/workflow/OverviewService.java)

- Populate `processTypeId` from ProcessInstance (once added)
- Add method `getClientOverview(String clientId, String status)` which reuses `getOverview()` with userId filter

---

### Component 4: Dashboard Department Load Fix

#### [MODIFY] [AnalyticsRepository.java](file:///c:/Users/Toshiba/Desktop/parcial1_SW1/sw_p1/bpm/src/main/java/com/workflow/bpm/analytics/AnalyticsRepository.java)

- In `getDepartmentLoad()` (QUERY 4), change grouping from `laneId` to `departmentId` so department load maps correctly

#### [MODIFY] [AnalyticsService.java](file:///c:/Users/Toshiba/Desktop/parcial1_SW1/sw_p1/bpm/src/main/java/com/workflow/bpm/analytics/AnalyticsService.java)

- In `getDashboard()`, map `DepartmentLoad` using `departmentId` field instead of `laneId`
- Enrich department load with actual department names from `DepartmentRepository`

#### [MODIFY] [DepartmentLoad.java](file:///c:/Users/Toshiba/Desktop/parcial1_SW1/sw_p1/bpm/src/main/java/com/workflow/bpm/analytics/dto/DepartmentLoad.java)

- Add `departmentId` and `departmentName` fields alongside existing `laneId`/`laneName` for backwards compatibility

---

### Component 5: Swagger Annotations Completion

#### [MODIFY] [AnalyticsController.java](file:///c:/Users/Toshiba/Desktop/parcial1_SW1/sw_p1/bpm/src/main/java/com/workflow/bpm/analytics/AnalyticsController.java)

- Add `@Tag`, `@Operation`, `@Parameter` on all endpoints

#### [MODIFY] [PolicyController.java](file:///c:/Users/Toshiba/Desktop/parcial1_SW1/sw_p1/bpm/src/main/java/com/workflow/bpm/policy/PolicyController.java)

- Add `@Operation` on endpoints that are missing it

#### [MODIFY] [UserController.java](file:///c:/Users/Toshiba/Desktop/parcial1_SW1/sw_p1/bpm/src/main/java/com/workflow/bpm/user/UserController.java)

- Add `@Parameter` annotations for filter params

#### [MODIFY] [DepartmentController.java](file:///c:/Users/Toshiba/Desktop/parcial1_SW1/sw_p1/bpm/src/main/java/com/workflow/bpm/department/DepartmentController.java)

- Already has good annotations, minor additions for parameters

---

### Component 6: Security Cleanup

#### [MODIFY] [BpmApplication.java](file:///c:/Users/Toshiba/Desktop/parcial1_SW1/sw_p1/bpm/src/main/java/com/workflow/bpm/BpmApplication.java)

- Remove test document insertion on startup (side effect)

---

## Open Questions

> [!IMPORTANT]
> **Department Load aggregation**: Currently the dashboard aggregates task load by `laneId`. Should I change this to group by `departmentId` (which is the actual department reference stored on each task)? Lanes and departments have a 1:1 mapping through `Lane.departmentId`, but grouping by `departmentId` directly is more correct and maps to actual department names. **My plan assumes YES — group by `departmentId`.**

> [!NOTE]
> **Existing functionality already in place**: The workflow engine already fully supports START, TASK/ACTIVITY, DECISION, FORK, JOIN, and END nodes with proper executors. The `OverviewService` already performs the N+1-optimized grouped query. The `my-requests` endpoint already works. The `start-form` endpoint already exists. User filtering by role and departmentId already works. I will focus only on the gaps identified above.

---

## Verification Plan

### Automated Tests

```bash
./mvnw.cmd clean compile
```

Compile must succeed with zero errors.

### Manual Verification

1. Start the application: `./mvnw.cmd spring-boot:run`
2. Check Swagger UI: `http://localhost:8080/swagger-ui/index.html`  
   - Verify new overview endpoint appears
   - Verify all endpoints have proper descriptions
3. Test new endpoints with curl:
   - `GET /api/workflow/instances/overview`  
   - `GET /api/workflow/instances/overview?status=IN_PROGRESS`
   - `GET /api/workflow/instances/overview?departmentId=XXX`
   - `GET /api/workflow/instances/my-requests/overview` (with JWT)
