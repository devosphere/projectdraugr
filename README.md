# Project Draugr

> **Every life leaves a mark.**  
> *The world remembers. The Overseer never forgets.*

---

# Overview

Project Draugr is a browser-based, persistent survival RPG set in a mysterious world existing parallel to Earth.

Players begin as ordinary people from modern-day Earth who are suddenly transported into another universe. There are no chosen heroes, predefined classes, or scripted destinies. Every player arrives with nothing except the clothes they were wearing at the moment of transportation.

The world does not exist for the player.

The player becomes part of a world that has existed long before their arrival and will continue long after their death.

---

# Vision

Project Draugr aims to create a living world where stories emerge naturally through player decisions, environmental simulation, and the passage of real time.

Rather than following quests or predefined narratives, players create their own Chronicle through survival, exploration, learning, and the consequences of their actions.

Every Chronicle is unique.

Every life leaves a permanent mark.

---

# World Philosophy

## The World Exists Without Humanity

The transported humans are not arriving in an established fantasy civilization.

There are no kingdoms waiting to be ruled.

No villages.

No towns.

No cities.

No merchants.

No blacksmiths.

No rulers.

No human empires.

Instead, the world is an ancient, living ecosystem containing:

- Forests
- Rivers
- Lakes
- Mountains
- Plains
- Swamps
- Oceans
- Wildlife
- Monsters
- Ancient ruins
- Forgotten structures
- Lost civilizations

The evidence of intelligent civilizations exists only through ruins, relics, abandoned architecture, and forgotten history.

What destroyed them—or whether they simply disappeared—remains one of the world's greatest mysteries.

Human civilization begins only when transported humans choose to build it.

---

## Civilization Is Player-Created

Every settlement within Project Draugr originates from player actions.

Every camp.

Every bridge.

Every farm.

Every road.

Every village.

Every city.

Every kingdom.

Every empire.

Exists because one or more Chronicles built it.

If civilization flourishes, players created it.

If civilization collapses, the world simply remembers.

Future Chronicles may rediscover forgotten settlements built years earlier by players who are long gone.

---

## The World Is Indifferent

Project Draugr is not designed around fairness.

Nature is neither cruel nor kind.

The world does not exist to help the player.

Nor does it exist to destroy them.

The world simply exists.

Players survive only by understanding it.

---

# Core Principles

## One Persistent World

Project Draugr contains a single canonical world shared across every Chronicle.

Mountains never move.

Rivers never change location.

Ancient ruins remain where history placed them.

Only the world's state evolves.

---

## One Chronicle

Each player may possess only one active Chronicle.

When a character dies, that Chronicle permanently concludes.

Death cannot be undone.

There are no save reloads.

A new Chronicle begins as another transported human entering the same persistent world.

History continues.

---

## History Is Permanent

Buildings.

Roads.

Graves.

Journals.

Settlements.

Discoveries.

These become part of the world's history whenever technically possible.

Future Chronicles inherit the consequences of those who came before them.

---

## Actions Define Identity

Players never choose:

- Classes
- Professions
- Backgrounds
- Skill Trees

Identity emerges naturally through repeated actions.

A farmer becomes a farmer through farming.

A builder through building.

A hunter through hunting.

Knowledge replaces experience points.

Practice replaces levels.

The world recognizes capability—not labels.

---

## Real-Time Continuity

Project Draugr progresses alongside the real world.

The in-game calendar and clock mirror real-world date and time.

The world continues evolving even while players are away.

Before leaving, players are responsible for preparing their lives for their absence.

A well-managed settlement may survive weeks.

A poorly prepared camp may not survive a single night.

The world never pauses.

---

# Invisible Forces

## The Overseer

An unseen entity known only as **The Overseer** silently watches every transported human.

The Overseer does not command.

It does not reward.

It does not punish.

It does not intervene.

It simply observes.

Remembers.

Records.

Every Chronicle.

Every discovery.

Every settlement.

Every failure.

Every death.

Its true purpose remains unknown.

---

## The Primordial Principles

Beyond the visible world exist two primordial principles.

**Creation**

**Destruction**

Whether these are beings, intelligences, forces, or something beyond mortal understanding has never been confirmed.

No player knows they exist.

Most never will.

Creation is not good.

Destruction is not evil.

Neither seeks dominance.

For if either were to completely overcome the other, existence itself would cease.

Creation expands existence.

Destruction renews existence.

The world remains alive only because neither primordial principle ever truly triumphs.

Everything that exists is born from their eternal balance.

---

# Emergent World Simulation

The world evolves independently of player presence.

Wildlife migrates.

Monsters establish territories.

Vegetation grows.

