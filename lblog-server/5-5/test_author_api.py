#!/usr/bin/env python3
"""Full author API test suite"""
import urllib.request, json, time

BASE = "http://localhost:8099/iblogserver/api/v1"
PASS, FAIL = 0, 0
results = []

def api(method, path, token=None, body=None, headers=None):
    url = f"{BASE}{path}"
    data = json.dumps(body).encode() if body else None
    hs = {"Content-Type": "application/json"} if data else {}
    if token: hs["Authorization"] = f"Bearer {token}"
    if headers: hs.update(headers)
    req = urllib.request.Request(url, data=data, headers=hs, method=method)
    try:
        return json.loads(urllib.request.urlopen(req).read())
    except urllib.error.HTTPError as e:
        return json.loads(e.read())
    except Exception as e:
        return {"code": -1, "message": str(e)}

def report(tid, status, msg):
    global PASS, FAIL
    if status == "PASS": PASS += 1
    else: FAIL += 1
    results.append((tid, status, msg))
    print(f"  [{status}] {tid} - {msg}")

print("=" * 60)
print("  Author API Full Test Suite")
print("=" * 60)

# Login
alice = api("POST", "/auth/login", body={"username":"alice","password":"alice123"})
ekko = api("POST", "/auth/login", body={"username":"ekko","password":"admin123"})
at = alice["data"]["accessToken"]
et = ekko["data"]["accessToken"]
print(f"  Alice (author) token: {at[:20]}...")
print(f"  Ekko (admin) token: {et[:20]}...")

ts = int(time.time())

# ===== POSTS =====
print("\n--- POSTS ---")

r = api("GET", "/author/posts", token=at)
report("LIST-1", "PASS" if r["code"] == 0 and r["data"]["total"] >= 0 else "FAIL",
       f"Alice list posts: code={r['code']}, total={r['data']['total']}")

r = api("GET", f"/author/posts/check-slug?slug=test-{ts}", token=at)
report("SLUG-1", "PASS" if r["code"] == 0 and r["data"]["available"] == True else "FAIL",
       f"Check available slug: available={r['data']['available']}")

r = api("GET", "/author/posts/check-slug?slug=spring-boot-3-features", token=at)
report("SLUG-2", "PASS" if r["code"] == 0 and r["data"]["available"] == False else "FAIL",
       f"Check taken slug: available={r['data']['available']}")

# Create
r = api("POST", "/author/posts", token=at, body={
    "title": f"Full Test {ts}", "slug": f"full-test-{ts}",
    "body": "# Full Test\n\nBody content.", "status": 1, "categoryId": 1, "tagIds": [1, 2]
})
if r["code"] == 0:
    pid = r["data"]["id"]
    report("CREATE-1", "PASS", f"Alice create post: id={pid}")

    r = api("GET", f"/author/posts/{pid}", token=at)
    report("READ-1", "PASS" if r["code"] == 0 and r["data"]["body"] else "FAIL",
           f"Alice read own post: code={r['code']}, has_body={bool(r.get('data',{}).get('body'))}")

    r = api("PUT", f"/author/posts/{pid}", token=at, body={"title": "Updated Title"})
    report("UPDATE-1", "PASS" if r["code"] == 0 else "FAIL", f"Alice update own post: code={r['code']}")

    r = api("DELETE", f"/author/posts/{pid}", token=at)
    report("DELETE-1", "PASS" if r["code"] == 0 else "FAIL", f"Alice delete own post: code={r['code']}")
else:
    report("CREATE-1", "FAIL", f"Create failed")

r = api("GET", "/author/posts/1", token=at)
report("READ-2", "PASS" if r["code"] == 403 else "FAIL", f"Alice read ekko post: code={r['code']}")

r = api("PUT", "/author/posts/1", token=at, body={"title": "Hack"})
report("UPDATE-2", "PASS" if r["code"] == 403 else "FAIL", f"Alice update ekko post: code={r['code']}")

r = api("DELETE", "/author/posts/1", token=at)
report("DELETE-2", "PASS" if r["code"] == 403 else "FAIL", f"Alice delete ekko post: code={r['code']}")

r = api("POST", "/author/posts", token=at, body={"title":"","slug":"","body":"","status":1})
report("CREATE-2", "PASS" if r["code"] == 400 else "FAIL", f"Create missing fields: code={r['code']}")

r = api("POST", "/author/posts", token=at, body={
    "title":"Dup","slug":"spring-boot-3-features","body":"test","status":1
})
report("CREATE-3", "PASS" if r["code"] == 400 else "FAIL", f"Duplicate slug: code={r['code']}")

# ===== CATEGORIES =====
print("\n--- CATEGORIES ---")

