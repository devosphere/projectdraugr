# Chronicle 9 — traceable scenario matrix

**Issue:** [#37](https://github.com/devosphere/projectdraugr/issues/37) — *Chronicle 9 playthrough exposes resolver, perception, and supply-chain gaps*
**Required review output (the ticket's words):** *"Create a traceable scenario matrix from the supplied run: submitted action → visible context → expected interpreted procedure(s) → expected state changes → actual result → linked issue/regression fixture. Categorise each failure as perception, action routing, unavailable supply chain, invalid physical procedure, persistence/database, narration, or UI desynchronisation."*

This document is that output. It is organised around the **ten complaints #37 names in its own prose**, because those — not a list of defects found later — are the supplied run. Each complaint is traced to what was measured, what was delivered, and the fixture that now holds it.

**Every "actual result" line in this document was measured on a booted backend**, not read off the source. Where something has not been measured, the row says so rather than guessing. The method is written up in [finding-defects-by-playing.md](../architecture/finding-defects-by-playing.md).

---

## A. The ten complaints

### A1 — "excessively hit a lot of crafting restrictions even if the procedure is rationally possible given the tools and materials around the vicinity and carried load"

| | |
|---|---|
| **Category** | unavailable supply chain / invalid physical procedure |
| **Submitted action** | a recipe's own name, said whole: `knap a flint scraper`, `sew a leather cap`, `haft the axe` |
| **Visible context** | the inputs within reach, the tool in hand |
| **Expected procedure** | the `material_process` whose output is the thing named |
| **Actual result (pre-fix)** | a Java intent claimed the sentence before the two-axis matcher saw it, and refused. 66 phrases a Java intent took from the catalogue; separately, nine garments whose own catalogued name reached a generic fibre version or nothing |
| **Expected state change** | the named output made, inputs consumed, mass conserved |
| **Delivered** | #832, #833, #835, #836, #837, #838 |
| **Fixture** | `EveryDeclaredKeywordReachesAProcessIntegrationTest` (2,661 declared keywords held to account, including `aKeywordOnlyOneProcessClaimsReachesThatProcess`), `DeadEndOutputInvariantTest` |
| **Status** | **Closed with proof.** 0 declared keywords unreachable. |

Also under this head: epic [#54](https://github.com/devosphere/projectdraugr/issues/54) (closed) found three real supply-chain bugs — immortal food, laundered perishables, and ore hidden by an aggregate.

#### A1 swept with the inputs ARMED, which is the only way this complaint can be tested

Every slice above held a *keyword* to account. The complaint is about **restrictions**, and a restriction can only be measured with the materials actually in reach — so for each process the sweep puts every declared input in the pack (`material_process_input` plus one member of each either/or `material_process_input_group`), hands over every tool in `tool_profile`, lights a fire, stands the Chronicle on a RIVER_BANK and raises all fourteen stations. Then it says the process's own first keyword. A refusal with all of that present is the complaint itself.

```
485 input-bearing recipes, nothing skipped:   474 succeeded,  2 asked which,  9 refused,  0 no-reply
```

**The complaint largely does not reproduce.** When the materials really are in reach, the restriction logic lets the work happen. The two "asked which" are #593's ambiguity rule working as designed, and the nine refusals are all a *bare one-word* first keyword — `tan`, `knap`, `ret`, `shape`, `scarf` — a sentence that names no material, which is the subject gate working rather than failing.

So A1 was a **reachability** problem, not a restriction one. A player who cannot find the phrase, or cannot attain the input, experiences that as a restriction; reachability is what every slice under this head has been closing.

`0 no-reply` is the number that makes the rest trustworthy. Two earlier attempts at this sweep reported 0-of-25 and then 166-of-485 failures, and every one of those was a fault in the harness, not the world — see section D.

#### A1's last shape: a recipe that disagrees with itself

The 14 refusals in the first armed run were one defect wearing two masks, and both are now closed.

| | |
|---|---|
| **Submitted action** | `taper the billet`, `cut a mortise`, `leach the bark`, `bind the fletching`, `half lap` |
| **Visible context** | the timber in the pack, the chisel in hand, the bench standing |
| **Expected procedure** | `shape_components`, `cut_mortise`, `prepare_tannin_mordant`, `fletch_arrows`, `lap_joint_planks` — each of which **declares the phrase as its own keyword** |
| **Actual result (pre-fix)** | nothing at all. `taper` is a keyword of shape_components and `billet` one of its subject terms, so both halves of that recipe claimed the sentence — but the guard that asks "does this sentence name something this process is about?" looked only among the KEYWORDS, never the SUBJECT TERMS, which is where the catalogue records what a recipe works on. Separately, 114 multi-word keywords named no subject term of the recipe declaring them, so each was a phrase the catalogue advertises and its own gate must refuse: `leather boots`, `cuir bouilli`, `pressure flake`, `brain tan`, `saddle notch`, `box joint`, `leach tannin` all reached nothing |
| **Expected state change** | the named output made, inputs consumed, mass conserved |
| **Delivered** | #861 (V462 + the guard reading the subject half) |
| **Fixture** | `EveryPhraseKeywordNamesItsOwnMaterialTest` (the standing guard, with the five phrases that name no material and need none), `TheRecipeAgreesWithItselfIntegrationTest` |
| **Status** | **Closed with proof.** 111 of the 114 phrases now reach the catalogue; the 485-recipe sweep went 469 → **474 succeeded with zero new refusals and no new ties**. `wash the plank` and `wash the bowl` still reach nothing, so the guard was read more carefully rather than weakened. |

This is the same defect V459 found once — `shape sling stones` declared while the subject term was the singular `sling stone`. The two halves of one recipe disagreeing about a word is the shape, and the standing guard now fails on the next one instead of letting it ship as a phrase the world quietly ignores.

### A2 — "Inspect, analyze, look, track, does not properly work"

| | |
|---|---|
| **Category** | perception |
| **Submitted action** | `examine the stone axe`, `track the deer` |
| **Actual result (pre-fix)** | `ExaminationService.resolveItem` searched the Chronicle's pack and **nothing else**, so examining anything standing in the world fell through to a generic ground survey. Tracking read only the ~12 seeded wildlife populations, so every other species in a 215-species registry was untrackable everywhere it plainly belongs |
| **Expected state change** | none — these are read-only readings; the no-mutation guard applies |
| **Fixture** | `ExaminingAThingYouCanSeeIntegrationTest`, `AmbientTrackingIntegrationTest` |
| **Status** | **Closed.** |

### A3 — "can't even know if there are fishes on the water body you're currently in"

| | |
|---|---|
| **Category** | perception |
| **Submitted action** | `are there fish here`, `what fish are here`, `what fish live here`, `are there any fish` |
| **Visible context** | a `RIVER_BANK` chunk, water in reach |
| **Expected procedure** | `ExaminationService.lifeOfKind(chunk, "PISCES", acuity)` |
| **Actual result (pre-fix)** | **UNKNOWN — all four, and every other phrasing.** The reading was complete and unreachable: `kindNamed` maps `fish`/`fishes` → PISCES and `lifeOfKind` carries an explicit carve-out keeping AQUATIC species *only* for PISCES, written for exactly this question. No sentence could reach it |
| **Actual result (post-fix, measured)** | `"Looking for fish, you make out minnow and sea trout."` On a forest chunk: `"You look for fish, and this ground offers none that are abroad now."` |
| **Expected state change** | none; measured none |
| **Delivered** | **[#848](https://github.com/devosphere/projectdraugr/pull/848)** |
| **Fixture** | `IntentClassificationRegressionTest.askingWhatLivesHere`, `ChronicleNineWordingsIntegrationTest` |
| **Status** | **Closed.** |

### A4 — "narration doesn't say if its a river a stream a lake or what"

| | |
|---|---|
| **Category** | narration |
| **Submitted action** | `is this a river or a lake`, `what kind of water is this`, `is there water here`, `how is the water`, `what water is near` |
| **Expected procedure** | `judgeWater(location)`, whose first line is `waterNamed(location)` |
| **Actual result (pre-fix)** | four of them UNKNOWN; `what kind of water is this` reached EXAMINE and answered **"Plain ground, unremarkable at a glance"** — asked about the water, told about the ground. The gate in front of JUDGE_WATER required a word for *safety* as well as a word for water, so the plainest phrasings of the question the intent is named for fell past it |
| **Actual result (post-fix, measured)** | `"You crouch at the river and look at it properly. It runs, and running water carries its own filth away…"`; with no water: `"There is no water here to judge — nothing running, nothing standing, and nothing built to hold any."` |
| **Delivered** | **[#848](https://github.com/devosphere/projectdraugr/pull/848)** |
| **Fixture** | `IntentClassificationRegressionTest.askingWhatTheWaterIs` |
| **Status** | **Closed.** |

### A5 — "Wildlife / monsters are not even traceable on the actions we currently have"

| | |
|---|---|
| **Category** | perception |
| **Actual result (pre-fix)** | the architectural find of the cycle: **only ~12 wildlife populations existed in the whole world**, and perception, tracking, taming, monster lairs and hunting all read *only those*. On ~98% of the map `hunt the deer` answered *"The ground answers only with rain and the small movements of the forest."* |
| **Resolution shape** | read-only features **name** what belongs to the biome; acting features **materialise** it |
| **Fixture** | `AmbientTrackingIntegrationTest`, `HuntableGroundIntegrationTest` |
| **Status** | **Closed — all five faces.** |

### A6 — "We have hunt action but that won't work if we can't monitor, track, analyze, and survey the surrounding area"

| | |
|---|---|
| **Category** | action routing |
| **Submitted action** | `swing the axe at the boar`, `loose an arrow at the deer`, and 12 more weapon sentences |
| **Actual result (pre-fix)** | the hunt gate wanted a **verb and an animal**; nothing read the weapon side, so **14 of 14 weapon sentences missed a fully wired fight**. Separately, `confront` took *no action text at all* — it chose a quarry by its own ordering, carnivores first, and otherwise the biggest thing the biome holds |
| **Delivered** | #831, and the hunt's catalogue read |
| **Fixture** | `TheHuntReadsTheWordsIntegrationTest`, `HuntableGroundIntegrationTest` |
| **Status** | **Closed.** |

### A7 — "Even aerial species, insects are not returned by the Narrator even if the chronicle intently look for them on sensible locations"

| | |
|---|---|
| **Category** | perception |
| **Submitted action** | *imperative:* `look for birds`, `look for insects`; *question:* `are there birds here`, `what insects are here`, `are there snakes here`, `are there frogs here` |
| **Actual result (pre-fix), imperative** | birds and insects drew from the same naming budget as deer and boar and lost every time; the visibility model put a TINY creature beyond an ordinary eye outright. A Chronicle could stand in a summer meadow, look intently, and be told nothing lived there |
| **Actual result (pre-fix), question** | **UNKNOWN — sixteen phrasings, across all five kingdoms.** The imperative had been fixed; the question form was never swept. `look for birds` answered while `are there birds here` reached nothing |
| **Actual result (post-fix, measured)** | `"Looking for snakes or lizards, you make out grass snake and river turtle."` / `"Looking for frogs or newts, you make out common frog and bullfrog."` |
| **Delivered** | `SmallLifePerceptionIntegrationTest`'s slice (imperative), **[#848](https://github.com/devosphere/projectdraugr/pull/848)** (question) |
| **Status** | **Closed, both halves.** |

### A8 — "our AI analyzer can't even analyze reasonable actions to have the other AIs work properly"

| | |
|---|---|
| **Category** | action routing |
| **Reading of the complaint** | the deterministic router, not the AI layer, decides what a sentence reaches; an interpreter cannot rescue a sentence the router sends to the wrong act. So this was treated as a routing-coverage problem and attacked as one |
| **Actual result (pre-fix)** | measured, not estimated: 2,157 then 2,661 declared keywords audited; the recurring defect is **one matcher, many readers** — found in five distinct shapes this cycle (a reader applying a stricter rule than the act, #834; reports rolling their own matcher, #839; a rule never asking what its neighbour asked, #840; readers of one quantity counting different *units*, #844; a family fixed in the past tense only, #847) |
| **Status** | **Substantially closed on the deterministic side.** 0 unreachable declared keywords. The AI-layer half of this complaint is **not assessed** — see A9/A10. |

### A9 — "Architect is not even triggered during the playthrough"

| | |
|---|---|
| **Category** | persistence/database |
| **Known cause** | the launcher password is the master switch for AI; it did not cascade to the Architect. Fixed in #244, verified then via the boot log and `chronicle_tech_discovery` |
| **Status** | **NOT RE-VERIFIED IN THIS CYCLE.** Confirming it needs a run with AI enabled, which needs the launcher password — the user's to supply. Every measurement in this document was taken with `draugr.ai.enabled=false`. Recorded here as open rather than claimed. |

### A10 — "We don't see token consumption for the Architect → QA → Auditor AI engines"

| | |
|---|---|
| **Category** | UI desynchronisation |
| **Status** | **ASSESSED AS ABSENT, not merely unmeasured.** Nothing records it anywhere, so there was never a number to display. This is the clearest remaining candidate for the next slice, and it is now specified rather than guessed at. |

What the complaint needs, read off the code and the schema rather than estimated:

1. **The response's own `usage` block is discarded.** `usage`, `input_tokens` and `output_tokens` do not appear anywhere in `backend/src/main/java/com/devosphere/draugr/ai/` — `AnthropicLanguageModel` is the single call site and it parses only the text out.
2. **There is no table to put it in.** The schema holds no table matching `token`, `usage` or `cost`. The one AI-side record is `ai_procedure_receipt` (V359, V372), which keeps the Interpreter's verdict, reason code, confidence and cited context — and no counts.
3. **Six agents would need tagging at the call site**, which is also exactly the breakdown #37 asks to see: `SimulationNarrator`, `ProcedureInterpreter`, `PersistentStateArchitect`, `QaCritic`, `AuditorSummarizer`, `RuntimeArchitect`.
4. **It cannot be verified without the user.** Every measurement in this document was taken with `draugr.ai.enabled=false`; the master switch is the launcher password, so confirming a token count is real needs a run only the user can start. Building this blind would be the one thing this cycle has consistently refused to do — ship a number nobody has seen.

---

## B. Prioritised regressions still open

#37's acceptance asks for *"prioritized regressions that map to existing child stories rather than a new generic AI dependency."* These are measured, reproducible, and deliberately left:

| Priority | Scenario | Category | Note |
|---|---|---|---|
| 1 | Per-agent AI token accounting is not recorded at all (A10) | UI desync | specified in A10 above; the response usage block is discarded and no table holds it |
| 2 | Architect triggering unverified under a real AI run (A9) | persistence | needs the launcher password |
| 3 | `look for fish` on a **water** chunk fishes, and has twice taken an animal (a crayfish, then a catfish) in answer to a look | action routing | **a documented decision whose reasoning holds only where there is no water** — see C below |
| 4 | No per-limb body state: `how is my leg`, `how are my feet` | perception | **diagnosed in B2** — nothing anywhere records where an injury landed, so this is a design decision rather than a defect |
| 5 | Wound infection, broken bones and healing time are not modelled, so `is the cut infected` is an honest miss | perception | held as a deliberate miss by `TheQuestionsABodyCanAnswerIntegrationTest` |
| 6 | `open the pen` reaches nothing | action routing | measured |
| 7 | `hang it to dry` names no material | invalid physical procedure | measured |
| 8 | `what is in the water` is ambiguous between the fish and the silt | perception | left unguessed on purpose |
| 9 | EXAMINE answers about the **ground** rather than a named subject (e.g. a named mushroom) | narration | recorded in `ChronicleActionService`'s own comment; the water face of it is fixed by #848 |
| 10 | `put the oxen out to graze` reaches MOVE | action routing | **decided: MOVE is right, except on the ground it names.** The MOVE handler is a real drive (`towardStock` → the herd follows the keeper, the ticks read the keeper's chunk), so the routing stands. The defect was underneath it: `towardStock` checks only the four neighbours, so a keeper ON pasture walked the herd off it (or was told "no open grazing within a step" while standing in it), and the same for water. Fixed: on ground that already fits, a grass drive is TURN_OUT_STOCK here and a water drive takes no step — one shared `stockGround` predicate for both |

### B2 — what the open items turned out to be, once diagnosed

Three of section B's rows were investigated further. Two are now specified rather than merely listed, and one is **not a defect at all**:

**Per-limb body state — fully diagnosed, and it is a design decision, not a bug.** Nothing in this world records *where* an injury landed. `chronicle_physiology` carries `injury_severity`, `pain_level`, `blood_loss_ml` and `stress_level` as single scalars; `chronicle_body` is a whole-body summary; and no column, class or query anywhere in the backend mentions a body part, limb or wound site. `applyInjury(chronicle, severity)` simply adds to the scalars. So a per-limb table would need **every injury source to invent a location** — which is exactly what `theFeetUnderTheBeast` calls "the opposite of this ticket's whole point". It is attainable (a bite, a fall, a knapping cut each imply a part) but that is new simulation design and wants a decision, not a defect fix.

**`dry_meat` declares a keyword no sentence can reach.** Its keyword list includes `hang it to dry`, but the two-axis matcher needs a keyword **and** a subject term on the same process, and that phrase names no subject — "it" is a pronoun. Proved rather than assumed: with three raw game meat in the pack, `hang it to dry` still reached DRY_BODY, because `actionMatchesProcess` is false. Giving `dry_meat` a subject term that matches a pronoun would make every subject-less sentence reach meat, so it is left alone.

**`examine the plantain` reaches the ground survey, and the fix is not a widening.** The head noun of `plantain_herb` is the *part*, not the kind. The food reader (#851) can match any non-preparation word of a name because it only searches the handful of foods **in reach**; this reader searches every plant the biome grows, where `ground_ivy` would hijack `examine the ground` — which has a test asserting the survey. That needs a stoplist, and a stoplist here would be guesswork.

### B3 — measured NEGATIVES, recorded so they are not "fixed" later

Three findings from the husbandry sweep looked like defects and are not. Each cost a fixture rebuild to establish, so they are written down:

| Looked like | Actually |
|---|---|
| `how are the foxes` reaches nothing while `how are the goats` reaches CHECK_STOCK | **Correct.** `namesAKeptAnimal` asks `draft_species UNION tamed_yield` — the species a keeper can actually keep. A fox is neither, so a fox is not stock. The test fixture was the unrealistic part, not the code. |
| `turn the goats out`, `pen the goats`, `bring the stock in` all reach nothing | **Correct, and the harness was at fault.** These are gated on keeping stock, and the Chronicle kept none. With a tamed herd bonded, every one of them routes: `turn the foxes out` → TURN_OUT_STOCK, `pen the foxes` → BRING_IN_STOCK, `put them in the pen` → BRING_IN_STOCK, `what do i keep` → CHECK_STOCK. |
| `is the fox lame` reports "you keep no tamed animals" while `how are my animals` names the herd | **A fixture artefact.** The sweep harness recreates the Chronicle on a 409, which orphans the bond mid-run. Re-measured against a live bond, the two agree. |

The lesson those three share is in the method section below: **a husbandry sentence's routing is not pure over text** — several of these rules consult `keepsAnyStock()` — so a sweep taken with a dead fixture reads as a wall of defects.

Two genuine small gaps did come out of it, and they are the two ambiguous ones:

| Priority | Scenario | Category | Note |
|---|---|---|---|
| 11 | `open the pen`, `close the pen`, `open the gate`, `close the gate` reach nothing | action routing | the acts exist — `let them out` is TURN_OUT_STOCK and `put them in the pen` is BRING_IN_STOCK — but naming the *structure* instead of the animals reaches neither |
| 12 | `check the stock` reaches nothing while `check on the stock` reaches CHECK_STOCK | action routing | genuinely ambiguous between the camp stocktake and the herd report, which the welfare rule's own comment names as the reason it demands `check … over`. Wants the ask-when-ambiguous rule, not a guess |
| 13 | `put the meat on the rack`, `put the hide on the rack` reach nothing | action routing | loading a built rack is not modelled; `dry the meat` is the process that exists |

---

## C. Three "easy wins" that were documented decisions

Worth recording as a finding in its own right, because it is the main way this review wasted effort and the main thing that stopped it doing harm:

1. **`how are my feet`** reaches nothing on purpose — *"the body model keeps no foot state, so there is no honest answer to route it to and inventing one would be the opposite of this ticket's whole point."*
2. **`is the cut infected`** is a deliberate miss — *"a body report that did not mention any of them would be a near-miss dressed as an answer."*
3. **`look for fish` → FISH** is called *"the right answer to it"* by `ChronicleNineWordingsIntegrationTest`, because FISH refuses with *"there is no water here that holds anything worth taking."* On the forest ground that test stands on, that is exactly what it does, verbatim. **On a river bank it is false** — it fishes, and twice took an animal. The decision was left standing and the contradiction recorded in both places.

**The rule this yields: grep the tests for the gap before filling it.** And its sharper form, from the third case: a decision's *reasoning* can be true on the ground it was written on and false elsewhere — so check the premise, not just the verdict.

---

## D. Method and integrity

Every row above was produced by the loop in [finding-defects-by-playing.md](../architecture/finding-defects-by-playing.md): boot the stack against a throwaway Postgres, move the Chronicle to the ground the question is about, replay whole sentences through the real `POST /api/actions` boundary, and **grade by what the engine did, not by the intent it returned.**

Three traps this review hit and had to build guards against, all of which previously produced a clean-looking run that had tested nothing:

- **A dead port answers every phrase with nothing**, and a sweep of 523 phrases reported "0 shadowed" having tested none of them. Every sweep now **counts no-replies** and prints the total; every measurement quoted here ran at 0 no-reply.
- **A green CI gate can have run none of the database tests.** `ubuntu-latest` rolled to ubuntu-26.04, whose Docker engine refuses Testcontainers 1.21.0's API, so every `@Testcontainers` class reported `Tests run: 0` — which the gate counts as a pass. One merge went green on 416 of 1340 tests. Fixed in #846; the standing rule is **read the test total, not just the four conclusions.**
- **A green local suite is not evidence for a new integration test**, because those skip locally. New SQL is validated against a throwaway container before it is pushed.

On the integrity assertions #37 requires: every integration test in this project asserts `auditor.inspect().consistent()`, and one catalogue row that breaks mass balance fails roughly 300 of them. Current state, immutable history, UUID identity and physical location/owner are covered by that gate rather than by anything written for this review.
