# Architecture

## Core principle: Everything is metadata

Metagen is a **metadata-driven** platform. Instead of writing code to create a CRUD application, you define your data model (entities, fields, relations, workflows, permissions) and the engine generates everything at runtime.

```
Developer defines:            Engine generates:
──────────────────            ─────────────────
MetaEntity "supplier"    →    REST API /api/data/supplier
  field: name STRING     →    PostgreSQL table data_supplier
  field: siret STRING    →    Swagger documentation
  workflow: validation   →    Workflow state transitions
  role BUYER: READ/WRITE →    RBAC enforcement
  view: kanban           →    React Kanban component
```

All definitions are stored in **PostgreSQL** (the `meta_*` tables) and can be **exported to YAML** for Git versioning.

---

## Module breakdown

### `metagen-core` — Domain model

Pure library module. Contains:
- **JPA entities** for the metadata model (`MetaEntity`, `MetaField`, `MetaWorkflow`, `MetaState`, `MetaTransition`, `MetaRole`, `MetaPermission`, `MetaView`)
- **Spring Data repositories** with optimised JPQL queries
- **Enums** (`MetaFieldType`, `MetaRelationType`, `MetaViewType`)

No Spring Boot auto-configuration. Designed to be independently testable.

### `metagen-engine` — Runtime engine *(Phase 2)*

Reads metadata and generates runtime behaviour:
- **DynamicCrudService** — executes SELECT/INSERT/UPDATE/DELETE on `data_*` tables
- **DynamicRestController** — registers Spring MVC endpoints at startup for each deployed entity
- **WorkflowEngine** — evaluates conditions and executes transitions
- **RbacEnforcer** — applies permissions, row filters, and field masks per request

### `metagen-server` — Spring Boot application

Assembles all modules and exposes:
- **Studio API** (`/api/studio/*`) — manage the metadata model
- **Runtime API** (`/api/data/*`) — dynamically generated CRUD endpoints
- **Auth API** (`/api/auth/*`) — login, token refresh
- Flyway migrations, Spring Security, OpenAPI docs

---

## Database schema

```
meta_workflow ──< meta_state
     │
     └──< meta_transition (from_state → to_state)

meta_entity ──< meta_field
     │
     ├──< meta_relation (source → target entity)
     │
     ├──> meta_workflow (optional lifecycle)
     │
     └──< meta_view

meta_role ──< meta_permission ──> meta_entity
```

**Application data** is stored in dynamically created tables (`data_*`), separate from the metadata tables (`meta_*`). Flyway only manages the `meta_*` schema; `data_*` tables are created/altered by the engine.

---

## Config as Code

Every metadata object can be serialised to YAML:

```yaml
# entities/supplier.yml
entity:
  name: supplier
  label: Fournisseur
  softDelete: true
  auditable: true
  workflow: supplier_validation
  fields:
    - name: name
      label: Raison sociale
      type: STRING
      required: true
      searchable: true
    - name: siret
      label: SIRET
      type: STRING
      unique: true
  permissions:
    - role: BUYER
      canRead: true
      canWrite: true
    - role: ADMIN
      canRead: true
      canWrite: true
      canDelete: true
```

The CLI (`metagen-cli`) provides:
```bash
metagen export --output ./config/        # dump all metadata to YAML
metagen import --input  ./config/        # apply YAML to the database
metagen diff   --env prod --env staging  # compare two environments
metagen apply  --env prod                # push local config to prod
```

---

## Security model

Authentication: **JWT Bearer tokens** issued by `/api/auth/login`.

Authorization: **RBAC** evaluated per request:
1. Extract roles from JWT claims
2. Look up `MetaPermission` rows for `(role, entity)` pairs
3. Check `canRead` / `canWrite` / `canDelete`
4. Apply `rowFilterScript` as a SQL WHERE clause
5. Apply `fieldMask` to hide or mark fields as readonly

---

## AI assistant *(Phase 4)*

The AI assistant uses **Spring AI + Claude** to:
1. Accept a natural-language description of a business domain
2. Generate a `MetaProposal` (entities, fields, relations, workflows) as structured JSON
3. Present the proposal in the Studio for review and confirmation
4. On approval, persist the metadata and deploy the entity

```
User: "I need to manage suppliers with contacts,
       orders, and a payment tracking workflow"

AI → MetaProposal {
  entities: [supplier, contact, order],
  relations: [supplier→contacts, supplier→orders],
  workflow: {payment_tracking: PENDING→INVOICED→PAID|DISPUTED}
}
```

---

## Design decisions

| Decision | Rationale |
|---|---|
| Java 21 + Spring Boot 3.4 | LTS, virtual threads (Project Loom), mature ecosystem |
| PostgreSQL as sole datastore | JSONB for flexible config, excellent indexing, Flyway support |
| Maven multi-modules | Clear dependency graph, independent testability per module |
| Flyway (not JPA DDL) | Reproducible, auditable migrations — same file runs on every env |
| Testcontainers for integration tests | Real database, no mocks — avoids the divergence bugs that plagued Simplicité |
| JWT (stateless) | Scales horizontally without session affinity |
| Spring AI + Claude | Best-in-class reasoning for schema generation from natural language |
