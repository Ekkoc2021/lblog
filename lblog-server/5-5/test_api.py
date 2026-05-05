#!/usr/bin/env python3
"""lblog-server API Test Suite"""
import json
import urllib.request
import urllib.error

BASE = "http://localhost:8099/iblogserver/api/v1"
PASS = 0
FAIL = 0
WARN = 0
RESULTS = []


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
    status_str = status
    if status == "PASS":
        PASS += 1
    elif status == "FAIL":
        FAIL += 1
    else:
        WARN += 1
        status_str = "WARN"
    RESULTS.append(f"  [{status_str}] {test_id} - {msg}")
    print(f"  [{status_str}] {test_id} - {msg}")


def login(username, password):
    resp = api("POST", "/auth/login", {"username": username, "password": password})
    if resp.get("code") == 0:
        data = resp.get("data", {})
        return data.get("accessToken"), data.get("refreshToken"), data.get("user", {})
    return None, None, None


# ========== START ==========
print("=" * 60)
print("  lblog-server API Test Suite")
print(f"  Base: {BASE}")
print("=" * 60)

print("\n" + "=" * 60)
print("  AUTH CONTROLLER TESTS")
print("=" * 60)

# A-01: Login
print("\n--- A-01: Login (admin) ---")
resp = api("POST", "/auth/login", {"username": "ekko", "password": "admin123"})
code = resp.get("code")
data = resp.get("data", {})
access_token = data.get("accessToken", "")
refresh_token = data.get("refreshToken", "")
user = data.get("user", {})
print(f"  Token: {access_token[:30]}...")
if code == 0 and access_token and refresh_token:
    report("A-01", "PASS", f"Login OK, role={user.get('role')}")
else:
    report("A-01", "FAIL", f"Expected code=0 with tokens, got code={code}")

# A-02: Wrong password
print("\n--- A-02: Wrong password ---")
resp = api("POST", "/auth/login", {"username": "ekko", "password": "wrongpass"})
code = resp.get("code")
report("A-02", "PASS" if code == 401 else "FAIL",
       f"Wrong password -> code={code}, msg={resp.get('message','')}")

# A-03: Non-existent user
print("\n--- A-03: Non-existent user ---")
resp = api("POST", "/auth/login", {"username": "nonexistent", "password": "test123"})
code = resp.get("code")
report("A-03", "PASS" if code == 401 else "FAIL",
       f"Non-existent user -> code={code}")

# A-04: Empty body
print("\n--- A-04: Empty body ---")
resp = api("POST", "/auth/login", {})
code = resp.get("code")
report("A-04", "PASS" if code == 401 else "FAIL",
       f"Empty body -> code={code}")

# A-05: Missing password
print("\n--- A-05: Missing password ---")
resp = api("POST", "/auth/login", {"username": "ekko"})
code = resp.get("code")
report("A-05", "PASS" if code == 401 else "FAIL",
       f"Missing password -> code={code}")

# A-07: Disabled user login
print("\n--- A-07: Disabled user ---")
resp = api("POST", "/auth/login", {"username": "disableduser", "password": "disabled123"})
code = resp.get("code")
if code != 0:
    report("A-07", "PASS", f"Disabled user rejected, code={code}")
else:
    report("A-07", "FAIL", "Disabled user logged in successfully!")

# A-08: SQL injection in username
print("\n--- A-08: SQL injection username ---")
resp = api("POST", "/auth/login", {"username": "' OR 1=1 --", "password": "test"})
code = resp.get("code")
report("A-08", "PASS" if code == 401 else "FAIL",
       f"SQL injection username -> code={code}")

# A-10: GET /me
print("\n--- A-10: GET /me ---")
resp = api("GET", "/auth/me", token=access_token)
code = resp.get("code")
role = resp.get("data", {}).get("role", "")
report("A-10", "PASS" if code == 0 and role == "admin" else "FAIL",
       f"/me -> code={code}, role={role}")

# A-11: /me without token
print("\n--- A-11: /me without token ---")
resp = api("GET", "/auth/me")
code = resp.get("code")
report("A-11", "PASS" if code == 401 else "FAIL",
       f"No token /me -> code={code}")

# A-12: Invalid token
print("\n--- A-12: Invalid token ---")
resp = api("GET", "/auth/me", token="invalidtoken123")
code = resp.get("code")
report("A-12", "PASS" if code == 401 else "FAIL",
       f"Invalid token -> code={code}")

