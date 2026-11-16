# Pollenz — Specification

Group-ordering board for hostels and societies. A host opens a **pool** for a quick-commerce app
(Blinkit, Instamart, Zepto). Others in the same community join with their items so the cart crosses
the free-delivery minimum. The host places the real order by hand in that app; Pollenz handles
discovery, item collection, bill split and payment tracking. There is no integration with any
quick-commerce API and no payment gateway.

This file is the contract for every phase. If code and this file disagree, fix one of them on purpose.

## Stack

| Layer | Choice |
|---|---|
| Language / framework | Java 21, Spring Boot 4.1.x, Maven (wrapper) |
| Web | Spring Web MVC, Bean Validation |
| Persistence | PostgreSQL 17, Spring Data JPA, Flyway migrations |
| Auth | Spring Security, stateless JWT (HS256), BCrypt |
| Realtime | Spring WebSocket + STOMP |
| Jobs | `@Scheduled` |
| API docs | springdoc-openapi (Swagger UI) |
| Tests | JUnit 5, Spring Boot Test, Testcontainers (PostgreSQL) |
| Frontend | React + Vite + Tailwind (JavaScript), in `frontend/` |
| Local infra | `docker-compose.yml` (PostgreSQL) |
| CI / deploy | GitHub Actions; Render (API), Neon (DB), Vercel (frontend) |

## Repository layout

```
backend/                     Spring Boot app, base package com.pollenz
  src/main/java/com/pollenz/
    common/                  error handling, shared types
    config/                  security, websocket, openapi config
    auth/  user/  community/  pool/  chat/
  src/main/resources/db/migration/   Flyway: V1__..., V2__...
frontend/                    React app
docs/SPEC.md
docker-compose.yml
```

Each feature package holds its own controller, service, repository, entity and DTOs.

## Conventions

- Package by feature. Controllers are thin; rules live in services.
- DTOs are Java `record`s. Never return a JPA entity from a controller. No Lombok.
- Money is `long` **paise** everywhere (API, Java, DB). Never floating point.
- IDs are `BIGINT` identity columns. Timestamps are `TIMESTAMPTZ` / `Instant`, UTC.
- Schema changes only through Flyway. `spring.jpa.hibernate.ddl-auto=validate`.
- Errors use one JSON shape from a single `@RestControllerAdvice`:
  `{ "status": 409, "error": "POOL_NOT_OPEN", "message": "..." }`
  400 validation, 401 unauthenticated, 403 not allowed, 404 not found, 409 state conflict.
  Generic codes: `VALIDATION_FAILED` (400), `UNAUTHORIZED` (401), `FORBIDDEN` (403),
  `INTERNAL_ERROR` (500). Feature codes so far: `EMAIL_TAKEN`, `INVALID_CREDENTIALS`,
  `COMMUNITY_NOT_FOUND`, `ALREADY_MEMBER`, `NOT_A_MEMBER`.
- Creating a resource returns 201 with its DTO. The current user id reaches a controller as
  `@AuthenticationPrincipal Long userId`.
- Config through environment variables with local defaults: `DB_URL`, `DB_USER`, `DB_PASSWORD`,
  `JWT_SECRET`, `CORS_ORIGIN`.
- Every service rule gets a test. Integration tests use Testcontainers, not H2.

## Data model

```
users          id, name, email (unique, lower-case), password_hash, upi_id (nullable), created_at
communities    id, name, invite_code (unique, 8 chars), created_by -> users, created_at
memberships    id, user_id, community_id, role (ADMIN | MEMBER), joined_at
               unique (user_id, community_id)
pools          id, community_id, host_id -> users, platform, title, target_amount_paise,
               closes_at, pickup_spot, status, fees_paise (nullable), version, created_at
pool_members   id, pool_id, user_id, share_paise (nullable), paid (bool), paid_at (nullable)
               unique (pool_id, user_id)
pool_items     id, pool_member_id, name, quantity, est_price_paise, final_price_paise (nullable)
messages       id, pool_id, sender_id -> users, body, created_at
```

`platform`: `BLINKIT | INSTAMART | ZEPTO | BIGBASKET | OTHER`.
`est_price_paise` and `final_price_paise` are line totals, not unit prices.
`pools.version` is a JPA `@Version` column for optimistic locking.

