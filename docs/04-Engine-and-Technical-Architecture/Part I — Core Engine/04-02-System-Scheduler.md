# Project Draugr

# 04-02 - System Scheduler

| Field | Value |
|--------|-------|
| Document ID | PD-004.02 |
| Version | 1.0.0 |
| Status | Approved |
| Category | Engine & Technical Architecture |
| Depends On | 04-01 - Entity Component System |
| Related Documents | 03-01 - Simulation Core Runtime |

---

# Purpose

The System Scheduler is responsible for orchestrating the execution of every simulation system within Project Draugr.

It determines:

- when a system executes;
- how frequently it executes;
- which systems may execute concurrently;
- which systems must execute sequentially; and
- how dependencies between systems are respected.

The scheduler does not perform simulation.

It coordinates simulation.

---

# Design Philosophy

Reality is not updated randomly.

Every process within the simulation occurs according to an ordered schedule.

Some changes happen every moment.

Others occur hourly.

Others only once per season.

The scheduler exists to execute the simulation in a deterministic, scalable, and predictable manner.

---

# Core Principles

## Deterministic Execution

Given identical inputs, the scheduler must always produce identical execution order and simulation results.

---

## Dependency Before Performance

System correctness takes priority over execution speed.

Parallel execution is allowed only when dependencies permit.

---

## Frequency Matches Reality

Not every system requires execution every simulation tick.

Systems execute according to the temporal requirements of the simulation.

---

## Independent Systems Execute Independently

Whenever possible, unrelated systems should execute simultaneously.

---

## Simulation Never Depends on Frame Rate

Simulation time is independent of rendering.

Rendering observes the simulation.

It never controls it.

---

# Responsibilities

The System Scheduler is responsible for:

- scheduling systems;
- dependency resolution;
- execution ordering;
- frequency management;
- parallel job dispatch;
- workload balancing;
- synchronization;
- simulation timing.

It never performs gameplay logic.

---

# Scheduler Pipeline

Every simulation cycle follows the same execution pipeline.

```
Simulation Tick

↓

Collect Eligible Systems

↓

Resolve Dependencies

↓

Build Execution Graph

↓

Dispatch Parallel Jobs

↓

Synchronize Results

↓

Publish Updated World State
```

---

# Execution Categories

Systems are classified according to execution frequency.

---

## Per Tick

Executed every simulation tick.

Examples:

- movement
- physics
- animation synchronization
- environmental updates

---

## Per Minute

Examples:

- temperature adjustment
- hunger
- fatigue
- weather transitions

---

## Per Hour

Examples:

- AI planning
- market updates
- crop growth

---

## Per Day

Examples:

- economy
- diplomacy
- migration
- settlement management

---

## Event Driven

Executed only when triggered.

Examples:

- disasters
- discoveries
- achievements
- Chronicle updates

---

## Maintenance

Low-priority systems executed opportunistically.

Examples:

- cleanup
- archival
- diagnostics
- optimization

---

# Scheduling Domains

The scheduler groups systems into execution domains.

Examples:

```
Environment

↓

Biology

↓

Civilization

↓

Economy

↓

Narrative

↓

Persistence
```

Domains simplify dependency management.

---

# Dependency Resolution

Every system declares:

- required inputs;
- produced outputs;
- component access;
- execution constraints.

The scheduler automatically constructs an execution graph.

Example:

```
Weather

↓

Agriculture

↓

Economy

↓

Politics
```

Execution order is derived rather than manually maintained.

---

# Parallel Execution

Systems without conflicting dependencies may execute simultaneously.

Example:

```
Weather

+

Ocean Simulation

+

Astronomy
```

These systems may execute in parallel if they modify different component sets.

Synchronization occurs before dependent systems begin execution.

---

# Synchronization Points

The scheduler introduces synchronization barriers between dependent phases.

Example:

```
Movement
        +
Physics
        +
Weather

↓

Synchronization

↓

Economy

↓

Synchronization

↓

Narrative
```

Barriers guarantee simulation consistency.

---

# Job Dispatch

Systems are divided into jobs suitable for concurrent execution.

A job represents the smallest schedulable unit of work.

Examples:

- simulate one region;
- process one settlement;
- update one ecosystem;
- evaluate one AI population.

Job sizing should maximize hardware utilization while minimizing scheduling overhead.

---

# Workload Balancing

The scheduler distributes jobs dynamically based on:

- CPU availability;
- workload complexity;
- active simulation regions;
- entity count;
- simulation fidelity.

Balancing should minimize idle processing resources.

---

# Priority Levels

Every system receives an execution priority.

## Critical

Must execute every cycle.

Examples:

- movement
- causality
- validation

---

## High

Frequently executed simulation systems.

Examples:

- weather
- AI
- economy

---

## Medium

Background simulation.

Examples:

- research
- diplomacy
- education

---

## Low

Long-term evolution.

Examples:

- historical analysis
- archival
- optimization

---

# Simulation Budget

The scheduler operates within configurable execution budgets.

Budgets may include:

- CPU time;
- memory bandwidth;
- thread utilization;
- simulation latency.

If limits are reached, lower-priority systems may defer execution without compromising simulation correctness.

---

# Adaptive Scheduling

Execution frequency may change according to simulation activity.

Example:

A remote, inactive region may execute at reduced frequency.

An active battlefield may execute every simulation tick.

Adaptive scheduling improves scalability while preserving perceived realism.

---

# World Partition Integration

The scheduler integrates with the world partition system.

Only active partitions receive full simulation.

Inactive partitions may execute:

- reduced simulation;
- statistical simulation;
- deferred simulation.

Partition policies are defined independently.

---

# Failure Handling

If a system fails during execution:

1. current job terminates;
2. dependent systems are prevented from executing;
3. simulation integrity is preserved;
4. diagnostics are recorded;
5. recovery procedures are initiated.

Simulation correctness always takes priority over completion.

---

# Debug Scheduling

The scheduler supports deterministic debugging modes.

Capabilities include:

- single-system execution;
- step-by-step simulation;
- dependency visualization;
- execution replay;
- timing analysis.

These modes exist only within development builds.

---

# Relationship With Other Systems

Consumes:

- Simulation Tick
- ECS Components
- System Metadata

Produces:

- Ordered Execution Graph
- Job Queue
- Synchronization Events

Provides execution services to every simulation system within the engine.

---

# Performance Considerations

The scheduler should minimize:

- synchronization stalls;
- thread contention;
- idle cores;
- unnecessary dependency barriers;
- cache invalidation.

Scheduling overhead should remain insignificant compared to simulation workload.

---

# Developer Notes

The System Scheduler is the conductor of the simulation.

It owns no gameplay behavior and performs no simulation itself.

Its sole responsibility is to ensure that every system executes at the correct time, in the correct order, with the correct dependencies.

Maintaining this separation allows gameplay systems to evolve independently without requiring changes to scheduling logic.

---

# Future Expansion

Future versions may introduce:

- distributed scheduling;
- cloud simulation clusters;
- GPU job scheduling;
- adaptive thread allocation;
- machine-learned workload prediction;
- heterogeneous hardware optimization;
- deterministic rollback scheduling.

---

# Revision History

| Version | Date | Description |
|----------|------|-------------|
| 1.0.0 | July 2026 | Initial System Scheduler architecture. |