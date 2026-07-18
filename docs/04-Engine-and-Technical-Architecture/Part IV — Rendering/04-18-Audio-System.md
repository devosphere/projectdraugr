# Project Draugr

# 04-18 - Audio System

| Field | Value |
|--------|-------|
| Document ID | PD-004.18 |
| Version | 1.0.0 |
| Status | Approved |
| Category | Engine & Technical Architecture |
| Depends On | 04-05 - Simulation Pipeline, 04-16 - Rendering Pipeline |
| Related Documents | 02-08 - Weather System, 02-13 - Ecosystem, 02-17 - Player Action System, 02-34 - Creatures, 04-09 - Navigation System |

---

# Purpose

The Audio System defines how the simulation of Project Draugr is transformed into an immersive acoustic experience.

Like rendering, audio is a presentation layer.

The simulation creates events.

The Audio System allows the player to hear them.

Nothing exists because it makes a sound.

It makes a sound because it exists.

---

# Design Philosophy

The world is never silent.

Even when nothing important is happening, the world breathes.

Leaves rustle.

Rivers flow.

Animals call.

Buildings creak.

Wind howls.

Audio should communicate that the world continues existing regardless of whether the player is paying attention.

---

# Core Principles

## Simulation Authoritative

Simulation generates sound-producing events.

Audio represents them.

---

## Physically Grounded

Sounds originate from real sources within the world.

---

## Spatially Accurate

Every sound has a location.

---

## Environmentally Influenced

Terrain, weather, architecture, and atmosphere affect sound propagation.

---

## Information Through Sound

Players should be able to understand the world through hearing alone.

---

# Responsibilities

The Audio System is responsible for:

- sound playback;
- spatial audio;
- environmental acoustics;
- ambient soundscapes;
- music triggering;
- sound propagation;
- reverberation;
- occlusion;
- audio mixing.

It is not responsible for:

- gameplay logic;
- AI decisions;
- event generation;
- simulation timing.

---

# Audio Pipeline

Every audio update follows the same process.

```text
Simulation Update

↓

Audio Events

↓

Sound Source Generation

↓

Propagation

↓

Environmental Processing

↓

Spatialization

↓

Mixing

↓

Output
```

Audio always consumes finalized simulation data.

---

# Sound Sources

Every sound originates from a simulation entity or world event.

Examples:

- creatures;
- NPCs;
- weather;
- rivers;
- waterfalls;
- machinery;
- collapsing structures;
- footsteps;
- combat.

No sound exists without a source.

---

# Categories of Audio

---

## Environmental

Examples:

- wind;
- rain;
- thunder;
- flowing water;
- insects;
- forests;
- caves.

Environmental audio defines atmosphere.

---

## Creature Audio

Examples:

- footsteps;
- breathing;
- growls;
- speech;
- screams;
- eating;
- sleeping.

Creature audio reflects current simulation state.

---

## Object Audio

Examples:

- doors;
- tools;
- weapons;
- armor;
- furniture;
- carts;
- machinery.

Objects produce sound through interaction.

---

## Physics Audio

Examples:

- falling rocks;
- collapsing buildings;
- breaking wood;
- splashing water;
- rolling debris.

Physics events generate procedural sounds.

---

## User Interface

Examples:

- menus;
- notifications;
- inventory.

UI audio remains separate from world audio.

---

## Music

Music supports emotional pacing.

Unlike environmental audio, music is not necessarily diegetic.

Music responds to:

- danger;
- exploration;
- mystery;
- triumph;
- tragedy.

Music never replaces environmental awareness.

---

# Spatial Audio

Every sound possesses:

- position;
- direction;
- distance;
- intensity.

Spatialization enables players to locate events naturally.

---

# Sound Propagation

Sound travels through space.

Factors include:

- distance;
- obstacles;
- elevation;
- atmospheric conditions;
- enclosed spaces.

Sound weakens naturally over distance.

---

# Occlusion

Solid objects obstruct sound.

Examples:

- walls;
- mountains;
- caves;
- buildings.

