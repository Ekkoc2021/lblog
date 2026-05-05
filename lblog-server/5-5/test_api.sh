#!/bin/bash
# API Test Script for lblog-server
BASE="http://localhost:8099/iblogserver/api/v1"
PASS=0
FAIL=0
RESULTS=()

# Helper functions
extract() {
    python3 -c "import sys,json; print(json.load(sys.stdin)['$1'], end='')"
}

report() {
    local id=$1 status=$2 msg=$3
    if [ "$status" = "PASS" ]; then
        ((PASS++))
    else
        ((FAIL++))
    fi
    RESULTS+=("$id|$status|$msg")
    echo "[$status] $id - $msg"
}

echo "=============================================="
echo "  lblog-server API Test Suite"
echo "  Base: $BASE"
echo "=============================================="

# ========== AUTH ==========
echo ""
echo "========== AUTH CONTROLLER TESTS =========="

# A-01: Login with admin user
echo ""
echo "--- A-01: Login (admin) ---"
LOGIN_RESP=$(curl -s -X POST "$BASE/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"ekko","password":"admin123"}')
CODE=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
HAS_TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin)['data']; print('accessToken' in d and 'refreshToken' in d)")
ACCESS_TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")
REFRESH_TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['refreshToken'])")
echo "  AccessToken: ${ACCESS_TOKEN:0:30}..."
echo "  RefreshToken: ${REFRESH_TOKEN:0:30}..."
if [ "$CODE" = "0" ] && [ "$HAS_TOKEN" = "True" ]; then
    report "A-01" "PASS" "Login successful, tokens obtained"
else
    report "A-01" "FAIL" "Expected code=0 with tokens, got code=$CODE"
fi

# A-02: Wrong password
echo ""
echo "--- A-02: Wrong password ---"
RESP=$(curl -s -X POST "$BASE/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"ekko","password":"wrongpass"}')
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
MSG=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('message',''))")
if [ "$CODE" = "401" ]; then
    report "A-02" "PASS" "Got 401 for wrong password (msg: $MSG)"
else
    report "A-02" "FAIL" "Expected 401, got code=$CODE"
fi

# A-03: Non-existent user
echo ""
echo "--- A-03: Non-existent user ---"
RESP=$(curl -s -X POST "$BASE/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"nonexistent","password":"test123"}')
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "401" ]; then
    report "A-03" "PASS" "Got 401 for nonexistent user"
else
    report "A-03" "FAIL" "Expected 401, got code=$CODE"
fi

# A-04: Empty body
echo ""
echo "--- A-04: Empty body ---"
RESP=$(curl -s -X POST "$BASE/auth/login" \
    -H "Content-Type: application/json" \
    -d '{}')
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "401" ]; then
    report "A-04" "PASS" "Got 401 for empty body"
else
    report "A-04" "FAIL" "Expected 401, got code=$CODE"
fi

# A-05: Missing password field
echo ""
echo "--- A-05: Missing password ---"
RESP=$(curl -s -X POST "$BASE/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"ekko"}')
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "401" ]; then
    report "A-05" "PASS" "Got 401 for missing password"
else
    report "A-05" "FAIL" "Expected 401, got code=$CODE"
fi

# A-07: Disabled user
echo ""
echo "--- A-07: Disabled user ---"
RESP=$(curl -s -X POST "$BASE/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"disableduser","password":"disabled123"}')
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
echo "  Disabled user login response code: $CODE"
if [ "$CODE" != "0" ]; then
    report "A-07" "PASS" "Disabled user login rejected (code=$CODE)"
else
    report "A-07" "FAIL" "Disabled user logged in successfully but should be rejected"
fi

# A-10: GET /me
echo ""
echo "--- A-10: GET /me ---"
RESP=$(curl -s -X GET "$BASE/auth/me" \
    -H "Authorization: Bearer $ACCESS_TOKEN")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
ROLE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['role'])")
if [ "$CODE" = "0" ] && [ "$ROLE" = "admin" ]; then
    report "A-10" "PASS" "Got user info, role=admin"
else
    report "A-10" "FAIL" "Expected code=0 with admin role, got code=$CODE role=$ROLE"
fi

# A-11: GET /me without token
echo ""
echo "--- A-11: /me without token ---"
RESP=$(curl -s -X GET "$BASE/auth/me")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "401" ]; then
    report "A-11" "PASS" "Got 401 for no token"
else
    report "A-11" "FAIL" "Expected 401, got code=$CODE"
fi

