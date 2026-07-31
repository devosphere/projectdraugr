#!/usr/bin/env bash
# E2E regression: drive the real API exactly as the frontend does.
API=${DRAUGR_API:-http://localhost:8099}
pass=0; fail=0
chk(){ if [ "$2" = "$3" ]; then echo "  PASS  $1"; pass=$((pass+1)); else echo "  FAIL  $1 (expected '$3', got '$2')"; fail=$((fail+1)); fi; }
chknz(){ if [ -n "$2" ] && [ "$2" != "null" ]; then echo "  PASS  $1"; pass=$((pass+1)); else echo "  FAIL  $1 (empty/null)"; fail=$((fail+1)); fi; }

echo "### 1. Chronicle lifecycle"
ACTIVE=$(curl -s $API/api/chronicles/active)
if echo "$ACTIVE" | grep -q '"id"'; then echo "  (existing chronicle reused)"; else
  curl -s -X POST $API/api/chronicles >/dev/null; echo "  (chronicle created)"; fi
ACTIVE=$(curl -s $API/api/chronicles/active)
chknz "GET /api/chronicles/active returns a chronicle" "$(echo "$ACTIVE" | grep -o '"id":"[^"]*"' | head -1)"

echo "### 2. Frontend-consumed read endpoints return valid shapes"
for ep in "active/body" "active/environment" "active/location" "active/discoveries"; do
  R=$(curl -s -o /dev/null -w '%{http_code}' $API/api/chronicles/$ep); chk "GET /api/chronicles/$ep -> 200" "$R" "200"; done
chk "GET /api/items/state -> 200" "$(curl -s -o /dev/null -w '%{http_code}' $API/api/items/state)" "200"
chk "GET /api/literature -> 200" "$(curl -s -o /dev/null -w '%{http_code}' $API/api/literature)" "200"
chk "GET /api/actions/history -> 200" "$(curl -s -o /dev/null -w '%{http_code}' "$API/api/actions/history?limit=20")" "200"

echo "### 3. Action contract: fields the frontend destructures"
# A survival simulation is entitled to kill its test subject — the chronicle can die
# of exposure or hunger mid-run, and the API correctly answers 409 afterwards. The
# harness treats that as expected, awakens a fresh chronicle, and retries once, so a
# legitimate death never reads as a classification failure.
rawact(){ curl -s -X POST $API/api/actions -H 'Content-Type: application/json' \
  -d "{\"text\":$(printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g; s/^/"/; s/$/"/'),\"idempotencyKey\":\"$(powershell -c '[guid]::NewGuid().ToString()' | tr -d '\r')\"}"; }
act(){ R1=$(rawact "$1")
  if echo "$R1" | grep -q 'No living Chronicle'; then
    curl -s -X POST $API/api/chronicles >/dev/null; R1=$(rawact "$1"); fi
  printf '%s' "$R1"; }
R=$(act "look around carefully")
for f in actionId intent outcome durationMinutes resolvedAt perception body; do
  chknz "action response has .$f" "$(echo "$R" | grep -o "\"$f\":" | head -1)"; done
chknz "action response has .frame (F1 seam)" "$(echo "$R" | grep -o '"frame":' | head -1)"

echo "### 4. New Sprint-002 intents classify and resolve (no 500s)"
declare -a CASES=(
 "gather mushrooms from the forest floor|GATHER_PLANT"
 "fell the oak tree with my stone axe|FELL_TREE"
 "raid the honeybee hive using smoke|RAID_HIVE"
 "dig for earthworms|COLLECT_INSECTS"
 "fish the stream with a spear|FISH"
 "set a snare across the run|SET_TRAP"
 "check my trap|CHECK_TRAP"
 "look for tracks on the ground|TRACK"
 "approach the goat calmly and offer food|TAME"
 "leave bait to draw them in|LURE"
)
for c in "${CASES[@]}"; do
  txt="${c%%|*}"; want="${c##*|}"
  R=$(act "$txt"); got=$(echo "$R" | grep -o '"intent":"[^"]*"' | head -1 | cut -d'"' -f4)
  chk "\"$txt\" -> $want" "$got" "$want"
done

echo "### 5. ActionInputClassifier (DR-0019)"
R=$(act "masturbate"); chk "personal act -> PERSONAL_ACT" "$(echo "$R"|grep -o '"intent":"[^"]*"'|head -1|cut -d'"' -f4)" "PERSONAL_ACT"
R=$(act "asdfghjkl zxcvbnm"); chk "gibberish -> NONSENSICAL" "$(echo "$R"|grep -o '"intent":"[^"]*"'|head -1|cut -d'"' -f4)" "NONSENSICAL"
chk "gibberish costs no world time" "$(echo "$R"|grep -o '"durationMinutes":[0-9]*'|head -1|cut -d: -f2)" "0"
R=$(act "teleport to the mountain"); chk "impossible -> PHYSICALLY_IMPOSSIBLE" "$(echo "$R"|grep -o '"intent":"[^"]*"'|head -1|cut -d'"' -f4)" "PHYSICALLY_IMPOSSIBLE"

echo "### 6. Idempotency (double-delivery must not advance world twice)"
KEY=$(powershell -c '[guid]::NewGuid().ToString()' | tr -d '\r')
B1=$(curl -s -X POST $API/api/actions -H 'Content-Type: application/json' -d "{\"text\":\"rest for a moment\",\"idempotencyKey\":\"$KEY\"}")
B2=$(curl -s -X POST $API/api/actions -H 'Content-Type: application/json' -d "{\"text\":\"rest for a moment\",\"idempotencyKey\":\"$KEY\"}")
chk "replay returns same actionId" "$(echo "$B2"|grep -o '\"actionId\":\"[^\"]*\"'|cut -d'"' -f4)" "$(echo "$B1"|grep -o '\"actionId\":\"[^\"]*\"'|cut -d'"' -f4)"

echo "### 7. Invariants still hold after the run"
AUD=$(curl -s $API/api/audit)
chk "audit consistent after playthrough" "$(echo "$AUD"|grep -o '\"consistent\":[a-z]*'|cut -d: -f2)" "true"

echo ""; echo "======== E2E RESULT: $pass passed, $fail failed ========"
[ "$fail" -eq 0 ] || exit 1
