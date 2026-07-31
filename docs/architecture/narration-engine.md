# Narration Engine

> **Project Draugr — Architecture**
>
> *The world describes itself. The AI adds the feeling.*

---

## Purpose

This document defines the hybrid narration architecture for Project Draugr.

Two components work in sequence:

1. **NarrationEngine** — deterministic prose generator. Produces complete, factually correct, witness-stance narration for every action. Zero API cost.
2. **NarrationRouter** → **Claude API refinement** — routes selected moments to the AI, which adds one to two sentences of atmospheric flavor on top of the engine's output.

See DR-0016 (hybrid narration decision) and DR-0017 (router routing rules).

---

## Why Two Layers

Full AI generation from a raw PerceptionFrame costs approximately 800 output tokens per call.

AI refinement of existing backend prose costs approximately 60 output tokens per call — roughly 13× cheaper per meaningful moment.

More importantly: the NarrationEngine produces correct prose unconditionally. If the Claude API is unavailable, the game continues functioning at full correctness. The AI is the upgrade layer, not the dependency.

---

## ActionInputClassifier — Pre-Pass Filter

**Class:** `com.devosphere.draugr.narration.ActionInputClassifier`

**Runs:** Before `classifyIntent()` in `ChronicleActionService.resolve()`. First thing. Every time.

**Method:** `classify(String actionText) → InputClass`

### Classification Categories

| Class | Triggers | Time passes | Physiology | World state | AI call |
|-------|----------|-------------|-----------|-------------|---------|
| `PERSONAL_PHYSICAL_ACT` | masturbate, sexual self-acts | YES — 20 min | YES — energy, hunger, thirst, hygiene drain | NO | NO |
| `AGGRESSION_TOWARD_WILDLIFE` | sexual acts toward creature, violent contact attempt | YES — 5 min | YES — injury from resistance | YES — population ALERT | NO |
| `AGGRESSION_TOWARD_INANIMATE` | "fuck that tree", profanity as verb at object | YES — 1 min | energy -2 | NO | NO |
| `NONSENSICAL` | gibberish, < 3 chars, keyboard mash, zero recognizable words | NO | NO | NO | NO |
| `PHYSICALLY_IMPOSSIBLE` | fly, teleport, cast spell, summon, turn invisible | NO | NO | NO | NO |
| `VALID` | everything else | normal | normal | normal | per router |

### Physiological Consequences

#### PERSONAL_PHYSICAL_ACT (masturbation, sexual self-acts)

The body is performing physical activity. Physics applies.

| Physiology field | Change per act |
|-----------------|---------------|
| energy | -15 (moderate exertion) |
| hunger | -8 (caloric burn) |
| thirst | -10 (fluid loss) |
| hygiene | -12 |
| bladder | +5 |
| duration | 20 minutes of world time |

Repeated use: Energy depletes toward Exhausted → Collapse. Thirst accelerates toward Critical Dehydration. The physiology tick applies the same death vectors as any other cause. A chronicle who does this instead of eating, drinking, or sleeping will die. No special case needed — the simulation handles it.

`chronicle_action` IS written with intent `PERSONAL_ACT`.

#### AGGRESSION_TOWARD_WILDLIFE

The animal responds to physical threat.

- Triggers passive encounter logic equivalent to `CONFRONT_WILDLIFE` with tactic_bonus = -20.
- The animal resists. Injury severity follows normal wound formula.
- Population shifts to ALERT or FLEEING.
- Duration: 5 minutes.
- `chronicle_action` IS written.

#### AGGRESSION_TOWARD_INANIMATE

- Energy -2. 1 minute passes. `chronicle_action` IS written. No world_object changes.

### Detection Strategy

- **PERSONAL_PHYSICAL_ACT:** keywords: masturbate, masturbation, jerk off, touch myself, pleasure myself. Case-insensitive.
- **AGGRESSION_TOWARD_WILDLIFE:** sexual keywords + animal noun proximity; also violent contact toward named creature without weapon equipped.
- **AGGRESSION_TOWARD_INANIMATE:** profanity as leading verb + inanimate noun target.
- **NONSENSICAL:** length < 3 chars, or > 80% non-alphabetic, or zero recognizable words in first 5 tokens.
- **PHYSICALLY_IMPOSSIBLE:** fly, teleport, disappear, cast spell, summon, turn invisible, become [non-human creature].

