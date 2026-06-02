# AGENTS.md

## Project snapshot
- Kotlin 2.1 + Spring Boot 3.5 + Java 21 backend for Valora Cloud.
- The API is mounted under `/api` (`server.servlet.context-path`), and the default profile is `dev`.
- OpenAPI is exposed at `/api/docs` and JSON at `/api/docs-json`.

## Architecture to keep in mind
- This codebase is organized by domain feature under `src/main/kotlin/com/valoracloud/api/<feature>/`.
- Keep controllers thin: `AuthController`, `ServersController`, and `BillingController` mostly forward to service classes.
- Shared persistence is centralized: entities live in `entity/AllEntities.kt`, and Spring Data repositories live in `config/Repositories.kt`.
- Cross-cutting infra is in `config/` (`SecurityConfig`, `WebMvcConfig`, `OpenApiConfig`, `RedisConfig`, `JpaConfig`).

## Request/response conventions
- Controllers usually return DTOs or simple maps; they rarely build `ResponseEntity` unless an endpoint needs special handling.
- Validation is done with `jakarta.validation` annotations on request DTOs.
- Use `@CurrentUser` for the authenticated user id; do not read `SecurityContextHolder` directly in controllers.
- The custom argument resolver is wired in `WebMvcConfig`.

## Security and public routes
- `SecurityConfig` permits `/auth/**`, `/docs/**`, `/docs-json/**`, `/health/**`, `/billing/webhook`, `/billing/crypto-webhook`, plus GET access to `/plans/**` and `/domains/tld-pricing/**`.
- Admin-only routes are under `/admin/**`.
- JWT auth is handled by `auth/security/JwtAuthFilter.kt` and `JwtProvider.kt`.

## Data and persistence patterns
- The database schema uses PostgreSQL enums and JSON columns; see the custom Hibernate `UserType` implementations in `AllEntities.kt`.
- `User` uses soft-delete semantics (`deletedAt` + `@SQLRestriction("\"deletedAt\" IS NULL")`).
- Audit timestamps come from Spring Data JPA auditing (`JpaConfig` + `BaseEntity`).

## External integrations
- `contabo/ContaboService.kt` is the main API client for infrastructure provisioning and uses `WebClient` plus Redis token caching.
- `billing/service/BillingService.kt` handles Stripe webhooks and SHKeeper crypto callbacks, then dispatches provisioning work.
- `provisioning/processor/ProvisioningProcessor.kt` is the synchronous replacement for the old queue worker; it creates resources, performs SSH post-provisioning, and updates order/server state.
- `notifications/service/NotificationsService.kt` sends transactional emails and records every send in `EmailLog`.

## Developer workflows
- From PowerShell, run the app with `./gradlew.bat bootRun`.
- Run tests with `./gradlew.bat test` and build a jar with `./gradlew.bat clean bootJar`.
- There are currently no checked-in tests under `src/test`, but the build already includes JUnit 5, MockK, Spring Security test support, and Testcontainers for PostgreSQL.

## Repo-specific cautions
- `src/main/resources/application-dev.yml` and `.env` are gitignored local-development files; do not commit secrets there.
- For webhook endpoints, preserve the raw request body when signature verification depends on exact bytes (see `BillingController`).
- When adding a new domain area, follow the existing pattern: `controller/`, `service/`, and optional `dto/` or `security/` subpackages under the feature folder.
- Prefer updating the existing centralized entity/repository files unless a new file clearly improves cohesion.
