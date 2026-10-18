-- #108 / #37 — the plain word for a building reached nothing, fifteen times over.
--
-- #108 asks for species-appropriate animal infrastructure, and most of it IS BUILT: pig_sty, poultry_coop,
-- cattle_byre, horse_stable, ox_shed, hay_rack, mud_wallow, shade_shelter, sick_animal_shelter,
-- farrowing_shelter, brooder_shelter, fodder_store and tack_room all exist, and all of them FUNCTION — every
-- capability they declare (shelters_stock, shelters_birth, isolates_sick, shelters_gear, aids_mounting,
-- holds_an_animal_still, gives_shade, is_wallow, separates_dangerous, gives_salt, shelters_up_to_size) is read
-- by live code. Nothing here is a token.
--
-- What was missing was the WORD. Measured on a booted backend, correct articles and all:
--
--     build a chicken coop  ->  nothing at all      |  build a hen house  ->  works
--     build a coop          ->  nothing at all
--     build an oven, a tent, a store, a platform, a landing, a basin, a gate, a cabin, a catchment,
--     a woodpile, a dugout, a crossing, a fold, a screen  ->  nothing at all
--
-- "Chicken coop" is the commonest English phrase for the thing and was the one that missed, while "hen house"
-- and "fowl house" were both declared. That is this project's family-word defect in its sharpest form: not a
-- rare synonym overlooked, but the first words anybody would reach for.
--
-- AND IT IS THE TWO-SIDED SHAPE AGAIN, which this cycle has now met three times. `poultry_coop` already
-- declares "coop" as a SUBJECT. It has never been reachable, because no keyword of that assembly can match a
-- sentence whose only noun is "coop" — and the keyword gate runs first, so the subject sitting right there is
-- never consulted. A subject with no keyword beside it on the same row is inert. An audit across every assembly
-- found 58 such subject terms on 30 assemblies; the fifteen above are the ones that were genuinely unreachable
-- when tested live, the rest being owned by a Java intent, a process, or a longer keyword of their own.