# A-12: Invalid token
echo ""
echo "--- A-12: Invalid token ---"
RESP=$(curl -s -X GET "$BASE/auth/me" \
    -H "Authorization: Bearer invalidtoken123")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "401" ]; then
    report "A-12" "PASS" "Got 401 for invalid token"
else
    report "A-12" "FAIL" "Expected 401, got code=$CODE"
fi

# A-13: Wrong auth prefix (Basic)
echo ""
echo "--- A-13: Basic auth prefix ---"
RESP=$(curl -s -X GET "$BASE/auth/me" \
    -H "Authorization: Basic xyz")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "401" ]; then
    report "A-13" "PASS" "Got 401 for Basic auth"
else
    report "A-13" "FAIL" "Expected 401, got code=$CODE"
fi

# A-17: Logout
echo ""
echo "--- A-17: Logout ---"
RESP=$(curl -s -X POST "$BASE/auth/logout" \
    -H "Authorization: Bearer $ACCESS_TOKEN")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "0" ]; then
    report "A-17" "PASS" "Logout successful"
else
    report "A-17" "FAIL" "Logout failed, code=$CODE"
fi

# A-18: Repeated logout
echo ""
echo "--- A-18: Repeated logout ---"
RESP=$(curl -s -X POST "$BASE/auth/logout" \
    -H "Authorization: Bearer $ACCESS_TOKEN")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "0" ]; then
    report "A-18" "PASS" "Repeated logout is idempotent (code=0)"
else
    report "A-18" "FAIL" "Expected 0, got code=$CODE"
fi

# A-19: Use revoked token to access /me
echo ""
echo "--- A-19: Revoked token /me ---"
RESP=$(curl -s -X GET "$BASE/auth/me" \
    -H "Authorization: Bearer $ACCESS_TOKEN")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "401" ]; then
    report "A-19" "PASS" "Revoked token rejected"
else
    report "A-19" "FAIL" "Expected 401, got code=$CODE"
fi

# A-20: Logout without token
echo ""
echo "--- A-20: Logout without token ---"
RESP=$(curl -s -X POST "$BASE/auth/logout")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "401" ]; then
    report "A-20" "PASS" "Logout without token returns 401"
else
    report "A-20" "FAIL" "Expected 401, got code=$CODE"
fi

# Login again for more tests
echo ""
echo "--- Re-login for A-21 onwards ---"
LOGIN_RESP=$(curl -s -X POST "$BASE/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"ekko","password":"admin123"}')
ACCESS_TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")
REFRESH_TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['refreshToken'])")
echo "  New tokens obtained"

# A-21: Refresh token
echo ""
echo "--- A-21: Refresh token ---"
REFRESH_RESP=$(curl -s -X POST "$BASE/auth/refresh" \
    -H "Content-Type: application/json" \
    -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}")
CODE=$(echo "$REFRESH_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
NEW_ACCESS=$(echo "$REFRESH_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")
NEW_REFRESH=$(echo "$REFRESH_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['refreshToken'])")
if [ "$CODE" = "0" ] && [ -n "$NEW_ACCESS" ] && [ -n "$NEW_REFRESH" ]; then
    report "A-21" "PASS" "Refresh successful, new tokens issued"
else
    report "A-21" "FAIL" "Refresh failed, code=$CODE"
fi

# A-22: Old refresh token invalid after refresh
echo ""
echo "--- A-22: Old refresh token invalid ---"
RESP=$(curl -s -X POST "$BASE/auth/refresh" \
    -H "Content-Type: application/json" \
    -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "401" ]; then
    report "A-22" "PASS" "Old refresh token rejected after rotation"
else
    report "A-22" "FAIL" "Expected 401 for old refresh token, got $CODE"
fi

# A-24: Empty refresh token
echo ""
echo "--- A-24: Empty refresh token ---"
RESP=$(curl -s -X POST "$BASE/auth/refresh" \
    -H "Content-Type: application/json" \
    -d '{"refreshToken":""}')
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "400" ]; then
    report "A-24" "PASS" "Empty refresh token rejected with 400"
else
    report "A-24" "FAIL" "Expected 400, got code=$CODE"
fi

# A-25: Invalid refresh token
echo ""
echo "--- A-25: Invalid refresh token ---"
RESP=$(curl -s -X POST "$BASE/auth/refresh" \
    -H "Content-Type: application/json" \
    -d '{"refreshToken":"garbage"}')
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "401" ]; then
    report "A-25" "PASS" "Invalid refresh token rejected with 401"
else
    report "A-25" "FAIL" "Expected 401, got code=$CODE"
fi

