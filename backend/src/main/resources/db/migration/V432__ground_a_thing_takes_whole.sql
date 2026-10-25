-- #77 — footprint: one thing may take a whole chunk of ground, and then nothing else may ring it.
--
-- #77's last open criterion is footprint and capacity: whether a structure needs room, and whether two can
-- crowd one chunk. Nothing limited it, and ONE Java intent already did: BUILD_PEN refuses a second pen on the
-- same ground —
--
--     "A pen already stands here; there is no room to ring another around this ground."
--
-- That refusal is right and it was the only one of its kind, hard-coded against a single construction_kind.
-- This promotes it to data, the same promotion the well's comment asked for and V431 made for site.
--
-- WHAT WAS NOT DONE, AND WHY, because the obvious version of this is wrong twice over:
--
--   * NOT AN AREA BUDGET. Giving every kind a footprint in paces and every chunk an allowance would gate every
--     build in the game on arithmetic nobody asked for, and would need a number invented for 117 kinds. It can
--     be added later if a camp ever needs to feel finite; nothing here forecloses it.
--   * NOT THE `encloses` FLAG, which was my first instinct and is the wrong flag: ANIMAL_PEN has
--     encloses = FALSE. That column means a roofed space you get INSIDE — cabins, huts, byres, 32 kinds — not
--     "rings the ground". Using it would have constrained the wrong thirty-two and left the pen itself free.
--
-- THE RULE: at most one ground-taking structure stands on a chunk. Not "nothing else may stand there" — a pen
-- ringing a byre is ordinary husbandry, and a shelter inside a fold is the point of a fold. Two rings around
-- one piece of ground is the incoherent thing, and that is all this refuses.
--
-- THE SEVEN, chosen from the catalogue's own words and nothing else:
--
--   BULL_ISOLATION_YARD  "a stout walled yard set apart from the rest"
--   GOAT_FOLD            "a ring of hurdles set on open ground"
--   POULTRY_RUN          "a fenced stretch of ground with brush laid over part of it"
--   WATERFOWL_RUN        "a fenced stretch taking in standing water"
--   MUD_WALLOW           "a broad shallow hollow where the ground already lies wet"
--   WATERFOWL_POOL       "a hollow dug wide and shallow and puddled tight with clay"
--   ANIMAL_PEN           the Java-built pen, whose own refusal is the precedent being generalised
--
-- A NOTE ON FINDING THEM: the first audit query for this matched `hitching_post`, because "set on O-PEN ground"
-- contains "pen". My own query fell into the substring trap it existed to look for — the same shape as
-- w-EAT-her and ca-BIN. Re-run with \m…\M word boundaries it returned these six and nothing spurious.

ALTER TABLE construction_kind
    ADD COLUMN IF NOT EXISTS takes_the_whole_ground BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE construction_kind SET takes_the_whole_ground = TRUE
 WHERE project_kind IN ('BULL_ISOLATION_YARD','GOAT_FOLD','POULTRY_RUN','WATERFOWL_RUN',
                        'MUD_WALLOW','WATERFOWL_POOL','ANIMAL_PEN')
   AND NOT takes_the_whole_ground;

DO $$
DECLARE n INT; bad TEXT;
BEGIN
    -- 1. The seven, and only the seven. A kind swept in without being named above is a kind nobody decided on.
    SELECT COUNT(*) INTO n FROM construction_kind WHERE takes_the_whole_ground;
    IF n <> 7 THEN RAISE EXCEPTION 'V432: % kinds take the whole ground; seven were named', n; END IF;

    SELECT string_agg(project_kind, ', ') INTO bad FROM construction_kind
     WHERE takes_the_whole_ground
       AND project_kind NOT IN ('BULL_ISOLATION_YARD','GOAT_FOLD','POULTRY_RUN','WATERFOWL_RUN',
                                'MUD_WALLOW','WATERFOWL_POOL','ANIMAL_PEN');
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V432: % was constrained without being named at the top of this file', bad; END IF;

    -- 2. THE PEN IS IN IT. The whole point is generalising BUILD_PEN's refusal, so if ANIMAL_PEN ever loses the
    --    flag the Java rule it replaced has quietly come back as the only one of its kind.
    IF NOT EXISTS (SELECT 1 FROM construction_kind WHERE project_kind='ANIMAL_PEN' AND takes_the_whole_ground)
    THEN RAISE EXCEPTION 'V432: ANIMAL_PEN must take the whole ground — its own refusal is what this generalises'; END IF;

    -- 3. NOT TIED TO `encloses`, asserted because that was the wrong flag and the mistake is easy to repeat.
    --    If these two columns ever agree exactly, somebody has conflated "rings the ground" with "you get
    --    inside it", and thirty-two cabins and huts have been constrained by accident.
    SELECT COUNT(*) INTO n FROM construction_kind WHERE takes_the_whole_ground <> encloses;
    IF n = 0 THEN RAISE EXCEPTION
        'V432: takes_the_whole_ground now matches encloses everywhere, which means the wrong flag is in use — '
        'encloses is a roofed space you get inside, not ground that is ringed'; END IF;

    -- 4. Every one of them is a real, reachable kind: a flag on a kind no assembly or intent builds gates
    --    nothing. ANIMAL_PEN is raised by the Java BUILD_PEN intent rather than an assembly, so it is exempt
    --    from the assembly check and named explicitly.
    SELECT string_agg(ck.project_kind, ', ') INTO bad FROM construction_kind ck
     WHERE ck.takes_the_whole_ground AND ck.project_kind <> 'ANIMAL_PEN'
       AND NOT EXISTS (SELECT 1 FROM assembly_definition ad
                        WHERE ad.construction_kind = ck.project_kind AND ad.review_state = 'VERIFIED');
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V432: % is not built by any verified assembly, so the flag gates nothing', bad; END IF;
END $$;
