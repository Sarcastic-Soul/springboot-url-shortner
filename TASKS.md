# Enterprise URL Shortener — Versioned Delivery Plan

## v0.1 — Foundation (Deployable Baseline)
**Goal:** A running app with clean architecture, observability, and safe defaults.

- [X] Initialize backend (Spring Boot 3.x, Java 21, Maven) and frontend (React + Vite + Mantine).
- [ ] Add core dependencies only: Spring Security, Data JPA, Validation, Flyway, Actuator, Micrometer, MapStruct, Lombok.
- [ ] Provision PostgreSQL + Valkey (single instance) with Docker Compose.
- [ ] Set up package boundaries (`controller -> service -> repository`) and DTO-first API contracts.
- [ ] Add Flyway baseline schema and indexes (`short_code`, `user_id`, `expires_at`, `created_at`).
- [ ] Add global exception handling with RFC7807 Problem Details.
- [ ] Add structured logs + correlation/request IDs (`X-Request-ID` in responses).
- [ ] Add health checks (Actuator) and first GitHub Actions pipeline (build + tests).
- [ ] Add ArchUnit rules to enforce architecture boundaries and prevent layer violations.

## v0.2 — Authentication and Access Control
**Goal:** Secure, stateless identity with production-grade auth flows.

- [ ] Implement signup/login with BCrypt.
- [ ] Implement JWT access + refresh tokens with refresh-token rotation.
- [ ] Add email verification + password reset (JavaMailSender, async send).
- [ ] Add OAuth login for GitHub + Google only.
- [ ] Add RBAC baseline (`USER`, `ADMIN`) and endpoint authorization policies.
- [ ] Add session/device listing + revoke token sessions.
- [ ] Add strict validation for auth inputs and password policy.
- [ ] Add security headers, CORS allowlist, HTTPS enforcement outside local.
- [ ] Add auth integration tests with Testcontainers.

## v0.3 — Core URL Shortener
**Goal:** End-to-end URL lifecycle with fast redirects.

- [ ] Implement URL create/read/update/disable/archive/restore.
- [ ] Use soft delete for URL records (no hard delete path by default).
- [ ] Add optimistic locking (`@Version`) on mutable aggregates.
- [ ] Implement short code generator (Base62 default; pluggable strategy interface).
- [ ] Add custom alias flow with reserved words + collision checks.
- [ ] Add expiration policies (datetime, max clicks, manual disable).
- [ ] Add optional password-protected URLs.
- [ ] Implement redirect path: Valkey lookup -> PostgreSQL fallback -> fast `302`.
- [ ] Publish domain events (e.g., `UrlCreatedEvent`) instead of synchronous side effects.

## v0.4 — Caching, Rate Limiting, and API Quality
**Goal:** Improve latency and abuse resistance without operational overkill.

- [ ] Add cache for short-code resolution with TTL + targeted invalidation.
- [ ] Add Redis-backed rate limiting (per IP, user, API key).
- [ ] Add API key lifecycle (create/revoke/rotate, hashed-at-rest key storage).
- [ ] Add pagination/search/filter/sort for URL listing endpoints.
- [ ] Add ETag + conditional GET support (`If-None-Match`, `304 Not Modified`) on read-heavy endpoints.
- [ ] Keep APIs DTO-based (no entity exposure) with MapStruct mappings.

## v0.5 — Analytics (Async + Queryable)
**Goal:** Analytics without slowing down redirect SLA.

- [ ] Capture click analytics asynchronously via Spring events + async handlers.
- [ ] Store privacy-safe click fields (IP hash, user-agent derived metadata, referrer, UTM).
- [ ] Build daily/weekly/monthly aggregation jobs (scheduler).
- [ ] Add analytics APIs with cursor-based pagination.
- [ ] Add dashboard-ready summary endpoints (top links, geo/device/browser, peak hours).
- [ ] Add analytics retention + cleanup scheduled tasks.

## v0.6 — Teams and Enterprise Collaboration
**Goal:** Multi-user workflows and governance.

- [ ] Implement teams with roles (`OWNER`, `ADMIN`, `EDITOR`, `VIEWER`).
- [ ] Add team invitation, membership management, and permission checks.
- [ ] Add shared links and shared analytics scope.
- [ ] Add audit logs for security-sensitive and team actions.
- [ ] Expand API key policies with team/user scoping and per-key limits.
- [ ] Publish OpenAPI docs with realistic request/response examples.

## v0.7 — Feature Completeness + UX
**Goal:** Production-credible feature set for demos and interviews.

- [ ] Implement QR generation (PNG/SVG, regenerate/download).
- [ ] Add folders, tags, favorites, and bulk URL actions.
- [ ] Build frontend dashboard (cards, charts, activity feed, recent links).
- [ ] Add resilient frontend auth handling (token refresh, guarded routes, retries).
- [ ] Add validation in frontend forms (React Hook Form + Zod).

## v0.8 — Production Hardening (Single-Engineer Practical)
**Goal:** Strong operational posture without distributed-system overhead.

- [ ] Containerize services with production-ready Dockerfiles.
- [ ] Extend CI: unit, integration, security scans, build artifacts.
- [ ] Add metrics export (Micrometer + Prometheus) and Grafana dashboards.
- [ ] Add reverse proxy config (Nginx) for TLS termination and routing.
- [ ] Validate horizontal scaling at app tier by running multiple stateless API instances.
- [ ] Run load tests for redirect + core APIs and tune DB/connection pool/cache settings.

## v1.0 — Release Readiness
**Goal:** A maintainable, secure, interview-ready enterprise-style project.

- [ ] Run full regression (unit/integration/security + core load test baseline).
- [ ] Complete threat model review (auth, open redirect abuse, API key misuse, SSRF vectors).
- [ ] Verify auditability, request tracing, and incident-debugging workflow.
- [ ] Freeze API contracts and publish release documentation.
- [ ] Tag and release `v1.0`.

---

## Cross-Version Non-Negotiables

- [ ] Keep backend stateless to support horizontal scaling (no sticky-session dependency).
- [ ] Keep redirect path minimal; analytics and side effects must stay async.
- [ ] Enforce validation and authorization on every external endpoint.
- [ ] Every schema change includes Flyway migration + index review.
- [ ] Every milestone remains deployable, not just partially built.
- [ ] Every milestone ships with meaningful tests (JUnit, Mockito, Testcontainers, ArchUnit where relevant).

## Dependency Guardrail

> Add a dependency only when it removes substantial custom code and solves a concrete problem.