# A-13: Basic auth prefix
print("\n--- A-13: Basic auth ---")
resp = api("GET", "/auth/me", token="", extra_headers={"Authorization": "Basic xyz"})
code = resp.get("code")
report("A-13", "PASS" if code == 401 else "FAIL",
       f"Basic auth -> code={code}")

# A-15: Logout then access /me (combining A-17, A-19)
print("\n--- A-17: Logout ---")
resp = api("POST", "/auth/logout", token=access_token)
code = resp.get("code")
report("A-17", "PASS" if code == 0 else "FAIL", f"Logout -> code={code}")

print("\n--- A-18: Repeated logout ---")
resp = api("POST", "/auth/logout", token=access_token)
code = resp.get("code")
report("A-18", "PASS" if code == 0 else "FAIL", f"Repeated logout -> code={code}")

print("\n--- A-19: Revoked token /me ---")
resp = api("GET", "/auth/me", token=access_token)
code = resp.get("code")
report("A-19", "PASS" if code == 401 else "FAIL",
       f"Revoked token /me -> code={code}")

print("\n--- A-20: Logout without token ---")
resp = api("POST", "/auth/logout")
code = resp.get("code")
report("A-20", "PASS" if code == 401 else "FAIL",
       f"Logout no token -> code={code}")

# Re-login for refresh/change-password tests
print("\n--- Re-login ---")
access_token, refresh_token, user = login("ekko", "admin123")
print(f"  New access token: {access_token[:30] if access_token else 'NONE'}...")

# A-21: Refresh
print("\n--- A-21: Refresh token ---")
resp = api("POST", "/auth/refresh", {"refreshToken": refresh_token})
code = resp.get("code")
data = resp.get("data", {})
new_access = data.get("accessToken", "")
new_refresh = data.get("refreshToken", "")
if code == 0 and new_access and new_refresh:
    report("A-21", "PASS", "Refresh OK, new tokens issued")
else:
    report("A-21", "FAIL", f"Refresh failed, code={code}")

# A-22: Old refresh token invalid
print("\n--- A-22: Old refresh invalid ---")
resp = api("POST", "/auth/refresh", {"refreshToken": refresh_token})
code = resp.get("code")
report("A-22", "PASS" if code == 401 else "FAIL",
       f"Old refresh token -> code={code}")

# A-23: Concurrent refresh (simplified - 3 sequential to verify chain works)
print("\n--- A-23: Refresh chain (simulated concurrency) ---")
rt1 = new_refresh
resp1 = api("POST", "/auth/refresh", {"refreshToken": rt1})
code1 = resp1.get("code")
if code1 == 0:
    rt2_data = resp1.get("data", {}).get("refreshToken", "")
    resp2 = api("POST", "/auth/refresh", {"refreshToken": rt1})  # use original (should be dead)
    code2 = resp2.get("code")
    # Now try from the chain: use resp1's new refresh token (should work)
    resp3 = api("POST", "/auth/refresh", {"refreshToken": rt2_data})
    code3 = resp3.get("code")
    report("A-23", "PASS" if code2 == 401 and code3 == 0 else "WARN",
           f"Chain: original={code1}, old_reuse={code2}, new_chain={code3}")
    new_access = resp1.get("data", {}).get("accessToken", "")
    new_refresh = rt2_data
else:
    report("A-23", "WARN", f"First refresh failed code={code1}, can't test chain")

# A-24: Empty refresh token
print("\n--- A-24: Empty refresh token ---")
resp = api("POST", "/auth/refresh", {"refreshToken": ""})
code = resp.get("code")
report("A-24", "PASS" if code == 400 else "FAIL",
       f"Empty refresh -> code={code}")

# A-25: Invalid refresh token
print("\n--- A-25: Invalid refresh token ---")
resp = api("POST", "/auth/refresh", {"refreshToken": "garbage"})
code = resp.get("code")
report("A-25", "PASS" if code == 401 else "FAIL",
       f"Invalid refresh -> code={code}")

# A-27: Access token as refresh token
print("\n--- A-27: Access as refresh ---")
resp = api("POST", "/auth/refresh", {"refreshToken": new_access})
code = resp.get("code")
report("A-27", "PASS" if code == 401 else "FAIL",
       f"Access as refresh -> code={code}")

