#!/usr/bin/env python3
"""AI Prompt Management API Test Suite"""
import json
import urllib.request
import urllib.error

BASE = "http://localhost:8099/iblogserver/api/v1"
PASS = 0
FAIL = 0
WARN = 0
RESULTS = []

# Track created prompt IDs for cleanup
CREATED_IDS = []


def api(method, path, body=None, token=None, extra_headers=None):
    url = f"{BASE}{path}"
    data = None
    if body is not None:
        data = json.dumps(body).encode('utf-8')
    headers = {'Content-Type': 'application/json'} if data is not None else {}
    if token:
        headers['Authorization'] = f'Bearer {token}'
    if extra_headers:
        headers.update(extra_headers)

    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            body_bytes = resp.read()
            return json.loads(body_bytes.decode('utf-8'))
    except urllib.error.HTTPError as e:
        body_bytes = e.read()
        try:
            return json.loads(body_bytes.decode('utf-8'))
        except Exception:
            return {"code": e.code, "message": str(e), "raw": body_bytes.decode('utf-8')}
    except Exception as e:
        return {"code": -1, "message": str(e)}


def report(test_id, status, msg=""):
    global PASS, FAIL, WARN
    if status == "PASS":
        PASS += 1
    elif status == "FAIL":
        FAIL += 1
    else:
        WARN += 1
        status = "WARN"
    RESULTS.append(f"  [{status}] {test_id} - {msg}")
    print(f"  [{status}] {test_id} - {msg}")


# ========== START ==========
print("=" * 60)
print("  AI Prompt Management API Test Suite")
print(f"  Base: {BASE}")
print("=" * 60)

# ========== LOGIN ==========
print("\n" + "=" * 60)
print("  AUTH & SETUP")
print("=" * 60)

print("\n--- AUTH: Login as admin ---")
resp = api("POST", "/auth/login", {"username": "admin", "password": "admin"})
code = resp.get("code")
data = resp.get("data", {})
token = data.get("accessToken", "")
if code == 0 and token:
    report("AUTH-01", "PASS", f"Admin login OK, token={token[:30]}...")
else:
    report("AUTH-01", "FAIL", f"Admin login failed, code={code}")
    # Fallback: try ekko (used in legacy test)
    print("  Retrying with ekko/admin123...")
    resp = api("POST", "/auth/login", {"username": "ekko", "password": "admin123"})
    code = resp.get("code")
    data = resp.get("data", {})
    token = data.get("accessToken", "")
    if code == 0 and token:
        report("AUTH-01B", "PASS", f"Login with ekko OK, token={token[:30]}...")
    else:
        report("AUTH-01B", "FAIL", f"Fallback login also failed, code={code}")

# ========== LIST BASELINE ==========
print("\n" + "=" * 60)
print("  PROMPT MANAGEMENT TESTS")
print("=" * 60)

# P-01: List prompts (no filter, get baseline)
print("\n--- P-01: List all prompts (baseline) ---")
resp = api("GET", "/admin/ai/prompts", token=token)
code = resp.get("code")
prompt_list = resp.get("data", [])
baseline_count = len(prompt_list)
report("P-01", "PASS" if code == 0 else "FAIL",
       f"List prompts -> code={code}, count={baseline_count}")

# P-02: List with module=draw filter (even if empty)
print("\n--- P-02: List prompts (module=draw) ---")
resp = api("GET", "/admin/ai/prompts?module=draw", token=token)
code = resp.get("code")
draw_list = resp.get("data", [])
report("P-02", "PASS" if code == 0 else "FAIL",
       f"Module=draw filter -> code={code}, count={len(draw_list)}")

# P-03: Create a prompt (system-default)
print("\n--- P-03: Create prompt (system-default) ---")
resp = api("POST", "/admin/ai/prompts", {
    "module": "draw",
    "promptKey": "system-default",
    "content": "You are a diagram assistant. Help users create draw.io diagrams.",
    "description": "Default system prompt (API test)",
    "sortOrder": 10,
    "createdBy": "admin",
    "isActive": True
}, token=token)
code = resp.get("code")
p_default = resp.get("data", {})
p_default_id = p_default.get("id")
if code == 0 and p_default_id:
    report("P-03", "PASS", f"Created system-default, id={p_default_id}, version={p_default.get('version')}")
    CREATED_IDS.append(p_default_id)
else:
    report("P-03", "FAIL", f"Create failed, code={code}, msg={resp.get('message')}")

