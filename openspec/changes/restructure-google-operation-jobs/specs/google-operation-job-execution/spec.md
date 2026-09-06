# Google operation job execution

## Requirement: One ordered persistent queue supports typed jobs

The system SHALL persist Google operation jobs in one table ordered by integration
sequence, while hydrating each stored row as its concrete job scope type.

#### Scenario: existing jobs are migrated

- **WHEN** the hierarchy migration is applied to existing job rows
- **THEN** sync rows are hydrated as sync jobs
- **AND** existing event-resource rows are hydrated as event jobs
- **AND** their queue state, sequence, retry, and terminal information is retained.

## Requirement: Event jobs own event write data

The system SHALL keep event ID, mutation kind, serialized event snapshot, and
CREATE provider identity on the event job type rather than exposing them as
generic queue-job fields.

#### Scenario: CREATE event job is retried

- **WHEN** a CREATE job is retried after a provider-side create may have succeeded
- **THEN** the same deterministic provider identity is used for recovery
- **AND** the event snapshot remains the snapshot captured when it was enqueued.

#### Scenario: legacy resource keys are removed

- **WHEN** typed job persistence is enabled
- **THEN** an Event job stores its Event ID in its own typed field
- **AND** no Job entity field represents a resource scope or resource key as a string.

## Requirement: Typed dispatch preserves queue policy

The system SHALL dispatch by concrete job scope while retaining existing lease,
failure classification, retry, and integration ordering behavior.

#### Scenario: a claimed event job executes

- **WHEN** the processor claims an event job
- **THEN** it invokes event CREATE, UPDATE, or DELETE handling according to its
  mutation kind
- **AND** provider calls are outside database transactions
- **AND** mapping finalization remains transactional.

## Requirement: Scope handlers are extensible without processor changes

The system SHALL select a single scope handler from a claimed Job's concrete
entity type, while retaining queue lifecycle policy in the processor.

#### Scenario: a new scope handler is registered

- **WHEN** a future Job subtype and its handler are registered
- **THEN** the registry selects that handler for the subtype
- **AND** the processor does not require a scope-specific branch.
