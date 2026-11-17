# Getting Started

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Java | 21+ | Temurin recommended |
| Maven | 3.9+ |  |
| Docker | 24+ | For PostgreSQL |
| Docker Compose | 2.20+ | Included with Docker Desktop |
| Git | any | |

---

## 1. Clone the repository

```bash
git clone https://github.com/your-org/metagen.git
cd metagen
```

---

## 2. Start the development database

```bash
docker compose -f docker/docker-compose.dev.yml up -d
```

This starts:
- **PostgreSQL 16** on `localhost:5432` (user: `metagen`, password: `metagen`, db: `metagen`)
- **pgAdmin 4** on [http://localhost:5050](http://localhost:5050)

Verify PostgreSQL is healthy:
```bash
docker compose -f docker/docker-compose.dev.yml ps
# postgres   running (healthy)
```

---

## 3. Build the project

```bash
mvn compile
```

---

## 4. Run the server

```bash
mvn spring-boot:run -pl metagen-server
```

On first startup:
- Flyway creates all `meta_*` tables
- Two built-in roles are seeded: `ADMIN` and `USER`

The server is ready when you see:
```
Started MetagenApplication in X.XXX seconds
```

---

## 5. Explore the API

Open [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html).

---

## 6. Run the tests

```bash
# Unit tests (all modules)
mvn test

# Integration tests (requires Docker for Testcontainers)
mvn verify -pl metagen-server
```

---

## Environment variables

All configuration can be overridden via environment variables.
Copy [docker/.env.example](../docker/.env.example) to `.env` for the full stack.

| Variable | Default | Description |
|---|---|---|
| `METAGEN_DB_URL` | `jdbc:postgresql://localhost:5432/metagen` | JDBC URL |
| `METAGEN_DB_USER` | `metagen` | DB username |
| `METAGEN_DB_PASSWORD` | `metagen` | DB password |
| `METAGEN_JWT_SECRET` | (insecure default) | **Change in production** |
| `METAGEN_JWT_EXPIRATION_MS` | `86400000` | Token TTL (24h) |
| `METAGEN_AI_ENABLED` | `false` | Enable AI assistant |
| `ANTHROPIC_API_KEY` | — | Required when AI is enabled |

---

## IDE Setup (IntelliJ IDEA)

1. **File → Open** → select the `metagen` directory
2. Import as Maven project when prompted
3. Set SDK to Java 21 (**File → Project Structure → SDK**)
4. Enable annotation processing: **Settings → Build → Compiler → Annotation Processors → Enable**
   - Required for Lombok and MapStruct

---

## Useful commands

```bash
# Start only the database
docker compose -f docker/docker-compose.dev.yml up -d postgres

# Stop all dev containers
docker compose -f docker/docker-compose.dev.yml down

# Wipe the database (fresh start)
docker compose -f docker/docker-compose.dev.yml down -v

# View server logs
mvn spring-boot:run -pl metagen-server | tee server.log

# Check Flyway migration status
mvn flyway:info -pl metagen-server
```
