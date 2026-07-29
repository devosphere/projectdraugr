# Project Draugr UI Specification v0.1.0

**Status:** Active
**Released:** 2026-07-29  
**Scope:** Local-first MVP onboarding and first forest playthrough shell.

## Purpose

This is the authoritative acceptance specification for the current player experience. It does not reveal canonical Overseer/world-seed information to the player.

## Delivery status

- **Implemented shell:** onboarding, awakening transition, forest playthrough framing, qualitative preview HUD, and constrained Action Composer.
- **In progress:** wiring the Action Composer to authoritative action resolution and replacing preview values with the active Chronicle's complete state.
- **Specified next:** settings persistence and host-window/display application. The current Settings dialog is a presentation prototype; its selections do not yet persist or change host resolution.

## Onboarding

- The screen uses original manhua-inspired environmental art with no visible human or NPC.
- It presents: **Awaken** when no Chronicle is living; **Soul Link** when a living Chronicle exists; **Chronicle Archive**; and **Settings**.
- It displays: “Every life leaves a mark. The world remembers. The Overseer never forgets.”
- A Chronicle's starting location is selected by the system. The player receives no spawn-location choice.
- The awakening text is first person and presents a painful, disorienting consciousness transition from Earth, followed by restrained residual confusion beneath an unfamiliar sky.
- The confirmation text and its button are centered.

## Playthrough layout

- The primary view is a first-person environmental backdrop. v0.1.0 uses the forest backdrop.
- The header displays only player-permitted environmental information such as time and weather.
- The Body HUD presents qualitative state only: health, hunger, thirst, energy, temperature, wetness, bladder, bowel, and hygiene. It never exposes numerical values.
- The narration panel is the largest content region. It describes allowed perception and resolved consequences; it never diagnoses internal state, gives survival advice, creates objectives, or exposes unknown world facts.
- The Action Composer accepts at most 2,500 characters, displays no more than four lines before scrolling, submits with Enter, and inserts a new line with Shift+Enter.
- While the world is resolving an action, the interface uses a non-intrusive rotating hourglass/loading state. It must not insert placeholder narration that changes layout.
- The expandable menu is a compact hamburger control. It lists only systems with current player-permitted state. Crafting and Construction are absent until the Chronicle has discovered a usable method or project.

## Settings

- Settings affect presentation and accessibility only; never world rules, physiology, time, or difficulty.
- v0.1.0 defines common display-resolution choices where supported by the host, UI scaling, text/readability options, and reduced motion.
- When settings persistence is implemented, it must be local and separate from Chronicle/world state.

## Out of scope

- Canonical Overseer Atlas access in player mode.
- Chronicle map, equipment, storage, crafting, construction, and archive detail views.
- Stream and quarry backdrops; these are planned compatible additions.
- Multiplayer UI.

## Queued next slice

- Extend the authoritative location response with biome/location presentation data.
- Select the playthrough backdrop from that authoritative location data, so a Chronicle sees the appropriate environment after travel rather than a fixed forest scene.

## Change log

### v0.1.0 — 2026-07-29

- Established the first versioned contract for the approved onboarding and forest playthrough UI.