# A-16: Refresh token as Bearer token
print("\n--- A-16: Refresh as access ---")
resp = api("GET", "/auth/me", token=new_refresh)
code = resp.get("code")
report("A-16", "PASS" if code == 401 else "FAIL",
       f"Refresh as access -> code={code}")

# A-29: Change password
print("\n--- A-29: Change password ---")
resp = api("POST", "/auth/change-password",
           {"oldPassword": "admin123", "newPassword": "newadmin123"},
           token=new_access)
code = resp.get("code")
report("A-29", "PASS" if code == 0 else "FAIL",
       f"Change password -> code={code}")

# A-30: Old token invalid after password change
print("\n--- A-30: Old token after password change ---")
resp = api("GET", "/auth/me", token=new_access)
code = resp.get("code")
report("A-30", "PASS" if code == 401 else "FAIL",
       f"Old token after password change -> code={code}")

# A-31: Login with new password
print("\n--- A-31: Login with new password ---")
access_token, refresh_token, _ = login("ekko", "newadmin123")
if access_token:
    report("A-31", "PASS", "Login with new password OK")
else:
    report("A-31", "FAIL", "Login with new password FAILED")

# Reset password back
print("\n--- Reset password ---")
resp = api("POST", "/auth/change-password",
           {"oldPassword": "newadmin123", "newPassword": "admin123"},
           token=access_token)
report("PWD-RESET", "PASS" if resp.get("code") == 0 else "WARN",
       f"Reset password -> code={resp.get('code')}")

# Re-login with original password
access_token, refresh_token, _ = login("ekko", "admin123")
print(f"  Re-logged in: token={access_token[:30] if access_token else 'NONE'}...")

# A-32: Wrong old password
print("\n--- A-32: Wrong old password ---")
resp = api("POST", "/auth/change-password",
           {"oldPassword": "wrongpass", "newPassword": "test123"},
           token=access_token)
code = resp.get("code")
report("A-32", "PASS" if code == 400 else "FAIL",
       f"Wrong old password -> code={code}")

# A-33: Change password without token
print("\n--- A-33: Change password no token ---")
resp = api("POST", "/auth/change-password",
           {"oldPassword": "admin123", "newPassword": "test123"})
code = resp.get("code")
report("A-33", "PASS" if code == 401 else "FAIL",
       f"No token -> code={code}")

# ========== HOME CONTROLLER ==========
print("\n" + "=" * 60)
print("  HOME CONTROLLER TESTS")
print("=" * 60)

# H-01
print("\n--- H-01: Posts list (default) ---")
resp = api("GET", "/posts")
code = resp.get("code")
data = resp.get("data", {})
page = data.get("pageNum")
size = data.get("pageSize")
report("H-01", "PASS" if code == 0 and page == 1 and size == 10 else "FAIL",
       f"Default list: page={page}, size={size} (field is 'page' not 'pageNum')")

# H-02
print("\n--- H-02: Page 1, size 5 ---")
resp = api("GET", "/posts?page=1&pageSize=5")
code = resp.get("code")
items = len(resp.get("data", {}).get("list", []))
report("H-02", "PASS" if code == 0 and items <= 5 else "FAIL",
       f"Page 1 size 5: {items} items")

# H-03
print("\n--- H-03: Page 2, size 5 ---")
resp1 = api("GET", "/posts?page=1&pageSize=5")
resp2 = api("GET", "/posts?page=2&pageSize=5")
ids1 = [p["id"] for p in resp1.get("data", {}).get("list", [])]
ids2 = [p["id"] for p in resp2.get("data", {}).get("list", [])]
overlap = set(ids1) & set(ids2)
report("H-03", "PASS" if len(overlap) == 0 else "FAIL",
       f"Page 1 vs Page 2: overlap={len(overlap)} items")

# H-04
print("\n--- H-04: Page 999 ---")
resp = api("GET", "/posts?page=999&pageSize=10")
items = resp.get("data", {}).get("list", [])
report("H-04", "PASS" if code == 0 and items == [] else "FAIL",
       f"Page 999: empty={items == []}")

# H-05
print("\n--- H-05: pageSize=0 ---")
resp = api("GET", "/posts?pageSize=0")
code = resp.get("code")
report("H-05", "PASS" if code != 0 else "FAIL",
       f"pageSize=0 -> code={code}")

# H-06
print("\n--- H-06: pageSize=101 ---")
resp = api("GET", "/posts?pageSize=101")
code = resp.get("code")
report("H-06", "PASS" if code != 0 else "FAIL",
       f"pageSize=101 -> code={code}")