### Narration Templates (witness-stance, flat, no judgment)

**PERSONAL_PHYSICAL_ACT:**
- "The act is done. Twenty minutes have passed. The forest does not acknowledge it."
- "The body does what it does. Time passes. The hunger is slightly deeper. The world waits."
- "It is finished. The energy cost is real. The world is unchanged."

**AGGRESSION_TOWARD_WILDLIFE:**
- "The [species] does not cooperate with that. It responds the only way it knows."
- "The animal reads the approach as threat. What follows is the animal's answer, not yours."

**AGGRESSION_TOWARD_INANIMATE:**
- "The [object] receives that. It does not respond. A moment has passed."
- "The sentiment goes into the air. Nothing moves that was not already moving."

**NONSENSICAL:**
- "The body has no shape for that intent. Nothing moves."
- "That resolves into nothing. The ground is still beneath you."

**PHYSICALLY_IMPOSSIBLE:**
- "The attempt produces exactly what the physics of this place allow: nothing."
- "The body reaches toward that and finds no path. It stands where it stood."

### What the Narrator Never Does

- Never expresses surprise, humor, disgust, or moral judgment.
- Never hints at what the player should do instead.
- Never names the act as wrong, inappropriate, or unusual.
- Never breaks from witness-stance.

The world applies physics. That is all.

---

## NarrationEngine

**Class:** `com.devosphere.draugr.narration.NarrationEngine`

**Role:** Generates witness-stance prose for every `(intent, outcome, context)` combination. Never hints. Never advises. Never names Body HUD labels.

### Template Structure

Templates are composed from fragments across five dimensions:

| Dimension | Examples |
|-----------|---------|
| `intent × outcome` | GATHER_PLANT/SUCCESS, CONFRONT_WILDLIFE/PARTIAL, FISH/FAIL |
| `species` | gray_wolf, common_adder, chanterelle, river_trout |
| `biome` | FOREST, HIGHLAND, WETLAND, CAVE |
| `severity_tier` | MINOR (<15), MODERATE (15–34), SERIOUS (35–69), CRITICAL (70–99) |
| `time_of_day` | DAWN, MIDDAY, DUSK, NIGHT |

The engine selects and assembles fragments into a coherent 2–3 sentence narration. No sentence begins with "You should" or ends with a question about the player's next step.

### Intent × Outcome Coverage

Every dispatched intent must have NarrationEngine coverage before Sprint 002 closes.

**Gathering / Flora:**

| Intent | Outcome | Template focus |
|--------|---------|---------------|
| GATHER_PLANT | SUCCESS | What was found, how it came away, sensory detail of the plant |
| GATHER_PLANT | FAIL | Site empty or season wrong — what the hand found instead |
| FELL_TREE | SUCCESS | The fall, the weight of the log, the gap in the canopy |
| FELL_TREE | FAIL_NO_TOOL | The attempt, the unresponsive bark, what the hands could not do |
| GATHER_CLAY | SUCCESS | Soil texture, color, yield |
| STRIP_BARK | SUCCESS | The peel, the white wood underneath, the roll of bark |

**Survival / Fire:**

| Intent | Outcome | Template focus |
|--------|---------|---------------|
| LIGHT_FIRE | LIT | The catch, the first smoke, the spread of heat |
| LIGHT_FIRE | NO_KIT | The attempt, the hands, nothing catches |
| LIGHT_FIRE | NO_TINDER | The kit works but nothing holds the ember |
| ADD_FUEL | SUCCESS | The fire responds, the light changes |
| MAKE_CHARCOAL | SUCCESS | The slow smolder, the blackened result |
| SLEEP | SUCCESS | What the body felt, the passage of time, what greeted the waking |
| SLEEP | DISTURBED | What broke the rest |

**Wildlife / Combat:**

