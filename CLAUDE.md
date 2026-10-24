# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Backend for **BiletFlow**, an event-ticketing platform (university SWE project, SRS v0.3).
Spring Boot 4.1 / Java 21 / PostgreSQL 16, REST API secured with stateless JWT.
Implemented so far: auth, users, organizer profiles, venues, events, ticket types.
Not implemented yet: orders/purchases, tickets/QR, check-in (EVENT_ADMIN role), payouts.

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
./mvnw test                     # run tests
./mvnw test -Dtest=ClassName#method   # single test
```

- API: http://localhost:8080 — Swagger UI: http://localhost:8080/swagger-ui.html (use **Authorize** with the `accessToken` from `/api/auth/login`).
- Only test today is `BackendApplicationTests.contextLoads()`, a full `@SpringBootTest` — it needs a reachable Postgres and `JWT_SECRET`, so it fails without the env vars.
- When running outside Docker, add `-Duser.timezone=Asia/Almaty` (Docker sets it) so `@Future` checks and `LocalDateTime.now()` match user input.

## Configuration

All config comes from env vars (see `.env`, `docker-compose.yml`, `src/main/resources/application.yaml`):
`POSTGRES_DB/USER/PASSWORD` (compose), `DB_URL/DB_USERNAME/DB_PASSWORD`, `JWT_SECRET` (≥64 chars, HS512),
`JWT_EXPIRATION_MS` (default 1 day), `CORS_ALLOWED_ORIGINS`, `DDL_AUTO` (default `update`), `SEED_DATA`.

- `.env` holds real secrets — never commit it, never print its values.
- Schema is managed by Hibernate `ddl-auto=update` (no Flyway/Liquibase yet). Changing an entity changes the DB schema on next start; renames/removals are not migrated.

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
- Times are `LocalDateTime` (Asia/Almaty, no zone stored). Money is `BigDecimal(12,2)` in KZT; price 0 = free.

## Domain rules (implemented)

- **Users**: register always creates `ATTENDEE`. Soft delete (`@SQLDelete` sets `is_deleted`, `@SQLRestriction` hides deleted rows). Admin endpoints under `/api/users`, self-service under `/api/users/me`.
- **Organizer profile**: an ATTENDEE calls `POST /api/organizers/profile` → becomes ORGANIZER, gets a new token. One profile per user. Verification status enum exists but no admin verification flow yet.
- **Venues**: created by ORGANIZER or PLATFORM_ADMIN; listing is public.
- **Events**: status `DRAFT → PUBLISHED ⇄ DRAFT`, any → `CANCELLED` (terminal; cancelled events can't be edited/published). Visibility `PUBLIC` (listed), `UNLISTED` (open by link, not in lists), `PRIVATE` (owner/admin only).
  Timing: registration opening < closing ≤ start < end (all optional except start). `duplicate` copies as a new DRAFT (ticket types not copied).
  Public list = PUBLISHED + PUBLIC, plus the requester's own events; PLATFORM_ADMIN sees everything.
- **Ticket types**: belong to an event; only its organizer can create/update/hide. Sum of quantities (hidden included) must not exceed event capacity. Sales window must end before event start. Hidden types are visible only to owner/admin. "Sold" count will come from orders (TODO in `TicketTypeServiceImpl`).

## Demo data

`DataSeeder` (when `SEED_DATA=true` → `app.seed.enabled`; default `true` in docker-compose, `false` otherwise) creates users `admin@`, `attendee@`, `scanner@`, `organizer1@`, `organizer2@biletflow.kz`, all with password `Password123!`, plus venues, events in every status/visibility, and ticket types. Skips if the admin already exists. Never enable in production.

## Housekeeping notes

- `HELP.md` is the unmodified Spring Initializr stub; there is no real README.
- `backend.zip` and `projv1.zip` sit inside `src/main/java/...` — old archives, not source. Don't edit or rely on them.
- Some code comments are in Russian; that's fine.
- Commit messages loosely follow Conventional Commits (`feat(scope): ...`, `refactor: ...`).