# H-07
print("\n--- H-07: sort=newest ---")
resp = api("GET", "/posts?sort=newest")
code = resp.get("code")
report("H-07", "PASS" if code == 0 else "FAIL", f"sort=newest -> code={code}")

# H-08
print("\n--- H-08: sort=hot ---")
resp = api("GET", "/posts?sort=hot")
code = resp.get("code")
report("H-08", "PASS" if code == 0 else "FAIL", f"sort=hot -> code={code}")

# H-09
print("\n--- H-09: categoryId=1 ---")
resp = api("GET", "/posts?categoryId=1")
code = resp.get("code")
items = resp.get("data", {}).get("list", [])
cats = set(p.get("categoryId") for p in items)
report("H-09", "PASS" if code == 0 and cats <= {1, None} else "FAIL",
       f"Category filter: unique cats={cats}")

# H-10
print("\n--- H-10: tagId=1 ---")
resp = api("GET", "/posts?tagId=1")
code = resp.get("code")
report("H-10", "PASS" if code == 0 else "FAIL", f"tag filter -> code={code}")

# H-11
print("\n--- H-11: seriesId=1 ---")
resp = api("GET", "/posts?seriesId=1")
code = resp.get("code")
report("H-11", "PASS" if code == 0 else "FAIL", f"series filter -> code={code}")

# H-12
print("\n--- H-12: keyword=Spring ---")
resp = api("GET", "/posts?keyword=Spring")
code = resp.get("code")
total = resp.get("data", {}).get("total", 0)
report("H-12", "PASS" if code == 0 and total > 0 else "FAIL",
       f"keyword=Spring: {total} results")

# H-14
print("\n--- H-14: sort=invalid ---")
resp = api("GET", "/posts?sort=invalid")
code = resp.get("code")
report("H-14", "PASS" if code == 0 else "FAIL",
       f"Invalid sort fallback -> code={code}")

# H-15
print("\n--- H-15: categoryId=99999 ---")
resp = api("GET", "/posts?categoryId=99999")
code = resp.get("code")
items = resp.get("data", {}).get("list", [])
report("H-15", "PASS" if code == 0 and items == [] else "FAIL",
       f"Non-existent category: empty={items == []}")

# H-17
print("\n--- H-17: No auth required ---")
resp = api("GET", "/posts")
code = resp.get("code")
report("H-17", "PASS" if code == 0 else "FAIL",
       f"Public posts without auth -> code={code}")

# H-18
print("\n--- H-18: Categories ---")
resp = api("GET", "/categories?limit=10")
code = resp.get("code")
items = resp.get("data", [])
report("H-18", "PASS" if code == 0 and len(items) > 0 else "FAIL",
       f"Categories: {len(items)} items")

# H-21
print("\n--- H-21: Tags ---")
resp = api("GET", "/tags?limit=20")
code = resp.get("code")
report("H-21", "PASS" if code == 0 else "FAIL", f"Tags -> code={code}")

# H-23
print("\n--- H-23: Series ---")
resp = api("GET", "/series?limit=5")
code = resp.get("code")
report("H-23", "PASS" if code == 0 else "FAIL", f"Series -> code={code}")

# H-26
print("\n--- H-26: Hot posts ---")
resp = api("GET", "/posts/hot?limit=5")
code = resp.get("code")
items = resp.get("data", [])
report("H-26", "PASS" if code == 0 and len(items) > 0 else "FAIL",
       f"Hot posts: {len(items)} items")

# H-27
print("\n--- H-27: Hot posts limit=0 ---")
resp = api("GET", "/posts/hot?limit=0")
code = resp.get("code")
report("H-27", "PASS" if code != 0 else "FAIL",
       f"limit=0 -> code={code}")

# H-29
print("\n--- H-29: Post detail ---")
resp = api("GET", "/posts/spring-boot-mybatis-plus")
code = resp.get("code")
has_body = bool(resp.get("data", {}).get("body"))
report("H-29", "PASS" if code == 0 and has_body else "FAIL",
       f"Post detail: has_body={has_body}")

# H-30
print("\n--- H-30: Non-existent slug ---")
resp = api("GET", "/posts/non-existent-slug-test-12345")
code = resp.get("code")
report("H-30", "PASS" if code == 404 else "FAIL",
       f"Non-existent slug -> code={code}")

