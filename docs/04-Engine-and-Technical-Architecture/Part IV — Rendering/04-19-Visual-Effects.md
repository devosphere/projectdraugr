# Project Draugr

# 04-19 - Visual Effects

| Field | Value |
|--------|-------|
| Document ID | PD-004.19 |
| Version | 1.0.0 |
| Status | Approved |
| Category | Engine & Technical Architecture |
| Depends On | 04-05 - Simulation Pipeline, 04-16 - Rendering Pipeline, 04-17 - Animation System |
| Related Documents | 02-08 - Weather System, 02-14 - Corruption System, 02-15 - World Events, 04-18 - Audio System |

---

# Purpose

The Visual Effects (VFX) System defines how temporary visual phenomena are generated from the authoritative simulation.

Visual effects exist to communicate energy, motion, transformation, and environmental processes that cannot be fully expressed through geometry and animation alone.

Every effect is a visual consequence of the world's simulation.

Nothing is created solely for spectacle.

---

# Design Philosophy

The world is constantly changing.

Fire burns.

Rain falls.

Dust rises.

Magic reshapes reality.

Visual effects reveal these processes to the player.

Effects should make the world feel alive—not cinematic.

---

# Core Principles

## Simulation Authoritative

Simulation determines when effects exist.

The VFX System only visualizes them.

---

## Physically Grounded

Whenever possible, effects should follow physical rules.

Smoke rises.

Water splashes downward.

Dust follows wind.

Ash settles.

---

## Temporary Representation

Visual effects never become authoritative world objects.

They visualize transient processes.

---

## Environment Aware

Weather, lighting, terrain, and atmosphere influence every effect.

---

## Performance Adaptive

Visual fidelity may scale according to hardware.

Simulation never changes.

---

# Responsibilities

The Visual Effects System is responsible for:

- particle systems;
- volumetric effects;
- environmental effects;
- destruction effects;
- magical effects;
- impact effects;
- atmospheric effects;
- effect lifecycle management.

It is not responsible for:

- gameplay logic;
- physics simulation;
- AI;
- rendering pipeline scheduling;
- simulation state.

---

# VFX Pipeline

Every visual effect follows the same lifecycle.

```text
Simulation Event

↓

Effect Request

↓

Parameter Generation

↓

Particle / Volume Creation

↓

Environmental Adaptation

↓

Rendering

↓

Expiration
```

Effects exist only while their originating simulation event remains relevant.

---

# Categories of Effects

---

## Environmental Effects

Examples:

- rain;
- snow;
- fog;
- mist;
- dust;
- pollen;
- leaves;
- sand.

Environmental effects continuously represent world conditions.

---

## Fire Effects

Examples:

- flames;
- sparks;
- embers;
- smoke;
- heat distortion.

Fire visuals reflect actual combustion occurring within the simulation.

---

## Water Effects

Examples:

- splashes;
- ripples;
- waterfalls;
- foam;
- bubbles;
- spray.

Water effects depend upon simulated fluid interactions.

---

## Weather Effects

Examples:

- lightning;
- blizzards;
- ash storms;
- sandstorms;
- hail;
- hurricanes.

Weather effects are driven entirely by the Weather System.

---

## Creature Effects

Examples:

- blood;
- footprints;
- breath;
- sweat;
- saliva;
- feathers;
- fur movement.

Creature effects visualize biological processes.

---

## Combat Effects

Examples:

- weapon impacts;
- shield collisions;
- debris;
- broken wood;
- shattered stone.

Combat effects represent physical interactions.

---

## Construction Effects

Examples:

- falling dust;
- flying chips;
- hammer sparks;
- collapsing debris.

Construction becomes visually tangible.

---

## Destruction Effects

Examples:

- collapsing buildings;
- landslides;
- cave-ins;
- explosions.

Effects visualize structural failure.

---

## Magical Effects

Examples:

- spell energy;
- portals;
- enchantments;
- rituals;
- corruption;
- dimensional distortions.

Even fantastical effects obey internally consistent simulation rules.

---

# Particle Systems

Particles represent large numbers of transient objects.

Examples:

