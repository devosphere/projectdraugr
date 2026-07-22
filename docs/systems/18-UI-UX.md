# 18 – UI & UX

> **Project Draugr**
>
> *The interface should disappear. The world should remain.*

---

# Purpose

This document defines the User Interface (UI) and User Experience (UX) philosophy of Project Draugr.

The interface exists only to provide information that the player cannot naturally perceive through a computer screen.

Whenever possible, the world itself communicates information.

The interface supplements reality.

It never replaces it.

---

# Design Philosophy

Project Draugr follows a **World-First Interface Philosophy**.

The interface should never become the player's primary source of understanding.

Instead:

- the world communicates external information;
- the HUD communicates internal information.

The player is responsible for observing, interpreting, and acting upon that information.

---

# Information Responsibility Principle

Project Draugr provides truthful information.

It does not make decisions for the player.

The player is responsible for:

- observation
- interpretation
- planning
- survival

Failure caused by ignored information is considered part of survival rather than poor game design.

The game never protects players from negligence.

---

# Internal vs External Information

## Internal Information

Internal information represents conditions that the character physically feels but the player cannot.

These are permanently available through the Body HUD.

Examples include:

- Health
- Hunger
- Thirst
- Energy
- Body Temperature

The narrator never reminds players about these conditions.

The HUD already provides that information.

---

## External Information

External information belongs to the world.

Examples include:

- weather
- daylight
- clouds
- rainfall
- snow
- wind
- wildlife behavior
- environmental sounds

These are communicated primarily through narration and world presentation.

The player learns through observation.

---

# Body HUD

The Body HUD replaces physical sensations the player cannot experience.

Example:

```text
══════════════════════════════

DATE
July 20, 2026

TIME
16:34

BODY

Health
Healthy

Hunger
Satisfied

Thirst
Hydrated

Energy
Rested

Temperature
Comfortable

══════════════════════════════
```

No numerical values are displayed.

Body conditions are expressed through natural language.

---

# Time

Project Draugr progresses in real time.

For the MVP:

Server Time = Regional World Time

The Body HUD always displays:

- Date
- Time

Future versions may introduce multiple regional time zones as the world expands.

---

# Season & Weather

Season and weather belong to the external world.

They are not explained through text inside the HUD.

Instead, the HUD may display only simple visual indicators.

Examples:

☀️

🌧️

⛈️

❄️

🌫️

Players are expected to understand their meaning through observation and experience.

The narrator describes environmental conditions naturally.

---

# Narrator Responsibilities

The narrator never:

- reminds players to eat
- reminds players to drink
- reminds players to sleep
- recommends actions
- explains solutions

Instead, the narrator describes the world.

Examples:

"The afternoon wind grows colder."

"Dark clouds gather beyond the western ridge."

"The forest falls unusually silent."

"The stream flows lower than yesterday."

Consequences emerge naturally through player decisions.

---

# Consequences

Ignoring information produces realistic outcomes.

Example:

The HUD indicates:

Hunger
Starving

The narrator says nothing.

The player continues chopping wood.

The narrator describes:

"After only a few swings, your arms begin to weaken."

Eventually:

"You collapse."

The game never reminds.

The world simply reacts.

---

# Equipment

Players possess no abstract inventory.

Equipment is physically worn or carried.

Equipment slots include:

Head

Torso

Hands

Legs

Feet

Additional equipment depends entirely upon crafted or discovered items.

---

# Containers

Project Draugr contains no universal inventory.

Instead, the world contains physical containers.

Examples include:

Pockets

Carrier Nets

Sling Carriers

Backpacks

Crates

Shelves

Storage Boxes

Carts

Wagons

Warehouses

Every container exists physically within the world.

---

# Physical Storage

Players may inspect only containers that are physically accessible.

If a container is too far away:

Its contents cannot be viewed.

The game does not display remote storage contents.

Players must physically return to that location.

---

# Weight & Dimensions

Characters possess physical characteristics.

Examples include:

Height

Weight

Body Build

These influence realistic carrying capability.

Every object possesses:

Weight

Volume

Physical Dimensions

Whether an object can be carried depends upon available space and physical limitations rather than abstract inventory slots.

---

# Nearby Objects

Players primarily interact with nearby objects.

Examples include:

Campfire

Drying Rack

Tool Rack

Storage Chest

Large Sling Carrier

Wild Boar

Interaction begins by approaching the object.

The interface reflects physical presence rather than global accessibility.

---

# Item Identity

Every physical object possesses a permanent Item ID.

The Item ID never changes.

The game does not permanently track ownership.

Objects simply exist somewhere within the world.

Their location determines accessibility.

---

# Engravings

Players may engrave objects.

Engravings become permanent parts of the object.

Examples include:

Names

Dates

Messages

Symbols

Memorials

Engravings remain visible for future Chronicles.

---

# Observer-Dependent Information

Project Draugr does not reveal identical information to every player.

Object descriptions depend upon the observer's knowledge.

Examples:

A beginner examining a sword may observe:

"Iron Sword."

An experienced blacksmith may observe:

"Well-balanced forged blade."

A master smith may observe:

"Medium-carbon forged blade with exceptional heat treatment."

The object never changes.

Only the observer's understanding changes.

---

# Chronicle

The Chronicle serves as the player's personal record.

It contains:

Journal

Research

Maps

Discoveries

Construction Records

Personal History

The Chronicle grows throughout the player's life.

---

# Menus

Menus remain simple and purposeful.

Examples include:

Chronicle

Crafting

Construction

Settings

Menus exist only when necessary.

---

# Accessibility

Project Draugr should support accessibility whenever possible.

Examples include:

Adjustable Text Size

Colorblind Modes

Remappable Controls

Subtitle Support

Audio Controls

Accessibility should never compromise immersion.

---

# Design Principles

The interface should always remain:

Minimal

Immersive

Consistent

Responsive

Readable

Observer-Centered

The world remains the primary interface.

---

# Final Principle

Project Draugr does not attempt to make decisions for the player.

It provides truthful information.

The player survives—or dies—according to how well they understand the world.

The interface exists only to replace the senses that reality already provides.

Nothing more.