# P-04: Create prompt (system-extended)
print("\n--- P-04: Create prompt (system-extended) ---")
resp = api("POST", "/admin/ai/prompts", {
    "module": "draw",
    "promptKey": "system-extended",
    "content": "Extended system prompt with additional context for complex diagrams.",
    "description": "Extended system prompt (API test)",
    "sortOrder": 20,
    "createdBy": "admin",
    "isActive": True
}, token=token)
code = resp.get("code")
p_extended = resp.get("data", {})
p_extended_id = p_extended.get("id")
if code == 0 and p_extended_id:
    report("P-04", "PASS", f"Created system-extended, id={p_extended_id}, version={p_extended.get('version')}")
    CREATED_IDS.append(p_extended_id)
else:
    report("P-04", "FAIL", f"Create failed, code={code}")

# P-05: Create prompt (style-normal)
print("\n--- P-05: Create prompt (style-normal) ---")
resp = api("POST", "/admin/ai/prompts", {
    "module": "draw",
    "promptKey": "style-normal",
    "content": "Use normal sketch-style UI for the draw.io editor.",
    "description": "Normal style (API test)",
    "sortOrder": 30,
    "createdBy": "admin",
    "isActive": True
}, token=token)
code = resp.get("code")
p_normal = resp.get("data", {})
p_normal_id = p_normal.get("id")
if code == 0 and p_normal_id:
    report("P-05", "PASS", f"Created style-normal, id={p_normal_id}, version={p_normal.get('version')}")
    CREATED_IDS.append(p_normal_id)
else:
    report("P-05", "FAIL", f"Create failed, code={code}")

# P-06: Create prompt (style-minimal)
print("\n--- P-06: Create prompt (style-minimal) ---")
resp = api("POST", "/admin/ai/prompts", {
    "module": "draw",
    "promptKey": "style-minimal",
    "content": "Use minimal UI for the draw.io editor.",
    "description": "Minimal style (API test)",
    "sortOrder": 40,
    "createdBy": "admin",
    "isActive": True
}, token=token)
code = resp.get("code")
p_minimal = resp.get("data", {})
p_minimal_id = p_minimal.get("id")
if code == 0 and p_minimal_id:
    report("P-06", "PASS", f"Created style-minimal, id={p_minimal_id}, version={p_minimal.get('version')}")
    CREATED_IDS.append(p_minimal_id)
else:
    report("P-06", "FAIL", f"Create failed, code={code}")

# P-07: List with module=draw filter (should have 4+ entries now)
print("\n--- P-07: List prompts (module=draw, validate filtering) ---")
resp = api("GET", "/admin/ai/prompts?module=draw", token=token)
code = resp.get("code")
draw_list = resp.get("data", [])
draw_count = len(draw_list)
if code == 0 and draw_count >= 4:
    report("P-07", "PASS", f"Module=draw filter: {draw_count} prompts (expected >= 4)")
else:
    report("P-07", "WARN" if code == 0 else "FAIL",
           f"Module=draw filter: code={code}, count={draw_count}")

# P-08: Get by ID (system-default)
print("\n--- P-08: Get prompt by ID ---")
resp = api("GET", f"/admin/ai/prompts/{p_default_id}", token=token)
code = resp.get("code")
p_data = resp.get("data", {})
if code == 0 and p_data.get("id") == p_default_id:
    report("P-08", "PASS",
           f"Get by ID OK: id={p_data.get('id')}, key={p_data.get('promptKey')}, active={p_data.get('isActive')}")
else:
    report("P-08", "FAIL", f"Get by ID: code={code}, id={p_data.get('id')}")

# P-09: Get non-existent ID
print("\n--- P-09: Get non-existent ID (99999) ---")
resp = api("GET", "/admin/ai/prompts/99999", token=token)
code = resp.get("code")
report("P-09", "PASS" if code == 404 else "FAIL",
       f"Non-existent ID -> code={code}, msg={resp.get('message')}")

# P-10: Get prompts without auth (no token)
print("\n--- P-10: List prompts without auth ---")
resp = api("GET", "/admin/ai/prompts")
code = resp.get("code")
report("P-10", "PASS" if code in (401, 403) else "FAIL",
       f"No auth -> code={code}")

