# Project Draugr

# 04-16 - Rendering Pipeline

| Field | Value |
|--------|-------|
| Document ID | PD-004.16 |
| Version | 1.0.0 |
| Status | Approved |
| Category | Engine & Technical Architecture |
| Depends On | 04-01 - Entity Component System, 04-02 - System Scheduler, 04-05 - Simulation Pipeline |
| Related Documents | 03-02 - World Simulation Layer, 04-07 - Streaming System, 04-17 - Animation System, 04-19 - Visual Effects |

---

# Purpose

The Rendering Pipeline defines how the simulation state of Project Draugr is transformed into the visual experience presented to the player.

Unlike traditional game engines where gameplay and rendering are tightly coupled, Project Draugr treats rendering as a consumer of authoritative simulation data.

The renderer never owns reality.

It only visualizes it.

---

# Design Philosophy

Reality exists independently of observation.

Rendering is merely observation.

Whether an object is rendered has absolutely no influence on whether it exists.

The renderer is a window into the world—not the world itself.

---

# Core Principles

## Simulation First

Simulation always executes before rendering.

---

## Read-Only Consumer

The rendering pipeline never modifies simulation state.

---

## Deterministic Presentation

Given the same simulation state, the renderer should always produce the same visual output.

---

## Decoupled Architecture

Rendering and gameplay evolve independently.

---

## Performance Adaptive

Visual quality may change according to hardware capability.

Simulation correctness never does.

---

# Responsibilities

The Rendering Pipeline is responsible for:

- visualizing world state;
- camera rendering;
- visibility determination;
- render ordering;
- lighting submission;
- material submission;
- frame composition;
- presentation.

It is not responsible for:

- gameplay;
- physics;
- AI;
- world simulation;
- networking;
- save data.

---

# Rendering Flow

Every rendered frame follows the same sequence.

```text
Simulation Update

↓

World Snapshot

↓

Visibility Determination

↓

Render Queue Generation

↓

Lighting

↓

Materials

↓

Geometry

↓

Post Processing

↓

Frame Composition

↓

Presentation
```

Rendering begins only after simulation has completed its current update.

---

# Simulation Snapshot

The renderer reads from an immutable world snapshot.

The snapshot contains:

- transforms;
- meshes;
- materials;
- lighting data;
- particle states;
- animation poses;
- environmental conditions.

The renderer never accesses mutable simulation data directly.

---

# World Snapshot Isolation

Rendering always operates on a stable snapshot.

Meanwhile:

```text
Simulation

↓

Produces Snapshot A

↓

Renderer Draws Snapshot A

↓

Simulation Produces Snapshot B

↓

Renderer Draws Snapshot B
```

Simulation and rendering never compete for ownership.

---

# Visibility Determination

Only potentially visible objects are submitted.

Visibility considers:

- camera frustum;
- occlusion;
- streaming state;
- distance;
- level of detail;
- environmental conditions.

Invisible objects remain fully simulated.

---

# Render Queue

Visible objects are organized into render queues.

Typical queues include:

- opaque geometry;
- transparent geometry;
- terrain;
- vegetation;
- water;
- sky;
- particles;
- UI.

Queues improve rendering efficiency.

---

# Camera System

The renderer supports multiple cameras.

Examples:

- player camera;
- cinematic camera;
- spectator camera;
- replay camera;
- debugging camera.

Each camera visualizes the same simulation from a different perspective.

---

# Materials

Materials describe visual appearance.

Examples:

- surface color;
- roughness;
- metallic properties;
- transparency;
- emissive effects.

Materials never contain gameplay logic.

---

# Lighting

Lighting is derived from simulation state.

Examples:

- sun position;
- moonlight;
- torches;
- fires;
- magical light sources.

Lighting reflects the simulated world.

---

# Shadows

Shadow generation considers:

- light source;
- geometry;
- time of day;
- atmospheric conditions.

Shadow quality may scale with hardware.

---

# Weather Rendering

Weather visuals are driven entirely by the Weather System.

Examples:

- rain;
- snow;
- fog;
- dust;
- storms.