# A-27: Use access token as refresh token
echo ""
echo "--- A-27: Access token as refresh token ---"
RESP=$(curl -s -X POST "$BASE/auth/refresh" \
    -H "Content-Type: application/json" \
    -d "{\"refreshToken\":\"$NEW_ACCESS\"}")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "401" ]; then
    report "A-27" "PASS" "Access token rejected as refresh token"
else
    report "A-27" "FAIL" "Expected 401, got code=$CODE"
fi

# Use new tokens going forward
ACCESS_TOKEN=$NEW_ACCESS
REFRESH_TOKEN=$NEW_REFRESH

# A-16: Use refresh token as access token
echo ""
echo "--- A-16: Refresh token as Bearer token ---"
RESP=$(curl -s -X GET "$BASE/auth/me" \
    -H "Authorization: Bearer $REFRESH_TOKEN")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "401" ]; then
    report "A-16" "PASS" "Refresh token rejected when used as access token"
else
    report "A-16" "FAIL" "Expected 401, got code=$CODE"
fi

# A-29: Change password
echo ""
echo "--- A-29: Change password ---"
RESP=$(curl -s -X POST "$BASE/auth/change-password" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"oldPassword":"admin123","newPassword":"newadmin123"}')
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "0" ]; then
    report "A-29" "PASS" "Password changed successfully"
else
    report "A-29" "FAIL" "Expected 0, got code=$CODE"
fi

# A-30: Old token invalid after password change
echo ""
echo "--- A-30: Old token after password change ---"
RESP=$(curl -s -X GET "$BASE/auth/me" \
    -H "Authorization: Bearer $ACCESS_TOKEN")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "401" ]; then
    report "A-30" "PASS" "Old token revoked after password change"
else
    report "A-30" "FAIL" "Expected 401, got code=$CODE"
fi

# A-31: Login with new password
echo ""
echo "--- A-31: Login with new password ---"
LOGIN_RESP=$(curl -s -X POST "$BASE/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"ekko","password":"newadmin123"}')
CODE=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "0" ]; then
    report "A-31" "PASS" "Login with new password successful"
else
    report "A-31" "FAIL" "Expected 0, got code=$CODE"
fi

# Change password back to original
ACCESS_TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")
echo ""
echo "--- Change password back to original ---"
RESP=$(curl -s -X POST "$BASE/auth/change-password" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"oldPassword":"newadmin123","newPassword":"admin123"}')
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
echo "  Reset password: code=$CODE"

# A-32: Wrong old password
echo ""
echo "--- A-32: Wrong old password ---"
LOGIN_RESP=$(curl -s -X POST "$BASE/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"ekko","password":"admin123"}')
ACCESS_TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")
RESP=$(curl -s -X POST "$BASE/auth/change-password" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"oldPassword":"wrongpass","newPassword":"newadmin123"}')
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "400" ]; then
    report "A-32" "PASS" "Wrong old password rejected with 400"
else
    report "A-32" "FAIL" "Expected 400, got code=$CODE"
fi

# A-33: Change password without login
echo ""
echo "--- A-33: Change password without token ---"
RESP=$(curl -s -X POST "$BASE/auth/change-password" \
    -H "Content-Type: application/json" \
    -d '{"oldPassword":"admin123","newPassword":"test123"}')
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "401" ]; then
    report "A-33" "PASS" "No token change password returns 401"
else
    report "A-33" "FAIL" "Expected 401, got code=$CODE"
fi

# ========== HOME CONTROLLER (Public) ==========
echo ""
echo "========== HOME CONTROLLER TESTS =========="

# H-01: Posts list (default)
echo ""
echo "--- H-01: Posts list (default) ---"
RESP=$(curl -s "$BASE/posts")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
PAGE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['pageNum'])")
SIZE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['pageSize'])")
if [ "$CODE" = "0" ] && [ "$PAGE" = "1" ] && [ "$SIZE" = "10" ]; then
    report "H-01" "PASS" "Default list: page=$PAGE, size=$SIZE"
else
    report "H-01" "FAIL" "Expected page=1, size=10, got page=$PAGE size=$SIZE"
fi

# H-02: Page 1, size 5
echo ""
echo "--- H-02: Page 1, size 5 ---"
RESP=$(curl -s "$BASE/posts?page=1&pageSize=5")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
SIZE=$(echo "$RESP" | python3 -c "import sys,json; print(len(json.load(sys.stdin)['data']['list']))")
if [ "$CODE" = "0" ] && [ "$SIZE" -le 5 ]; then
    report "H-02" "PASS" "Page 1 size 5 returned $SIZE items"
