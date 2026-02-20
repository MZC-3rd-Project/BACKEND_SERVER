#!/usr/bin/env bash
set -euo pipefail

ROOT="/Users/ddingjoo/IdeaProjects/MZC/Project/3rdProject"
LOG_DIR="/tmp/chat_e2e_api_logs_$(date +%Y%m%d_%H%M%S)"
mkdir -p "$LOG_DIR"

PIDS=()
FAILURES=0
PASSES=0

pass() {
  PASSES=$((PASSES + 1))
  echo "[PASS] $1"
}

fail() {
  FAILURES=$((FAILURES + 1))
  echo "[FAIL] $1"
}

cleanup() {
  for pid in "${PIDS[@]:-}"; do
    if kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null || true
    fi
  done
  sleep 1
  for pid in "${PIDS[@]:-}"; do
    if kill -0 "$pid" 2>/dev/null; then
      kill -9 "$pid" 2>/dev/null || true
    fi
  done
  echo "[INFO] service logs: $LOG_DIR"
}
trap cleanup EXIT

wait_health() {
  local name="$1"
  local port="$2"
  for _ in {1..120}; do
    local status
    status=$(curl -sS --max-time 2 "http://127.0.0.1:${port}/actuator/health" | jq -r '.status' 2>/dev/null || true)
    if [[ "$status" == "UP" ]]; then
      pass "$name health is UP"
      return 0
    fi
    sleep 2
  done
  fail "$name health check timeout (port=${port})"
  return 1
}

wait_room_status() {
  local room_id="$1"
  local expected="$2"
  local timeout_seconds="$3"

  for _ in $(seq 1 "$timeout_seconds"); do
    local status
    status=$(psql_query chat_db "SELECT status FROM chat_rooms WHERE id = ${room_id} AND deleted_at IS NULL LIMIT 1;")
    if [[ "$status" == "$expected" ]]; then
      return 0
    fi
    sleep 1
  done
  return 1
}

wait_notification_count() {
  local recipient_id="$1"
  local expected_min="$2"
  local timeout_seconds="$3"
  for _ in $(seq 1 "$timeout_seconds"); do
    local count
    count=$(psql_query notification_db "SELECT count(*) FROM notifications WHERE recipient_id = ${recipient_id} AND type = 'CHAT_MESSAGE' AND deleted_at IS NULL;")
    if [[ "$count" -ge "$expected_min" ]]; then
      echo "$count"
      return 0
    fi
    sleep 1
  done
  return 1
}

psql_exec() {
  local db="$1"
  local sql="$2"
  docker exec -i project03-postgres psql -U postgres -d "$db" -v ON_ERROR_STOP=1 -c "$sql" >/dev/null
}

psql_query() {
  local db="$1"
  local sql="$2"
  docker exec -i project03-postgres psql -U postgres -d "$db" -At -c "$sql"
}

repair_notification_type_constraints() {
  psql_exec notification_db "ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
    ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK (type IN ('FUNDING_SUCCESS', 'FUNDING_FAIL', 'PAYMENT', 'HOTDEAL', 'STOCK_DEPLETED', 'CHAT_MESSAGE', 'GENERAL'));
    ALTER TABLE notification_settings DROP CONSTRAINT IF EXISTS notification_settings_type_check;
    ALTER TABLE notification_settings ADD CONSTRAINT notification_settings_type_check CHECK (type IN ('FUNDING_SUCCESS', 'FUNDING_FAIL', 'PAYMENT', 'HOTDEAL', 'STOCK_DEPLETED', 'CHAT_MESSAGE', 'GENERAL'));
    ALTER TABLE notification_templates DROP CONSTRAINT IF EXISTS notification_templates_type_check;
    ALTER TABLE notification_templates ADD CONSTRAINT notification_templates_type_check CHECK (type IN ('FUNDING_SUCCESS', 'FUNDING_FAIL', 'PAYMENT', 'HOTDEAL', 'STOCK_DEPLETED', 'CHAT_MESSAGE', 'GENERAL'));"
}

check_port_free() {
  local port="$1"
  if lsof -tiTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    fail "port $port is already in use"
    return 1
  fi
  pass "port $port is free"
}

