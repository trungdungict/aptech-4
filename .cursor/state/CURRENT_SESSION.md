# CURRENT_SESSION

## Session objective

Customer AI regressions: paid-order listing, sold-out availability, admin analytics messaging, checkout copy.

## Task ID

Ad-hoc (not a numbered roadmap task). Progress remains **54/64**.

## Root causes

| Issue | Cause |
|-------|--------|
| `List my paid orders.` | No deterministic route → LLM invented permissions / fake IDs |
| Sold-Out Notebook in available search | Filter (`available > 0`) was correct; seed upsert used `max(onHand, reserved)` and left DEMO-HOME-OOS at on_hand=80 |
| Store-wide analytics → “need JWT” | Authenticated Customer hit LLM; model mis-described auth instead of Admin-only |
| `simulateFailure` in checkout | Copied into Customer-facing checkout help |

## Fixes

- `ORDER_LIST_BY_STATUS` / `ORDER_COUNT_BY_STATUS` + `ADMIN_ANALYTICS_FORBIDDEN`
- Seed inventory honors fixture on_hand=0 (clamp reserved); invalidate catalog cache after seed
- Checkout copy without `simulateFailure`; ProductResponse `inStock` docs = available

## Live verified

- Paid list → `deterministic_orders_by_status`, total **22** (= `/orders?status=PAID`)
- Products under 500000 → no Sold-Out; DB available=0 for DEMO-HOME-OOS
- Store-wide → Admin-only message (no JWT ask)
- Checkout → no `simulateFailure`

## Exact next action

Resume roadmap P15/P16. Keep **54/64**.
