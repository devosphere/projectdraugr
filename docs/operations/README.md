# Operations Runbooks

Operational instructions are part of Project Draugr's source-controlled product record. They must be reviewed and committed with the application changes they describe.

## Runbook versioning

- Each runbook declares a semantic `major.minor.patch` version and `Last verified` date.
- A change to required commands, ports, services, credentials, backup formats, or recovery behavior requires a runbook version increase in the same commit.
- Use a **major** increment for an incompatible procedure, **minor** for an additive procedure, and **patch** for a correction or clarification.
- Commands must be safe for local use and must state whether they are read-only, create state, or can destroy state.
- Secrets belong in ignored local environment files or an approved secret store; they never belong in documentation, examples, commits, screenshots, or issue comments.

## Current runbooks

- [Database Monitoring v0.1.0](database-monitoring.md)

## Required future runbooks

- Backup and restore verification
- Local world reset and seed replacement
- Migration incident and recovery procedure
- Release and GitHub Pages deployment verification
