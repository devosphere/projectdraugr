-- #161/#37 — the three reptiles V312 forgot, and a guard so there is never a fourth.
--
-- V312 ("what sleeps through the winter") wrote the rule in prose and then enumerated the keys by hand:
--
--     "Snakes and lizards: November to February. Brumation, and it is the cold that decides it, not the
--      species' homeland — every reptile catalogued here is catalogued on temperate ground, constrictor and
--      monitor included."
--
-- and then listed eight of them. The catalogue holds fourteen. Three were left abroad all winter against the
-- migration's own stated rule:
--
--     slow_worm    GRASSLAND, TEMPERATE_FOREST   a legless lizard; brumates under stones and log piles
--     wood_gecko   TEMPERATE_FOREST, GRASSLAND   a gecko on temperate ground keeps the same winter
--     crocodilian  WETLAND                       goes torpid in the cold and does not feed; AMPHIBIOUS, not
--                                                aquatic, so it is reachable on a bank and was reachable in
--                                                January
--
-- That is this project's most familiar shape in a place nobody looked twice: a rule declared in one breath and
-- then applied to a hand-written list that does not match it. It showed up as a survey on a December morning
-- naming "slow worm and wood gecko" among the reptiles abroad, with every other reptile of that ground properly
-- underground — a world that was two thirds consistent and said so in one even voice.
--
-- MUDPUPPY IS LEFT ABROAD ON PURPOSE, and this says so rather than leaving the next reader to wonder. It is a
-- fully aquatic salamander; it stays active all winter beneath the ice and is at its most findable then. V312's
-- amphibian line ("frogs, toads, newts and salamanders") is about the ones that leave the water. This one never
-- does.
--
-- AND A GUARD, because the hole was in the enumeration, not in the rule: a reptile or amphibian added to the
-- catalogue from here on must declare dormant_months or be named in the exemption below. No second list, and no
-- quiet third.

UPDATE wildlife_species SET dormant_months = ARRAY[11,12,1,2]::smallint[]
 WHERE species_key IN ('slow_worm','wood_gecko','crocodilian') AND dormant_months IS NULL;

DO $$
DECLARE bad text; n int;
BEGIN
    SELECT string_agg(species_key, ', ' ORDER BY species_key), count(*) INTO bad, n
      FROM wildlife_species
     WHERE kingdom_class IN ('REPTILIA','AMPHIBIA') AND dormant_months IS NULL
       -- The deliberate exemption, by the reason and not by the name: an animal that spends the winter in the
       -- water it already lives in has no dormancy to declare.
       AND species_key NOT IN ('mudpuppy');
    IF n > 0 THEN
        RAISE EXCEPTION 'V452: % reptile/amphibian species are abroad all winter against V312''s own rule: %', n, bad;
    END IF;

    -- And the three are actually down. A migration that updates nothing is the hole it was written to close.
    SELECT count(*) INTO n FROM wildlife_species
     WHERE species_key IN ('slow_worm','wood_gecko','crocodilian')
       AND 12 = ANY (dormant_months) AND NOT (6 = ANY (dormant_months));
    IF n <> 3 THEN RAISE EXCEPTION 'V452: expected three newly wintering reptiles, found %', n; END IF;

    -- The rule itself is still the one definition, and still reads the clock. If wildlife_abroad ever stopped
    -- existing, every one of these arrays would become decoration.
    IF NOT EXISTS (SELECT 1 FROM pg_proc WHERE proname = 'wildlife_abroad')
    THEN RAISE EXCEPTION 'V452: wildlife_abroad is gone; dormant_months would be a column nobody reads'; END IF;
END $$;
