# Design: Google operation job hierarchy

## Persistence model

`google_operation_jobs` remains the single ordered queue. The entity hierarchy
uses `SINGLE_TABLE` with a `job_scope` discriminator:

- `SYNC` -> `GoogleCalendarSyncJob`
- `EVENT` -> `GoogleCalendarEventJob`

The abstract base owns only data and operations valid for every queued job:
operation and integration identity, integration sequence, state, retry,
runnable time, owner token, terminal information, and conflict state. It does
not expose event payload, provider identity, or resource-key accessors.

`GoogleCalendarEventJob` owns `eventId`, mutation kind, payload, and create
provider identity. `GoogleCalendarSyncJob` owns its sync trigger. A future
recurrence-event or recurrence-override job can use another discriminator value
and subtype without changing the queue table or its claim queries.

The migration assigns existing `SYNC` jobs to `SYNC` and existing event resource
scope rows to `EVENT`, while retaining existing fields for backwards-compatible
data reads.

## Execution model

`GoogleOperationProcessor` retains all queue ownership, lease, retry,
termination, and reconnect handling. It dispatches once by concrete job type:
`GoogleCalendarSyncJob` invokes the current sync service; `GoogleCalendarEventJob`
invokes the event job service. The event service switches on its closed mutation
kind to CREATE, UPDATE, or DELETE.

No general executor registry is introduced. Adding a scope requires an explicit
producer, subtype, processor branch, migration contract, and tests, so a dynamic
registry would hide rather than reduce required change.

## Transaction and idempotency invariants

- Event contents are serialized when the job is enqueued, never reloaded from
  the mutable Event aggregate while the worker runs.
- CREATE uses a deterministic Google provider identity based on integration and
  event identity; retry recovery remains possible.
- Google API calls remain outside the database transaction.
- Mapping state is read and finalized in short transactions, including ETag and
  conflict/local-change decisions.
- Jobs retain integration sequence ordering and the existing generic failure
  classifier controls retry versus terminal state.
