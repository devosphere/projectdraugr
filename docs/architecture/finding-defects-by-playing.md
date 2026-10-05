# Finding defects by playing

This is the working method that produced PRs #708–#734. It is written down because each cycle has otherwise
re-derived it, and because two of its rules were learned by shipping the mistake first.

---

## The method

**Play the game and write down every answer that is wrong.** Not "run the tests" — the tests are green while the
defects are there, because the tests assert what the code does and the defects are places where what the code
does is not what the world says. A session of forty plain phrases, typed the way a person would type them,
reliably finds a dozen.

Then **turn each hand-found bug into a sweep.** One snowshoe routed to boots (#719) became 29 paired garment
families (#720), became 183 keywords with a foreign-category verb (#721), became five hyphenated keywords that
could never match (#722), became a permanent guard holding 2,157 keywords to account (#723). The single bug is
the thread; the sweep is the garment.

And **report the negative.** Several audits found nothing wrong, and saying so was the deliverable: the herbal
infusion already existed, willow bark was already an input, every breedable species already had a fitting
shelter. A cycle that cannot say "I looked and it was fine" will keep re-looking.

---

## The six faces of the one defect

Every defect this project has found is the same defect: **the world knows something and the code does not ask.**

1. **Declared but ignored** — data states a capability, code never reads it. *Four draft vehicles were one
   vehicle because the haul asked `EXISTS(draft_vehicle)` and never read the four declared bed sizes.*
2. **Read but blind** — read for every subject alike. *`shelters_stock` meant "animals, any of them".*
3. **Gated on a column nothing advances** — green on a fixture, dead in play.
4. **The plain family word reaches nothing** — the catalogue has nine poultices and "poultice" reaches none of
   them; the world grows *meadow* grass and drops *dry* grass and "grass" reaches neither.
5. **The world knew and would not say** — the season, the cold where you stand, your own injuries, how long you
   have been here, what you have built, why your stock will not breed.
6. **The confidently WRONG answer** — and **these come first**. A missing answer announces itself; a wrong one,
   delivered in the same even voice as every true one, does not. *"You fix a name to this place: place."*
   *"You gather 6 beech mast"* when asked for grass. *"Is the water safe to drink"* answered by drinking it.

---

## The shape of a good fix

- **Ask the catalogue, not a list.** Almost every defect above began as a hand-written list in Java standing in
  for a table: thirty forage nouns without grass, three string literals for `'LATRINE'`, two gear constants
  before `draft_gear`. When you find one, replace it with the question the data can answer — and if a word must
  be held back, hold *that* back by name with a reason (`OWNED_ELSEWHERE`, `NOT_A_NAME`).
- **Never remove — add the mechanic.** A refusal that names the reason is not a removal; a capability quietly
  taken away is.
- **Better process means better RECOVERY, never more matter.** The Auditor's conservation gate enforces it, and
  one bad catalogue row fails ~300 of ~680 tests.
- **Assert the ASYMMETRY, not the refusal.** A report that merely says *something* passes while saying the same
  thing in both cases — which is the defect. Assert that the geared team's line differs from the bare team's,
  that the pit changes the refuse, that the watered stand out-yields the dry one.
- **One rule, one place.** When two statements must agree, share the clause as a method rather than copying it
  (`beastsAtWork()`), or one of them will drift.

---

## Verification, in order

1. **`mvn -o test`** — ~315 unit tests run locally in a couple of minutes. They do *not* include the integration
   tests: Testcontainers cannot reach Docker Desktop here, so every `@SpringBootTest` DB test **skips**, and a
   green local suite is **no evidence at all** for a new integration test.
2. **A throwaway Postgres** — `docker run` a fresh 16, `CREATE EXTENSION pgcrypto`, and let **Flyway** apply the
   migrations by booting the backend against it. Applying them by hand with `psql` leaves no Flyway history and
   the next boot fails; and `psql` without `-1` runs each statement in its own transaction, which trips the
   deferred cognition gate in V346.
3. **The booted backend, through `/api/actions`** — this is where the real evidence is. Every measured table in
   the PRs above came from here, because it is the only place that exercises classifier → dispatch → SQL → prose
   as a player meets it.
4. **`tests/regression/*.sql` (65 replays) and the routing probe** — run them against the throwaway DB *before*
   pushing, not after a 50-minute CI round.
5. **All FOUR CI checks** — Backend/PostgreSQL (~65 min, serialised), Routing reachability, Frontend build,
   Journeys. **Every conclusion must read pass.** Nine PRs were once merged through a red one because the merge
   step grepped only the backend suite's name.

---

## Traps that have each cost a round

- **A DB-backed predicate is invisible to the local suite.** It reads the catalogue; the unit tests have no
  database; it therefore answers `false` in every one of them. Sweep it against a booted backend or it ships
  untested.
- **The suite shares one `simulation_clock`.** Anything touching sight passes at noon and fails at one in the
  morning. Pin the clock (and the weather) and restore both in a `finally`.
- **Pinning the clock is one of THREE things, and doing only the first breaks the other two.** In one session I
  walked all three in order, each costing a CI round:
  1. **Pin it** — or your class drifts the world and starves somebody else's fixture. (A people's elders died,
     and a test nobody touched failed on `voices_to_stay`.)
  2. **Date every fixture from it** — `construction_project.last_structural_update` defaults to `now()`, the
     *wall* clock, so a structure inserted into a world pinned to 2031 arrives five years old and is worn to
     nothing before anything looks at it.
  3. **Let the body keep pace with it** — `chronicle_physiology.last_metabolic_update` is left wherever the
     previous class put it, so the next action charges the Chronicle for every hour in between and it dies
     mid-test. Surfaces as `IllegalState No living Chronicle exists`, several resolves into a test about words.

  ```sql
  UPDATE chronicle_physiology SET last_metabolic_update = (SELECT simulated_at FROM simulation_clock WHERE id=1),
         hours_without_food=0, hours_without_water=0, sleep_debt_hours=0
  ```
- **A multi-day clock jump kills the Chronicle of thirst** — ask the other questions first.
- **`chronicle_event` is immutable**; a trigger refuses to rewrite when a Chronicle woke. Move the clock instead.
- **An annotation belongs to the method under it.** Inserting a field or a helper between `@Transactional` and
  its method compiles as "annotation not applicable".
- **Java files are CRLF.** Normalise before scripted multi-line edits, and write edit scripts to a file —
  PowerShell mangles here-strings and bash eats backticks and `$$`.
- **Widening a rule to catch one phrase reliably steals another.** Assert the phrase it must NOT take, in the
  same test, every time.
- **A test fixture that selects by `display_name` sweeps the whole world.** One replay broke on any database
  where somebody had gathered firewood, because it claimed every object called "Dry branch".
- **Grep truncation hides call sites.** `items.workDraftBeasts` sat past column 140 on the MOVE line, so a fix
  wired only to TRAVEL was correct and invisible in play.
- **A `queryForObject` that assumes one row** fails the first time the suite runs in a different order. Ask by the
  object id the fixture created, not by item key and owner — by then the Chronicle owns two of them.
- **An assertion that assumes time stands still.** "Asking must not slake thirst" compared `hours_without_water`
  for equality; an action takes simulated minutes and a body goes on drying out, so it crept 5.0000 → 5.0500.
  Assert the **direction**, and put the body in a known state first or the assertion is vacuous.
- **`chunk_refuse` is drained against SIMULATED time**, so a fixture row stamped `now()` is drained to nothing the
  moment the world turns. Date it from `simulation_clock`. Same family as `construction_project.last_structural_update`.
- **A TEST MUST OWN WHAT IT MEASURES.** One world and one Chronicle sequence are shared across ~925 tests, so
  anything measuring state it did not create is order-dependent, and adding a single test class reshuffles the
  order. Four shapes, three of them found in one session:
  - *a resource other tests already stamped* — the colony depletion test picks the first GRASSLAND chunk in
    grid order, which much of the suite stands on, and asserts a depletion; clear the rows first.
  - *a GLOBAL count compared as a delta* — `EATEN_BY_COMMUNITY` counted world-wide, so another community's
    meals landed in this one's total. Scope it (`destroyed_location_id` = the community's home chunk).
  - *a fixture the matcher cannot tell from a stranger's* — several classes leave `Leather pouch` objects
    about, the matcher takes the longest name first, and the emptying found somebody else's empty pouch.
  - *asking the world for a thing instead of making it* — only 3 of 10 draft species have a population in a
    default world and the suite hunts across it, so `queryForObject` threw. **Materialise what you need.**

  The tell: a test that passes locally and fails in CI, or whose failure message changes between runs, is
  almost always one of these rather than a defect in the world. **Check whether `development` is green before
  blaming your own branch** — mine inherited a red one twice.
- **Every Auditor rule has the conservation gate's blast radius, not just conservation.** Two
  `item_definition` rows added without `item_source` rows gave *"2 item definition(s) have no way to be
  obtained"* and failed **399 of 926 tests**. Any new item declares its source, and `CODE` with the producing
  method in `detail` is the answer for anything Java makes. Run the Auditor's own query before pushing.
- **The merge gate is the four conclusions, never an exit code, and never a column position.** `gh pr checks
  --watch` has now exited **0** on a failing suite twice, and the rows do not come back in a stable order — a
  positional read of `pass pending pass pass` was the *frontend* passing, not the backend. The gate that works:
  `gh pr checks <n> | awk -F'\t' '$2!="pass"{print $1" = "$2}'` must print nothing.
- **A rebase can be the reason CI never ran.** Two branches took a push, showed the new head, and produced no
  workflow run at all; rebasing them onto `development` and force-pushing started one immediately. If `gh pr
  checks` says "no checks" for more than a few minutes, rebase rather than wait.

---

## Two rules of the world that a fixture must obey

Both are enforced by `PersistentStateAuditor`, and nearly every integration test asserts `auditor.consistent()`,
so breaking one fails far more than your own test.

- **A destroyed object holds no live location or owner**, and records how it ended. Setting
  `lifecycle_state='DESTROYED'` alone fails with *"N destroyed object(s) still have an active location or owner"*.
  Mirror `retire()`: set `destroyed_at`, `destroyed_location_id`, `destroyed_cause`, and NULL both live columns.
- **A completed construction at zero integrity while ACTIVE is an inconsistency** — no exemption. The tick takes
  such a thing down in the same pass that wears it out, and the only kinds excluded from that sweep (workstations)
  never weather either, so the state is unreachable in play.

The second one cost more than a fixture. I had written a *"Past mending"* branch into the camp stocktake and a
test that manufactured a ruin to prove it, and the Auditor was right: **it was prose for a state the world is not
allowed to be in**, which is the catalogue token in another form. Both were deleted. Before writing a sentence
about a state, check that the world can be in it.

---

## The ship test

**Does anything change because it exists?** If a row, a column, a keyword or a sentence cannot be shown to change
an outcome a player can reach, it is a catalogue token, and this project exists to not have those.
