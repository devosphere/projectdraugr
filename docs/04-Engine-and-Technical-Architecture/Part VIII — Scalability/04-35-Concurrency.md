# Project Draugr

# 04-35 - Concurrency

| Field | Value |
|--------|-------|
| Document ID | PD-004.35 |
| Version | 1.0.0 |
| Status | Approved |
| Category | Engine & Technical Architecture |
| Depends On | 04-02 - System Scheduler, 04-03 - Data-Oriented Architecture, 04-33 - Performance Strategy |
| Related Documents | 03-02 - World Simulation Layer, 04-01 - Entity Component System, 04-30 - Profiling System, 04-34 - Memory Management, 04-36 - Distributed Simulation |

---

# Purpose

The Concurrency architecture defines how Project Draugr executes multiple simulation tasks simultaneously while preserving deterministic world behavior.

Concurrency exists to increase computational throughput.

It must never compromise simulation correctness.

Regardless of processor count, hardware platform, or execution order, identical simulation inputs must always produce identical world states.

Parallel execution is an implementation detail.

Reality remains singular.

---

# Design Philosophy

Project Draugr does not execute systems because processors are available.

It executes systems because independent work exists.

The engine discovers parallelism from the simulation itself.

Threads do not own the world.

They temporarily execute portions of it.

---

# Core Principles

## Deterministic Execution

Simulation results must never depend on thread scheduling.

---

## Task-Oriented Design

Work is divided into tasks rather than permanently assigned threads.

---

## Data Ownership

Only one authoritative writer may modify a piece of simulation state during a synchronization phase.

---

## Lock-Free Whenever Possible

Avoid locks by designing data flow correctly.

Synchronization is the exception.

Not the rule.

---

## Scalable Architecture

Performance should improve as processing resources increase.

---

# Concurrency Model

The engine separates execution into:

```text
Simulation Tick

↓

Task Graph Construction

↓

Dependency Resolution

↓

Task Scheduling

↓

Parallel Execution

↓

Synchronization

↓

World Commit
```

Only committed results become part of reality.

---

# Thread Model

The engine distinguishes between:

- Main Thread
- Simulation Workers
- Rendering Thread
- Asset Streaming Thread
- Audio Thread
- Networking Thread
- Background Utility Workers

Each has clearly defined responsibilities.

---

# Main Thread

Responsible for:

- frame orchestration;
- scheduler coordination;
- player input;
- world synchronization;
- presentation.

The Main Thread does not perform large-scale simulation work.

---

# Worker Threads

Worker threads execute independent simulation tasks.

Examples:

- ECS iteration;
- AI reasoning;
- weather simulation;
- ecosystem updates;
- pathfinding;
- procedural generation.

Workers never directly own world state.

---

# Job System

Every unit of work becomes a Job.

A Job contains:

- identifier;
- dependencies;
- priority;
- execution function;
- completion state.

Jobs are lightweight and disposable.

---

# Task Graph

Jobs are organized into a directed dependency graph.

```text
Simulation Tick

↓

Task A

↓

Task B ── Task C

↓

Task D

↓

Synchronization
```

Independent tasks execute concurrently.

Dependent tasks wait automatically.

---

# Dependency Resolution

Every task declares:

- required inputs;
- produced outputs;
- execution dependencies.

The scheduler derives execution order from these relationships.

---

# Synchronization Barriers

Synchronization occurs only at defined phases.

Typical barriers include:

- end of simulation tick;
- world state commit;
- networking synchronization;
- persistence checkpoint.

Barriers prevent inconsistent world states.

---

# World State Ownership

During execution:

Every simulation object has exactly one authoritative writer.

Multiple readers are permitted.

Multiple writers are prohibited unless explicitly coordinated.

---

# Read-Only Parallelism

Many systems execute entirely in parallel because they only observe data.

Examples:

- visibility calculations;
- AI perception;
- analytics;
- diagnostics.

Read-only work scales efficiently.

---

# Write Isolation

Systems modifying simulation state perform writes only within designated ownership boundaries.

Examples:

- ECS archetypes;
- terrain chunks;
- weather cells;
- settlement regions.

Isolation minimizes synchronization.

---

# Lock-Free Communication

Worker threads communicate through:

- message queues;
- command buffers;
- event streams;
- atomic state transitions.

Global mutexes should remain rare.

---

# Double Buffering

Many simulation structures utilize double buffering.

```text
Current State

↓

Worker Processing

↓

Next State

↓

Commit

↓

Swap
```

Workers never modify live world state directly.

---

# Command Buffers

Instead of immediate modification:

Workers produce commands.

Examples:

- create entity;
- destroy entity;
- modify component;
- trigger event.

Commands execute during synchronization.

---

# Work Stealing

Idle workers may acquire unfinished tasks from other workers.

Benefits include:

- improved CPU utilization;
- balanced workloads;
- adaptive scheduling.

Stealing never violates dependencies.

---

# Priority Scheduling

Jobs may define priorities.

Critical

- scheduler
- networking
- synchronization

High

- nearby simulation
- player interaction

Medium

- settlements
- ecosystems

Low

- distant history
- analytics
- diagnostics

Priority affects scheduling only.

Never correctness.

---

# Background Tasks

Certain operations execute outside the simulation tick.

Examples:

- asset loading;
- save compression;
- texture streaming;
- diagnostics;
- telemetry.

Background tasks never modify authoritative simulation directly.

---

# Thread Affinity

Some systems require fixed execution contexts.

Examples:

Rendering

↓

GPU Thread

Audio

↓

Audio Thread

Networking

↓

Network Thread

Simulation remains task-oriented.

---

# Deadlock Prevention

The architecture minimizes deadlocks through:

- immutable data;
- lock-free structures;
- dependency graphs;
- explicit ownership.

Circular waiting should never occur.

---

# Race Condition Prevention

Race conditions are prevented through:

- deterministic scheduling;
- write isolation;
- command buffering;
- synchronization barriers.

Correctness is architectural rather than accidental.

---

# Deterministic Parallelism

Regardless of:

- CPU manufacturer;
- processor count;
- operating system;
- thread scheduling;

The same Chronicle should evolve identically.

This is a fundamental engine guarantee.

---

# Failure Recovery

If a worker fails:

```text
Task Failure

↓

Scheduler Detection

↓

Job Cancellation

↓

Simulation Rollback

↓

Diagnostic Report
```

Partial simulation updates never become authoritative.

---

# Integration with ECS

The ECS exposes work naturally through archetype iteration.

Concurrency operates on ECS data without violating ownership.

---

# Integration with Scheduler

The System Scheduler determines *when* work should occur.

Concurrency determines *how* that work is executed.

---

# Integration with Profiling

Profiling measures:

- worker utilization;
- task latency;
- scheduling efficiency;
- synchronization cost.

Concurrency uses these metrics for optimization.

---

# Integration with Distributed Simulation

Distributed Simulation expands the same task model across multiple machines.

The concurrency architecture remains unchanged.

Only execution boundaries change.

---

# Debug Support

Developer tools should visualize:

- worker activity;
- task graph;
- dependency chains;
- synchronization barriers;
- execution timelines;
- stalled jobs;
- lock contention;
- work-stealing statistics.

Parallel execution should remain transparent.

---

# Performance Considerations

The Concurrency architecture should optimize:

- CPU utilization;
- cache locality;
- task granularity;
- scheduling overhead;
- synchronization frequency;
- deterministic execution.

Concurrency should increase throughput without increasing architectural complexity.

---

# Developer Notes

Most engines ask:

"How many threads does the CPU have?"

Project Draugr asks:

"How much independent reality exists right now?"

Threads are temporary.

Tasks are temporary.

Only the world persists.

The engine should never expose parallelism to the simulation.

To every creature...

Every civilization...

Every Chronicle...

Reality unfolds as though only one universe has ever existed.

---

# Future Expansion

Future versions may introduce:

- fiber-based job execution;
- heterogeneous CPU scheduling;
- GPU compute simulation;
- distributed task graphs;
- adaptive job partitioning;
- AI-assisted scheduler optimization;
- cloud-scale concurrency orchestration;
- autonomous workload prediction.

---

# Revision History

| Version | Date | Description |
|----------|------|-------------|
| 1.0.0 | July 2026 | Initial Concurrency architecture defining deterministic task-based parallel execution for Project Draugr. |