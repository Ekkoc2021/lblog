#!/bin/bash
BASE="http://localhost:8099/iblogserver/api/v1"
PASS=0; FAIL=0

ok() { PASS=$((PASS+1)); echo "  ✅ $1"; }
fail() { FAIL=$((FAIL+1)); echo "  ❌ $1"; }

# Login
RESP=$(curl -s -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"ekko","password":"admin123"}')
TOKEN=$(echo "$RESP" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
H="Authorization: Bearer $TOKEN"
CT="Content-Type: application/json"

echo "=== Diagram CRUD ==="

# 1. POST create
R=$(curl -s -X POST "$BASE/diagrams" -H "$H" -H "$CT" \
  --data-raw '{"title":"test diagram","xmlData":"<test/>","fileSize":100}')
echo "$R" | grep -q '"code":0' && ok "POST /diagrams" || fail "POST /diagrams"
DID=$(echo "$R" | grep -o '"id":[0-9]*' | cut -d: -f2)
echo "    id=$DID"

# 2. GET list
R=$(curl -s "$BASE/diagrams?page=1&pageSize=10" -H "$H")
echo "$R" | grep -q '"code":0' && ok "GET /diagrams" || fail "GET /diagrams"

# 3. GET detail
R=$(curl -s "$BASE/diagrams/$DID" -H "$H")
echo "$R" | grep -q '"code":0' && ok "GET /diagrams/$DID" || fail "GET /diagrams/$DID"

# 4. PUT update
R=$(curl -s -X PUT "$BASE/diagrams/$DID" -H "$H" -H "$CT" \
  --data-raw '{"title":"updated","xmlData":"<updated/>","fileSize":50}')
echo "$R" | grep -q '"code":0' && ok "PUT /diagrams/$DID" || fail "PUT /diagrams/$DID"

# 5. PATCH meta
R=$(curl -s -X PATCH "$BASE/diagrams/$DID" -H "$H" -H "$CT" \
  -d '{"title":"renamed","description":"new desc"}')
echo "$R" | grep -q '"code":0' && ok "PATCH /diagrams/$DID" || fail "PATCH /diagrams/$DID"

# 6. DELETE
R=$(curl -s -X DELETE "$BASE/diagrams/$DID" -H "$H")
echo "$R" | grep -q '"code":0' && ok "DELETE /diagrams/$DID" || fail "DELETE /diagrams/$DID"

# 7. Verify 404
R=$(curl -s "$BASE/diagrams/$DID" -H "$H")
echo "$R" | grep -q '"code":404' && ok "GET /diagrams/$DID (deleted=404)" || fail "GET /diagrams/$DID (deleted=404)"

echo ""
echo "结果: $PASS/$((PASS+FAIL)) 通过, $FAIL 失败"
exit $FAIL