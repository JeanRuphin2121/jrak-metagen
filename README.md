# Metagen Platform

**Open-source metadata-driven low-code platform** — build business applications from a data model. CRUD APIs, workflows, role-based access control, and custom views are generated automatically.

> Metagen is designed to solve the real pain points of existing low-code tools: **poor developer experience, rigid UI, untestable business logic, and no CI/CD story**.

---

## Why Metagen?

| Problem with existing tools | Metagen's answer |
|---|---|
| Config locked in a database — no Git diff | **Config as YAML** — versionnable, reviewable, diffable |
| Business logic in text fields — impossible to test | **Hooks as Java beans** — standard JUnit, full IDE support |
| UI hard to customise | **Decoupled React frontend** — every component is overridable |
| Slow on large datasets | **Native SQL** + Redis cache + automatic index suggestions |
| No CI/CD story | **CLI** for import/export/diff + pipeline-first design |
| No AI assistance | **Claude integration** via Spring AI — describe → generate |

---

## Features

- **Data modelling** — define entities, fields, and relations via UI or YAML
- **Auto-generated CRUD API** — REST endpoints created dynamically from the model
- **Workflow engine** — finite state machines with declarative guards, role checks, audit history and Java action beans
- **RBAC** — role-based permissions with declarative row-level filters and field masks
- **Custom views** — List, Form, Kanban, Calendar, Chart — configurable via JSON
- **AI assistant** — describe your model in natural language, get a ready-to-use schema
- **Config as Code** — export / import / diff your entire configuration as YAML

---

## Architecture

```
┌──────────────────────────────────────────────────────────┐
│                    React Frontend                         │
│        Studio (config)  │  App Runtime (end-users)        │
└─────────────────────────┬────────────────────────────────┘
                          │ REST / JSON
┌─────────────────────────▼────────────────────────────────┐
│                  Spring Boot 3 Server                     │
│                                                           │
│  metagen-core    Domain model (JPA entities + repos)      │
│  metagen-engine  Runtime: dynamic CRUD, workflow, RBAC    │
│  metagen-server  HTTP API, auth (JWT), Studio endpoints   │
└─────────────────────────┬────────────────────────────────┘
                          │
                    PostgreSQL 16
```

See [docs/architecture.md](docs/architecture.md) for the full design.

---

## Quick Start

### Prerequisites

- Java 21+
- Docker & Docker Compose
- Maven 3.9+ 

### 1. Start the database

```bash
docker compose -f docker/docker-compose.dev.yml up -d
```

PostgreSQL is now available at `localhost:5432`.  
pgAdmin UI is at [http://localhost:5050](http://localhost:5050) (admin@metagen.dev / admin).

### 2. Run the server

```bash
mvn spring-boot:run -pl metagen-server
```

The server starts on [http://localhost:8080](http://localhost:8080).  
Flyway runs the schema migrations automatically on startup.

### 3. Explore the API

Open the Swagger UI at [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html).

---

## Project Structure

```
metagen/
├── metagen-core/          # Domain model: JPA entities + Spring Data repositories
│   └── src/main/java/io/metagen/core/
│       ├── domain/        # MetaEntity, MetaField, MetaWorkflow, MetaRole, ...
│       └── repository/    # Spring Data JPA repositories
│
├── metagen-engine/        # Runtime engine (Phase 2 — in development)
│   └── src/main/java/io/metagen/engine/
│       ├── crud/          # Dynamic CRUD service
│       ├── api/           # Dynamic REST controller
│       └── hook/          # Business hook interfaces
│
├── metagen-server/        # Spring Boot application
│   └── src/main/
│       ├── java/io/metagen/
│       │   ├── config/    # Spring Security, OpenAPI, etc.
│       │   ├── meta/      # Studio REST API (entities, fields, workflows)
│       │   └── security/  # JWT filter, RBAC enforcement
│       └── resources/
│           ├── application.yml
│           └── db/migration/   # Flyway SQL migrations
│
├── docker/
│   ├── docker-compose.dev.yml  # Dev: PostgreSQL + pgAdmin only
│   ├── docker-compose.yml      # Prod: full stack
│   ├── Dockerfile              # Multi-stage build
│   └── .env.example
│
├── docs/
│   ├── architecture.md
│   └── getting-started.md
│
└── .github/workflows/ci.yml    # GitHub Actions: build → test → docker push
```

---

## Status

| Area | State |
|------|-------|
| Dynamic CRUD (`/api/data/{entity}`), soft delete, search, sort, filters | done, covered by tests |
| Table creation / additive migration (`deploy`) | done — never drops anything, reports what it could not reconcile |
| Workflow: guards, role checks, actions, audit history, optimistic concurrency | done |
| RBAC: roles, row filters, field masks (hidden / readonly) | done |
| Studio API, config as YAML (dry run, atomic import, export) | done |
| React UI: generated list / form / detail with workflow actions, Studio, YAML page | done (build-checked; no browser test suite yet) |
| AI assistant (Spring AI + Claude) | compiles, disabled by default — **not exercised against the live API** |
| Views (Kanban, Calendar, Chart) | stored in metadata, **not rendered yet** |
| CLI (`metagen export/import/diff`) | not started — use the `/api/studio/config` endpoints |

See [docs/low-code-model.md](docs/low-code-model.md) for how business logic is expressed
and [config/demo-purchase-requests.yml](config/demo-purchase-requests.yml) for a complete example.

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

---

## License

Apache License 2.0 — see `LICENSE` (add the Apache-2.0 text before publishing).
