# How business logic is expressed

Metagen stores **no executable code and no SQL** in its metadata. Behaviour is built from
three declarative or compiled pieces:

| Need | Mechanism | Where it lives |
|------|-----------|----------------|
| Who sees which rows | `rowFilter` condition on a permission | metadata (YAML / Studio) |
| When a transition is allowed | `condition` on the transition | metadata |
| What happens on a transition | `action` = name of a `WorkflowAction` Spring bean | Java, in Git |
| Validation / enrichment on save | `PreSaveHook` / `PostSaveHook` beans | Java, in Git |

## Conditions

```yaml
{ field: amount, op: lte, value: 5000 }
{ field: created_by, op: eq, value: $user }
{ and: [ {...}, { or: [ {...}, { not: {...} } ] } ] }
```

Operators: `eq ne gt gte lt lte in contains is_null not_null`. Variables: `$user`, `$today`.
Values are type-checked against the field at import time and always sent as bind parameters.
The same condition is compiled to SQL (row filters) or evaluated in memory (transition guards).

## Permissions

* `ADMIN` bypasses everything.
* With several roles the most permissive wins; row filters of different roles are OR-ed, and a role
  with no filter lifts the restriction.
* `fieldMask.hidden` fields are never returned and cannot be used as filters; `hidden` and
  `readonly` fields in a write payload are **ignored** (so a UI can send a whole record back).
* System columns (`id state created_at updated_at created_by updated_by deleted_at`) cannot be
  written through the API; `state` only changes through workflow transitions.

## Workflows

A transition is identified by `(from, to, label)`: give two transitions between the same states
different labels (`Approve (manager)`, `Approve (director)`). `requiredRole` restricts who may fire
it, `condition` restricts on the record, `action` runs inside the same database transaction (an
exception cancels the state change). Every transition is recorded in `workflow_history`.
Concurrent transitions on one record are safe: the state update is compare-and-set.

A state marked `terminal: true` **freezes the record**: it can no longer be updated or deleted
(HTTP 422), except by `ADMIN`. The UI hides the Edit / Delete buttons accordingly.

## Writing an action

```java
@Component
class NotifyManager implements WorkflowAction {
    public String name() { return "notify_manager"; }
    public void execute(TransitionContext ctx) { /* send mail, create a record… */ }
}
```

Then reference it: `action: notify_manager`. Unknown action names are rejected at import time.

## Config as code

`GET /api/studio/config/export`, `POST /api/studio/config/import?dryRun=true|false&deploy=true|false`.
Import is validated as a whole first, applied atomically, and never deletes anything.

## Known limits

* `deploy` is additive: it creates tables and adds columns / indexes / foreign keys, but never
  drops or retypes. A field removed from metadata keeps its column.
* Views (Kanban, Calendar…) are stored but not rendered by the UI yet.
* Row filters cannot traverse references (no joins).