else
    report "H-02" "FAIL" "Expected <=5 items, got $SIZE"
fi

# H-04: Out of range page
echo ""
echo "--- H-04: Page 999 ---"
RESP=$(curl -s "$BASE/posts?page=999&pageSize=10")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
LIST=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['list'])")
if [ "$CODE" = "0" ] && [ "$LIST" = "[]" ]; then
    report "H-04" "PASS" "Page 999 returns empty list"
else
    report "H-04" "FAIL" "Expected empty list for page 999"
fi

# H-05: pageSize=0 (validation)
echo ""
echo "--- H-05: pageSize=0 ---"
RESP=$(curl -s "$BASE/posts?pageSize=0")
CODE=$(echo "$RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('code','no_code'))")
if [ "$CODE" != "0" ]; then
    report "H-05" "PASS" "pageSize=0 rejected (code=$CODE)"
else
    report "H-05" "FAIL" "Expected validation error for pageSize=0"
fi

# H-06: pageSize=101 (validation)
echo ""
echo "--- H-06: pageSize=101 ---"
RESP=$(curl -s "$BASE/posts?pageSize=101")
CODE=$(echo "$RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('code','no_code'))")
if [ "$CODE" != "0" ]; then
    report "H-06" "PASS" "pageSize=101 rejected (code=$CODE)"
else
    report "H-06" "FAIL" "Expected validation error for pageSize=101"
fi

# H-07: Sort=newest
echo ""
echo "--- H-07: Sort=newest ---"
RESP=$(curl -s "$BASE/posts?sort=newest")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "0" ]; then
    report "H-07" "PASS" "Sort=newest works"
else
    report "H-07" "FAIL" "Expected 0, got $CODE"
fi

# H-08: Sort=hot
echo ""
echo "--- H-08: Sort=hot ---"
RESP=$(curl -s "$BASE/posts?sort=hot")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "0" ]; then
    report "H-08" "PASS" "Sort=hot works"
else
    report "H-08" "FAIL" "Expected 0, got $CODE"
fi

# H-09: Filter by category
echo ""
echo "--- H-09: Filter by category ---"
RESP=$(curl -s "$BASE/posts?categoryId=1")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "0" ]; then
    report "H-09" "PASS" "Category filter works"
else
    report "H-09" "FAIL" "Expected 0, got $CODE"
fi

# H-10: Filter by tag
echo ""
echo "--- H-10: Filter by tag ---"
RESP=$(curl -s "$BASE/posts?tagId=1")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "0" ]; then
    report "H-10" "PASS" "Tag filter works"
else
    report "H-10" "FAIL" "Expected 0, got $CODE"
fi

# H-11: Filter by series
echo ""
echo "--- H-11: Filter by series ---"
RESP=$(curl -s "$BASE/posts?seriesId=1")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "0" ]; then
    report "H-11" "PASS" "Series filter works"
else
    report "H-11" "FAIL" "Expected 0, got $CODE"
fi

# H-12: Keyword search
echo ""
echo "--- H-12: Keyword search ---"
RESP=$(curl -s "$BASE/posts?keyword=Spring")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
TOTAL=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['total'])")
if [ "$CODE" = "0" ] && [ "$TOTAL" -gt 0 ]; then
    report "H-12" "PASS" "Keyword search found $TOTAL results"
else
    report "H-12" "FAIL" "Expected results for 'Spring', got total=$TOTAL"
fi

# H-14: Invalid sort
echo ""
echo "--- H-14: Invalid sort ---"
RESP=$(curl -s "$BASE/posts?sort=invalid")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
# Should fall back to default sort (recommend) without error
if [ "$CODE" = "0" ]; then
    report "H-14" "PASS" "Invalid sort falls back to default"
else
    report "H-14" "FAIL" "Expected fallback to default sort, got code=$CODE"
fi

# H-15: Non-existent category
echo ""
echo "--- H-15: Category 99999 ---"
RESP=$(curl -s "$BASE/posts?categoryId=99999")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
LIST=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['list'])")
if [ "$CODE" = "0" ] && [ "$LIST" = "[]" ]; then
    report "H-15" "PASS" "Non-existent category returns empty list"
else
    report "H-15" "FAIL" "Expected empty list"
fi

# H-17: No auth required for posts
echo ""
echo "--- H-17: No auth for public endpoints ---"
RESP=$(curl -s "$BASE/posts")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "0" ]; then
    report "H-17" "PASS" "Posts list works without auth"