r = api("POST", "/author/categories", token=at, body={"name":f"TestCat {ts}","slug":f"tcat-{ts}"})
if r["code"] == 0:
    cid = r["data"]["id"]
    report("CCAT-1", "PASS", f"Alice create category: id={cid}")

    r = api("PUT", f"/author/categories/{cid}", token=at, body={"name":"Updated","slug":f"tcat-{ts}"})
    report("UCAT-1", "PASS" if r["code"] == 0 else "FAIL", f"Alice update own category: code={r['code']}")

    r = api("DELETE", f"/author/categories/{cid}", token=at)
    report("DCAT-1", "PASS" if r["code"] == 0 else "FAIL", f"Alice delete own category: code={r['code']}")
else:
    report("CCAT-1", "FAIL", f"Create failed")

r = api("PUT", "/author/categories/1", token=at, body={"name":"Hack","slug":"hack"})
report("UCAT-2", "PASS" if r["code"] == 403 else "FAIL", f"Alice update ekko category: code={r['code']}")

r = api("DELETE", "/author/categories/1", token=at)
report("DCAT-2", "PASS" if r["code"] == 403 else "FAIL", f"Alice delete ekko category: code={r['code']}")

# ===== TAGS =====
print("\n--- TAGS ---")

r = api("POST", "/author/tags", token=at, body={"name":f"TestTag {ts}","slug":f"ttag-{ts}"})
if r["code"] == 0:
    tid2 = r["data"]["id"]
    report("CTAG-1", "PASS", f"Alice create tag: id={tid2}")

    r = api("PUT", f"/author/tags/{tid2}", token=at, body={"name":"Updated","slug":f"ttag-{ts}"})
    report("UTAG-1", "PASS" if r["code"] == 0 else "FAIL", f"Alice update own tag: code={r['code']}")

    r = api("DELETE", f"/author/tags/{tid2}", token=at)
    report("DTAG-1", "PASS" if r["code"] == 0 else "FAIL", f"Alice delete own tag: code={r['code']}")
else:
    report("CTAG-1", "FAIL", f"Create failed")

r = api("DELETE", "/author/tags/1", token=at)
report("DTAG-2", "PASS" if r["code"] == 403 else "FAIL", f"Alice delete ekko tag: code={r['code']}")

# ===== SERIES =====
print("\n--- SERIES ---")

r = api("POST", "/author/series", token=at, body={
    "title": f"TestSeries {ts}", "slug": f"tser-{ts}", "description": "desc"
})
if r["code"] == 0:
    sid = r["data"]["id"]
    report("CSER-1", "PASS", f"Alice create series: id={sid}")

    r = api("PUT", f"/author/series/{sid}", token=at, body={"title":"Updated"})
    report("USER-1", "PASS" if r["code"] == 0 else "FAIL", f"Alice update own series: code={r['code']}")

    r = api("DELETE", f"/author/series/{sid}", token=at)
    report("DSER-1", "PASS" if r["code"] == 0 else "FAIL", f"Alice delete own series: code={r['code']}")
else:
    report("CSER-1", "FAIL", f"Create failed")

r = api("DELETE", "/author/series/1", token=at)
report("DSER-2", "PASS" if r["code"] == 403 else "FAIL", f"Alice delete ekko series: code={r['code']}")

r = api("PUT", "/author/series/1", token=at, body={"title":"Hack"})
report("USER-2", "PASS" if r["code"] == 403 else "FAIL", f"Alice update ekko series: code={r['code']}")

# ===== STATISTICS =====
print("\n--- STATISTICS ---")

r = api("GET", "/author/statistics", token=at)
report("STAT-1", "PASS" if r["code"] == 0 else "FAIL", f"Alice statistics: code={r['code']}")

r = api("GET", "/author/statistics", token=et)
report("STAT-2", "PASS" if r["code"] == 0 else "FAIL", f"Ekko statistics: code={r['code']}")

# ===== UNAUTHENTICATED =====
print("\n--- UNAUTHENTICATED ---")

r = api("GET", "/author/posts")
report("AUTH-1", "PASS" if r.get("code") in (401, 403) else "FAIL",
       f"No token access: code={r.get('code')}")

r = api("POST", "/author/posts", body={"title":"x","slug":"x","body":"x","status":1})
report("AUTH-2", "PASS" if r.get("code") in (401, 403) else "FAIL",
       f"No token create: code={r.get('code')}")

# ===== SUMMARY =====
print("\n" + "=" * 60)
print(f"  PASS: {PASS}  FAIL: {FAIL}  TOTAL: {PASS + FAIL}")
print("=" * 60)
for t, s, m in results:
    print(f"  [{s}] {t} - {m}")