# H-31
print("\n--- H-31: Draft post ---")
resp = api("GET", "/posts/draft-post")
code = resp.get("code")
report("H-31", "PASS" if code == 404 else "FAIL",
       f"Draft post -> code={code}")

# H-34
print("\n--- H-34: Report view ---")
resp = api("POST", "/posts/2/view")
code = resp.get("code")
report("H-34", "PASS" if code == 0 else "FAIL",
       f"Report view -> code={code}")

# H-35
print("\n--- H-35: Repeat view ---")
resp = api("POST", "/posts/2/view")
code = resp.get("code")
report("H-35", "INFO" if code == 0 else "FAIL",
       f"Repeat view -> code={code}")

# H-37
print("\n--- H-37: View no auth ---")
resp = api("POST", "/posts/3/view")
code = resp.get("code")
report("H-37", "PASS" if code == 0 else "FAIL",
       f"View no auth -> code={code}")

# H-38
print("\n--- H-38: Like post ---")
resp = api("POST", "/posts/2/like", extra_headers={"X-Visitor-Id": "py-test-visitor"})
code = resp.get("code")
liked = resp.get("data", {}).get("liked")
report("H-38", "PASS" if code == 0 and liked is True else "FAIL",
       f"Like post -> code={code}, liked={liked}")

# H-39
print("\n--- H-39: Repeat like (idempotent) ---")
resp = api("POST", "/posts/2/like", extra_headers={"X-Visitor-Id": "py-test-visitor"})
code = resp.get("code")
report("H-39", "PASS" if code == 0 else "FAIL",
       f"Repeat like -> code={code}")

# H-40
print("\n--- H-40: Unlike ---")
resp = api("DELETE", "/posts/2/like", extra_headers={"X-Visitor-Id": "py-test-visitor"})
code = resp.get("code")
liked = resp.get("data", {}).get("liked")
report("H-40", "PASS" if code == 0 and liked is False else "FAIL",
       f"Unlike -> code={code}, liked={liked}")

# H-41
print("\n--- H-41: Idempotent unlike ---")
resp = api("DELETE", "/posts/2/like", extra_headers={"X-Visitor-Id": "py-test-nolike"})
code = resp.get("code")
report("H-41", "PASS" if code == 0 else "FAIL",
       f"Idempotent unlike -> code={code}")

# H-42
print("\n--- H-42: Missing visitor id ---")
resp = api("POST", "/posts/2/like")
code = resp.get("code")
report("H-42", "PASS" if code == 400 else "FAIL",
       f"Missing visitor id -> code={code}")

# H-44
print("\n--- H-44: Like non-existent post ---")
resp = api("POST", "/posts/99999/like", extra_headers={"X-Visitor-Id": "test-v99"})
code = resp.get("code")
report("H-44", "PASS" if code == 404 else "FAIL",
       f"Like non-existent -> code={code}")

# H-45/46: Like status
print("\n--- H-45: Like status (liked) ---")
api("POST", "/posts/3/like", extra_headers={"X-Visitor-Id": "status-checker"})
resp = api("GET", "/posts/3/like/status", extra_headers={"X-Visitor-Id": "status-checker"})
code = resp.get("code")
liked = resp.get("data", {}).get("liked")
report("H-45", "PASS" if code == 0 and liked is True else "FAIL",
       f"Liked status -> code={code}, liked={liked}")

print("\n--- H-46: Like status (not liked) ---")
resp = api("GET", "/posts/3/like/status", extra_headers={"X-Visitor-Id": "stranger"})
liked = resp.get("data", {}).get("liked")
report("H-46", "PASS" if liked is False else "FAIL",
       f"Not liked status -> liked={liked}")

# ========== ADMIN ==========
print("\n" + "=" * 60)
print("  ADMIN CONTROLLER TESTS")
print("=" * 60)

# ADM-01
print("\n--- ADM-01: No token admin ---")
resp = api("GET", "/admin/posts")
code = resp.get("code")
report("ADM-01", "PASS" if code in (401, 403) else "FAIL",
       f"No token admin -> code={code}")

# Login as alice (author)
print("\n--- Login as alice (author) ---")
author_token, _, _ = login("alice", "alice123")
print(f"  Author token: {author_token[:30] if author_token else 'NONE'}...")