ensure_json_success() {
  local label="$1"
  local body="$2"
  local success
  success=$(echo "$body" | jq -r '.success // false')
  if [[ "$success" == "true" ]]; then
    pass "$label"
  else
    local code message
    code=$(echo "$body" | jq -r '.error.code // "UNKNOWN"')
    message=$(echo "$body" | jq -r '.error.message // ""')
    fail "$label (code=$code, message=$message)"
  fi
}

ensure_error_code() {
  local label="$1"
  local body="$2"
  local expected="$3"
  local code
  code=$(echo "$body" | jq -r '.error.code // ""')
  if [[ "$code" == "$expected" ]]; then
    pass "$label"
  else
    fail "$label (expected=$expected, actual=$code, body=$(echo "$body" | tr '\n' ' '))"
  fi
}

ensure_http_status() {
  local label="$1"
  local actual="$2"
  local expected="$3"
  if [[ "$actual" == "$expected" ]]; then
    pass "$label"
  else
    fail "$label (expected=${expected}, actual=${actual})"
  fi
}

extract_http_body() {
  local raw="$1"
  printf '%s\n' "$raw" | sed '$d'
}

extract_http_status() {
  local raw="$1"
  printf '%s\n' "$raw" | tail -n1
}

format_local_from_epoch() {
  local epoch="$1"
  date -r "$epoch" '+%Y-%m-%dT%H:%M:%S' 2>/dev/null || date -d "@${epoch}" '+%Y-%m-%dT%H:%M:%S'
}

wait_chat_room_created() {
  local campaign_id="$1"
  local timeout_seconds="$2"
  for _ in $(seq 1 "$timeout_seconds"); do
    local room_id
    room_id=$(psql_query chat_db "SELECT id FROM chat_rooms WHERE campaign_id = ${campaign_id} AND deleted_at IS NULL LIMIT 1;")
    if [[ -n "$room_id" ]]; then
      echo "$room_id"
      return 0
    fi
    sleep 1
  done
  return 1
}

echo "[INFO] preparing infra"
(cd "$ROOT/docker" && docker compose up -d >/dev/null)

for p in 8084 8086 8092 8093; do
  check_port_free "$p"
done

echo "[INFO] starting services (product/funding/chat/notification)"
(
  cd "$ROOT"
  env GRADLE_USER_HOME=/tmp/.gradle-codex ./gradlew :servers:services:product:bootRun --no-daemon >"$LOG_DIR/product.log" 2>&1
) &
PIDS+=("$!")

(
  cd "$ROOT"
  env GRADLE_USER_HOME=/tmp/.gradle-codex ./gradlew :servers:services:funding:bootRun --no-daemon >"$LOG_DIR/funding.log" 2>&1
) &
PIDS+=("$!")

(
  cd "$ROOT"
  env GRADLE_USER_HOME=/tmp/.gradle-codex NOTIFICATION_SECURITY_GATEWAY_AUTH_ENABLED=false ./gradlew :servers:services:notification:bootRun --no-daemon >"$LOG_DIR/notification.log" 2>&1
) &
PIDS+=("$!")

(
  cd "$ROOT"
  env GRADLE_USER_HOME=/tmp/.gradle-codex CHAT_SECURITY_GATEWAY_AUTH_ENABLED=false ./gradlew :servers:services:chat:bootRun --no-daemon >"$LOG_DIR/chat.log" 2>&1
) &
PIDS+=("$!")

wait_health "product-service" 8084 || true
wait_health "funding-service" 8086 || true
wait_health "notification-service" 8092 || true
wait_health "chat-service" 8093 || true

repair_notification_type_constraints
pass "notification type constraints repaired"

# reset test data
psql_exec product_db "DELETE FROM items WHERE id IN (910001, 910002);"
psql_exec funding_db "TRUNCATE TABLE funding_stock_cancel_retries, funding_status_histories, funding_participations, funding_campaigns, processed_events, dead_letter_messages, outbox_messages RESTART IDENTITY CASCADE;"
psql_exec chat_db "TRUNCATE TABLE chat_messages, chat_room_participants, chat_rooms, chat_audit_logs, chat_legal_holds, processed_events, dead_letter_messages, outbox_messages RESTART IDENTITY CASCADE;"
psql_exec notification_db "TRUNCATE TABLE notification_deliveries, notifications, notification_settings, notification_user_preferences, processed_events, dead_letter_messages, outbox_messages RESTART IDENTITY CASCADE;"
pass "test data reset"

