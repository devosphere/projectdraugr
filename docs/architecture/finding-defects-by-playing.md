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

---

## The ship test

**Does anything change because it exists?** If a row, a column, a keyword or a sentence cannot be shown to change
an outcome a player can reach, it is a catalogue token, and this project exists to not have those.