Vegetation dies.

Weather changes.

Resources regenerate.

Civilizations built by players rise and fall.

The simulation never waits for the player.

The player participates within it.

They are never its center.

---

# What Makes Project Draugr Different?

- 🌍 One persistent world shared across every Chronicle.
- 🌲 A living ecosystem without existing human civilization.
- 🏛 Civilization is entirely player-created.
- 💀 Permanent death with no save reloading.
- ⏰ Real-world synchronized time.
- 🏡 Responsibility-based offline survival.
- 📖 Emergent storytelling instead of scripted quests.
- 🧠 Knowledge-driven progression instead of experience levels.
- 👁 The Overseer silently preserving every Chronicle.
- ⚖️ A world sustained by the eternal balance of Creation and Destruction.
- 🪦 Previous Chronicles permanently shape future ones.

---

# Project Philosophy

Project Draugr is not about saving the world.

It is not about becoming the chosen one.

It is not about defeating an ultimate evil.

It is about existing within a world that neither knows nor needs you.

The game asks only one question:

> **Who do you become when everything familiar is taken away?**

The answer is never given.

It is discovered through the choices each player makes.

Every life leaves a mark.

The world remembers.

The Overseer never forgets.

---

# Documentation

Project documentation is organized under the `/docs` directory.

| Document | Description |
|----------|-------------|
| `00-Project-Overview.md` | Executive overview of the project. |
| `01-Vision-Bible.md` | Creative vision and design philosophy. |
| `02-Game-Design-Document.md` | Gameplay systems and mechanics. |
| `03-Technical-Requirements.md` | Technical requirements and implementation goals. |
| `04-System-Architecture.md` | High-level software architecture. |
| `05+` | Additional design and engineering documentation. |

---

# Current Status

Project Draugr is transitioning from the **Blueprint Phase** into an implementation foundation. The initial backend lives in [`backend/`](backend/) and provides PostgreSQL-backed runtime objects, immutable world events, and an authoritative simulation tick boundary.

## Local development

Start PostgreSQL with `docker compose up -d`, then run the Spring Boot service from `backend/` with Maven: `mvn spring-boot:run`. If using this workspace's local Maven installation, run `..\.tools\apache-maven-3.9.11\bin\mvn.cmd spring-boot:run` instead. The service applies its Flyway migrations at startup. `POST /api/simulation/ticks` advances authoritative simulation time and appends an immutable tick event.

For the current local MVP launcher, start Docker Desktop and run `powershell -ExecutionPolicy Bypass -File .\scripts\Start-Draugr.ps1`. It starts PostgreSQL, backend, and frontend, then opens the local game. This is the operational precursor to the packaged desktop executable.

For a normal double-click launch, use `Project-Draugr.cmd` in the repository root after Docker Desktop is running. It invokes the same checked launcher and keeps any startup error visible instead of silently closing.

To stop the local stack without deleting its persistent world data, run `powershell -ExecutionPolicy Bypass -File .\scripts\Stop-Draugr.ps1`.

After a launch, verify the exact backend, database migration, and read-only Persistent State Auditor with `powershell -ExecutionPolicy Bypass -File .\scripts\Verify-Draugr.ps1`. A successful verification is the local-play prerequisite before beginning a new Chronicle.

Before testing a risky playthrough, create a local database snapshot with `powershell -ExecutionPolicy Bypass -File .\scripts\Backup-Draugr.ps1`. Backups are stored under ignored `backups/` and are never committed.

To recover a stopped local world from a chosen backup, run `powershell -ExecutionPolicy Bypass -File .\scripts\Restore-Draugr.ps1 -BackupFile .\backups\draugr-YYYYMMDD-HHMMSS.sql`. Restore deliberately asks for the exact word `RESTORE` because it replaces the current local database. It is never performed automatically.

To begin again from a fresh local world, first stop Draugr, then run `powershell -ExecutionPolicy Bypass -File .\scripts\Reset-Draugr.ps1`. It creates a backup before requiring the exact confirmation `RESET WORLD`, then clears only the local database. Launch Draugr again to apply current migrations.

The local frontend is in `frontend/`. Run `npm install` and `npm run dev` there after the backend is running. It displays the server's current simulation state and can request a simulation tick; it never determines state locally.

The immediate objective is to build a believable persistent world simulation.

Artificial intelligence is considered an evolutionary enhancement—not the foundation of the project.

The world must feel alive before it becomes intelligent.

---

# License

This repository is private.

All concepts, documentation, artwork, source code, and assets are proprietary and may not be copied, redistributed, or reused without explicit permission from the project owner.

---

> **Project Draugr**
>
> **Every life leaves a mark.**