Occluded sounds become quieter and more muffled.

---

# Reverberation

Environment affects echoes.

Examples:

Open field →

Minimal reflection

Dense forest →

Soft absorption

Stone cathedral →

Long reverberation

Deep cave →

Strong echo

Reverberation communicates environmental scale.

---

# Weather Influence

Weather modifies acoustics.

Examples:

Rain →

Masks distant sounds

Wind →

Carries sound directionally

Thunder →

Dominates ambient mix

Snow →

Softens environmental reflections

Weather changes how the world sounds.

---

# Terrain Influence

Terrain affects sound behavior.

Examples:

- forests absorb sound;
- valleys amplify echoes;
- swamps dampen footsteps;
- stone pathways increase impact sounds.

Acoustics reinforce environmental identity.

---

# Dynamic Soundscapes

Ambient audio evolves continuously.

Examples:

Morning:

- birds;
- insects.

Night:

- owls;
- wolves;
- distant winds.

Cities:

- conversations;
- commerce.

Battlefields:

- distant combat;
- smoke;
- fire.

Soundscapes reflect the living world.

---

# Voice System

Speech audio represents communication.

Features include:

- language support;
- emotional tone;
- shouting;
- whispering;
- crowd conversations.

Speech originates from actual dialogue events.

---

# Procedural Audio

Some sounds are generated dynamically.

Examples:

- material impacts;
- debris;
- weather intensity;
- crowd density;
- equipment noise.

Procedural generation reduces repetition.

---

# Audio Prioritization

When too many sounds occur simultaneously, priority is determined by:

- proximity;
- importance;
- threat level;
- player relevance.

Important information should remain audible.

---

# Listener

Normally the player camera acts as the primary listener.

Additional listeners may exist for:

- replay systems;
- cinematic cameras;
- debugging tools.

---

# AI Integration

Sound is not only presentation.

Simulation also produces auditory perception events for AI.

Examples:

Footsteps →

Nearby guard investigates.

Explosion →

Wildlife flees.

Tree falls →

Villagers become alert.

The simulation event feeds both:

- AI hearing;
- player audio.

The Audio System visualizes (acoustically) the same event.

---

# Accessibility

Support includes:

- subtitle integration;
- dynamic range presets;
- hearing accessibility profiles;
- independent volume controls;
- visual sound indicators (optional).

Accessibility should not compromise simulation accuracy.

---

# Debug Support

Developer tools should visualize:

- sound emitters;
- propagation radius;
- occlusion paths;
- reverberation zones;
- active audio channels;
- listener position;
- mixing priorities.

Audio debugging should reveal why a sound is heard—not merely that it is playing.

---

# Performance Considerations

The Audio System should optimize:

- emitter culling;
- virtual voices;
- adaptive update frequency;
- streaming audio assets;
- propagation caching;
- hardware acceleration.

Audio complexity should scale independently of simulation complexity.

---

# Integration with Rendering

Rendering provides visual observation.

Audio provides auditory observation.

Both consume the same authoritative simulation snapshot.

Neither modifies world state.

---

# Integration with Simulation

Simulation determines:

- what happened;
- where it happened;
- when it happened.

The Audio System determines:

- how it sounds.

Simulation remains authoritative.

---

# Developer Notes

The Audio System should make it possible for a player to close their eyes and still understand the world around them.

A player should hear:

a river before seeing it,

a predator before encountering it,

a marketplace before entering it,

a collapsing building before the dust reaches them.

When players instinctively react to sounds rather than UI notifications, the Audio System has achieved its purpose.

---

# Future Expansion

Future versions may introduce:

- physically simulated acoustics;
- wave-based sound propagation;
- biome-specific ambient composers;
- adaptive orchestral music;
- procedural creature vocalizations;
- voice synthesis;
- vibration and haptic audio integration;
- full acoustic simulation for destructible environments.

---

# Revision History

| Version | Date | Description |
|----------|------|-------------|
| 1.0.0 | July 2026 | Initial Audio System architecture defining sound as the acoustic representation of the authoritative simulation. |