Visual effects never determine weather.

They represent it.

---

# Environmental Rendering

Environmental state influences presentation.

Examples:

- seasonal colors;
- vegetation density;
- water levels;
- snow accumulation;
- corruption effects.

Visual changes originate from simulation data.

---

# Level of Detail (LOD)

Objects may render using different detail levels.

Factors include:

- distance;
- importance;
- screen size;
- hardware performance.

LOD affects visuals only.

Simulation fidelity remains unchanged.

---

# Culling

Rendering performance is improved through culling.

Examples:

- frustum culling;
- occlusion culling;
- distance culling;
- portal culling.

Culled entities continue existing within the simulation.

---

# Animation Submission

Animation data is prepared after simulation updates.

The Animation System determines poses.

The renderer visualizes them.

---

# Particle Rendering

Particle systems visualize simulation events.

Examples:

- smoke;
- fire;
- rain;
- dust;
- magical energy.

Particles enhance visual fidelity without altering gameplay.

---

# Water Rendering

Water appearance reflects simulation.

Examples:

- wave intensity;
- current direction;
- water level;
- reflections.

The renderer visualizes fluid state generated elsewhere.

---

# Atmospheric Rendering

Atmospheric rendering includes:

- sky;
- clouds;
- volumetric fog;
- sunlight scattering;
- stars;
- auroras.

Atmospheric visuals reflect current world conditions.

---

# Post Processing

Post-processing occurs after geometry rendering.

Examples:

- bloom;
- tone mapping;
- color grading;
- depth of field;
- motion blur;
- ambient occlusion.

Post-processing enhances presentation without modifying simulation.

---

# User Interface Composition

UI renders after world rendering.

Examples:

- HUD;
- inventory;
- dialogue;
- menus;
- developer overlays.

UI remains a separate rendering layer.

---

# Frame Presentation

Completed frames are submitted to the display.

Presentation timing is synchronized independently from simulation updates.

---

# Scalability

Rendering quality scales according to hardware.

Examples:

Low:

- simplified lighting;
- reduced shadows;
- shorter draw distance.

High:

- ray tracing;
- volumetric lighting;
- dense foliage;
- advanced reflections.

Simulation behavior remains identical.

---

# Debug Rendering

Developer tools should visualize:

- collision shapes;
- navigation meshes;
- ECS entities;
- AI paths;
- simulation partitions;
- lighting volumes;
- streaming regions.

Debug rendering must never affect gameplay.

---

# Integration with ECS

Renderable entities expose presentation data through components.

Examples:

- MeshComponent;
- MaterialComponent;
- TransformComponent;
- LightComponent;
- CameraComponent.

Rendering systems consume these components without modifying them.

---

# Integration with Streaming

Only loaded assets are eligible for rendering.

Streaming controls asset availability.

Rendering adapts automatically.

---

# Integration with Simulation Pipeline

The renderer always consumes the finalized simulation snapshot produced by the Simulation Pipeline.

Rendering never bypasses authoritative world state.

---

# Performance Considerations

The Rendering Pipeline should optimize:

- GPU utilization;
- draw call batching;
- instancing;
- visibility determination;
- asynchronous asset loading;
- render graph scheduling;
- frame pacing.

Rendering performance must never compromise simulation integrity.

---

# Developer Notes

The Rendering Pipeline exists to make the invisible simulation visible.

It should be possible to completely replace the renderer while leaving the simulation untouched.

Likewise, the simulation should continue functioning even if nothing is rendered.

This separation is fundamental to Project Draugr's architecture.

The player sees the world because the renderer observes it—not because the renderer creates it.

---

# Future Expansion

Future versions may introduce:

- hardware ray tracing;
- path tracing;
- neural rendering;
- procedural material generation;
- cinematic replay rendering;
- virtual photography tools;
- holographic rendering;
- volumetric global illumination.

---

# Revision History

| Version | Date | Description |
|----------|------|-------------|
| 1.0.0 | July 2026 | Initial Rendering Pipeline architecture defining rendering as a read-only presentation layer over the authoritative simulation. |