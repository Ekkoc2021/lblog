#!/usr/bin/env python3
"""AI Prompt Management — Complete API Integration Test Suite (post-fix)"""
import json
import urllib.request
import urllib.error
import sys

BASE = "http://localhost:8099/iblogserver/api/v1"
PASS = 0
FAIL = 0
WARN = 0

def api(method, path, body=None, token=None):
    url = f"{BASE}{path}"
    data = json.dumps(body).encode('utf-8') if body is not None else None
    headers = {'Content-Type': 'application/json'} if data else {}
    if token:
        headers['Authorization'] = f'Bearer {token}'
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return json.loads(resp.read().decode('utf-8'))
    except urllib.error.HTTPError as e:
        body_bytes = e.read()
        try:
            return json.loads(body_bytes.decode('utf-8'))
        except Exception:
            return {"code": e.code, "message": str(e)}

def check(test_id, condition, detail=""):
    global PASS, FAIL
    if condition:
        PASS += 1
        print(f"  [PASS] {test_id} {detail}")
    else:
        FAIL += 1
        print(f"  [FAIL] {test_id} {detail}")

def warn(test_id, detail=""):
    global WARN
    WARN += 1
    print(f"  [WARN] {test_id} {detail}")

def section(title):
    print(f"\n{'='*60}")
    print(f"  {title}")
    print(f"{'='*60}")

# ======== AUTH ========
section("T1: Authentication")

resp = api("POST", "/auth/login", {"username": "ekko", "password": "admin123"})
TOKEN = resp.get("data", {}).get("accessToken", "")
check("T1-01 Valid login", resp["code"] == 0 and bool(TOKEN), f"token={'...' if TOKEN else 'N/A'}")
check("T1-02 Role is admin", resp.get("data", {}).get("user", {}).get("role") == "admin")

resp = api("POST", "/auth/login", {"username": "ekko", "password": "wrong"})
check("T1-03 Invalid login rejected", resp["code"] == 401)

resp = api("GET", "/admin/ai/prompts")
check("T1-04 No auth rejected", resp["code"] in (401, 403), f"code={resp['code']}")

# ======== LIST & QUERY ========
section("T2: List & Query")

resp = api("GET", "/admin/ai/prompts", token=TOKEN)
check("T2-01 List all", resp["code"] == 0 and isinstance(resp["data"], list),
      f"count={len(resp.get('data',[]))}")

resp = api("GET", "/admin/ai/prompts?module=draw", token=TOKEN)
check("T2-02 Filter by module", resp["code"] == 0,
      f"count={len(resp.get('data',[]))}")

resp = api("GET", "/admin/ai/prompts/1", token=TOKEN)
check("T2-03 Get by id", resp["code"] == 0 and resp.get("data", {}).get("module") == "draw",
      f"module={resp.get('data', {}).get('module', 'N/A')}")

resp = api("GET", "/admin/ai/prompts/99999", token=TOKEN)
check("T2-04 Non-existent id", resp["code"] == 404)

resp = api("GET", "/admin/ai/prompts?promptKey=system-default", token=TOKEN)
check("T2-05 Filter by key", resp["code"] == 0,
      f"count={len(resp.get('data',[]))}")

# ======== CREATE ========
section("T3: Create Prompt")

resp = api("POST", "/admin/ai/prompts", {
    "module": "test", "promptKey": "suite-test",
    "content": "Integration test prompt content",
    "description": "Created by test suite", "sortOrder": 1
}, token=TOKEN)
NEW_ID = resp.get("data", {}).get("id")
check("T3-01 Create", resp["code"] == 0 and NEW_ID is not None,
      f"id={NEW_ID} v={resp.get('data', {}).get('version', 'N/A')}")

resp = api("POST", "/admin/ai/prompts", {
    "module": "draw", "promptKey": "system-default",
    "content": "duplicate should fail"
}, token=TOKEN)
check("T3-02 Duplicate rejected", resp["code"] != 0, f"code={resp['code']}")

# ======== UPDATE CONTENT ========
section("T4: Update Content (Versioning)")

resp = api("PUT", f"/admin/ai/prompts/{NEW_ID}", {
    "content": "Updated content v2", "operator": "ekko"
}, token=TOKEN)
d = resp.get("data") or {}
check("T4-01 Content update v2", resp["code"] == 0 and d.get("version") == 2,
      f"v={d.get('version', 'N/A')} code={resp.get('code', 'N/A')}")

resp = api("PUT", f"/admin/ai/prompts/{NEW_ID}", {
    "content": "Updated content v3", "operator": "ekko"
}, token=TOKEN)
d = resp.get("data") or {}
check("T4-02 Content update v3", resp["code"] == 0 and d.get("version") == 3,
      f"v={d.get('version', 'N/A')} code={resp.get('code', 'N/A')}")

