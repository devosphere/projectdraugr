# Project Draugr

# 04-03 - Data-Oriented Architecture

| Field | Value |
|--------|-------|
| Document ID | PD-004.03 |
| Version | 1.0.0 |
| Status | Approved |
| Category | Engine & Technical Architecture |
| Depends On | 04-01 - Entity Component System, 04-02 - System Scheduler |
| Related Documents | 03-01 - Simulation Core Runtime |

---

# Purpose

The Data-Oriented Architecture (DOA) defines how information is organized, stored, accessed, and processed throughout Project Draugr.

Rather than optimizing software around objects, the engine is optimized around the movement of data through modern computer hardware.

The objective is to maximize simulation throughput by minimizing memory latency, improving cache utilization, enabling parallel execution, and reducing unnecessary computation.

This document establishes the memory philosophy upon which the entire engine is built.

---

# Design Philosophy

Modern CPUs spend significantly more time waiting for data than performing calculations.

Performance is therefore determined less by algorithm complexity alone and more by how efficiently data moves through memory.

Project Draugr is designed around this reality.

Data should move efficiently.

Computation should follow data.

Memory access should be predictable.

The architecture is built for hardware rather than programming language abstractions.

---

# Core Principles

## Data First

Data organization takes priority over object organization.

---

## Sequential Access

Sequential memory access is always preferred over random access.

---

## Cache Locality

Frequently accessed data should be stored together.

---

## Predictable Memory Layout

Memory layouts should remain stable throughout runtime.

---

## Minimize Memory Movement

Moving data is expensive.

Whenever possible, systems should process data in place.

---

## Composition Over Hierarchy

Behavior emerges from independent datasets rather than inheritance trees.

---

# Why Data-Oriented Design?

Project Draugr simulates:

- millions of entities;
- dynamic ecosystems;
- evolving civilizations;
- large-scale economies;
- persistent world history.

Traditional object-oriented memory layouts introduce:

- pointer chasing;
- fragmented memory;
- cache misses;
- poor scalability.

Data-Oriented Design minimizes these issues through contiguous storage and predictable iteration.

---

# Memory Hierarchy

Understanding hardware is fundamental to engine design.

```
CPU Registers
        │
        ▼
L1 Cache
        │
        ▼
L2 Cache
        │
        ▼
L3 Cache
        │
        ▼
Main Memory (RAM)
        │
        ▼
Storage
```

Access time increases dramatically with each level.

The architecture strives to keep active simulation data within CPU caches whenever possible.

---

# Cache Locality

Components processed together should be stored together.

Example:

```
Health

Health

Health

Health

Health
```

Rather than:

```
Player

↓

Health

Inventory

Quest

Animation

Dialogue

Physics
```

The first arrangement minimizes cache misses during health processing.

---

# Struct of Arrays (SoA)

Project Draugr primarily favors a Struct of Arrays layout.

Example:

```
Health[]

100

95

87

40

12
```

```
Hunger[]

20

10

80

45

90
```

Each component type is stored independently.

Benefits include:

- sequential iteration;
- SIMD compatibility;
- cache efficiency;
- parallel processing.

---

# Array of Structs (AoS)

Traditional object layouts resemble:

```
Player

Health

Hunger

Inventory

Position
```

Repeated for every entity.

Although intuitive, this layout often loads unnecessary data into cache.

AoS should only be used where access patterns justify it.

---

# Access Patterns

The engine should optimize for common access patterns.

Examples:

Movement System

Needs:

- Position
- Velocity

Only.

The system should never load unrelated information such as:

- Dialogue
- Inventory
- Reputation

Unused data wastes cache bandwidth.

---

# Component Storage

Each component type maintains independent storage.

Example:

```
Transform Storage

Position

Rotation

Scale
```

```
Health Storage

Current

Maximum

Disease
```

Systems retrieve only the storage required for execution.

---

# Memory Alignment

Component arrays should respect hardware alignment requirements.

Proper alignment improves:

- cache utilization;
- SIMD instructions;
- vectorized operations;
- memory throughput.

Alignment strategies should remain platform-aware.

---

# False Sharing Prevention

Parallel systems should avoid multiple threads writing to data within the same cache line.

Strategies include:

- partitioned workloads;
- thread-local buffers;
- write isolation;
- deferred synchronization.

Reducing false sharing improves scalability on multi-core processors.

---

# Data Ownership

Each dataset has one authoritative owner.

Ownership prevents:

- duplicated state;
- synchronization ambiguity;
- conflicting writes.

Data ownership should always remain explicit.

---

# Immutable Data

Whenever practical, shared data should be immutable during simulation phases.

Examples include:

- terrain definitions;
- biome metadata;
- species templates;
- crafting recipes.

Immutable data simplifies concurrency and reduces synchronization costs.

---

# Mutable Data

Mutable datasets include:

- health;
- hunger;
- weather;
- economy;
- AI state;
- relationships.

Mutable data is updated only by authorized systems.

---

# Hot and Cold Data

Frequently accessed information ("hot") should be separated from infrequently accessed information ("cold").

Example:

Hot:

- Position
- Velocity
- Health

Cold:

- Biography
- Dialogue History
- Chronicle Metadata

Separating these datasets reduces unnecessary memory traffic.

---

# Data Streaming

Large worlds require incremental data loading.

The architecture supports:

- streaming active regions;
- unloading inactive regions;
- deferred asset loading;
- incremental component initialization.

Streaming policies are defined separately.

---

# Temporary Memory

Simulation systems should prefer temporary working memory for intermediate calculations.

Temporary allocations should be:

- short-lived;
- reusable;
- pooled whenever possible.

Frequent heap allocation during simulation should be avoided.

---

# Memory Pools

Reusable memory pools reduce fragmentation and allocation overhead.

Suitable candidates include:

- entities;
- events;
- pathfinding nodes;
- temporary jobs;
- simulation buffers.

Pooling improves deterministic performance.

---

# Data Versioning

Persistent component formats may evolve over time.

Version metadata accompanies serialized data to support:

- backward compatibility;
- migration;
- world upgrades.

Version handling is defined within the persistence architecture.

---

# SIMD Optimization

Sequential component storage enables vectorized execution.

Examples include:

- movement updates;
- environmental simulation;
- resource decay;
- temperature calculations.

Vectorization should remain transparent to higher-level systems.

---

# NUMA Awareness

Future distributed simulations may operate across NUMA architectures.

The engine should minimize unnecessary memory transfers between processor nodes by maintaining locality wherever practical.

---

# Integration with ECS

The Entity Component System defines *what* data exists.

The Data-Oriented Architecture defines *how* that data is organized in memory.

Together they form the foundation of the simulation engine.

---

# Integration with System Scheduler

The scheduler determines execution order.

The Data-Oriented Architecture determines how efficiently scheduled systems process memory.

Execution performance depends upon both.

---

# Performance Considerations

The architecture should optimize for:

- cache hit rate;
- predictable iteration;
- memory bandwidth;
- low allocation frequency;
- efficient multithreading;
- deterministic execution.

CPU efficiency should improve as data organization improves.

---

# Developer Notes

Project Draugr deliberately separates software architecture from memory architecture.

An elegant API does not guarantee efficient execution.

Engine development should prioritize measurable performance characteristics over abstraction.

Every optimization should be supported by profiling data.

Premature optimization is discouraged.

Poor data organization is not.

---

# Future Expansion

Future versions may introduce:

- automatic archetype packing;
- runtime memory compaction;
- adaptive cache optimization;
- GPU-native component storage;
- compressed simulation datasets;
- heterogeneous memory management;
- hardware-specific optimization layers;
- data-oriented profiling tools.

---

# Revision History

| Version | Date | Description |
|----------|------|-------------|
| 1.0.0 | July 2026 | Initial Data-Oriented Architecture specification. |