| Intent | Outcome × Species | Template focus |
|--------|------------------|---------------|
| CONFRONT_WILDLIFE | SUCCESS (kill) × gray_wolf | The kill, the silence after, the weight of the animal |
| CONFRONT_WILDLIFE | PARTIAL (wound) × brown_bear | The impact, the damage, the bear retreating |
| CONFRONT_WILDLIFE | FAIL × eurasian_lynx | The ambush, the blood, what survived and fled |
| HARVEST | SUCCESS | The work of butchering, the yield, the state of the carcass |
| FISH | SUCCESS × river_trout | The catch, the struggle, the silver of the fish |
| FISH | FAIL | The water, the waiting, what passed beneath |
| TRACK | FOUND × PRINTS | What the ground held, the direction it pointed |
| TRACK | NOTHING | What the terrain offered, what it withheld |
| TAME | TRUST_GAIN | The animal's response, the distance it allowed |
| TAME | TRUST_LOSS | What broke the moment, the retreat |

**Literature / Writing:**

| Intent | Outcome | Template focus |
|--------|---------|---------------|
| WRITE | SUCCESS | The act of marking, the surface, what the hand recorded |
| EDIT_DOCUMENT | SUCCESS | The revision, what was struck and what replaced it |
| SKETCH_MAP | SUCCESS | The effort of memory, the lines on the surface |

**Observation:**

| Intent | Attention | Template focus |
|--------|-----------|---------------|
| OBSERVE | HIGH | Full biome sensory panorama — light, sound, smell, movement, distance |
| OBSERVE | MODERATE | Partial scene — what was in motion, what was still |

### Species-Specific Fragments

The engine maintains a fragment library per species for wounds, kills, and behavioral outcomes. Examples:

```
gray_wolf/wound: "The wolf's jaws close on [body_region] with a snap of force..."
gray_wolf/kill:  "The wolf goes down hard, legs finding no purchase..."
gray_wolf/flee:  "The wolf breaks, low to the ground, and the forest closes around it..."

common_adder/attack: "The strike is almost too fast to register — a small impact, a burning line..."
eurasian_lynx/ambush: "There is no warning. Something drops from the branches..."
river_trout/catch: "The trout runs hard against the pull, silver and cold..."
chanterelle/gather: "The mushroom pulls cleanly from the soil, apricot-colored, heavy for its size..."
```

### Wound Severity Tier Phrases

| Tier | Phrase register |
|------|----------------|
| MINOR (<15) | "a graze", "shallow", "stings but does not slow" |
| MODERATE (15–34) | "cuts deep", "bleeds freely", "the arm is stiff" |
| SERIOUS (35–69) | "driving force", "the ground comes up", "ribs struck" |
| CRITICAL (70–99) | "the whole weight of it", "bones give", "the world tilts" |
| FATAL (≥100) | Not narrated by NarrationEngine — always AI_REFINE |

### Time-of-Day Fragments

Brief openers that ground the moment temporally:

| time_of_day | Opener examples |
|-------------|----------------|
| DAWN | "The light is still grey and flat..." / "Mist sits in the low places..." |
| MIDDAY | "The sun is high and the shadows short..." |
| DUSK | "The light is going warm at the edges..." |
| NIGHT | "The forest is loud with things that do not need to see..." |

---

## NarrationRouter

**Class:** `com.devosphere.draugr.narration.NarrationRouter`

**Method:** `shouldUseAI(PerceptionFrame frame, String actionText) → boolean`

**No database dependency.** Pure function on existing frame fields.

### Routing Rules (DR-0017)

**Always DETERMINISTIC (return false):**

- Intent in: `EQUIP, UNEQUIP, DROP, ADD_FUEL, DESIGNATE, MARK, GATHER_PLANT, FELL_TREE, COLLECT_INSECTS, LURE, SET_TRAP`
- `frame.attention() == "LOW"` AND outcome is not death or injury
- `TRACK` with outcome `NOTHING`
- `FISH` with action text length < 200 chars and outcome `SUCCESS`

**Always AI_REFINE (return true):**