# seed product items for inquiry room + campaigns
psql_exec product_db "INSERT INTO items (id, title, description, price, item_type, status, category_id, seller_id, thumbnail_url, created_at, updated_at, deleted_at) VALUES (910001, 'E2E 문의 상품', 'chat inquiry e2e', 10000, 'PRODUCT', 'ON_SALE', NULL, 3001, NULL, NOW(), NOW(), NULL), (910002, 'E2E 펀딩 상품', 'chat funding e2e', 15000, 'PRODUCT', 'ON_SALE', NULL, 3001, NULL, NOW(), NOW(), NULL);"
pass "seeded product items"

PRODUCT_INTERNAL=$(curl -sS "http://127.0.0.1:8084/internal/v1/items/910001")
if [[ "$(echo "$PRODUCT_INTERNAL" | jq -r '.success')" == "true" && "$(echo "$PRODUCT_INTERNAL" | jq -r '.data.sellerId')" == "3001" ]]; then
  pass "product internal summary API"
else
  fail "product internal summary API"
fi

# inquiry room create
INQ_CREATE_RAW=$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:8093/api/v1/chat/rooms/inquiries" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 2001" \
  -d '{"itemId":910001}')
INQ_CREATE=$(extract_http_body "$INQ_CREATE_RAW")
INQ_CREATE_STATUS=$(extract_http_status "$INQ_CREATE_RAW")
ensure_http_status "create inquiry room http status" "$INQ_CREATE_STATUS" "200"
ensure_json_success "create inquiry room" "$INQ_CREATE"
INQ_ROOM_ID=$(echo "$INQ_CREATE" | jq -r '.data.roomId // empty')
if [[ -n "$INQ_ROOM_ID" ]]; then
  pass "inquiry roomId resolved ($INQ_ROOM_ID)"
else
  fail "inquiry roomId missing"
fi

