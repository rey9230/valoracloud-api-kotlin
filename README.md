# Valora Cloud API — Kotlin Spring Boot

## Stack
- Kotlin 2.1 + Spring Boot 3.5 + Java 21
- Spring Data JPA + Hibernate + Flyway
- PostgreSQL (Supabase)
- Redis (cache + queues)
- Spring Security + JWT
- Stripe + SHKeeper (crypto)
- Contabo API
- Thymeleaf (emails)

## Quick Start

```bash
# 1. Start PostgreSQL and Redis (or use Supabase/Upstash)
docker compose up -d postgres redis

# 2. Configure environment
cp .env.example .env
# Edit .env with your credentials

# 3. Build & Run
./gradlew bootRun

# Dev with hot reload
./gradlew bootRun --args='--spring.profiles.active=dev'
```

## Environment Variables

See `.env.example` for all required variables. Key services:

| Service | Env Prefix | Description |
|---------|-----------|-------------|
| Auth | `JWT_*` | JWT secrets and expiry |
| Database | `DATABASE_URL` | PostgreSQL connection |
| Redis | `REDIS_*` | Cache and queue |
| Contabo | `CONTABO_*` | Infrastructure provider API |
| Stripe | `STRIPE_*` | Payment processing |
| SHKeeper | `SHKEEPER_*` | Crypto payments |
| SMTP | `SMTP_*` | Email delivery |

## API Docs

Once running: `http://localhost:8080/api/docs`

## Project Structure

```
src/main/kotlin/com/valoracloud/api/
├── ValoracloudApiApplication.kt
├── config/           # Security, JPA, Redis, OpenAPI, CORS configs
├── entity/           # JPA entities
├── common/           # Exceptions, DTOs, utilities
├── auth/             # Authentication (register, login, JWT)
├── users/            # User management
├── plans/            # Hosting plans
├── orders/           # Orders lifecycle
├── servers/          # Server management
├── provisioning/     # Async provisioning
├── billing/          # Stripe + crypto billing
├── invoices/         # Invoice management
├── contabo/          # Contabo API client
├── domains/          # Domain registration & DNS
├── objectstorage/    # S3-compatible storage
├── firewalls/        # Firewall management
├── privatenetworks/  # VLAN management
├── vips/             # Virtual IPs
├── snapshots/        # Server snapshots
├── images/           # Custom images
├── secrets/          # SSH keys & passwords
├── tags/             # Resource tags
├── monitoring/       # Uptime monitoring & alerts
├── notifications/    # Email notifications
├── admin/            # Admin panel API
├── shkeeper/         # Crypto payment integration
├── facebook/         # Facebook Conversions API
└── health/           # Health checks
```

## Rewrite Notes

This is a full rewrite of the NestJS/TypeScript Valora Cloud API into Kotlin Spring Boot.
The original repository: https://github.com/rey9230/valoracloud-api
