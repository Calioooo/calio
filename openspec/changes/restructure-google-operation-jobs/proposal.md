# Google operation job hierarchy

## Why

`GoogleOperationJob` currently stores every scope in one generic entity and asks
callers to recover its meaning from string `kind`, resource scope, and resource
key fields. The asynchronous event write flow is correct, but this makes the
processor and event executor responsible for parsing persistence details that
belong to the job's type.

The queue must remain globally ordered per integration. Future recurrence-event
and recurrence-override writes will share that queue, but their resource data and
execution rules must not be inferred from a growing collection of string fields.

## Change

- Keep one `google_operation_jobs` table and introduce JPA `SINGLE_TABLE`
  inheritance for the job's persistent scope.
- Keep queue lifecycle state in an abstract `GoogleOperationJob` base class.
- Add concrete `GoogleCalendarSyncJob` and `GoogleCalendarEventJob` types for
  the scopes that can currently be enqueued.
- Replace mutation-kind strings with a closed `GoogleOperationJobKind` enum.
- Remove persisted `effectiveResourceScope` and `effectiveResourceKey`; jobs
  own typed identifiers instead.
- Dispatch claimed jobs through a scope handler registry. A handler owns its
  scope's CREATE, UPDATE, and DELETE operations directly.
- Preserve every existing event-write policy: enqueue snapshot, deterministic
  create identity, integration sequence ordering, provider calls outside the
  transaction, mapping finalization in a transaction, and conflict/retry
  handling.

## Non-goals

- This change does not invent outbound recurrence-event or override policies.
  Those scopes will gain their own concrete subtype and executor only when their
  payload and provider semantics are implemented.
- This change does not add a command mapper, handler registry, or forwarding
  executor layer. The job contract is closed and direct dispatch is clearer.
