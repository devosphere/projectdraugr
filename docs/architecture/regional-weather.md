# Regional weather — the design, and why it is not built yet

**Issue:** #912 — *Ordinary storms cover the entire seed world and cannot be escaped by travel.*
**Status:** BUILT. Designed 2026-11-15, shipped 2026-11-16 as V469 + the readers below. Kept because the
reasoning is still the map of this seam — if weather gets another regional feature, start here.

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
V469  ALTER TABLE world_weather
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

## Why it waited, and what changed when it did not have to

Four PRs were in flight when this was written (#926, #927, #928, #929), all touching `ChronicleActionService`.
Resolving the same cross-cutting 22-statement edit four ways is how a careful change becomes a careless one,
so it waited for them to land. It was built on a clean `development` as its own PR, with nothing bundled.

**Two things the design got wrong, found by building it.**

1. **The Java copy of the rule was not needed at all.** This document said `LocalWeatherService.at` would
   compute the footprint in Java and be kept in step with the SQL *by hand*. There is no reason for that:
   `LocalWeatherService`'s query can simply select from `local_sky(chunk)`, so the rule exists ONCE, and the
   hand-sync hazard this document worried about never has to be managed. What genuinely is written twice is
   much smaller — the per-kind intensity, wind and temperature adjustment, which SQL needs to give a chunk
   outside the front a sky of its own — and `TheSkyIsWrittenInTwoPlacesIntegrationTest` holds the two against
   each other.
2. **A drifting front blows off the map.** The first cut drew a start position and moved the centre a chunk
   an hour from it. On the first live run that put a rain front at centre `(-28, 14)` of a 28-wide world: a
   whole rainy day on which no rain fell on any ground anywhere. The seasonal draw says it rains on 28% of
   spring days and that has to keep meaning something. The centre is now a position along a CROSSING — it
   enters just off one edge, is over the middle at midday, leaves by the other, and wraps, with a seed-drawn
   phase so two worlds are not rained on at the same hour.

## What "done" looked like

Taken from #912's own acceptance. Measured against a booted backend on a throwaway Postgres, one chunk at
(4,4) as the day's rain front crossed the map:

```
  00:00  centre x= 12   sky at (4,4): OVERCAST   4.8C  12 kph
  02:00  centre x=  8   sky at (4,4): RAIN       0.2C  24 kph
  06:00  centre x=  1   sky at (4,4): RAIN       1.8C  24 kph
  08:00  centre x= -2   sky at (4,4): RAIN       4.4C  24 kph
  10:00  centre x= -6   sky at (4,4): CLEAR     12.6C   8 kph
  14:00  centre x= 29   sky at (4,4): CLEAR     16.8C   8 kph
```

and, at one simulated minute with a storm centred at (4,4) radius 3, every reader on both sides of its edge:

```
  under    local_sky=STORM   HUD=STORM 5.8C 64kph    guttering=true
  beyond   local_sky=CLEAR   HUD=CLEAR 13.7C 16kph   guttering=false
```

The acceptance, point by point:

- **Two chunks, one minute, different skies** — measured above, and held by
  `AStormHasEdgesIntegrationTest.atOneMinuteTwoGroundsReportDifferentSkies`.
- **The HUD, the narration and the gameplay effects move together** — held by
  `everyReaderOfTheSkyAgreesOnWhichSideOfTheFrontYouAreOn`, which asks four different readers from four
  different files on both sides of the edge. All 22 statements were moved; `grep world_weather` over
  `backend/src/main/java` now finds only `WeatherSimulationService`, which writes it.
- **Reproducible for a seed** — `AFrontHasEdgesTest.theSameSeedDayAndHourAlwaysGiveTheSameFootprint`, and
  `anotherSeedPutsTheFrontSomewhereElse` for the other half of that claim.
- **A null footprint still covers the world**, so an existing save reads exactly as it did until it ticks —
  held in the migration's own `DO` block and again in `aFrontWithNoFootprintStillCoversTheWorld`.
- **Nothing about *when* it rains changed**, only *where*: `pickWeather` is untouched and its distribution
  tests still pass.
- And one the issue did not ask for but the build needed: **no front may ever cover the whole world**, on any
  day, at any hour — `AFrontHasEdgesTest.noFrontEverCoversTheWholeWorld`, which is #912 restated as a guard.