-- A BARE WORD WHERE IT IS SAFE. Longest-keyword-wins means "build a poultry coop" (20) still beats "coop" (4),
-- and where two assemblies answer to one word the resolver asks which was meant and changes nothing while it
-- asks (#38/#593) — so "build a cabin" becoming a question between a bark cabin and a log cabin is the right
-- answer, not a collision.
UPDATE assembly_definition SET keywords = keywords || ',coop,build a coop,chicken coop,build a chicken coop,chicken house,build a chicken house'
 WHERE assembly_key = 'poultry_coop' AND ',' || keywords || ',' NOT LIKE '%,coop,%';

UPDATE assembly_definition SET keywords = keywords || ',oven,build an oven'
 WHERE assembly_key = 'earth_oven' AND ',' || keywords || ',' NOT LIKE '%,oven,%';

-- AND THE EARTH OVEN COULD NOT BE BUILT AT ALL, by any words, which the family word above only uncovered.
-- `build an earth oven` — the assembly's OWN oldest keyword — reached nothing either. AssemblyService selects
-- `WHERE review_state='VERIFIED'`, and of ninety assemblies exactly ONE was DRAFT: this one. It is not a stub.
-- It has three stages (dig the pit, line it with stone, bank the earth), a striking tool and eight field stone
-- at the second, an hour's work at the third, narration for each, and a construction_kind row declaring
-- is_workstation, holds_fire and retained_heat_minutes=240 — every one of which live code reads. The
-- retained-heat reader is the one that matters: "no stone still holding yesterday's" is the refusal a cook gets
-- without it, and an earth oven is precisely the structure that answers it. A first-era staple, complete, and
-- shut off by one column.
UPDATE assembly_definition SET review_state = 'VERIFIED'
 WHERE assembly_key = 'earth_oven'
   AND review_state <> 'VERIFIED'
   -- Never promote an empty shell. It must have stages, a kind of its own, and something to build it from.
   AND EXISTS (SELECT 1 FROM assembly_stage s WHERE s.assembly_key = 'earth_oven')
   AND EXISTS (SELECT 1 FROM construction_kind ck WHERE ck.project_kind = assembly_definition.construction_kind)
   AND EXISTS (SELECT 1 FROM assembly_stage_requirement r JOIN assembly_stage s ON s.stage_key = r.stage_key
                WHERE s.assembly_key = 'earth_oven');

UPDATE assembly_definition SET keywords = keywords || ',tent,build a tent,pitch a tent'
 WHERE assembly_key = 'hide_tent' AND ',' || keywords || ',' NOT LIKE '%,tent,%';

UPDATE assembly_definition SET keywords = keywords || ',landing,build a landing'
 WHERE assembly_key = 'fishing_landing' AND ',' || keywords || ',' NOT LIKE '%,landing,%';

UPDATE assembly_definition SET keywords = keywords || ',basin,build a basin'
 WHERE assembly_key = 'settling_basin' AND ',' || keywords || ',' NOT LIKE '%,basin,%';

UPDATE assembly_definition SET keywords = keywords || ',catchment,build a catchment'
 WHERE assembly_key = 'rainwater_catchment' AND ',' || keywords || ',' NOT LIKE '%,catchment,%';

UPDATE assembly_definition SET keywords = keywords || ',woodpile,build a woodpile'
 WHERE assembly_key = 'wood_store' AND ',' || keywords || ',' NOT LIKE '%,woodpile,%';

UPDATE assembly_definition SET keywords = keywords || ',crossing,build a crossing'
 WHERE assembly_key = 'footbridge' AND ',' || keywords || ',' NOT LIKE '%,crossing,%';

-- Deliberately a PHRASE and not the bare word, because the gate's own verbs are what a person says and a bare
-- "gate" would answer "open the gate" and "shut the gate" — which are not building anything.
UPDATE assembly_definition SET keywords = keywords || ',build a gate,raise a gate,put up a gate,hang a gate'
 WHERE assembly_key = 'simple_gate' AND ',' || keywords || ',' NOT LIKE '%,build a gate,%';

-- THREE WORDS THAT ARE SOMETHING ELSE FIRST, so each gets a building phrase rather than the bare noun. Verified
-- against the catalogue, not guessed:
--   * "fold" is a VERB in nine process keywords, and `make_bark_container` declares the bare word "fold" itself;
--   * "screen" names three PROCESSES that make a screen you carry (hide, brush, reed) as well as this assembly;
--   * "dugout" sits inside `carve_wooden_trough`'s keyword "dugout trough".
-- A bare keyword for any of the three would have taken a recipe's own phrase away from it.
UPDATE assembly_definition SET keywords = keywords || ',build a fold,sheep fold,build a sheep fold,raise a fold'
 WHERE assembly_key = 'goat_fold' AND ',' || keywords || ',' NOT LIKE '%,build a fold,%';

UPDATE assembly_definition SET keywords = keywords || ',build a screen,put up a screen,raise a screen'
 WHERE assembly_key = 'reed_screen' AND ',' || keywords || ',' NOT LIKE '%,build a screen,%';

UPDATE assembly_definition SET keywords = keywords || ',build a dugout,dugout hut,build a dugout hut'
 WHERE assembly_key = 'earth_sheltered_hut' AND ',' || keywords || ',' NOT LIKE '%,build a dugout,%';

-- Words that more than one building answers to. The ask is the point: "which store?" is a better answer than
-- silence, and the resolver prefers whichever has its materials in reach, so somebody holding only hay is not
-- asked at all.
UPDATE assembly_definition SET keywords = keywords || ',build a store,raise a store'
 WHERE assembly_key IN ('wood_store','fodder_store','tack_room') AND ',' || keywords || ',' NOT LIKE '%,build a store,%';

UPDATE assembly_definition SET keywords = keywords || ',cabin,build a cabin'
 WHERE assembly_key IN ('log_cabin','bark_cabin') AND ',' || keywords || ',' NOT LIKE '%,cabin,%';

UPDATE assembly_definition SET keywords = keywords || ',build a platform,raise a platform'
 WHERE assembly_key IN ('raised_sleeping_platform','raised_bed_platform') AND ',' || keywords || ',' NOT LIKE '%,build a platform,%';

DO $$
DECLARE n INT; bad TEXT;
BEGIN
    -- 1. Every word asked for is now declared by at least one assembly.
    FOR bad IN SELECT w FROM (VALUES ('coop'),('chicken coop'),('oven'),('tent'),('landing'),('basin'),
                                     ('catchment'),('woodpile'),('crossing'),('build a gate'),('build a fold'),
                                     ('build a screen'),('build a dugout'),('build a store'),('cabin'),
                                     ('build a platform')) v(w)
    LOOP
        SELECT COUNT(*) INTO n FROM assembly_definition
         WHERE ',' || replace(keywords,', ',',') || ',' LIKE '%,' || bad || ',%';
        IF n = 0 THEN RAISE EXCEPTION 'V420: nothing answers to "%"', bad; END IF;
    END LOOP;

    -- 2. THE COLLISION CHECK, MADE PERMANENT. No keyword added here may appear as a whole word inside any
    --    material_process keyword — that is exactly how a bare "fold", "screen" or "dugout" would have stolen a
    --    recipe's own phrase, and the next person to add a family word should be stopped by this rather than by
    --    playing the game and noticing.
    SELECT string_agg(DISTINCT v.w || ' <- ' || mp.process_key, ', ') INTO bad
      FROM (VALUES ('coop'),('oven'),('tent'),('landing'),('basin'),('catchment'),('woodpile'),('crossing'),('cabin')) v(w),
           material_process mp, unnest(string_to_array(mp.keywords,',')) k
     WHERE ' ' || lower(trim(k)) || ' ' LIKE '% ' || v.w || ' %';
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V420: a bare building word collides with a recipe: %', bad; END IF;

    -- 3. The specific must still beat the general — PER ASSEMBLY, against the word that assembly was actually
    --    given. The first cut of this guard compared every one of them against the longest word added anywhere
    --    ("build a chicken coop", 20 characters) and failed seven assemblies that were perfectly correct:
    --    hide_tent's own "pitch a hide tent" is 17, which beats the "tent" it was given by thirteen. A guard has
    --    to measure the thing it is guarding.
    SELECT string_agg(f.a || ' (' || f.w || ')', ', ') INTO bad
      FROM (VALUES ('poultry_coop','chicken coop'),('earth_oven','oven'),('hide_tent','tent'),
                   ('fishing_landing','landing'),('settling_basin','basin'),('rainwater_catchment','catchment'),
                   ('wood_store','woodpile'),('footbridge','crossing'),('simple_gate','build a gate'),
                   ('goat_fold','build a fold'),('reed_screen','build a screen'),
                   ('earth_sheltered_hut','build a dugout'),('fodder_store','build a store'),
                   ('tack_room','build a store'),('log_cabin','cabin'),('bark_cabin','cabin'),
                   ('raised_sleeping_platform','build a platform'),('raised_bed_platform','build a platform')) f(a,w)
     WHERE (SELECT MAX(length(trim(k))) FROM assembly_definition ad, unnest(string_to_array(ad.keywords,',')) k
             WHERE ad.assembly_key = f.a) <= length(f.w);
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V420: % has nothing more specific than its family word', bad; END IF;

    -- 4. Nothing may be left declared-but-unreachable the way the earth oven was. A DRAFT assembly is invisible
    --    to AssemblyService, so one that is otherwise complete is a building nobody can build — and it took
    --    reaching for the plain word "oven" to notice, because its own keywords failed just as silently.
    SELECT string_agg(ad.assembly_key, ', ') INTO bad
      FROM assembly_definition ad
     WHERE ad.review_state <> 'VERIFIED'
       AND EXISTS (SELECT 1 FROM assembly_stage s WHERE s.assembly_key = ad.assembly_key);
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V420: % is fully staged and still unreachable', bad; END IF;
END $$;
