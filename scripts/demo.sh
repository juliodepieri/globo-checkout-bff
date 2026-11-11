#!/usr/bin/env bash
# Walks through every scenario against a running BFF: ./scripts/demo.sh [base-url]
set -euo pipefail
BASE="${1:-http://localhost:8080}"

section() { # title
  echo -e "\n\033[1;36m==================== $1 ====================\033[0m"
}
pretty() { # json -> indented with jq when available, raw otherwise
  if command -v jq >/dev/null && jq . <<<"$1" 2>/dev/null; then :; else echo "$1"; fi
}
call() { # method path body
  echo -e "\n\033[1m>>> $1 $2\033[0m"
  if [ -n "${3:-}" ]; then echo "Request:"; pretty "$3"; fi
  local out code body
  out=$(curl -s -w '\n%{http_code}' -X "$1" "$BASE$2" -H 'Content-Type: application/json' -d "${3:-}")
  code="${out##*$'\n'}"
  body="${out%$'\n'*}"
  echo -e "\033[1m<<< HTTP $code\033[0m"
  pretty "$body"
}
pre_checkout() { # slug -> cartId
  curl -s -X POST "$BASE/operations/pre-checkout" -H 'Content-Type: application/json' \
    -d "{\"products\":[{\"slug\":\"$1\"}]}" | sed -E 's/.*"cartId":"([^"]+)".*/\1/'
}
card() { # cartId number
  echo "{\"cartId\":\"$1\",\"paymentMethod\":{\"name\":\"Cartão de Crédito\",\"card\":{\"holderName\":\"Ana Souza\",\"number\":\"$2\",\"expiry\":\"12/28\",\"cvv\":\"123\"}}}"
}

section "1. Pre-checkout"
call POST /operations/pre-checkout '{"products":[{"slug":"premium"}]}'

section "2. Unknown product"
call POST /operations/pre-checkout '{"products":[{"slug":"ultra"}]}'

section "3. Card approved, then duplicate payment"
CART=$(pre_checkout premium)
call POST /operations/checkout "$(card "$CART" 4111111111114242)"
call POST /operations/checkout "$(card "$CART" 4111111111114242)"

section "4. Card declined (ends in 0002), then retry with Pix"
CART=$(pre_checkout familia)
call POST /operations/checkout "$(card "$CART" 4111111111110002)"
call POST /operations/checkout "{\"cartId\":\"$CART\",\"paymentMethod\":{\"name\":\"Pix\"}}"

section "5. Unknown cart"
call POST /operations/checkout '{"cartId":"00000000-0000-0000-0000-000000000000","paymentMethod":{"name":"Pix"}}'

section "6. Payment method not offered / Pix with card data"
CART=$(pre_checkout basico)
call POST /operations/checkout "{\"cartId\":\"$CART\",\"paymentMethod\":{\"name\":\"Boleto\"}}"
call POST /operations/checkout "{\"cartId\":\"$CART\",\"paymentMethod\":{\"name\":\"Pix\",\"card\":{\"holderName\":\"Ana\",\"number\":\"4111111111114242\",\"expiry\":\"12/28\",\"cvv\":\"123\"}}}"
