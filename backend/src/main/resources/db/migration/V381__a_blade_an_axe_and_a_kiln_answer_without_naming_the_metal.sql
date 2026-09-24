-- #37 — "forge a blade", "forge an axe", "build a kiln".
--
-- Act five, a Chronicle with ore and a fire:
--
--     forge a blade -> UNKNOWN      forge a knife -> UNKNOWN
--     forge an axe  -> UNKNOWN      build a kiln  -> UNKNOWN
--
-- Twelve forge recipes exist and work, and so does the lime kiln. Every forge keyword names its METAL —
-- "forge a bronze knife", "forge an iron axe" — so a smith who has not yet decided which metal, or who does not
-- yet know which metals there are, cannot ask. The kiln answers only to "lime kiln". Same defect as V375's
-- supper, V377's poultice and V379's hut: the catalogue is specific and the plain word reaches nothing.
--
-- WHAT THIS DELIBERATELY DOES NOT TOUCH. "make an axe" already belongs to haft_stone_axe, which is the right
-- answer for it — hafting a stone axe is what somebody without a forge means. "make a knife" belongs to the Java
-- CRAFT_KNIFE intent. Both keep their phrasings: everything added here carries the verb "forge", except the
-- kiln, which nothing else claims. Checked against a running stack before writing, one phrase at a time.
--
-- The axes are a TIE between three metals, and that is the point rather than a problem: the resolver asks which
-- was meant and prefers whatever is in reach, so a smith with only copper forges copper without being asked, and
-- one with all three is asked a question worth asking (#38/#593).

UPDATE material_process SET keywords = keywords || ',forge a knife,forge knife,blade,forge a blade'
 WHERE process_key = 'forge_bronze_knife'
   AND ',' || keywords || ',' NOT LIKE '%,blade,%';

UPDATE material_process SET keywords = keywords || ',forge an axe,forge axe'
 WHERE process_key IN ('forge_bronze_axe', 'forge_copper_axe', 'forge_iron_axe')
   AND ',' || keywords || ',' NOT LIKE '%,forge an axe,%';

UPDATE material_process SET keywords = keywords || ',kiln,build a kiln,make a kiln'
 WHERE process_key = 'make_lime_kiln'
   AND ',' || keywords || ',' NOT LIKE '%,kiln,%';

-- A keyword is only half of it: all three of category, keyword and subject must agree. The axes already take
-- "axe" as a subject and the kiln takes "kiln"; the knife takes "knife" but not "blade".
INSERT INTO process_subject (process_key, subject_term) VALUES ('forge_bronze_knife', 'blade')
ON CONFLICT (process_key, subject_term) DO NOTHING;

DO $$
DECLARE n INT;
BEGIN
    SELECT COUNT(*) INTO n FROM material_process
     WHERE process_key IN ('forge_bronze_axe', 'forge_copper_axe', 'forge_iron_axe')
       AND ',' || keywords || ',' LIKE '%,forge an axe,%';
    IF n <> 3 THEN RAISE EXCEPTION 'V381: expected three axes to answer without their metal, found %', n; END IF;

    SELECT COUNT(*) INTO n FROM process_subject WHERE process_key = 'forge_bronze_knife' AND subject_term = 'blade';
    IF n <> 1 THEN RAISE EXCEPTION 'V381: the knife does not take blade as a subject'; END IF;

    -- Naming the metal must still settle it outright, which is what makes the three-way tie safe: every axe
    -- keeps a keyword longer than the metal-less one, so "forge an iron axe" is never a question.
    SELECT COUNT(*) INTO n FROM material_process mp
     WHERE mp.process_key IN ('forge_bronze_axe', 'forge_copper_axe', 'forge_iron_axe')
       AND NOT EXISTS (SELECT 1 FROM unnest(string_to_array(mp.keywords, ',')) k
                       WHERE length(trim(k)) > length('forge an axe'));
    IF n > 0 THEN RAISE EXCEPTION 'V381: % axe(s) have no keyword more specific than the metal-less one', n; END IF;

    -- And the two phrasings that already belonged to something else must still belong to it. haft_stone_axe is
    -- the right answer for "make an axe" — a stone axe is what somebody without a forge means by it.
    SELECT COUNT(*) INTO n FROM material_process
     WHERE process_key = 'haft_stone_axe' AND ',' || keywords || ',' LIKE '%,make an axe,%';
    IF n <> 1 THEN RAISE EXCEPTION 'V381: hafting a stone axe has lost "make an axe"'; END IF;
END $$;