- Outcome contains `"DEAD"` or `"FATAL"` (death of any cause)
- Species in encounter is a MONSTER-category species (wyvern, cave_troll, dire_wolf, harpy, roc, bog_wraith, etc.)
- Intent in: `WRITE, EDIT_DOCUMENT`
- `frame.attention() == "HIGH"` AND intent is `OBSERVE`
- Wildlife bond reaches `TAMED` stage
- Outcome contains `"DISCOVERY_OTHER_CHRONICLE"` (first find of dead chronicle's object)

**Conditional AI_REFINE (return true if any applies):**

- `actionText.length() > 300` AND intent not in the always-DETERMINISTIC list
- `frame.sinceLastFrame().size() >= 2` (multiple state changes this tick — significant moment)
- `CONFRONT_WILDLIFE` with wound severity ≥ 35

**Default:** false (deterministic).

---

## AI Refinement Call

When `shouldUseAI()` returns true:

1. Retrieve `backendNarration` from the PerceptionFrame (produced by NarrationEngine)
2. Assemble the refinement prompt:

```
System:
You are the witness narrator of a survival simulation.
You receive the existing narration and the action context.
Add ONE sentence — maximum two — of sensory or atmospheric detail.
Do not repeat what the narration already says.
Do not advise the player.
Do not name Body HUD values.
Do not explain why anything happened.
Begin your sentence as if continuing the existing prose.

User:
Existing narration:
{backendNarration}

Context:
- Intent: {intent}
- Outcome: {outcome}
- Location: {biome} at {timeOfDay}
- Weather: {weather.kind} intensity {weather.intensity}
- Nearby: {nearbyObjects}
- State changes this tick: {sinceLastFrame}
```

3. Append the AI response to the existing narration.
4. Store the combined string in `chronicle_action.narration`.
5. Return the combined string in `PerceptionFrame.narration`.

**Output bound:** max_tokens = 80. One to two sentences only. Temperature = 0.7.

---

## PerceptionFrame Field Addition

The frame gains one field to support the refinement call:

```java
public record PerceptionFrame(
    String intent,
    String outcome,
    LocationView location,
    String timeOfDay,
    WeatherView weather,
    String attention,
    List<String> nearbyObjects,
    ChroniclePhysiologyService.BodyHudSnapshot physiology,
    List<StateChange> sinceLastFrame,
    String backendNarration,   // ← new: NarrationEngine output before any AI call
    String narration           // ← final: backendNarration + AI refinement (or backendNarration alone)
) {}
```

---

## Cost Model

| Scenario | API calls | Approximate tokens per call | Notes |
|----------|-----------|----------------------------|-------|
| Player gathers clay 20 times | 0 | — | Always DETERMINISTIC |
| Player writes a 1500-char document | 1 | ~200 in, ~80 out | Always AI_REFINE |
| Player kills a gray wolf | 1 | ~200 in, ~80 out | Wound severity ≥ 35 |
| Player encounters wyvern | 1 | ~200 in, ~80 out | MONSTER always AI_REFINE |
| Player dies of starvation | 1 | ~200 in, ~80 out | Death always AI_REFINE |
| Player tames a goat | 1 | ~200 in, ~80 out | TAMED stage AI_REFINE |
| Player observes surroundings carefully | 1 | ~200 in, ~80 out | HIGH attention OBSERVE |
| Average session (60 actions) | 3–8 | ~200/~80 per call | ~90% actions deterministic |

At Haiku pricing (reference only — check current Anthropic pricing), a typical 60-action session costs under $0.01 in AI calls.

---

## Testing Requirements

### NarrationEngine

- Unit test: every intent × outcome combination produces non-empty prose
- Unit test: no prose contains Body HUD labels ("hunger", "blood_loss", "illness_severity")
- Unit test: no prose contains player advice ("you should", "try", "you need")
- No DB required

### NarrationRouter

- Unit test: 20+ cases covering every routing rule branch
- Test death outcome → always true
- Test EQUIP → always false
- Test action text > 300 chars with non-trivial intent → true
- Test sinceLastFrame ≥ 2 → true
- No DB required
