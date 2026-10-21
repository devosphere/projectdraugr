-- #77 — eligible site, which the code itself asked for.
--
-- #77's acceptance criteria open with "Each building defines an ELIGIBLE BIOME/SITE, footprint, ordered stages,
-- input bills, tool/workstation requirements...". Auditing the lifecycle showed nothing gated a structure on
-- its setting: a water draw platform could be raised on open grassland, miles from anything to draw from.
--
-- AssemblyService had already written down what to do about it. Beside the one place-conditioned assembly in
-- the catalogue, a well, the comment reads:
--
--     "A well is the first assembly with a condition on the PLACE rather than on what is carried, so this is a
--      switch on the construction_kind rather than a new column: THE SECOND SUCH ASSEMBLY IS THE ONE THAT
--      SHOULD PROMOTE IT TO DATA."
--
-- There are eight. This is the promotion.
--
-- CHOSEN FROM THE CATALOGUE'S OWN WORDS, not from my sense of what ought to need water. Every structure here
-- says so in its own narration:
--
--   riverbank_steps      "the way down to the water"              -> RIVER_BANK, and only that: it is in the name
--   water_draw_platform  "a short staging out over the water"
--   washing_place        "a flat stone set at the water's edge"
--   settling_basin       "a clay-lined hollow beside the water"
--   fishing_landing      "stands out over the water on its driven piles"
--   fishing_weir         "set across the shallows"
--   duck_house           "a ramp down to the water"
--   footbridge           "firm enough to carry a body dry over"   -> RIVER_BANK, WETLAND: it crosses a channel
--
-- AND WHAT THAT PHRASING CORRECTLY LEAVES OUT, which is the part that shows the method discriminates:
--
--   * waterfowl_pool PUDDLES ITS OWN WATER — "puddle clay into its sides until it holds". It does not need to
--     be beside water; it becomes the water. Left unconstrained.
--   * earth_sheltered_hut is "cut back into the bank", but that bank is an earth slope, not a river's. The
--     word is shared and the meaning is not, so it is left alone rather than guessed at.
--   * rainwater_catchment and sand_filter_bed take what falls and what is poured in. Neither needs a shore.
--
-- THE WELL STAYS IN CODE, and that is not a dodge. Its condition is the water TABLE — groundTooDryToSink reads
-- the ground's moisture — which is not a list of biomes and cannot be expressed as one: a high water table
-- under grassland takes a well, and a dry terrace by a river does not. A biome list would get it wrong in both
-- directions.

ALTER TABLE assembly_definition ADD COLUMN IF NOT EXISTS site_biomes VARCHAR(200);

UPDATE assembly_definition SET site_biomes = 'RIVER_BANK'
 WHERE assembly_key = 'riverbank_steps' AND site_biomes IS NULL;

UPDATE assembly_definition SET site_biomes = 'RIVER_BANK,WETLAND'
 WHERE assembly_key = 'footbridge' AND site_biomes IS NULL;

UPDATE assembly_definition SET site_biomes = 'RIVER_BANK,WETLAND,COAST'
 WHERE assembly_key IN ('water_draw_platform','washing_place','settling_basin','fishing_landing',
                        'fishing_weir','duck_house')
   AND site_biomes IS NULL;

DO $$
DECLARE n INT; bad TEXT;
BEGIN
    -- 1. EVERY BIOME NAMED MUST BE A REAL BIOME, checked against terrain_going rather than a list written here.
    --
    --    There is no biome table and no CHECK constraint anywhere — biomes are free strings referenced across
    --    half a dozen services — and writing a second list is the defect that made 45 garments unwearable.
    --    terrain_going carries exactly one row per biome (all ten), is catalogue data laid down by migrations,
    --    and is therefore the one authority a MIGRATION can actually consult.
    --
    --    THE FIRST VERSION OF THIS GUARD CHECKED world_chunk INSTEAD, and failed on a clean database — a
    --    migration runs before any world is generated, so world_chunk is empty and every biome looks unreal.
    --    It would have failed in CI on the first run. A migration guard may only depend on what the migrations
    --    themselves put there; "does the world actually generate this" is a question for the integration test,
    --    where a world exists, and that is where it now lives.
    SELECT string_agg(DISTINCT trim(b), ', ') INTO bad
      FROM assembly_definition d, unnest(string_to_array(d.site_biomes, ',')) b
     WHERE d.site_biomes IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM terrain_going tg WHERE tg.biome = trim(b));
    IF bad IS NOT NULL THEN RAISE EXCEPTION
        'V431: % is not a biome the catalogue knows, so anything needing it could never be built', bad; END IF;

    -- 2. All eight carry a requirement, and nothing else was swept up with them.
    SELECT COUNT(*) INTO n FROM assembly_definition WHERE site_biomes IS NOT NULL;
    IF n <> 8 THEN RAISE EXCEPTION 'V431: % structures carry a site requirement; eight were named', n; END IF;

    SELECT string_agg(assembly_key, ', ') INTO bad FROM assembly_definition
     WHERE site_biomes IS NOT NULL
       AND assembly_key NOT IN ('riverbank_steps','footbridge','water_draw_platform','washing_place',
                                'settling_basin','fishing_landing','fishing_weir','duck_house');
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V431: % was constrained without being named at the top of this file', bad; END IF;

    -- 3. THE THREE DELIBERATE EXCLUSIONS STAY EXCLUDED, asserted so a later cycle has to read why before
    --    changing its mind. A pool that puddles its own water does not need a shore to stand beside.
    SELECT string_agg(assembly_key, ', ') INTO bad FROM assembly_definition
     WHERE site_biomes IS NOT NULL
       AND assembly_key IN ('waterfowl_pool','earth_sheltered_hut','rainwater_catchment','sand_filter_bed');
    IF bad IS NOT NULL THEN RAISE EXCEPTION
        'V431: % makes or takes its own water and needs no shore — say why first', bad; END IF;

    -- 4. Every constrained structure is still VERIFIED, so the requirement has not been hung on something
    --    nobody can reach anyway. (Whether a world actually GENERATES enough of these biomes is asserted in
    --    SomeThingsOnlyGoUpByTheWaterIntegrationTest, where a generated world exists to ask.)
    SELECT string_agg(d.assembly_key, ', ') INTO bad FROM assembly_definition d
     WHERE d.site_biomes IS NOT NULL AND d.review_state <> 'VERIFIED';
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V431: % is not VERIFIED, so its site requirement gates nothing', bad; END IF;

    -- 5. And the structures a site requirement would strand are not constrained: anything already carrying a
    --    weather limit is harder to build, and stacking a scarce biome on top of a weather window would make
    --    some of them practically unreachable. None of the eight carries one, and this keeps it that way.
    SELECT string_agg(DISTINCT d.assembly_key, ', ') INTO bad
      FROM assembly_definition d JOIN assembly_stage s ON s.assembly_key = d.assembly_key
     WHERE d.site_biomes IS NOT NULL AND s.weather_limit IS NOT NULL;
    IF bad IS NOT NULL THEN RAISE EXCEPTION
        'V431: % would need both a scarce biome and a weather window; decide that deliberately, not by accident', bad; END IF;
END $$;
