# Contributing to Metagen

Thank you for your interest in contributing! This document explains how to get involved.

---

## Development workflow

1. **Fork** the repository
2. Create a feature branch: `git checkout -b feat/my-feature`
3. Commit your changes (see [Commit conventions](#commit-conventions))
4. Push and open a **Pull Request** against `develop`

All PRs must pass the CI pipeline (build + tests) before merging.

---

## Branch strategy

| Branch | Purpose |
|---|---|
| `main` | Stable releases — merges from `develop` only |
| `develop` | Integration branch — target for feature PRs |
| `feat/*` | New features |
| `fix/*` | Bug fixes |
| `docs/*` | Documentation only |
| `refactor/*` | Refactoring with no behaviour change |

---

## Commit conventions

We follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <short description>

[optional body]

[optional footer]
```

Types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `perf`

Examples:
```
feat(engine): add dynamic CRUD endpoint generation
fix(rbac): apply row filter script on list queries
docs(architecture): add workflow engine design notes
test(core): add MetaEntity repository integration tests
```

---

## Code standards

- **Java 21** — use records, sealed classes, text blocks, and pattern matching where appropriate
- **Lombok** — use `@Builder`, `@Getter`, `@Setter`; avoid `@Data` on JPA entities (equals/hashCode issues)
- **No `@Data` on entities** — implement `equals`/`hashCode` based on `id` only if needed
- **Javadoc** — all public API in `metagen-core` and `metagen-engine` must be documented
- **No business logic in domain classes** — keep entities as plain data containers
- **Test coverage** — new features must include unit tests; new engine features must include integration tests with Testcontainers

---

## Testing

```bash
# Unit tests
./mvnw test

# Integration tests (requires Docker)
./mvnw verify -pl metagen-server

# Single test class
./mvnw test -pl metagen-core -Dtest=MetaEntityRepositoryTest
```

Integration tests use **Testcontainers** to spin up a real PostgreSQL instance. No mocking of the database — this is intentional and critical for correctness.

---

## Adding a new domain concept

If you need to add a new metadata concept (e.g. `MetaIndex`, `MetaHook`):

1. Add the JPA entity in `metagen-core/src/main/java/io/metagen/core/domain/`
2. Add the repository interface in `metagen-core/.../repository/`
3. Add a Flyway migration in `metagen-server/src/main/resources/db/migration/`
   - Name: `V{N}__add_{concept}.sql` (next sequential number)
4. Add unit tests for the repository
5. Update `docs/architecture.md` if the concept affects the overall design

---

## Reporting issues

Please use the GitHub Issues tracker. Include:
- Metagen version (or commit SHA)
- Java version (`java -version`)
- Steps to reproduce
- Expected vs actual behaviour
- Relevant logs