else
    report "H-17" "FAIL" "Expected 0 without auth, got $CODE"
fi

# H-18: Categories
echo ""
echo "--- H-18: Categories ---"
RESP=$(curl -s "$BASE/categories?limit=10")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
COUNT=$(echo "$RESP" | python3 -c "import sys,json; print(len(json.load(sys.stdin)['data']))")
if [ "$CODE" = "0" ] && [ "$COUNT" -gt 0 ]; then
    report "H-18" "PASS" "Categories returned $COUNT items"
else
    report "H-18" "FAIL" "Expected categories, got count=$COUNT"
fi

# H-21: Tags
echo ""
echo "--- H-21: Tags ---"
RESP=$(curl -s "$BASE/tags?limit=20")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "0" ]; then
    report "H-21" "PASS" "Tags list works"
else
    report "H-21" "FAIL" "Expected 0, got $CODE"
fi

# H-23: Series
echo ""
echo "--- H-23: Series ---"
RESP=$(curl -s "$BASE/series?limit=5")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "0" ]; then
    report "H-23" "PASS" "Series list works"
else
    report "H-23" "FAIL" "Expected 0, got $CODE"
fi

# H-26: Hot posts
echo ""
echo "--- H-26: Hot posts ---"
RESP=$(curl -s "$BASE/posts/hot?limit=5")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "0" ]; then
    report "H-26" "PASS" "Hot posts works"
else
    report "H-26" "FAIL" "Expected 0, got $CODE"
fi

# H-27: limit=0 validation
echo ""
echo "--- H-27: Hot posts limit=0 ---"
RESP=$(curl -s "$BASE/posts/hot?limit=0")
CODE=$(echo "$RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('code','no_code'))")
if [ "$CODE" != "0" ]; then
    report "H-27" "PASS" "limit=0 rejected ($CODE)"
else
    report "H-27" "FAIL" "Expected validation error for limit=0"
fi

# H-29: Post detail by slug
echo ""
echo "--- H-29: Post detail ---"
SLUG="spring-boot-mybatis-plus"
RESP=$(curl -s "$BASE/posts/$SLUG")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
HAS_BODY=$(echo "$RESP" | python3 -c "import sys,json; d=json.load(sys.stdin)['data']; print('body' in d and d['body'] is not None)")
if [ "$CODE" = "0" ] && [ "$HAS_BODY" = "True" ]; then
    report "H-29" "PASS" "Post detail returns body content"
else
    report "H-29" "FAIL" "Expected post detail with body"
fi

# H-30: Non-existent slug
echo ""
echo "--- H-30: Non-existent slug ---"
RESP=$(curl -s "$BASE/posts/non-existent-slug-12345")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "404" ]; then
    report "H-30" "PASS" "Non-existent slug returns 404"
else
    report "H-30" "FAIL" "Expected 404, got $CODE"
fi

# H-31: Draft post not accessible
echo ""
echo "--- H-31: Draft post (status=0) ---"
RESP=$(curl -s "$BASE/posts/draft-post")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "404" ]; then
    report "H-31" "PASS" "Draft post returns 404 on public API"
else
    report "H-31" "FAIL" "Expected 404 for draft, got $CODE"
fi

# H-34: Report view
echo ""
echo "--- H-34: Report view ---"
RESP=$(curl -s -X POST "$BASE/posts/2/view")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "0" ]; then
    report "H-34" "PASS" "View report works"
else
    report "H-34" "FAIL" "Expected 0, got $CODE"
fi

# H-38: Like post
echo ""
echo "--- H-38: Like post ---"
RESP=$(curl -s -X POST "$BASE/posts/2/like" -H "X-Visitor-Id: test-visitor-1")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
LIKED=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['liked'])")
if [ "$CODE" = "0" ] && [ "$LIKED" = "True" ]; then
    report "H-38" "PASS" "Like successful"
else
    report "H-38" "FAIL" "Expected liked=true"
fi

# H-39: Duplicate like (idempotent)
echo ""
echo "--- H-39: Duplicate like ---"
RESP=$(curl -s -X POST "$BASE/posts/2/like" -H "X-Visitor-Id: test-visitor-1")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "0" ]; then
    report "H-39" "PASS" "Duplicate like is idempotent"
else
    report "H-39" "FAIL" "Expected 0, got $CODE"
fi

# H-40: Unlike post
echo ""
echo "--- H-40: Unlike post ---"
RESP=$(curl -s -X DELETE "$BASE/posts/2/like" -H "X-Visitor-Id: test-visitor-1")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
LIKED=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['liked'])")
if [ "$CODE" = "0" ] && [ "$LIKED" = "False" ]; then
    report "H-40" "PASS" "Unlike successful"
