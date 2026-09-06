# Tasks

- [x] Introduce the closed mutation-kind enum and the `SINGLE_TABLE` job base.
- [x] Add sync and event job subtypes and migrate current stored rows.
- [x] Remove legacy scope/key persistence and use typed Event IDs in pending-job checks.
- [x] Add a validated scope handler registry while keeping queue lifecycle in the processor.
- [x] Update enqueue, claim processing, and event execution to use typed jobs.
- [x] Preserve and adapt event create/update/delete and processor tests.
- [x] Run focused backend tests and compile checks.
- [ ] Consolidate the feature branch into reviewable final-architecture commits.
