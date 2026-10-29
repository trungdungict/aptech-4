# ADR-019: Local Ollama AI assistant (Customer + Admin)

- **Status:** Accepted
- **Date:** 2026-10-29
- **Related:** ADR-001 (monolith), ADR-005 (JWT), ADR-017/018 (notifications separate)

## Context

Operators want an in-app assistant that answers Customer order/catalog questions and Admin inventory/sales questions without a cloud LLM or a new microservice.

## Decision

1. **Runtime:** Spring AI **2.0.0** + Ollama inside the existing Spring Boot monolith.
2. **Optional:** `aptech.ai.enabled=false` and `spring.ai.model.chat=none` by default. Compose profile `ai` starts Ollama; `make deploy-local-ai` wires API → `http://ollama:11434`.
3. **Tools:** Read-only Spring AI `@Tool` methods. Customer tools always use `CurrentUser.requireUserId()`. Admin tools call `@PreAuthorize("hasRole('ADMIN')")` analytics services.
4. **Transport:** REST `/api/v1/chat/messages` + SSE `/messages/stream`. Do not reuse notification WebSockets.
5. **History:** Bounded in-memory conversations per user (no Liquibase for MVP).
6. **Language:** English default UI/greeting; Vietnamese supported via selector + detection. Manual selection persists in `localStorage`.
7. **Default model:** `llama3.2` (tool-calling capable; pull manually — never on app startup).

## Inventory / revenue semantics

- **available** = `quantity_on_hand - quantity_reserved`
- **out of stock** = available ≤ 0
- **low stock** = available ≤ configurable threshold (default 5)
- **revenue** = sum of `orders.total_amount` for statuses `PAID|PROCESSING|SHIPPED|COMPLETED` (mock payment succeeded). Excludes `PENDING_PAYMENT`, `CANCELLED`, `FAILED`. Product ranking uses `order_items` snapshots.

## Consequences

- Positive: works offline; same JWT/RBAC; stack runs without AI.
- Negative: local RAM/CPU for models; simple broker-less chat (no multi-instance shared history).
- Follow-ups: optional RAG for policy docs; persistent chat tables if demos need history across restarts.