- sparks;
- ash;
- snowflakes;
- insects;
- pollen.

Particles should remain lightweight and scalable.

---

# Volumetric Effects

Volume rendering represents continuous media.

Examples:

- fog;
- smoke;
- magical clouds;
- dust storms;
- toxic gas.

Volumes interact naturally with lighting.

---

# Environmental Interaction

Effects adapt to surroundings.

Examples:

Wind →

Smoke bends.

Rain →

Fire weakens.

Water →

Dust disappears.

Snow →

Footprints remain.

Effects respond dynamically to simulation conditions.

---

# Terrain Interaction

Terrain influences effect behavior.

Examples:

Rock →

Stone debris.

Wood →

Splinters.

Sand →

Dust clouds.

Water →

Ripples.

The same event may produce different visuals depending on the environment.

---

# Lighting Interaction

Effects receive and emit light.

Examples:

- glowing lava;
- torch smoke;
- magical energy;
- lightning flashes.

Lighting improves realism without affecting simulation.

---

# Corruption Visualization

The Corruption System influences visual effects.

Examples:

- dark mist;
- floating particles;
- reality fractures;
- unnatural lighting;
- biological mutation effects.

Corruption becomes visually recognizable before it becomes fully understood.

---

# Temporal Effects

Some effects evolve over time.

Examples:

- campfires gradually shrinking;
- smoke dispersing;
- puddles evaporating;
- magical residue fading.

Effect evolution mirrors simulation progression.

---

# Persistence

Certain effects persist briefly after the originating event ends.

Examples:

- smoke after fire;
- footprints after movement;
- scorch marks after explosions.

Persistence improves environmental continuity.

---

# Effect Prioritization

When resources are limited, effects are prioritized by:

- player proximity;
- gameplay relevance;
- visual significance;
- event importance.

Critical information should remain visible.

---

# Synchronization

Visual effects synchronize with:

- animation;
- audio;
- lighting;
- physics.

Multiple presentation systems represent the same simulation event simultaneously.

---

# Streaming Integration

Effect assets stream independently.

Missing assets degrade gracefully using fallback representations.

Simulation continues unaffected.

---

# Debug Support

Developer tools should visualize:

- active emitters;
- particle counts;
- effect lifetimes;
- environmental modifiers;
- simulation event sources;
- GPU usage;
- overdraw analysis.

Debugging should clearly show which simulation event produced each effect.

---

# Performance Considerations

The Visual Effects System should optimize:

- GPU particle simulation;
- effect instancing;
- adaptive quality;
- particle culling;
- volumetric resolution scaling;
- emitter pooling;
- asynchronous asset loading.

Large-scale battles and disasters should remain visually convincing without compromising simulation performance.

---

# Integration with Rendering

The Rendering Pipeline draws visual effects after receiving finalized simulation data.

Rendering displays effects.

It never creates them.

---

# Integration with Audio

Most simulation events produce both:

- visual effects;
- sound effects.

Examples:

Lightning →

Flash + Thunder

Explosion →

Debris + Blast

Campfire →

Flames + Crackling

Presentation systems remain synchronized while remaining independent.

---

# Integration with Simulation

Simulation determines:

- what occurred;
- where it occurred;
- how intense it was.

The VFX System determines only how it appears.

Simulation remains authoritative.

---

# Developer Notes

Visual effects should communicate processes, not decorate gameplay.

Players should recognize approaching storms by seeing distant rain curtains.

They should identify collapsing structures from drifting dust.

They should notice corruption spreading through subtle visual abnormalities before understanding its cause.

The goal is to make the invisible forces of the world visible without ever allowing visuals to become the source of truth.

The world burns first.

Then the player sees the fire.

---

# Future Expansion

Future versions may introduce:

- fully GPU-driven particle simulation;
- fluid simulation;
- volumetric fire;
- procedural destruction visualization;
- biome-specific atmospheric effects;
- adaptive magical visualization;
- photorealistic smoke simulation;
- neural particle generation.

---

# Revision History

| Version | Date | Description |
|----------|------|-------------|
| 1.0.0 | July 2026 | Initial Visual Effects architecture defining simulation-driven transient visual phenomena. |