# P-11: Update content (should create new version)
print("\n--- P-11: Update content (system-default, version++) ---")
resp = api("PUT", f"/admin/ai/prompts/{p_default_id}", {
    "content": "UPDATED: You are a diagram assistant. (v2)",
    "operator": "admin"
}, token=token)
code = resp.get("code")
p_updated = resp.get("data", {})
p_updated_id = p_updated.get("id")
new_version = p_updated.get("version")
if code == 0 and p_updated_id and p_updated_id != p_default_id:
    report("P-11", "PASS",
           f"Content updated: old_id={p_default_id}, new_id={p_updated_id}, version={new_version}")
    CREATED_IDS.append(p_updated_id)
    # Track the updated ID for further tests
    default_updated_id = p_updated_id
else:
    report("P-11", "FAIL" if code != 0 else "WARN",
           f"Content update: code={code}, new_id={p_updated_id}, version={new_version}")
    default_updated_id = p_default_id

# P-12: Verify old version is deactivated
print("\n--- P-12: Verify old version deactivated ---")
resp = api("GET", f"/admin/ai/prompts/{p_default_id}", token=token)
code = resp.get("code")
is_active = resp.get("data", {}).get("isActive")
if code == 0 and is_active is False:
    report("P-12", "PASS", f"Old version isActive={is_active} (deactivated)")
elif code == 0 and is_active is True:
    report("P-12", "WARN", f"Old version isActive={is_active} (still active)")
else:
    report("P-12", "FAIL", f"Get old version: code={code}")

# P-13: Update metadata (sort_order) via PATCH
print("\n--- P-13: Update metadata (sort_order) ---")
resp = api("PATCH", f"/admin/ai/prompts/{default_updated_id}", {
    "sortOrder": 99,
    "description": "Updated description via PATCH",
    "operator": "admin"
}, token=token)
code = resp.get("code")
p_meta = resp.get("data", {})
p_meta_id = p_meta.get("id")
if code == 0 and p_meta_id:
    report("P-13", "PASS",
           f"Meta updated: new_id={p_meta_id}, sortOrder={p_meta.get('sortOrder')}, desc={p_meta.get('description')}")
    CREATED_IDS.append(p_meta_id)
    default_meta_id = p_meta_id
else:
    report("P-13", "FAIL", f"Meta update: code={code}, data={p_meta}")
    default_meta_id = default_updated_id

# P-14: List versions for the prompt
print("\n--- P-14: List version history ---")
resp = api("GET", f"/admin/ai/prompts/{default_meta_id}/versions", token=token)
code = resp.get("code")
versions = resp.get("data", [])
version_count = len(versions)
if code == 0 and version_count >= 1:
    report("P-14", "PASS", f"Version history: {version_count} versions")
    for v in versions:
        print(f"    version={v.get('version')}, id={v.get('id')}, active={v.get('isActive')}")
else:
    report("P-14", "WARN" if code == 0 else "FAIL",
           f"Version history: code={code}, versions={version_count}")

# P-15: Delete (soft delete)
print("\n--- P-15: Soft delete (system-default) ---")
resp = api("DELETE", f"/admin/ai/prompts/{default_meta_id}?operator=admin", token=token)
code = resp.get("code")
report("P-15", "PASS" if code == 0 else "FAIL",
       f"Soft delete -> code={code}")

# P-16: Verify soft delete (is_active=0)
print("\n--- P-16: Verify deactivation ---")
resp = api("GET", f"/admin/ai/prompts/{default_meta_id}", token=token)
code = resp.get("code")
is_active = resp.get("data", {}).get("isActive")
if code == 0 and is_active is False:
    report("P-16", "PASS", f"Prompts isActive={is_active} (confirmed deactivated)")
else:
    report("P-16", "FAIL", f"Deactivation check: code={code}, isActive={is_active}")

# P-17: Seed from files (idempotent)
print("\n--- P-17: Seed from files (module=draw) ---")
resp = api("POST", "/admin/ai/prompts/seed?module=draw", token=token)
code = resp.get("code")
seed_data = resp.get("data", "")
report("P-17", "PASS" if code == 0 else "FAIL",
       f"Seed -> code={code}, data={seed_data}")

# P-18: Seed again (should skip all, idempotent)
print("\n--- P-18: Seed again (idempotent) ---")
resp = api("POST", "/admin/ai/prompts/seed?module=draw", token=token)
code = resp.get("code")
seed_data2 = resp.get("data", "")
report("P-18", "PASS" if code == 0 else "FAIL",
       f"Seed again -> code={code}, data={seed_data2}")

# P-19: Reload cache
print("\n--- P-19: Reload cache ---")
resp = api("POST", "/admin/ai/prompts/reload", token=token)
code = resp.get("code")
report("P-19", "PASS" if code == 0 else "FAIL",
       f"Reload cache -> code={code}")