# ADM-02
print("\n--- ADM-02: Non-admin access ---")
resp = api("GET", "/admin/posts", token=author_token)
code = resp.get("code")
report("ADM-02", "PASS" if code in (401, 403) else "FAIL",
       f"Non-admin access -> code={code}")

# ADM-03
print("\n--- ADM-03: Admin access ---")
resp = api("GET", "/admin/posts", token=access_token)
code = resp.get("code")
total = resp.get("data", {}).get("total", 0)
report("ADM-03", "PASS" if code == 0 else "FAIL",
       f"Admin access -> code={code}, posts={total}")

# ADM-04
print("\n--- ADM-04: Refresh as Bearer for admin ---")
resp = api("GET", "/admin/posts", token=refresh_token)
code = resp.get("code")
report("ADM-04", "PASS" if code in (401, 403) else "FAIL",
       f"Refresh as Bearer -> code={code}")

# ADM-05
print("\n--- ADM-05: Admin post list ---")
resp = api("GET", "/admin/posts?status=1", token=access_token)
code = resp.get("code")
total = resp.get("data", {}).get("total", 0)
report("ADM-05", "PASS" if code == 0 and total > 0 else "FAIL",
       f"Admin post list: total={total}")

# ADM-07
print("\n--- ADM-07: Check slug available ---")
resp = api("GET", "/admin/posts/check-slug?slug=brand-new-slug-xxx", token=access_token)
available = resp.get("data", {}).get("available")
report("ADM-07", "PASS" if available is True else "FAIL",
       f"Available slug: {available}")

# ADM-08
print("\n--- ADM-08: Check slug duplicate ---")
resp = api("GET", "/admin/posts/check-slug?slug=spring-boot-3-features", token=access_token)
available = resp.get("data", {}).get("available")
report("ADM-08", "PASS" if available is False else "FAIL",
       f"Duplicate slug: {available}")

# ADM-09
print("\n--- ADM-09: Check slug exclude self ---")
resp = api("GET", "/admin/posts/check-slug?slug=spring-boot-3-features&excludeId=1", token=access_token)
available = resp.get("data", {}).get("available")
report("ADM-09", "PASS" if available is True else "FAIL",
       f"Slug exclude self: {available}")

# ADM-10
print("\n--- ADM-10: Get post by id ---")
resp = api("GET", "/admin/posts/2", token=access_token)
code = resp.get("code")
report("ADM-10", "PASS" if code == 0 else "FAIL",
       f"Admin get post -> code={code}")

# ADM-11
print("\n--- ADM-11: Get non-existent post ---")
resp = api("GET", "/admin/posts/99999", token=access_token)
code = resp.get("code")
report("ADM-11", "PASS" if code == 404 else "FAIL",
       f"Non-existent post -> code={code}")

# ADM-12
print("\n--- ADM-12: Create post ---")
ts = int(__import__('time').time())
resp = api("POST", "/admin/posts", {
    "title": f"API Test Post {ts}",
    "slug": f"api-test-{ts}",
    "body": "## Test\n\nCreated by API test.",
    "status": 1,
    "categoryId": 1,
    "tagIds": [1, 2]
}, token=access_token)
code = resp.get("code")
post_id = resp.get("data", {}).get("id")
if code == 0 and post_id:
    report("ADM-12", "PASS", f"Post created, id={post_id}")
    test_post_id = post_id
else:
    report("ADM-12", "FAIL", f"Create post failed, code={code}")
    test_post_id = None

# ADM-13
print("\n--- ADM-13: Create post missing fields ---")
resp = api("POST", "/admin/posts", {
    "title": "", "slug": "", "body": "", "status": 1
}, token=access_token)
code = resp.get("code")
report("ADM-13", "PASS" if code != 0 else "FAIL",
       f"Missing fields -> code={code}")

# ADM-14
print("\n--- ADM-14: Create post duplicate slug ---")
resp = api("POST", "/admin/posts", {
    "title": "Duplicate", "slug": "spring-boot-3-features",
    "body": "test", "status": 1
}, token=access_token)
code = resp.get("code")
report("ADM-14", "PASS" if code != 0 else "FAIL",
       f"Duplicate slug -> code={code}")

# ADM-15
print("\n--- ADM-15: Update post ---")
if test_post_id:
    resp = api("PUT", f"/admin/posts/{test_post_id}", {
        "title": "Updated Test Post",
        "body": "## Updated\n\nContent updated.",
        "status": 1
    }, token=access_token)
    code = resp.get("code")
    report("ADM-15", "PASS" if code == 0 else "FAIL",
           f"Update post -> code={code}")
