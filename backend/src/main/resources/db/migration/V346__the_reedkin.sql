-- #115 (epic #109) — the reedkin, the first native people. Decision and full specification: DR-0024 in
-- docs/systems/06.3-Decision-Log.md.
--
-- A marsh-and-river-isle people: web-footed, fur-coated, amphibious in habit, living on raised reed-and-withy isles
-- where freshwater marsh meets running water. Fish is their staple; reed, withy, cordage and bone their material
-- culture. They govern by elders, speak through a headsperson, keep strangers to the edge of the isle until
-- invited, and answer harm as a community.
--
-- WHY THE AFFINITY IS EMPTY. A people lives where its communities are, not wherever a biome is. Twenty queries in
-- the ambient wildlife systems — hunting, trapping, taming, perception — choose species by
-- `biome_affinity ILIKE '%BIOME%'`; an empty affinity is matched by none of them, so a reedkin can never be
-- stumbled on as game, caught in a snare, or approached to be tamed. Where they are is decided by
-- NativeCommunityService.seedPeoples, which places their isles, and nothing else. The catalogue's own stranding
-- check ignores an empty token by design, so this is not "an entry with nowhere to live": it lives on its isles.
--
-- WHY NOTHING ELSE IS NEEDED TO PROTECT THEM. V344 already refuses to let a PEOPLE be domesticable, restrainable or
-- harvestable, and the code that tames and butchers reads those same columns. The reedkin are added into a world
-- that already knows what a person is.

INSERT INTO wildlife_species (species_key, kingdom_class, ecological_role, activity_cycle, movement_class, size_tier,
                              base_resistance, ambush_hunter, pack_hunter, territorial, tamability, biome_affinity,
                              toxic, venomous, temperament, needs_a_wallow)
VALUES ('reedkin', 'MAMMALIA', 'OMNIVORE', 'DIURNAL', 'AMPHIBIOUS', 'MEDIUM',
        30, FALSE, FALSE, TRUE, 0, '',
        FALSE, FALSE, 'WARY', FALSE)
ON CONFLICT (species_key) DO NOTHING;

-- Declared in the same migration, as V344's trigger requires of every creature.
INSERT INTO cognition_profile (species_key, cognition_class, communication_mode, symbolic_language, tool_culture,
                               individual_identity, kinship_model, community_membership, moral_agency, trade_eligible,
                               agreement_eligible, companionship_eligible, settlement_capable,
                               working_relationship_eligible, restraint_prohibited, domestication_prohibited,
                               remains_protected, social_risk_profile, review_note, classified_in)
VALUES ('reedkin', 'PEOPLE', 'LANGUAGE', TRUE, TRUE,
        TRUE, 'KIN_GROUP', TRUE, TRUE, TRUE,
        TRUE, TRUE, TRUE,
        TRUE, TRUE, TRUE,
        TRUE, 'COMMUNITY_RETALIATION',
        'the first native people (#115, DR-0024): a marsh-and-river-isle people of fish, reed and withy', 'V346')
ON CONFLICT (species_key) DO NOTHING;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM cognition_profile WHERE species_key='reedkin' AND cognition_class='PEOPLE' AND remains_protected AND domestication_prohibited) THEN
    RAISE EXCEPTION 'V346: the reedkin must be a protected people';
  END IF;
  -- Nothing may find them by biome: they are where their isles are.
  IF EXISTS (SELECT 1 FROM wildlife_species WHERE species_key='reedkin' AND (biome_affinity <> '' OR tamability <> 0)) THEN
    RAISE EXCEPTION 'V346: a people must not be reachable as ambient wildlife or tameable';
  END IF;
  -- #115: no more than two cultures in the first release.
  IF (SELECT count(*) FROM cognition_profile WHERE cognition_class IN ('PEOPLE','SOVEREIGN')) > 2 THEN
    RAISE EXCEPTION 'V346: more than two native cultures would exceed the first-release cap (#115)';
  END IF;
  -- What they live on must be real food the world keeps on a clock.
  IF NOT EXISTS (SELECT 1 FROM item_definition WHERE item_key='dried_fish' AND category='FOOD') THEN
    RAISE EXCEPTION 'V346: the reedkin staple (dried fish) is missing from the catalogue';
  END IF;
END $$;
