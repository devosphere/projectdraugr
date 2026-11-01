-- #37/#99 — the card for the camp-ground reading, and the guards on the five records it stands on.
--
-- Five things decide whether ground is worth camping on, and the world records every one of them:
--
--     water       ecology_site / the biome, or a catchment or well standing here
--     fuel        the tree stand (chunk_flora, or the natural stand of a wooded biome)
--     a roof      a completed construction that ENCLOSES and still has integrity, or rock overhead
--     the bed     the biome you would be lying on (a fen is a wet night)
--     the danger  the carnivores whose range this is (wildlife_population x wildlife_species)
--
-- Every one of those is already read by some act — the drinking, the felling, the sleeping, the hunting — and
-- the QUESTION reached nothing. Measured live: `is this a good place to camp`, `is this a good camp` and
-- `good place to camp` were all misses, so the only way to learn whether ground was worth stopping on was to
-- spend the night on it and find out.
--
-- The reading REPORTS and does not advise. It names what is here and what is not, because "poor ground" tells
-- a keeper nothing they can act on while "no water within reach and nothing standing to burn" sends them
-- twenty minutes downhill. The verdict counts the three that decide a night — water, fuel, a roof — and
-- invents no number of its own.
--
-- EVERY NEW INTENT NEEDS A CARD, and ActivityImpactCardIntegrationTest skips locally, so a missing one costs an
-- hour and a half of CI. Walking a piece of ground over and weighing it is attention and a few minutes.

INSERT INTO activity_impact (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire,
                             labor_energy, labor_hygiene, capability_domain, duration_minutes, notes)
VALUES ('JUDGE_CAMP_GROUND', NULL, 0, FALSE, FALSE, 1, 0, 'ATTENTION', 5,
        'Perception alters nothing it touches. Weighing this ground for a camp — water within reach, something '
        || 'standing to burn, a roof or rock overhead, the ground you would lie on, and whose country this is — '
        || 'costs a little attention and marks nothing. No camp is made by asking.')
ON CONFLICT (intent_key) DO NOTHING;

DO $$
DECLARE n INT; e INT; d INT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM activity_impact WHERE intent_key = 'JUDGE_CAMP_GROUND')
    THEN RAISE EXCEPTION 'V447: JUDGE_CAMP_GROUND has no activity_impact row'; END IF;

    SELECT COUNT(*) INTO n FROM activity_impact
     WHERE intent_key = 'JUDGE_CAMP_GROUND' AND (footprint_kind IS NOT NULL OR footprint_amount <> 0 OR drifts);
    IF n <> 0 THEN RAISE EXCEPTION 'V447: a reading must not mark the ground — no camp is made by asking'; END IF;
    SELECT labor_energy, duration_minutes INTO e, d FROM activity_impact WHERE intent_key = 'JUDGE_CAMP_GROUND';
    IF e < 1 OR d < 1 THEN RAISE EXCEPTION 'V447: the reading costs nothing and takes no time'; END IF;

    -- THE FIVE RECORDS. Each is a clause of the answer, and each is read elsewhere by an act — which is the
    -- point: the reading must say what the acts would do, not keep a second opinion.
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='construction_kind' AND column_name='encloses')
    THEN RAISE EXCEPTION 'V447: construction_kind.encloses is gone; the reading cannot tell a roof from a wall'; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='construction_project' AND column_name='integrity_percent')
    THEN RAISE EXCEPTION 'V447: construction_project.integrity_percent is gone; a ruin would read as a roof'; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='wildlife_species' AND column_name='ecological_role')
    THEN RAISE EXCEPTION 'V447: wildlife_species.ecological_role is gone; the reading cannot name whose country this is'; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='wildlife_population' AND column_name='population_count')
    THEN RAISE EXCEPTION 'V447: wildlife_population.population_count is gone; an empty range would read as danger'; END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name='chunk_flora' AND column_name='quantity')
    THEN RAISE EXCEPTION 'V447: chunk_flora.quantity is gone; the reading cannot see a stand to burn'; END IF;

    -- AND THE ROLE IT ASKS FOR IS A ROLE THE CATALOGUE USES. 'CARNIVORE' spelled any other way would make every
    -- ground read as empty of danger — a silent false negative, which is the shape this ticket exists to stop.
    SELECT COUNT(*) INTO n FROM wildlife_species WHERE ecological_role = 'CARNIVORE';
    IF n = 0 THEN RAISE EXCEPTION 'V447: no species carries the CARNIVORE role the reading asks for'; END IF;
END $$;