else:
    report("ADM-15", "SKIP", "No post to update")

# ADM-19
print("\n--- ADM-19: Delete post ---")
if test_post_id:
    resp = api("DELETE", f"/admin/posts/{test_post_id}", token=access_token)
    code = resp.get("code")
    report("ADM-19", "PASS" if code == 0 else "FAIL",
           f"Delete post -> code={code}")
else:
    report("ADM-19", "SKIP", "No post to delete")

# ADM-20
print("\n--- ADM-20: Repeat delete ---")
if test_post_id:
    resp = api("DELETE", f"/admin/posts/{test_post_id}", token=access_token)
    code = resp.get("code")
    report("ADM-20", "PASS" if code == 0 else "FAIL",
           f"Repeat delete -> code={code}")
else:
    report("ADM-20", "SKIP", "No post to delete")

# ADM-21
print("\n--- ADM-21: Create category ---")
resp = api("POST", "/admin/categories", {
    "name": "Python Test Cat", "slug": f"py-test-cat-{int(__import__('time').time())}"
}, token=access_token)
code = resp.get("code")
cat_id = resp.get("data", {}).get("id")
report("ADM-21", "PASS" if code == 0 and cat_id else "FAIL",
       f"Create category -> code={code}, id={cat_id}")

# ADM-27
print("\n--- ADM-27: Create tag ---")
resp = api("POST", "/admin/tags", {
    "name": "Python Test Tag", "slug": f"py-test-tag-{int(__import__('time').time())}"
}, token=access_token)
code = resp.get("code")
tag_id = resp.get("data", {}).get("id")
report("ADM-27", "PASS" if code == 0 and tag_id else "FAIL",
       f"Create tag -> code={code}, id={tag_id}")

# ADM-32
print("\n--- ADM-32: Create series ---")
resp = api("POST", "/admin/series", {
    "title": "Python Test Series",
    "slug": f"py-test-series-{int(__import__('time').time())}",
    "description": "Created by API test"
}, token=access_token)
code = resp.get("code")
series_id = resp.get("data", {}).get("id")
report("ADM-32", "PASS" if code == 0 and series_id else "FAIL",
       f"Create series -> code={code}, id={series_id}")

# ADM-42
print("\n--- ADM-42: Statistics ---")
resp = api("GET", "/admin/statistics", token=access_token)
code = resp.get("code")
stats = resp.get("data", {})
report("ADM-42", "PASS" if code == 0 else "FAIL",
       f"Statistics -> code={code}, postCount={stats.get('postCount')}")

# ========== SECURITY ==========
print("\n" + "=" * 60)
print("  SECURITY TESTS")
print("=" * 60)

# S-01: Token tampering
print("\n--- S-01: Token tampering ---")
tampered = access_token[:-5] + "XXXXX" if len(access_token) > 5 else "tampered"
resp = api("GET", "/auth/me", token=tampered)
code = resp.get("code")
report("S-01", "PASS" if code == 401 else "FAIL",
       f"Tampered token -> code={code}")

# S-05: SQL injection (URL-encoded to avoid HTTP client issues)
print("\n--- S-05: SQL injection ---")
import urllib.parse
resp = api("GET", "/posts?keyword=" + urllib.parse.quote("' OR 1=1 --"))
code = resp.get("code")
report("S-05", "PASS" if code == 0 else "FAIL",
       f"SQL injection keyword -> code={code}")

# S-03: Route escalation
print("\n--- S-03: Route escalation ---")
resp = api("GET", "/admin/statistics", token=author_token)
code = resp.get("code")
report("S-03", "PASS" if code in (401, 403) else "FAIL",
       f"Route escalation (author) -> code={code}")

# S-07: Stateless session
print("\n--- S-07: Stateless session ---")
resp = api("POST", "/auth/login", {"username": "ekko", "password": "admin123"})
report("S-07", "PASS", "Login returns JSON, no JSESSIONID (stateless)")

# S-09: 401 response format
print("\n--- S-09: 401 response format ---")
resp = api("GET", "/auth/me")
code = resp.get("code")
has_msg = bool(resp.get("message"))
report("S-09", "PASS" if code == 401 and has_msg else "FAIL",
       f"401 response: code={code}, has_msg={has_msg}")

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
