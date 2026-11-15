# Regional weather — the design, and why it is not built yet

**Issue:** #912 — *Ordinary storms cover the entire seed world and cannot be escaped by travel.*
**Status:** designed, not implemented. Written 2026-11-15 so the next cycle executes rather than re-derives.

## What is wrong

`world_weather` holds **one row per world**. A Chronicle on seed 681013497 — 560 tiles across a 28×20 map —
spawned in a storm, travelled a long way, and never left it. The same is true of rain and snow.

`BiomeClimate.at` already varies the *felt* weather by geography: altitude cools it by lapse rate, latitude
shifts it, humidity biases rain↔snow, so a storm can arrive as snow on high ground. What it cannot do is let
one region be under the front while another is not, because there is only ever one front and every chunk is
inside it. `WeatherSimulationService`'s own javadoc records this as the last unfinished piece of #28.

## The design

**One front, with a footprint.** Keep the single daily draw — it is reproducible, seasonal and correctly
weighted, and none of that needs to change. Give the front a **centre and a radius**, derived from the same
`(seed, day)` SplitMix64 stream that already picks the kind, so it stays reproducible per seed. Let the
centre **drift with the hour**, so a front crosses the map over a day and a Chronicle can walk out from under
it or be caught by it.

```
V4xx  ALTER TABLE world_weather
        ADD COLUMN front_centre_x    SMALLINT,
        ADD COLUMN front_centre_y    SMALLINT,
        ADD COLUMN front_radius_chunks SMALLINT;
```

Nullable on purpose: a row written before the migration has no footprint, and **a null footprint means the
front covers everything**, which is exactly today's behaviour and the right thing to show for a save that has
not ticked since.

**CLEAR and OVERCAST have no footprint.** They are not fronts; they are what the sky does when no front is
overhead. Only RAIN, STORM and SNOW get a bounded one — a storm about a third of the map across, rain about
half, which on 28×20 leaves real ground outside.

**Outside the footprint the sky is ordinary.** Inside the radius, the front's kind. Within a margin beyond it,
OVERCAST — the edge of a weather system is not a wall. Beyond that, CLEAR. Temperature and wind must be
corrected with the kind, not inherited: `ambient_temperature_c` carries the front's own adjustment (−3 for
rain and storm, −5 for snow, +2 for clear), so a chunk outside the front has to have that backed out and its
own applied, or a sunlit tile will read three degrees cold for a storm it is nowhere near.

## Why it is not a small change

**The one-seam claim in `LocalWeatherService` is aspirational.** That class says it is the only place the
environment query exists. It is not: **22 statements across 8 files read `world_weather` directly.**

| file | direct reads |
| --- | --- |
| `ChronicleActionService` | 7 |
| `PhysicalItemService` | 4 |
| `FireService` | 2 |
| `ChroniclePhysiologyService` | 2 |
| `BiomeClimate` | 1 |
| `ChronicleService`, `ConstructionService`, `WildlifeSimulationService` | the rest |

If only `LocalWeatherService` honours the footprint, the other eight keep answering from the global front —
and the player gets a HUD that says clear while their fire's ember hazard, their wet work-in-progress and the
wildlife around them all still think it is storming. That is this project's recurring defect (two readers of
one question) introduced deliberately, which is worse than the bug being fixed.

**So the footprint must be readable from SQL as easily as from Java.** The shape that does it:

```sql
CREATE FUNCTION local_weather_kind(chunk UUID) RETURNS TEXT ...
```

returning the front's kind inside the footprint and CLEAR/OVERCAST outside, so each of the 22 call sites
changes by one expression — `ww.weather_kind` becomes `local_weather_kind(wc.id)` — rather than being
rewritten. `LocalWeatherService.at` computes the same rule in Java for the readers that already go through
it, and the two must be kept in step **by hand**, exactly as the routing-reachability probe is (see
`routing-and-coverage-strategy.md`, which records what happened when that one drifted).

## Why it waits

Four PRs were in flight when this was written (#926, #927, #928, #929) and all four touch
`ChronicleActionService`; two also touch `PhysicalItemService`. A change that edits 22 statements across both
files would conflict with every one of them, and resolving the same cross-cutting edit four ways is how a
careful change becomes a careless one. **Do this on a clean `development`, as its own PR, and do not bundle
anything else into it.**

## What "done" looks like

Taken from #912's own acceptance, and all of it is testable:

- At one simulated time, two exposed chunks on opposite sides of the boundary report different kinds.
- Travelling across the boundary changes the HUD, the narration and the gameplay effects together — which is
  only true if all 22 readers were moved.
- Reproducible for a seed: the same `(seed, day, hour)` always gives the same footprint.
- A null footprint still covers the world, so an existing save reads exactly as it does today until it ticks.
- Seasonal and regional frequency still comes from the existing weighted draw; nothing about *when* it rains
  changes, only *where*.