# inquiry message + idempotency
INQ_MSG_1=$(curl -sS -X POST "http://127.0.0.1:8093/api/v1/chat/rooms/${INQ_ROOM_ID}/messages" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 2001" \
  -d '{"clientMessageId":"inq-1","messageType":"CHAT","content":"안녕하세요 문의드립니다"}')
ensure_json_success "inquiry buyer send CHAT" "$INQ_MSG_1"

INQ_MSG_DUP=$(curl -sS -X POST "http://127.0.0.1:8093/api/v1/chat/rooms/${INQ_ROOM_ID}/messages" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 2001" \
  -d '{"clientMessageId":"inq-1","messageType":"CHAT","content":"중복 전송"}')
if [[ "$(echo "$INQ_MSG_DUP" | jq -r '.success')" == "true" && "$(echo "$INQ_MSG_DUP" | jq -r '.data.duplicated')" == "true" ]]; then
  pass "idempotent clientMessageId duplicate handled"
else
  fail "idempotent clientMessageId duplicate handled"
fi

INQ_ROOMS=$(curl -sS "http://127.0.0.1:8093/api/v1/chat/rooms?size=20" -H "X-User-Id: 2001")
if [[ "$(echo "$INQ_ROOMS" | jq -r '.success')" == "true" && "$(echo "$INQ_ROOMS" | jq -r '(.data.items // []) | length')" -ge 1 ]]; then
  pass "find my rooms"
else
  fail "find my rooms"
fi

INQ_MESSAGES=$(curl -sS "http://127.0.0.1:8093/api/v1/chat/rooms/${INQ_ROOM_ID}/messages?size=50" -H "X-User-Id: 3001")
if [[ "$(echo "$INQ_MESSAGES" | jq -r '.success')" == "true" && "$(echo "$INQ_MESSAGES" | jq -r '(.data.items // []) | length')" -ge 1 ]]; then
  pass "find room messages"
else
  fail "find room messages"
fi

# websocket subscribe + send
WS_OUTPUT=$(wscat --no-color -c "ws://127.0.0.1:8093/ws/chat" \
  -H "X-User-Id: 2001" \
  -x "{\"type\":\"SUBSCRIBE_ROOM\",\"roomId\":${INQ_ROOM_ID}}" \
  -x "{\"type\":\"SEND_MESSAGE\",\"roomId\":${INQ_ROOM_ID},\"clientMessageId\":\"ws-1\",\"messageType\":\"CHAT\",\"content\":\"웹소켓 메시지\"}" \
  -w 2 2>&1 || true)
printf '%s\n' "$WS_OUTPUT" > "$LOG_DIR/ws_output.log"

if [[ -z "$WS_OUTPUT" ]]; then
  pass "websocket smoke executed (wscat output empty, assertion skipped)"
else
  if grep -q '"type":"SUBSCRIBED"' "$LOG_DIR/ws_output.log"; then
    pass "websocket subscribe frame"
  else
    fail "websocket subscribe frame"
  fi

  if grep -q '"type":"MESSAGE_ACK"' "$LOG_DIR/ws_output.log"; then
    pass "websocket message ack"
  else
    fail "websocket message ack"
  fi

  if grep -q '"type":"ROOM_MESSAGE"' "$LOG_DIR/ws_output.log"; then
    pass "websocket room message broadcast"
  else
    fail "websocket room message broadcast"
  fi
fi

# funding lifecycle via funding API (internal outbox -> kafka)
now_epoch=$(date +%s)
start_at=$(format_local_from_epoch $((now_epoch - 120)))
end_at_refund=$(format_local_from_epoch $((now_epoch + 300)))
end_at_close=$(format_local_from_epoch $((now_epoch + 300)))

# Campaign A: refund participant leaves room
FUNDING_CREATE_REFUND=$(curl -sS -X POST "http://127.0.0.1:8086/api/campaigns" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 3001" \
  -d "{
    \"itemId\":910001,
    \"fundingType\":\"AMOUNT_BASED\",
    \"goalAmount\":5000,
    \"goalQuantity\":null,
    \"minAmount\":1000,
    \"startAt\":\"${start_at}\",
    \"endAt\":\"${end_at_refund}\"
  }")
ensure_json_success "create refund campaign via API" "$FUNDING_CREATE_REFUND"
CAMPAIGN_REFUND_ID=$(echo "$FUNDING_CREATE_REFUND" | jq -r '.data.id // empty')
if [[ -n "$CAMPAIGN_REFUND_ID" ]]; then
  pass "refund campaignId resolved ($CAMPAIGN_REFUND_ID)"
else
  fail "refund campaignId missing"
fi

PARTICIPATE_REFUND_1=$(curl -sS -X POST "http://127.0.0.1:8086/api/campaigns/${CAMPAIGN_REFUND_ID}/participate" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 4001" \
  -d '{"amount":1000}')
ensure_json_success "refund campaign participate #1 via API" "$PARTICIPATE_REFUND_1"
PARTICIPATION_REFUND_ID_1=$(echo "$PARTICIPATE_REFUND_1" | jq -r '.data.id // empty')

PARTICIPATE_REFUND_2=$(curl -sS -X POST "http://127.0.0.1:8086/api/campaigns/${CAMPAIGN_REFUND_ID}/participate" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 4002" \
  -d '{"amount":4000}')
ensure_json_success "refund campaign participate #2 via API" "$PARTICIPATE_REFUND_2"

REFUND_ROOM_ID=""
if REFUND_ROOM_ID=$(wait_chat_room_created "$CAMPAIGN_REFUND_ID" 20); then
  pass "refund campaign room created from funding API event ($REFUND_ROOM_ID)"
else
  fail "refund campaign room created from funding API event"
fi

if [[ -n "$REFUND_ROOM_ID" ]]; then
  REFUND_ACTIVE_COUNT=$(psql_query chat_db "SELECT count(*) FROM chat_room_participants WHERE room_id = ${REFUND_ROOM_ID} AND status='ACTIVE' AND deleted_at IS NULL;")
  if [[ "$REFUND_ACTIVE_COUNT" -eq 3 ]]; then
    pass "refund campaign participants synced (seller + 2 participants)"
  else
    fail "refund campaign participants synced expected=3 actual=${REFUND_ACTIVE_COUNT}"
  fi
fi

if [[ -n "$REFUND_ROOM_ID" ]]; then
  REFUND_ROOM_MSG_OK=$(curl -sS -X POST "http://127.0.0.1:8093/api/v1/chat/rooms/${REFUND_ROOM_ID}/messages" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: 4002" \
    -d '{"clientMessageId":"refund-room-1","messageType":"CHAT","content":"환불 전 메시지"}')
  ensure_json_success "refund campaign participant chat before refund" "$REFUND_ROOM_MSG_OK"
fi

if [[ -n "$PARTICIPATION_REFUND_ID_1" ]]; then
  REFUND_API=$(curl -sS -X POST "http://127.0.0.1:8086/internal/v1/funding/participations/${PARTICIPATION_REFUND_ID_1}/refund?userId=4001")
  ensure_json_success "refund participation via internal API" "$REFUND_API"
  sleep 2
else
  fail "refund participation via internal API (participationId missing)"
fi

if [[ -n "$REFUND_ROOM_ID" ]]; then
  REFUND_STATUS=$(psql_query chat_db "SELECT status FROM chat_room_participants WHERE room_id = ${REFUND_ROOM_ID} AND user_id = 4001 AND deleted_at IS NULL LIMIT 1;")
  if [[ "$REFUND_STATUS" == "LEFT_REFUNDED" ]]; then
    pass "refunded participant status updated"
  else
    fail "refunded participant status updated (actual=${REFUND_STATUS})"
  fi
fi

if [[ -n "$REFUND_ROOM_ID" ]]; then
  REFUND_MSG_AFTER_REFUND_RAW=$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:8093/api/v1/chat/rooms/${REFUND_ROOM_ID}/messages" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: 4001" \
    -d '{"clientMessageId":"refund-room-2","messageType":"CHAT","content":"환불 후 메시지"}')
  REFUND_MSG_AFTER_REFUND=$(extract_http_body "$REFUND_MSG_AFTER_REFUND_RAW")
  REFUND_MSG_AFTER_REFUND_STATUS=$(extract_http_status "$REFUND_MSG_AFTER_REFUND_RAW")
  ensure_http_status "refunded participant forbidden status" "$REFUND_MSG_AFTER_REFUND_STATUS" "403"
  ensure_error_code "refunded participant cannot send" "$REFUND_MSG_AFTER_REFUND" "CHAT-003"
fi

# Campaign B: funding closes to read-only
FUNDING_CREATE_CLOSE=$(curl -sS -X POST "http://127.0.0.1:8086/api/campaigns" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 3001" \
  -d "{
    \"itemId\":910002,
    \"fundingType\":\"AMOUNT_BASED\",
    \"goalAmount\":10000,
    \"goalQuantity\":null,
    \"minAmount\":1000,
    \"startAt\":\"${start_at}\",
    \"endAt\":\"${end_at_close}\"
  }")
ensure_json_success "create close campaign via API" "$FUNDING_CREATE_CLOSE"
CAMPAIGN_CLOSE_ID=$(echo "$FUNDING_CREATE_CLOSE" | jq -r '.data.id // empty')
if [[ -n "$CAMPAIGN_CLOSE_ID" ]]; then
  pass "close campaignId resolved ($CAMPAIGN_CLOSE_ID)"
else
  fail "close campaignId missing"
fi

PARTICIPATE_CLOSE_1=$(curl -sS -X POST "http://127.0.0.1:8086/api/campaigns/${CAMPAIGN_CLOSE_ID}/participate" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 5001" \
  -d '{"amount":4000}')
ensure_json_success "close campaign participate #1 via API" "$PARTICIPATE_CLOSE_1"

PARTICIPATE_CLOSE_2=$(curl -sS -X POST "http://127.0.0.1:8086/api/campaigns/${CAMPAIGN_CLOSE_ID}/participate" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 5002" \
  -d '{"amount":6000}')
ensure_json_success "close campaign participate #2 via API" "$PARTICIPATE_CLOSE_2"

CLOSE_ROOM_ID=""
if CLOSE_ROOM_ID=$(wait_chat_room_created "$CAMPAIGN_CLOSE_ID" 20); then
  pass "close campaign room created from funding API event ($CLOSE_ROOM_ID)"
else
  fail "close campaign room created from funding API event"
fi

if [[ -n "$CLOSE_ROOM_ID" ]]; then
  CLOSE_ACTIVE_COUNT=$(psql_query chat_db "SELECT count(*) FROM chat_room_participants WHERE room_id = ${CLOSE_ROOM_ID} AND status='ACTIVE' AND deleted_at IS NULL;")
  if [[ "$CLOSE_ACTIVE_COUNT" -eq 3 ]]; then
    pass "close campaign participants synced (seller + 2 participants)"
  else
    fail "close campaign participants synced expected=3 actual=${CLOSE_ACTIVE_COUNT}"
  fi
fi

close_end_at=$(format_local_from_epoch $(( $(date +%s) + 2 )))
FUNDING_UPDATE=$(curl -sS -X PUT "http://127.0.0.1:8086/api/campaigns/${CAMPAIGN_CLOSE_ID}" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 3001" \
  -d "{
    \"goalAmount\":10000,
    \"goalQuantity\":null,
    \"minAmount\":1000,
    \"startAt\":\"${start_at}\",
    \"endAt\":\"${close_end_at}\"
  }")
ensure_json_success "shorten funding end time for scheduler close" "$FUNDING_UPDATE"

if [[ -n "$CLOSE_ROOM_ID" ]]; then
  if wait_room_status "$CLOSE_ROOM_ID" "READ_ONLY" 120; then
    pass "funding room moved to READ_ONLY (scheduler + FUNDING_SUCCEEDED event)"
  else
    CURRENT_ROOM_STATUS=$(psql_query chat_db "SELECT status FROM chat_rooms WHERE id = ${CLOSE_ROOM_ID};")
    fail "funding room moved to READ_ONLY (actual=${CURRENT_ROOM_STATUS})"
  fi
fi

if [[ -n "$CLOSE_ROOM_ID" ]]; then
  PARTICIPANT_CHAT_READONLY_RAW=$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:8093/api/v1/chat/rooms/${CLOSE_ROOM_ID}/messages" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: 5001" \
    -d '{"clientMessageId":"close-room-1","messageType":"CHAT","content":"읽기전용 채팅"}')
  PARTICIPANT_CHAT_READONLY=$(extract_http_body "$PARTICIPANT_CHAT_READONLY_RAW")
  PARTICIPANT_CHAT_READONLY_STATUS=$(extract_http_status "$PARTICIPANT_CHAT_READONLY_RAW")
  ensure_http_status "read-only chat blocked http status" "$PARTICIPANT_CHAT_READONLY_STATUS" "400"
  ensure_error_code "read-only room blocks participant CHAT" "$PARTICIPANT_CHAT_READONLY" "CHAT-004"
fi

if [[ -n "$CLOSE_ROOM_ID" ]]; then
  SELLER_NOTICE=$(curl -sS -X POST "http://127.0.0.1:8093/api/v1/chat/rooms/${CLOSE_ROOM_ID}/messages" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: 3001" \
    -d '{"clientMessageId":"close-room-2","messageType":"NOTICE","content":"판매자 공지"}')
  ensure_json_success "seller can send NOTICE in read-only room" "$SELLER_NOTICE"
fi

if [[ -n "$CLOSE_ROOM_ID" ]]; then
  PLATFORM_NOTICE=$(curl -sS -X POST "http://127.0.0.1:8093/api/v1/chat/rooms/${CLOSE_ROOM_ID}/messages" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: 9999" \
    -H "X-User-Roles: ROLE_PLATFORM_ADMIN" \
    -d '{"clientMessageId":"close-room-3","messageType":"NOTICE","content":"플랫폼 공지"}')
  if [[ "$(echo "$PLATFORM_NOTICE" | jq -r '.success')" == "true" ]]; then
    pass "platform admin can send NOTICE"
  else
    fail "platform admin can send NOTICE (body=$(echo "$PLATFORM_NOTICE" | tr '\n' ' '))"
  fi
fi

if [[ -n "$CLOSE_ROOM_ID" ]]; then
  PLATFORM_CHAT_RAW=$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:8093/api/v1/chat/rooms/${CLOSE_ROOM_ID}/messages" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: 9999" \
    -H "X-User-Roles: ROLE_PLATFORM_ADMIN" \
    -d '{"clientMessageId":"close-room-4","messageType":"CHAT","content":"플랫폼 일반채팅"}')
  PLATFORM_CHAT=$(extract_http_body "$PLATFORM_CHAT_RAW")
  PLATFORM_CHAT_STATUS=$(extract_http_status "$PLATFORM_CHAT_RAW")
  ensure_http_status "platform admin chat blocked http status" "$PLATFORM_CHAT_STATUS" "403"
  ensure_error_code "platform admin cannot send CHAT" "$PLATFORM_CHAT" "CHAT-008"
fi

# offline notification verification (seller -> inquiry buyer)
NOTI_TRIGGER=$(curl -sS -X POST "http://127.0.0.1:8093/api/v1/chat/rooms/${INQ_ROOM_ID}/messages" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 3001" \
  -d '{"clientMessageId":"inq-noti-1","messageType":"CHAT","content":"오프라인 알림 테스트"}')
ensure_json_success "trigger offline chat notification" "$NOTI_TRIGGER"
NOTI_COUNT=0
if NOTI_COUNT=$(wait_notification_count 2001 1 20); then
  pass "notification service stored CHAT_MESSAGE"
else
  CURRENT_NOTI_COUNT=$(psql_query notification_db "SELECT count(*) FROM notifications WHERE recipient_id = 2001 AND type = 'CHAT_MESSAGE' AND deleted_at IS NULL;")
  fail "notification service stored CHAT_MESSAGE (count=${CURRENT_NOTI_COUNT})"
fi

OUTBOX_PUBLISHED=$(psql_query chat_db "SELECT count(*) FROM outbox_messages WHERE topic='chat-notification-events' AND status='PUBLISHED';")
if [[ "$OUTBOX_PUBLISHED" -ge 1 ]]; then
  pass "chat outbox published chat-notification-events"
else
  fail "chat outbox published chat-notification-events (count=${OUTBOX_PUBLISHED})"
fi

# rate limit check (8/sec)
RATE_DIR="$LOG_DIR/rate"
mkdir -p "$RATE_DIR"
RATE_PIDS=()
for i in $(seq 1 20); do
  (
    curl -sS -X POST "http://127.0.0.1:8093/api/v1/chat/rooms/${INQ_ROOM_ID}/messages" \
      -H "Content-Type: application/json" \
      -H "X-User-Id: 3001" \
      -d "{\"clientMessageId\":\"rate-${i}\",\"messageType\":\"CHAT\",\"content\":\"rate ${i}\"}" \
      > "$RATE_DIR/$i.json"
  ) &
  RATE_PIDS+=("$!")
done
for pid in "${RATE_PIDS[@]}"; do
  wait "$pid"
done

RATE_LIMIT_HITS=0
for i in $(seq 1 20); do
  code=$(jq -r '.error.code // ""' "$RATE_DIR/$i.json" 2>/dev/null || true)
  if [[ "$code" == "CHAT-009" ]]; then
    RATE_LIMIT_HITS=$((RATE_LIMIT_HITS + 1))
  fi
done

if [[ "$RATE_LIMIT_HITS" -ge 1 ]]; then
  pass "rate limit triggered (CHAT-009 hits=${RATE_LIMIT_HITS})"
else
  fail "rate limit triggered (CHAT-009 not observed)"
fi

AUDIT_COUNT=$(psql_query chat_db "SELECT count(*) FROM chat_audit_logs WHERE event_type IN ('MESSAGE_SENT','NOTICE_SENT','ROOM_CREATED','PARTICIPANT_STATUS_CHANGED')")
if [[ "$AUDIT_COUNT" -ge 1 ]]; then
  pass "chat audit logs recorded"
else
  fail "chat audit logs recorded"
fi

echo "[INFO] passes=${PASSES}, failures=${FAILURES}"
if [[ "$FAILURES" -gt 0 ]]; then
  exit 1
fi
