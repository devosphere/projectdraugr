# Versioned UI Specifications

This directory contains release-style UI requirements for the implemented experience. It complements the long-lived design rationale in `docs/systems/18-*.md`.

## Versioning rules

- A specification version is `major.minor.patch`.
- Increase **major** when navigation, information hierarchy, or player-facing interaction changes incompatibly.
- Increase **minor** when a planned, compatible screen or interaction is added.
- Increase **patch** for clarified acceptance criteria or visual corrections that do not change behavior.
- Never overwrite a released specification. Create the next version and record what changed.
- Frontend behavior, screenshots, and automated tests should identify the applicable specification version when practical.

## Current version

- [UI Specification v0.1.0](UI-SPEC-v0.1.0.md) — onboarding and forest playthrough vertical-slice contract.