else
    report "H-40" "FAIL" "Expected liked=false"
fi

# H-41: Unlike without prior like (idempotent)
echo ""
echo "--- H-41: Idempotent unlike ---"
RESP=$(curl -s -X DELETE "$BASE/posts/2/like" -H "X-Visitor-Id: test-visitor-no-like")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "0" ]; then
    report "H-41" "PASS" "Idempotent unlike works"
else
    report "H-41" "FAIL" "Expected 0, got $CODE"
fi

# H-42: Missing X-Visitor-Id
echo ""
echo "--- H-42: Missing X-Visitor-Id ---"
RESP=$(curl -s -X POST "$BASE/posts/2/like")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "400" ]; then
    report "H-42" "PASS" "Missing visitor id returns 400"
else
    report "H-42" "FAIL" "Expected 400, got $CODE"
fi

# H-44: Like non-existent post
echo ""
echo "--- H-44: Like non-existent post ---"
RESP=$(curl -s -X POST "$BASE/posts/99999/like" -H "X-Visitor-Id: test-visitor-2")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "404" ]; then
    report "H-44" "PASS" "Like non-existent post returns 404"
else
    report "H-44" "FAIL" "Expected 404, got $CODE"
fi

# H-45/46: Like status
echo ""
echo "--- H-45/46: Like status ---"
# Like first
curl -s -X POST "$BASE/posts/2/like" -H "X-Visitor-Id: check-visitor" > /dev/null
RESP=$(curl -s "$BASE/posts/2/like/status" -H "X-Visitor-Id: check-visitor")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
LIKED=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['liked'])")
if [ "$CODE" = "0" ] && [ "$LIKED" = "True" ]; then
    report "H-45" "PASS" "Like status shows liked=true for liked visitor"
else
    report "H-45" "FAIL" "Expected liked=true, got liked=$LIKED"
fi
RESP=$(curl -s "$BASE/posts/2/like/status" -H "X-Visitor-Id: never-liked-visitor")
LIKED=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['liked'])")
if [ "$LIKED" = "False" ]; then
    report "H-46" "PASS" "Like status shows liked=false for new visitor"
else
    report "H-46" "FAIL" "Expected liked=false, got liked=$LIKED"
fi

# ========== ADMIN CONTROLLER (Auth Tests) ==========
echo ""
echo "========== ADMIN CONTROLLER - AUTH TESTS =========="

# ADM-01: No token access to admin
echo ""
echo "--- ADM-01: No token access ---"
RESP=$(curl -s "$BASE/admin/posts")
CODE=$(echo "$RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('code','no_code'))")
if [ "$CODE" != "0" ] && [ -n "$CODE" ]; then
    report "ADM-01" "PASS" "Admin endpoint rejects no-token (code=$CODE)"
else
    report "ADM-01" "FAIL" "Expected 401, got code=$CODE"
fi

# Login as normal user (alice, role=author)
echo ""
echo "--- Login as alice (author role) ---"
LOGIN_RESP=$(curl -s -X POST "$BASE/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"alice","password":"alice123"}')
AUTHOR_TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")
echo "  Author token obtained"

# ADM-02: Non-admin access
echo ""
echo "--- ADM-02: Non-admin access (author role) ---"
RESP=$(curl -s "$BASE/admin/posts" \
    -H "Authorization: Bearer $AUTHOR_TOKEN")
echo "  Response: $(echo "$RESP" | head -c 200)"
# This might return JSON error or redirect - check response
CODE=$(echo "$RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('code','no_code'))" 2>/dev/null || echo "parse_error")
if [ "$CODE" = "403" ] || [ "$CODE" = "401" ] || [ "$CODE" = "parse_error" ]; then
    report "ADM-02" "PASS" "Non-admin rejected (code=$CODE)"
else
    report "ADM-02" "FAIL" "Expected 403/401 for non-admin, got code=$CODE"
fi

# ADM-03: Admin access
echo ""
echo "--- ADM-03: Admin access ---"
LOGIN_RESP=$(curl -s -X POST "$BASE/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"ekko","password":"admin123"}')
ADMIN_TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")
RESP=$(curl -s "$BASE/admin/posts" \
    -H "Authorization: Bearer $ADMIN_TOKEN")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "0" ]; then
    report "ADM-03" "PASS" "Admin can access admin endpoints"
else
    report "ADM-03" "FAIL" "Expected 0, got $CODE"
fi

# ADM-04: Refresh token used for admin
echo ""
echo "--- ADM-04: Refresh token for admin access ---"
REFRESH_TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['refreshToken'])")
RESP=$(curl -s "$BASE/admin/posts" \
    -H "Authorization: Bearer $REFRESH_TOKEN")
