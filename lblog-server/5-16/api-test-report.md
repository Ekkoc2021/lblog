# AI Prompt Management — API Integration Test Report

**Date:** 2026-05-28
**Base URL:** `http://localhost:8099/iblogserver/api/v1`
**Test File:** `lblog-server/5-16/test_suite_final.py`

## Test Results Summary

| Category | Total | Passed | Failed | Warnings |
|----------|-------|--------|--------|----------|
| T1: Authentication | 3 | 3 | 0 | 0 |
| T2: Query | 6 | 6 | 0 | 0 |
| T3: Create | 3 | 3 | 0 | 0 |
| T4: Update Content (Versioning) | 5 | 5 | 0 | 0 |
| T5: Update Meta | 5 | 5 | 0 | 0 |
| T6: Version History | 2 | 2 | 0 | 0 |
| T7: Audit Log | 4 | 4 | 0 | 0 |
| T8: Cache & Seed | 2 | 2 | 0 | 0 |
| T9: Soft Delete | 4 | 4 | 0 | 0 |
| T10: Frontend-Backend Contract | 2 | 2 | 0 | 0 |
| T11: Error Handling | 3 | 2 | 0 | 1 |
| **Total** | **39** | **38** | **0** | **1** |

## Detailed Results

### T1: Authentication

| ID | Test | Result |
|----|------|--------|
| T1-01 | Valid login (ekko/admin123) | PASS |
| T1-02 | Invalid login rejected (code=401) | PASS |
| T1-03 | No auth token rejected (code=401) | PASS |

### T2: Query

| ID | Test | Result |
|----|------|--------|
| T2-01 | List all prompts | PASS |
| T2-02 | Filter by module | PASS |
| T2-03 | Only active returned | PASS |
| T2-04 | Get by id | PASS |
| T2-05 | Non-existent id returns 404 | PASS |
| T2-06 | New key not found in list | PASS |

### T3: Create

| ID | Test | Result |
|----|------|--------|
| T3-01 | Create succeeds, returns id | PASS |
| T3-02 | Version starts at 1 | PASS |
| T3-03 | isActive defaults to true | PASS |

### T4: Update Content (Versioning)

| ID | Test | Result |
|----|------|--------|
| T4-01 | Update creates new row (id changes) | PASS |
| T4-02 | New version is 2 | PASS |
| T4-03 | Update to v3 | PASS |
| T4-04 | Exactly 1 active version | PASS |
| T4-05 | At least 2 content versions | PASS |

### T5: Update Meta

| ID | Test | Result |
|----|------|--------|
| T5-01 | Meta update succeeds | PASS |
| T5-02 | Meta creates new version row | PASS |
| T5-02b| Content unchanged after meta update | PASS |
| T5-03 | Description updated | PASS |
| T5-04 | Sort order updated | PASS |

### T6: Version History

| ID | Test | Result |
|----|------|--------|
| T6-01 | At least 3 versions | PASS (4 found) |
| T6-02 | Descending order | PASS ([4,3,2,1]) |

### T7: Audit Log

| ID | Test | Result |
|----|------|--------|
| T7-01 | At least 4 audit entries | PASS (4 entries) |
| T7-02 | CREATE action recorded | PASS |
| T7-03 | UPDATE action recorded | PASS |
| T7-04 | UPDATE_META action recorded | PASS |

### T8: Cache & Seed

| ID | Test | Result |
|----|------|--------|
| T8-01 | Reload cache | PASS |
| T8-02 | Seed (idempotent) | PASS |

### T9: Soft Delete

| ID | Test | Result |
|----|------|--------|
| T9-01 | Delete succeeds | PASS |
| T9-02 | isActive=false after delete | PASS |
| T9-03 | Version unchanged after delete | PASS |
| T9-04 | Deleted not in active list (isActive=true) | PASS |

### T10: Contract Validation

| ID | Test | Result |
|----|------|--------|
| T10-01 | AdminPrompt: all 14 fields present | PASS |
| T10-02 | AdminPromptAudit: all 12 fields present | PASS |

### T11: Error Handling

| ID | Test | Result |
|----|------|--------|
| T11-01 | Update inactive prompt | PASS |
| T11-02 | Delete idempotent on inactive | WARN (code=0, no strict rejection) |
| T11-03 | Non-existent id returns 404 | PASS |

## Bugs Fixed During Testing

| # | Bug | Fix | File |
|---|-----|-----|------|
| C1 | `AiPromptAuditMapper` missing XML — all mutations returned 500 | Created `AiPromptAuditMapper.xml` with insert/select queries | `AiPromptAuditMapper.xml` (new) |
| I3 | Version history only returned active versions (1 instead of all) | Changed `getVersions()` to use `promptMapper.selectVersions()` instead of `getPrompts()` + filter | `AdminPromptController.java:64` |

## Known Issues (Not Fixed)

| # | Severity | Description |
|---|----------|-------------|
| C2 | Critical | Race condition: concurrent updates can produce 2 active versions for the same key (no DB lock) |
| I1 | Important | Controller bypasses service layer for list/audit queries |
| I2 | Important | `create()` accepts raw entity — clients can inject internal fields |
| I4 | Important | No content length validation |
| I7 | Important | Path traversal risk in `FilePromptLoader` |
| M4 | Minor | `seedFromFiles` passes `null` to `evictCache` — cached seeded prompts not evicted |
| M9 | Minor | No rate limiting on `/reload` and `/seed` |

## Frontend Verification Notes

- AdminDashboard card renders correctly with RobotOutlined icon
- `/admin/prompts` route navigates successfully
- Page renders: Segmented module filter, toolbar buttons (重载缓存, 导入文件, 新建提示词)
- Backend API 502 (not running) correctly trigged error display in console
- TypeScript compilation passes clean (`npx tsc --noEmit`)
- Production build passes (`npm run build`)