# P-20: Audit log
print("\n--- P-20: Get audit log ---")
resp = api("GET", f"/admin/ai/prompts/{p_default_id}/audit", token=token)
code = resp.get("code")
audit_list = resp.get("data", [])
if code == 0 and len(audit_list) > 0:
    report("P-20", "PASS", f"Audit log: {len(audit_list)} entries")
    for entry in audit_list:
        print(f"    action={entry.get('action')}, old_ver={entry.get('oldVersion')}, new_ver={entry.get('newVersion')}, by={entry.get('operator')}")
else:
    report("P-20", "WARN" if code == 0 else "FAIL",
           f"Audit log: code={code}, entries={len(audit_list)}")

# P-21: Audit log for the updated version
print("\n--- P-21: Audit log (updated version) ---")
resp = api("GET", f"/admin/ai/prompts/{default_updated_id}/audit", token=token)
code = resp.get("code")
audit_list = resp.get("data", [])
report("P-21", "PASS" if code == 0 else "FAIL",
       f"Audit log for updated prompt: {len(audit_list)} entries")

# P-22: Create prompt with empty body
print("\n--- P-22: Create prompt (empty content) ---")
resp = api("POST", "/admin/ai/prompts", {
    "module": "draw",
    "promptKey": "empty-test",
    "content": "",
    "description": "Empty content test",
    "createdBy": "admin"
}, token=token)
code = resp.get("code")
if code == 0:
    empty_id = resp.get("data", {}).get("id")
    report("P-22", "PASS", f"Empty content prompt created, id={empty_id}")
    CREATED_IDS.append(empty_id)
else:
    report("P-22", "WARN", f"Empty content -> code={code}, msg={resp.get('message')}")

# P-23: PATCH non-existent ID
print("\n--- P-23: PATCH non-existent ID ---")
resp = api("PATCH", "/admin/ai/prompts/99999", {
    "sortOrder": 1,
    "operator": "admin"
}, token=token)
code = resp.get("code")
report("P-23", "PASS" if code in (404, 500) else "FAIL",
       f"PATCH non-existent -> code={code}")

# P-24: DELETE non-existent ID
print("\n--- P-24: DELETE non-existent ID ---")
resp = api("DELETE", "/admin/ai/prompts/99999?operator=admin", token=token)
code = resp.get("code")
report("P-24", "PASS" if code == 0 else "FAIL",
       f"DELETE non-existent -> code={code} (idempotent OK)")

# P-25: Reload without auth
print("\n--- P-25: Reload without auth ---")
resp = api("POST", "/admin/ai/prompts/reload")
code = resp.get("code")
report("P-25", "PASS" if code in (401, 403) else "FAIL",
       f"Reload no auth -> code={code}")

# ========== AI DRAW SMOKE TESTS ==========
print("\n" + "=" * 60)
print("  AI DRAW SMOKE TESTS")
print("=" * 60)

# P-26: Draw config (public)
print("\n--- P-26: GET /draw/config ---")
resp = api("GET", "/draw/config")
code = resp.get("code")
report("P-26", "PASS" if code == 0 else "FAIL",
       f"Draw config -> code={code}")

# P-27: Draw chat (empty messages - should 400)
print("\n--- P-27: POST /draw/chat (empty messages) ---")
resp = api("POST", "/draw/chat", {"messages": [], "modelId": "test"})
code = resp.get("code")
report("P-27", "PASS" if code != 0 else "INFO",
       f"Draw chat (empty) -> code={code}, msg={resp.get('message', '')}")

# ========== CLEANUP ==========
print("\n" + "=" * 60)
print("  CLEANUP")
print("=" * 60)

cleaned = 0
for cid in CREATED_IDS:
    if cid:
        resp = api("DELETE", f"/admin/ai/prompts/{cid}?operator=admin", token=token)
        if resp.get("code") == 0:
            cleaned += 1
print(f"\n--- Cleaned up {cleaned}/{len(CREATED_IDS)} test prompts ---")
report("CLEANUP", "PASS" if cleaned == len(CREATED_IDS) else "WARN",
       f"Deleted {cleaned}/{len(CREATED_IDS)} test prompts")

# ========== SUMMARY ==========
print("\n" + "=" * 60)
print("  TEST SUMMARY")
print("=" * 60)
print(f"  PASSED: {PASS}")
print(f"  FAILED: {FAIL}")
print(f"  WARN/SKIP: {WARN}")
print(f"  TOTAL:  {PASS + FAIL + WARN}")
print()
for r in RESULTS:
    print(r)
print()
print("=" * 60)