CODE=$(echo "$RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('code','no_code'))" 2>/dev/null || echo "parse_error")
if [ "$CODE" = "401" ]; then
    report "ADM-04" "PASS" "Refresh token rejected for admin access"
else
    report "ADM-04" "FAIL" "Expected 401, got code=$CODE"
fi

# ADM-05: Admin posts list
echo ""
echo "--- ADM-05: Admin posts list ---"
RESP=$(curl -s "$BASE/admin/posts" \
    -H "Authorization: Bearer $ADMIN_TOKEN")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
TOTAL=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['total'])")
if [ "$CODE" = "0" ] && [ "$TOTAL" -gt 0 ]; then
    report "ADM-05" "PASS" "Admin posts list returns $TOTAL posts"
else
    report "ADM-05" "FAIL" "Expected posts list, got total=$TOTAL"
fi

# ADM-07: Check slug (available)
echo ""
echo "--- ADM-07: Check slug available ---"
RESP=$(curl -s "$BASE/admin/posts/check-slug?slug=new-test-slug" \
    -H "Authorization: Bearer $ADMIN_TOKEN")
AVAILABLE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['available'])")
if [ "$AVAILABLE" = "True" ]; then
    report "ADM-07" "PASS" "Available slug reported as available"
else
    report "ADM-07" "FAIL" "Expected available=true"
fi

# ADM-08: Check slug (duplicate)
echo ""
echo "--- ADM-08: Check slug duplicate ---"
RESP=$(curl -s "$BASE/admin/posts/check-slug?slug=spring-boot-3-features" \
    -H "Authorization: Bearer $ADMIN_TOKEN")
AVAILABLE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['available'])")
if [ "$AVAILABLE" = "False" ]; then
    report "ADM-08" "PASS" "Duplicate slug detected"
else
    report "ADM-08" "FAIL" "Expected available=false"
fi

# ADM-10: Get single post
echo ""
echo "--- ADM-10: Get post by id ---"
RESP=$(curl -s "$BASE/admin/posts/2" \
    -H "Authorization: Bearer $ADMIN_TOKEN")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "0" ]; then
    report "ADM-10" "PASS" "Admin get post by id works"
else
    report "ADM-10" "FAIL" "Expected 0, got $CODE"
fi

# ADM-11: Get non-existent post
echo ""
echo "--- ADM-11: Get non-existent post ---"
RESP=$(curl -s "$BASE/admin/posts/99999" \
    -H "Authorization: Bearer $ADMIN_TOKEN")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "404" ]; then
    report "ADM-11" "PASS" "Non-existent post returns 404"
else
    report "ADM-11" "FAIL" "Expected 404, got $CODE"
fi

# ADM-12: Create post
echo ""
echo "--- ADM-12: Create post ---"
RESP=$(curl -s -X POST "$BASE/admin/posts" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"title":"Test Post from API","slug":"test-post-api-'$(date +%s)'","body":"## Hello World\n\nThis is a test post.","status":1,"categoryId":1,"tagIds":[1,2]}')
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
POST_ID=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
if [ "$CODE" = "0" ] && [ -n "$POST_ID" ]; then
    report "ADM-12" "PASS" "Post created with id=$POST_ID"
    TEST_POST_ID=$POST_ID
else
    report "ADM-12" "FAIL" "Expected post creation, got code=$CODE"
    echo "  Response: $(echo $RESP | head -c 200)"
fi

# ADM-13: Create post missing required fields
echo ""
echo "--- ADM-13: Create post missing required ---"
RESP=$(curl -s -X POST "$BASE/admin/posts" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"title":"","slug":"","body":"","status":1}')
CODE=$(echo "$RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('code','no_code'))")
if [ "$CODE" != "0" ]; then
    report "ADM-13" "PASS" "Missing required fields rejected (code=$CODE)"
else
    report "ADM-13" "FAIL" "Expected validation error"
fi

# ADM-14: Create post duplicate slug
echo ""
echo "--- ADM-14: Create post duplicate slug ---"
RESP=$(curl -s -X POST "$BASE/admin/posts" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"title":"Duplicate","slug":"spring-boot-3-features","body":"test","status":1}')
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
MSG=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('message',''))")
if [ "$CODE" = "400" ] || [ "$CODE" = "0" ] && [[ "$MSG" == *"存在"* ]]; then
    report "ADM-14" "PASS" "Duplicate slug rejected (code=$CODE, msg=$MSG)"
