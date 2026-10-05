#!/usr/bin/env bash
# Walks one order through the happy path against a running stack (docker compose up -d --build).
#   ./scripts/demo.sh            standard order, auto-approved, paid, shipped
#   ./scripts/demo.sh unpaid     order that waits for payment (advance the deadline in Operate or tests)
set -euo pipefail

API="${API:-http://localhost:8081/api}"
MODE="${1:-paid}"

json_field() { sed -n "s/.*\"$1\":\"\{0,1\}\([^\",}]*\)\"\{0,1\}.*/\1/p"; }

status_of() { curl -fsS "$API/orders/$1" | json_field status; }

wait_for() {
  local id=$1 expected=$2
  for _ in $(seq 1 60); do
    local current
    current=$(status_of "$id")
    if [ "$current" = "$expected" ]; then echo "  status: $current"; return 0; fi
    sleep 1
  done
  echo "  timed out waiting for $expected (last: $(status_of "$id"))" >&2
  return 1
}

echo "1. Place an order"
response=$(curl -fsS -X POST "$API/orders" -H 'Content-Type: application/json' -d '{
  "customerId": "customer-42",
  "customerSegment": "STANDARD",
  "lines": [{"sku": "SKU-KEYBOARD", "quantity": 2}, {"sku": "SKU-MOUSE", "quantity": 1}]
}')
order_id=$(echo "$response" | json_field id)
total=$(echo "$response" | json_field totalAmount)
echo "  order $order_id, total $total EUR"

echo "2. Inventory check, decision, reservation and invoice run automatically"
wait_for "$order_id" AWAITING_PAYMENT

if [ "$MODE" = "unpaid" ]; then
  echo "Order $order_id now waits for payment. Open Operate at http://localhost:8088 (demo/demo)."
  exit 0
fi

echo "3. The payment provider confirms the payment (published to Kafka topic 'payments')"
curl -fsS -X POST "$API/orders/$order_id/payments" -H 'Content-Type: application/json' \
  -d "{\"amount\": $total, \"paymentId\": \"pay-$order_id\"}" > /dev/null

echo "4. The message is correlated, the order ships"
wait_for "$order_id" SHIPPED
echo "Done. See the finished instance in Operate: http://localhost:8088/operate"