## Pool lifecycle

```
OPEN --lock (host)-----------------------> LOCKED
OPEN --closes_at passed, >= 2 members----> LOCKED     (scheduled job)
OPEN --closes_at passed, < 2 members-----> EXPIRED    (scheduled job)
OPEN | LOCKED --cancel (host)------------> CANCELLED
LOCKED --order (host)--------------------> ORDERED
ORDERED --deliver (host)-----------------> DELIVERED
ORDERED | DELIVERED --all members paid---> SETTLED    (automatic)
```

Rules:

- Only community members can see or join a community's pools.
- The host is added as a pool member when the pool is created.
- Join, leave and item changes are allowed only while `OPEN`. The host cannot leave; they cancel.
- A member edits only their own items.
- Any other transition is rejected with 409.
- Concurrent joins and transitions must not corrupt state: unique constraint plus `@Version`,
  inside one transaction.

## Bill split (the `order` action)

The host submits the final line total of every item and `feesPaise` (delivery + handling + any
other shared charge; 0 if the pool crossed the free-delivery minimum).

- `share = sum(member's final item totals) + feesPaise / memberCount` (integer division).
- The remainder paise of the fee division is added to the host's share.
- Members with no items still pay a fee share.
- The host's `paid` is set to true at once: they paid the app directly.
- Invariant: the sum of all shares equals the sum of all final item totals plus `feesPaise`.

Payment: the host's `upi_id` is exposed to pool members so the frontend can build a
`upi://pay?pa=...&am=...` link. The host marks each member paid. When all are paid the pool
becomes `SETTLED`.

## REST API

All routes are under `/api`. All need `Authorization: Bearer <jwt>` except register and login.

```
POST   /auth/register                      { name, email, password }        -> { token, user }
POST   /auth/login                         { email, password }              -> { token, user }
GET    /users/me
PATCH  /users/me                           { name?, upiId? }

POST   /communities                        { name }            creator becomes ADMIN
POST   /communities/join                   { inviteCode }
GET    /communities                        communities I belong to
GET    /communities/{id}                   detail with member list

POST   /communities/{id}/pools             { platform, title, targetAmountPaise, closesAt, pickupSpot }
GET    /communities/{id}/pools?status=     list; each has currentAmountPaise and memberCount
GET    /pools/{id}                         detail: members, items, shares, host upiId

POST   /pools/{id}/join
DELETE /pools/{id}/leave
POST   /pools/{id}/items                   { name, quantity, estPricePaise }
PUT    /pools/{id}/items/{itemId}
DELETE /pools/{id}/items/{itemId}

POST   /pools/{id}/lock
POST   /pools/{id}/cancel
POST   /pools/{id}/order                   { feesPaise, items: [{ itemId, finalPricePaise }] }
POST   /pools/{id}/deliver
POST   /pools/{id}/members/{memberId}/paid

GET    /pools/{id}/messages                chat history, oldest first
```

`currentAmountPaise` is the sum of `est_price_paise` over the pool's items.

## Realtime (STOMP over WebSocket)

- Endpoint `/ws`. The JWT is sent in the STOMP `CONNECT` header `Authorization`.
- Subscribe `/topic/pools/{id}`: receives `{ type, payload }` events. Types: `POOL_UPDATED`,
  `MEMBER_JOINED`, `MEMBER_LEFT`, `ITEMS_CHANGED`, `PAYMENT_UPDATED`, `CHAT_MESSAGE`.
- Send `/app/pools/{id}/chat` with `{ body }`: saved, then broadcast as `CHAT_MESSAGE`.
- Only pool members may subscribe or send.

## Phases

| # | Scope |
|---|---|
| 1 | Scaffold, docker-compose, Flyway V1 with the full schema, error handling, health check |
| 2 | Auth, users, communities |
| 3 | Pools, items, lifecycle, expiry job |
| 4 | Bill split, payment tracking, settlement |
| 5 | WebSocket events and chat |
| 6 | Frontend |
| 7 | CI, deploy, README |

Out of scope: payment gateway, quick-commerce API integration, email, ratings, admin moderation.