else
    report "ADM-14" "INFO" "Duplicate slug result code=$CODE msg=$MSG"
fi

# ADM-19: Delete post (soft delete)
echo ""
echo "--- ADM-19: Delete post ---"
RESP=$(curl -s -X DELETE "$BASE/admin/posts/$TEST_POST_ID" \
    -H "Authorization: Bearer $ADMIN_TOKEN")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "0" ]; then
    report "ADM-19" "PASS" "Post soft deleted successfully"
else
    report "ADM-19" "FAIL" "Expected 0, got $CODE"
fi

# ADM-20: Repeat delete (idempotent)
echo ""
echo "--- ADM-20: Repeat delete ---"
RESP=$(curl -s -X DELETE "$BASE/admin/posts/$TEST_POST_ID" \
    -H "Authorization: Bearer $ADMIN_TOKEN")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "0" ]; then
    report "ADM-20" "PASS" "Repeat delete is idempotent"
else
    report "ADM-20" "FAIL" "Expected 0, got $CODE"
fi

# ADM-21: Create category
echo ""
echo "--- ADM-21: Create category ---"
RESP=$(curl -s -X POST "$BASE/admin/categories" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"name":"Test Category","slug":"test-cat-api"}')
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
CAT_ID=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
if [ "$CODE" = "0" ] && [ -n "$CAT_ID" ]; then
    report "ADM-21" "PASS" "Category created with id=$CAT_ID"
    TEST_CAT_ID=$CAT_ID
else
    report "ADM-21" "FAIL" "Expected category creation, got code=$CODE"
fi

# ADM-27: Create tag
echo ""
echo "--- ADM-27: Create tag ---"
RESP=$(curl -s -X POST "$BASE/admin/tags" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"name":"Test Tag","slug":"test-tag-api"}')
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
TAG_ID=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
if [ "$CODE" = "0" ] && [ -n "$TAG_ID" ]; then
    report "ADM-27" "PASS" "Tag created with id=$TAG_ID"
else
    report "ADM-27" "FAIL" "Expected tag creation, got code=$CODE"
fi

# ADM-32: Create series
echo ""
echo "--- ADM-32: Create series ---"
RESP=$(curl -s -X POST "$BASE/admin/series" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"title":"Test Series","slug":"test-series-api","description":"API test series"}')
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
SERIES_ID=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
if [ "$CODE" = "0" ] && [ -n "$SERIES_ID" ]; then
    report "ADM-32" "PASS" "Series created with id=$SERIES_ID"
else
    report "ADM-32" "FAIL" "Expected series creation, got code=$CODE"
fi

# ADM-42: Statistics
echo ""
echo "--- ADM-42: Statistics ---"
RESP=$(curl -s "$BASE/admin/statistics" \
    -H "Authorization: Bearer $ADMIN_TOKEN")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "0" ]; then
    report "ADM-42" "PASS" "Statistics endpoint works"
else
    report "ADM-42" "FAIL" "Expected 0, got $CODE"
fi

# ========== S-03: Route privilege escalation ==========
echo ""
echo "--- S-03: Non-admin route access ---"
# Use alice (author role) should get 403
RESP=$(curl -s "$BASE/admin/statistics" \
    -H "Authorization: Bearer $AUTHOR_TOKEN")
echo "  Author accessing admin: $(echo $RESP | head -c 100)"

# S-01: Token tampering
echo ""
echo "--- S-01: Token tampering ---"
TAMPERED="${ACCESS_TOKEN}xyz"
RESP=$(curl -s "$BASE/auth/me" \
    -H "Authorization: Bearer $TAMPERED")
CODE=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['code'])")
if [ "$CODE" = "401" ]; then
    report "S-01" "PASS" "Tampered token rejected"
else
    report "S-01" "FAIL" "Expected 401, got $CODE"
fi

# ========== SUMMARY ==========
echo ""
echo "=============================================="
echo "  TEST SUMMARY"
echo "=============================================="
echo "  PASSED: $PASS"
echo "  FAILED: $FAIL"
echo "  TOTAL:  $((PASS + FAIL))"
echo ""

for r in "${RESULTS[@]}"; do
    IFS='|' read -ra PARTS <<< "$r"
    echo "  [${PARTS[1]}] ${PARTS[0]} - ${PARTS[2]}"
done

echo ""
echo "=============================================="