# Verify old versions are inactive
resp = api("GET", f"/admin/ai/prompts/{NEW_ID}/versions", token=TOKEN)
versions = resp.get("data", [])
active_count = sum(1 for v in versions if v.get("isActive"))
check("T4-03 Only 1 active version", active_count == 1, f"active={active_count}")

# ======== UPDATE META ========
section("T5: Update Meta")

resp = api("PATCH", f"/admin/ai/prompts/{NEW_ID}", {
    "description": "Updated description by suite",
    "sortOrder": 99, "operator": "ekko"
}, token=TOKEN)
d = resp.get("data") or {}
check("T5-01 Update meta", resp["code"] == 0,
      f"desc={d.get('description', 'N/A')}")

# Verify meta update didn't change version
check("T5-02 Meta update keeps version", d.get("version") == 3,
      f"v={d.get('version', 'N/A')}")

# ======== VERSION HISTORY ========
section("T6: Version History")

resp = api("GET", f"/admin/ai/prompts/{NEW_ID}/versions", token=TOKEN)
check("T6-01 Version count", resp["code"] == 0 and len(resp.get("data", [])) >= 3,
      f"count={len(resp.get('data',[]))}")

# Check versions are ordered desc
vs = resp.get("data", [])
vers = [v["version"] for v in vs]
check("T6-02 Descending order", vers == sorted(vers, reverse=True), f"order={vers}")

# ======== AUDIT LOG ========
section("T7: Audit Log")

resp = api("GET", f"/admin/ai/prompts/{NEW_ID}/audit", token=TOKEN)
audits = resp.get("data", [])
check("T7-01 Audit entries exist", resp["code"] == 0 and len(audits) >= 4,
      f"count={len(audits)}")

actions = [a.get("action") for a in audits]
expected = ["UPDATE_META", "UPDATE", "UPDATE", "CREATE"]
all_present = all(a in actions for a in expected)
check("T7-02 All actions recorded", all_present, f"actions={actions[:6]}")

# Verify audit content capture
create_entry = [a for a in audits if a.get("action") == "CREATE"]
check("T7-03 CREATE audit has content",
      len(create_entry) > 0 and create_entry[0].get("newContent") is not None)

# ======== CACHE & SEED ========
section("T8: Cache & Seed")

resp = api("POST", "/admin/ai/prompts/reload", token=TOKEN)
check("T8-01 Reload cache", resp["code"] == 0)

resp = api("POST", "/admin/ai/prompts/seed?module=draw", token=TOKEN)
check("T8-02 Seed (idempotent)", resp["code"] == 0)

# ======== DELETE ========
section("T9: Delete (Soft Delete)")

resp = api("DELETE", f"/admin/ai/prompts/{NEW_ID}?operator=ekko", token=TOKEN)
check("T9-01 Soft delete", resp["code"] == 0)

# Verify it's now inactive
resp = api("GET", f"/admin/ai/prompts/{NEW_ID}", token=TOKEN)
d = resp.get("data") or {}
check("T9-02 Deleted prompt isActive=false",
      d.get("isActive") == False,
      f"isActive={d.get('isActive', 'N/A')}")

# ======== CONTRACT ========
section("T10: Frontend-Backend Contract")

required_fields = ["id","module","promptKey","content","version","sortOrder",
                   "description","isActive","effectiveFrom","effectiveTo",
                   "createdBy","updatedBy","createdAt","updatedAt"]
resp = api("GET", "/admin/ai/prompts/1", token=TOKEN)
data = resp.get("data") or {}
missing = [f for f in required_fields if f not in data]
check("T10-01 AdminPrompt contract", len(missing) == 0, f"missing={missing}" if missing else "all fields present")

audit_fields = ["id","promptId","module","promptKey","oldContent","newContent",
                "oldVersion","newVersion","action","operator","remark","createdAt"]
resp2 = api("GET", f"/admin/ai/prompts/{NEW_ID}/audit", token=TOKEN)
audit_data = (resp2.get("data") or [{}])[0] if resp2.get("data") else {}
audit_missing = [f for f in audit_fields if f not in audit_data]
check("T10-02 AdminPromptAudit contract",
      len(audit_missing) == 0,
      f"missing={audit_missing}" if audit_missing else "all fields present")

# ======== SUMMARY ========
section("RESULTS")
total = PASS + FAIL + WARN
print(f"  Total:  {total}")
print(f"  Passed: {PASS}")
print(f"  Failed: {FAIL}")
print(f"  Warnings: {WARN}")
print()

if FAIL == 0:
    print("  ALL TESTS PASSED")
else:
    print(f"  {FAIL} TEST(S) FAILED")
    sys.exit(1)
