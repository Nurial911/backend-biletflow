# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Backend for **BiletFlow**, an event-ticketing platform (university SWE project, SRS v0.3).
Spring Boot 4.1 / Java 21 / PostgreSQL 16, REST API secured with stateless JWT.
Implemented so far: auth, users, organizer profiles, venues, events (with poster images), ticket types, orders, tickets (payments through a built-in simulator or Halyk ePay's test environment), QR images, check-in staff + scanning, append-only audit log (written, no read API yet).
Also: admin event suspension + role management, organizer sales dashboard API, simulated paid-sales activation, PDF tickets, email + in-app notifications, email verification + password reset, promo codes + Campaign QR, support cases.
Not implemented yet: refunds, attendance analytics, activity timeline (audit read API), admin search + user suspension, calendar export, assigned seating, payouts.
`BiletFlow_SRS_Initial_Draft.pdf` (v0.3, in the user's Downloads) is the requirements source.

## Commands

```bash
# Run everything (Postgres + backend) — needs a filled-in .env in the project root
docker compose up --build

# Only Postgres in Docker (published on localhost:5432), backend run locally / from the IDE
docker compose up -d db         # then DB_URL=jdbc:postgresql://localhost:5432/<POSTGRES_DB>

# Build / run locally with the Maven wrapper (use mvnw.cmd on Windows)
./mvnw clean package            # build jar (runs tests)
./mvnw -DskipTests package      # build without tests
./mvnw spring-boot:run          # run locally; needs DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET env vars
./mvnw test                     # run tests (Docker must be running)
./mvnw test -Dtest=ClassName#method   # single test
```

- API: http://localhost:8080 — Swagger UI: http://localhost:8080/swagger-ui.html (use **Authorize** with the `accessToken` from `/api/auth/login`).
- `postman/BiletFlow.postman_collection.json` walks the whole API in order. It needs the seeded admin and scanner (`SEED_DATA=true`), registers its own organizer and attendee on each run, and its email folder needs Mailpit. `postman/halyk-test-checkout.html` opens Halyk's payment page from a `payment-session` response, and is the smallest working example of the frontend's job.
- Tests extend `ApiIntegrationTest` (helpers, over `IntegrationTestBase`): the full app on a random port against a throwaway Testcontainers Postgres, no env vars needed. `CoreFlowIntegrationTest` drives order → payment → ticket → QR → scan over HTTP, including the concurrency cases. Each test creates its own users and event. Use real Postgres, not H2: inventory, payment, promo limits and scan rely on row locks. `ticketType(...)` switches paid sales on with SQL; use `ticketTypeWithoutActivation` to test the gate. Email goes to `CapturingMailGateway`; call `deliverQueuedEmails()` (the background dispatcher is off in tests). `SmtpDeliveryIntegrationTest` uses a real Mailpit container. `HalykPaymentIntegrationTest` uses `FakeHalykServer`, whose responses were captured from Halyk's real test environment.
- ZXing's `PURE_BARCODE` mode is unreliable on non-integer pixel grids: render PDFs at 300 dpi or more when decoding them in tests.
- When running outside Docker, add `-Duser.timezone=Asia/Almaty` (Docker sets it) so `@Future` checks and `LocalDateTime.now()` match user input.

## Configuration

All config comes from env vars (see `.env`, `docker-compose.yml`, `src/main/resources/application.yaml`):
`POSTGRES_DB/USER/PASSWORD` (compose), `DB_URL/DB_USERNAME/DB_PASSWORD`, `JWT_SECRET` (≥64 chars, HS512),
`JWT_EXPIRATION_MS` (default 1 day), `CORS_ALLOWED_ORIGINS`, `DDL_AUTO` (default `update`), `SEED_DATA`,
`UPLOADS_DIR` (poster files; default `./uploads`, `/app/uploads` on the `uploads` Docker volume in compose),
`BACKEND_BIND` (compose only; address port 8080 is published on, default `127.0.0.1`, use `0.0.0.0` to reach it from a phone on the LAN),
`APP_FRONTEND_URL` (base of links in emails and Campaign QR codes; must be the https address on a server), `ACTIVATION_FEE_KZT` (default 5000, a placeholder),
`MAIL_ENABLED` (default `false`: nothing is sent, only the subject is logged), `MAIL_HOST/PORT/USERNAME/PASSWORD/FROM`, `MAIL_SMTP_AUTH`, `MAIL_STARTTLS`.
For a local email demo set `COMPOSE_PROFILES=mail` and `MAIL_ENABLED=true`, then read messages in Mailpit at http://localhost:8025.
`PAYMENT_PROVIDER` (`simulated` by default, or `halyk`), `HALYK_CLIENT_ID/CLIENT_SECRET/TERMINAL_ID` (required for `halyk`; Halyk publishes a shared test merchant at https://epayment.kz/en-US/docs/Test-credentials), `HALYK_CALLBACK_BASE_URL` (public https address of this backend), `HALYK_TEST_MODE` (default `true`), `HALYK_OAUTH_URL/API_URL/SCRIPT_URL` (default to Halyk's test hosts; production values are `https://epay-oauth.homebank.kz/oauth2/token`, `https://epay-api.homebank.kz`, `https://epay.homebank.kz/payform/payment-api.js`).

- `.env` holds real secrets — never commit it, never print its values.
- Schema is managed by Hibernate `ddl-auto=update` (no Flyway/Liquibase yet). Changing an entity changes the DB schema on next start; renames/removals are not migrated.
- Hibernate adds a CHECK constraint listing the values of every `@Enumerated(STRING)` column, and `ddl-auto=update` never widens it. A new value in an existing enum (e.g. `TicketStatus.REFUNDED`) fails on insert until `<table>_<column>_check` is dropped or replaced on existing databases.

**YAML nesting matters:** `jwt` lives under `spring:` (read by `JwtConfig` as `spring.jwt.*`), while `app`, `server` and
`springdoc` must stay at the top level (`app.seed.enabled`, `app.cors.allowed-origins` are read by `DataSeeder` / `SecurityConfig`).
Indenting them under `spring:` silently disables `SEED_DATA` and `CORS_ALLOWED_ORIGINS`.

## Architecture

Package root: `kz.edu.biletflow.backend`. Classic layered design:

```
controllers/        interfaces holding ALL Spring annotations (@RequestMapping, @PreAuthorize, @Valid, ...)
controllers/impl/   @RestController classes implementing them — no annotations on parameters, just delegate
services/           service interfaces;  services/impl/  @Service implementations with business rules
repositories/       Spring Data JPA (custom JPQL for visibility queries)
entities/           JPA entities + enums (Lombok @Getter/@Setter, not @Data)
dtos/               request/response DTOs (Lombok @Data, jakarta.validation annotations)
mappers/            MapStruct mappers (componentModel = "spring")
security/           JWT filter/service, UserPrincipal, auth controller + service (login/register)
configs/            SecurityConfig, JwtConfig, OpenApiConfig, DataSeeder
exception/          custom exceptions + GlobalExceptionHandler (@RestControllerAdvice)
```

### Conventions to follow when adding features

- **New endpoint** → add the method with annotations to the controller *interface*, implement in `impl/`, put logic in the service impl. Controllers never touch repositories.
- **Current user** → `@AuthenticationPrincipal UserPrincipal currentUser` (has `getId()`, `getEmail()`, `hasRole("X")`). It is `null` on anonymous requests to public GET endpoints — null-check it.
- **Roles**: `ATTENDEE`, `ORGANIZER`, `EVENT_ADMIN`, `PLATFORM_ADMIN` (`User.Role`). The JWT carries `role` and `id` claims; authorities are `ROLE_<role>`. Use `@PreAuthorize("hasRole('ORGANIZER')")` on the interface.
  Role is read from the token, not the DB — a role change requires issuing a new token (see `OrganizerProfileServiceImpl.createProfile`, which returns one).
- **Public routes** are set in `SecurityConfig`: `/api/auth/**`, Swagger, and **all GET** under `/api/events/**` and `/api/venues/**`. Anything new under those GET paths is public automatically — enforce visibility in the service.
- **Errors**: throw the custom exceptions, never return error bodies by hand:
  `ResourceNotFoundException` → 404, `ForbiddenOperationException` → 403, `DuplicateResourceException` → 409,
  `BusinessRuleViolationException` → 400 (domain rules), `BadCredentialsException` → 401. Response shape is `ErrorResponse {status, message, timestamp}`. Unknown exceptions → generic 500 without leaking messages.
- **Ownership checks** live in services (`getOwnedEvent`, `getOwnedTicketType`) and throw 403.
- **Hidden-existence rule**: if a requester may not see an event (not PUBLISHED, or PRIVATE, and not owner/admin), respond **404, not 403**, so its existence isn't revealed.
- **Mapping**: use MapStruct. Partial updates (PUT with optional fields) use `@BeanMapping(nullValuePropertyMappingStrategy = IGNORE)` — null means "leave unchanged". Explicitly `ignore` id, relations, status/hidden and timestamps; relations are resolved in the service.
- **Status changes** never go through plain update DTOs — they have dedicated endpoints (`/publish`, `/unpublish`, `/cancel`, `/hide`, `/unhide`).
- Create endpoints return **201** with a `Location` header (`ServletUriComponentsBuilder`). Lists are paged (`Pageable` → `Page<Dto>`), except ticket types (plain `List`).
- Mark write methods `@Transactional`, reads `@Transactional(readOnly = true)`. `open-in-view` is off, so touch lazy relations inside the transaction.
- **Audit**: organizer, staff, admin and payment actions call `AuditService.record(...)` inside the action's own transaction (it is `MANDATORY`), so they commit or roll back together. Add new actions to `AuditAction`. `AuditLogRepository` only exposes `save`; entries are never edited or deleted. Don't put ticket codes in descriptions.
- **Notifications**: tell a user about something with `NotificationService.notifyUser(s)` inside the action's transaction (`MANDATORY`); add new kinds to `NotificationType`. The row is both the in-app notification and the queue entry for its email: `NotificationEmailDispatcher` sends it within seconds and retries for about 30 minutes, so a mail outage can't fail or lose anything. Never put sign-in or reset links in a notification: `AccountEmailServiceImpl` mails those directly and stores only their hash.
- Times are `LocalDateTime` (Asia/Almaty, no zone stored). Money is `BigDecimal(12,2)` in KZT; price 0 = free.

## Domain rules (implemented)

- **Users**: register always creates `ATTENDEE`. Soft delete (`@SQLDelete` sets `is_deleted`, `@SQLRestriction` hides deleted rows). Admin endpoints under `/api/users`, self-service under `/api/users/me`.
  Registering emails a confirmation link; `User.verificationStatus` records it, and changing the address clears it. Nothing is blocked for an unverified address yet. `/api/auth/verify-email`, `/resend-verification`, `/forgot-password`, `/reset-password`: links are single-use (24 h / 1 h), and `resend` and `forgot` answer 204 for unknown addresses and send at most one email a minute. A password reset doesn't revoke JWTs already issued.
  `PUT /api/users/{id}/role {role}` (admin) is the only way to grant `EVENT_ADMIN`/`PLATFORM_ADMIN`. Rules: not on yourself; `ORGANIZER` only if the user has an organizer profile; an organizer who owns events can't be moved off ORGANIZER; leaving `EVENT_ADMIN` deletes the user's staff assignments. The user must log in again for the new role to take effect (old token keeps the old role until it expires).
- **Organizer profile**: an ATTENDEE calls `POST /api/organizers/profile` → becomes ORGANIZER, gets a new token. One profile per user.
  Simulated identity check: `POST /api/organizers/profile/verification` → PENDING, a PLATFORM_ADMIN decides via `GET /api/organizers/verification-requests` and `POST /api/organizers/{profileId}/verification {approved, note}`; REJECTED can ask again. `PUT /api/organizers/profile/payout-account {bankName, accountHolder, iban}` is simulated too and keeps only the last 4 IBAN characters.
- **Paid-sales activation** (`/api/events/{id}/activation`, per event, all simulated): paid tickets can't be ordered or paid until `Event.paidSalesActive` is true; free tickets ignore it. Checklist (`GET`, owner/admin): paid ticket type, verified organizer, payout account, `POST /pay-fee` (`app.payments.activation-fee-kzt`, reference `SIM-…`), `POST /accept-terms`; then `POST /activate` (400 naming what's missing). `POST /suspend {reason}` / `/unsuspend` are PLATFORM_ADMIN: new paid orders and payments stop, free tickets and sold tickets keep working, unpaid orders simply run out. `GET /api/admin/paid-sales-activations?status=`. Not copied by `duplicate`. Only `PaidSalesActivationServiceImpl` changes the flag.
- **Venues**: created by ORGANIZER or PLATFORM_ADMIN; listing is public. `capacity` is required on create (column stays nullable for legacy rows; null = no limit).
  Event capacity must be ≤ venue capacity (checked on event create/update); an event created without capacity inherits the venue's.
- **Events**: status `DRAFT → PUBLISHED ⇄ DRAFT`, any → `CANCELLED` (terminal; cancelled events can't be edited/published). Visibility `PUBLIC` (listed), `UNLISTED` (open by link, not in lists), `PRIVATE` (owner/admin only).
  Timing: registration opening < closing ≤ start < end (all optional except start). `duplicate` copies as a new DRAFT (ticket types not copied).
  Public list = PUBLISHED + PUBLIC, plus the requester's own events; PLATFORM_ADMIN sees everything.
- **Event posters**: `POST /api/events/{id}/poster` (multipart, part `file`; organizer only, not on CANCELLED) replaces the poster, `DELETE` removes it, `GET` serves it (same visibility as the event → 404).
  JPEG/PNG/WebP up to 5 MB (413 above), type detected from magic bytes (`PosterFormat`), no resizing. Files live in `UPLOADS_DIR` as `<uuid>.<ext>` (`PosterStorage`); `Event.posterKey` holds the name.
  `EventResponse.posterUrl` is absolute with `?v=<key>`, so the GET is cached for 30 days. File deletes run after commit; new files are removed on rollback. `duplicate` copies the poster file.
- **Ticket types**: belong to an event; only its organizer can create/update/hide. Sum of quantities (hidden included) must not exceed event capacity. Sales window must end before event start. Hidden types are visible only to owner/admin. `sold` = PAID + unexpired PENDING order items; price can't change and quantity can't drop below `sold` once any are sold/reserved.
- **Orders**: `POST /api/orders` (any authenticated user) reserves inventory for `app.orders.hold-minutes` (default 15) under a row lock on each ticket type (`findByIdForUpdate`). Free orders (total 0) are PAID immediately; otherwise, in the default simulated mode, `POST /api/orders/{id}/pay` (no provider, no money moves) → PAID and issues one `Ticket` (UUID `code`, for the QR) per unit. Each attempt writes a `Payment` (`simulated=true`, reference `SIM-<uuid>`, listed by `GET /api/orders/{id}/payments`). Optional body `{"simulatedOutcome":"DECLINED"}` answers 402, keeps a FAILED payment (`noRollbackFor`), issues nothing and leaves the order PENDING for a retry. `app.payments.processing-fee-percent` (default 2.5, a placeholder) is deducted from the organizer's payout, not added to the attendee's total. `attendeeName` on the order is optional, defaults to `User.fullName` then email, and is copied to each ticket. Pay and cancel lock the order row (`findByIdForUpdate`) so concurrent requests can't issue tickets twice. `PENDING → CANCELLED` by owner, `PENDING → EXPIRED` by `OrderExpiryScheduler` (every minute). Others' orders/tickets return 404. Endpoints: `/api/orders`, `/api/tickets`; `GET /api/tickets/{id}/qr` returns a PNG of the ticket `code` (owner/admin).
- **Halyk ePay** (`PAYMENT_PROVIDER=halyk`; `PaymentSessionServiceImpl`, `HalykClient`, table `payment_sessions`): `POST /api/orders/{id}/pay` is refused, because it would let buyers mark their own orders paid. `POST /api/orders/{id}/payment-session` returns `{provider, simulated, scriptUrl, payment}`; the browser loads `scriptUrl` and calls `halyk.pay(payment)`, so card numbers never reach this server (in simulated mode it returns `provider: SIMULATED` and the frontend calls `/pay`). The shopper returns to `APP_FRONTEND_URL/orders/{id}/payment-result?status=success|failure`, and that page calls `POST /api/orders/{id}/payment-check`.
  The bank's status API is the only source of truth. `POST /api/payments/halyk/callback` (public) just prompts a check, and `PaymentSessionScheduler` checks every 15 s, which is what completes payments locally where the bank can't reach the callback. The bank first holds the money (`AUTH`); we re-check the order under its row lock, capture (`CHARGE`), and only then issue tickets. A session stays `OPEN` through declines, since the bank's page allows 3 attempts, and each decline writes a FAILED `Payment`. Money we can't accept (order cancelled or expired, wrong amount, paying twice from two tabs, paid sales suspended) is released: `cancel` if held, `refund` if taken, with a FAILED `Payment` and a notification. Opening a session extends the reservation to the page's 20 minutes, capped at hold + 20 minutes from order creation. The activation fee is still simulated.
  Halyk's docs are partly out of date. Confirmed by real test payments: `testoauth.homebank.kz` (404) and `testepay.homebank.kz` (timeout) are dead; tokens last 20 minutes (`expires_in` is a string), not 2 hours; the page needs an email or phone, so we pre-fill the buyer's email. Not yet confirmed: delivery of the real result call, which needs a public address. `Payment.simulated` stays true while `HALYK_TEST_MODE=true`, as SRS 4.6 requires.
- **PDF tickets**: `GET /api/tickets/{id}/pdf` (owner/admin; 400 for a CANCELLED ticket). A4, one ticket per page, black on white, vector QR at a fixed position (`TicketPdfGenerator`), embedded Liberation Sans for Kazakh and Russian. The QR holds the same `code` as the digital ticket, so there is only ever one admission. Confirmation emails attach all of an order's tickets as one PDF.
- **Promo codes** (`PromoCampaignServiceImpl`, SRS 4.14): one server-generated code and one opaque token per campaign. `/api/events/{id}/campaigns` (POST, GET, `/{cid}`, `/{cid}/disable`, `/{cid}/enable`, `/{cid}/qr`); reads are owner/admin, anyone else 404. `GET /api/events/{id}/promo?code=` or `?token=` is public and validates for the event page. `POST /api/orders/quote` takes the order body and reserves nothing.
  `promoCode` on the order: discount is calculated only by the server, percent or fixed KZT (fixed = per order), applied to the campaign's ticket types (none listed = all) and capped at their subtotal. A rejected code fails the order with a clear 400. The redemption limit counts PAID + unexpired PENDING orders under a row lock on the campaign, taken before the ticket-type locks. The QR is `APP_FRONTEND_URL/events/{id}?promo=<token>`, indigo with a frame so it can't pass for an admission QR, and `scan` answers INVALID for it. `Order.totalAmount` is after discount; processing fees use it.
- **Support cases** (`/api/support/cases`, SRS 4.13): `POST` (`type` ATTENDEE by default or PLATFORM, `category`, `subject`, `message`, and `ticketId`/`orderId`/`eventId`), `GET ?status=&escalated=`, `GET /{id}` (case + thread; clients poll), `POST /{id}/messages`, `/assign {email}`, `/status {status}`, `/escalate`.
  ATTENDEE cases: requester, the event's organizer and its assigned staff, and platform admins. PLATFORM cases: the organizer and platform admins. Everyone else gets 404; a requester trying a staff action gets 403. Context is resolved by the server from what the requester owns. A requester's reply reopens a RESOLVED case; staff must change the status first. Staff appear as a team name, and assignee details are hidden from the requester.
- **Event suspension** (moderation, PLATFORM_ADMIN only): `POST /api/events/{id}/suspend` (optional body `{reason}`, ≤500 chars) / `/unsuspend`. A separate `suspended` flag, not a status, so unsuspend restores the previous state; CANCELLED events can't be suspended.
  A suspended event is invisible to everyone but owner/admin (404, also hidden from lists and its ticket types), new orders and payments are refused, and PENDING orders → CANCELLED. PAID orders/tickets stay valid and check-in keeps working.
  The organizer can still see it (with `suspensionReason`) and cancel it, but every other change (edit, publish/unpublish, duplicate, poster, ticket types) is a 400.
- **Organizer sales** (`/api/organizer`, `SalesService`): `GET /sales/events[?eventIds=1,2]` (ORGANIZER; own events + `totals`), `GET /events/{id}/sales` (adds `salesByDay`), `GET /events/{id}/orders?status=&q=` (paged, `q` = part of buyer email). Per-event endpoints also allow PLATFORM_ADMIN; anyone else (incl. foreign ids in `eventIds`) gets 404.
  Counting: sold/revenue = PAID orders only (free tickets count, revenue 0; revenue uses `OrderItem.unitPrice` snapshots, so it is gross); `discounts` = promo discounts on PAID orders, `netRevenue` = revenue minus discounts; pending = unexpired PENDING (reserved, not revenue); cancelled/expired counted separately; `processingFees` = SUCCEEDED payments, `estimatedPayout` = net revenue minus fees. No refunds yet — subtract them here once added. All figures come from grouped queries (`OrderItemRepository.aggregateSalesByTicketType`, `OrderRepository.countOrdersByEvent`), not per-order loops.
  Note: `TicketTypeResponse.sold` (public endpoint) means PAID + reserved; the sales API splits them into `sold` and `reserved`.
- **Event cancel** also flips the event's VALID tickets → CANCELLED and PENDING orders → CANCELLED (bulk updates). PAID orders stay PAID until refunds exist.
- **Check-in staff**: the organizer assigns `EVENT_ADMIN` users by email via `/api/events/{eventId}/staff` (POST/GET/DELETE). The GET is public at the filter level, so the service returns 404 to anyone but owner/admin.
- **Check-in** (`/api/check-in/**`, roles EVENT_ADMIN/ORGANIZER/PLATFORM_ADMIN; allowed for assigned staff, the event's organizer, admins — others get 404): `GET /events` (assigned events), `POST /events/{id}/scan {code}`, `POST /events/{id}/tickets/{ticketId}/undo`, `GET /events/{id}/attendees?q=` (part of email or name, or exact code), `GET /events/{id}/stats`.
  A scan always answers **200** with `CheckInResult` (`CHECKED_IN, ALREADY_USED, CANCELLED, WRONG_EVENT, INVALID`) — the scan result is data for the app, not an error. Tickets are row-locked during scan/undo; every check-in and undo is appended to `check_in_records`.

## Demo data

`DataSeeder` (when `SEED_DATA=true` → `app.seed.enabled`; default `false`, set `SEED_DATA=true` in a local `.env` to opt in) creates users `admin@`, `attendee@`, `scanner@`, `organizer1@`, `organizer2@biletflow.kz`, all with password `Password123!`, plus venues, events in every status/visibility, and ticket types. "Spring Jazz Night" (organizer1) has paid sales active and promo code `JAZZ2026` (20%); "Kazakh Folk Festival" (organizer2) has the whole activation checklist still to do. Skips if the admin already exists. Never enable in production.

## Housekeeping notes

- `HELP.md` is the unmodified Spring Initializr stub; there is no real README.
- `backend.zip` and `projv1.zip` sit inside `src/main/java/...` — old archives, not source. Don't edit or rely on them.
- Some code comments are in Russian; that's fine.
- Commit messages loosely follow Conventional Commits (`feat(scope): ...`, `refactor: ...`).
