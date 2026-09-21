-- #115 / #118 (epic #109) — the grovebound, the second people.
--
-- #115 caps the first release at two cultures and says the second is chosen by which candidate's missing system
-- gets built first (12.1-Native-Peoples). Two of the three were waiting on systems nobody had written. The
-- grovebound needed fire as a threat to a settlement — delivered with the burning and mending of houses (#662) —
-- and felling recognised as the gravest encroachment, which V365 gives any people the means to name. Both are in,
-- so the grovebound take the second slot.
--
-- WHO THEY ARE. A slow, long-lived woodland people of bark-like skin and fibrous limbs, living in one old-growth
-- grove which is their home and, in a real sense, their body: they are rooted to a place rather than to a house.
-- They live on mast, nuts and fungi; they work bark, resin and herbs; they speak slowly and cut their records into
-- bark. They fear fire above everything, and the loss of the standing wood is the one wrong they do not forgive.
--
-- WHAT THIS MIGRATION DOES NOT DO. It does not teach any service about them. Every system a people has — contact,
-- trade, agreements, companionship, membership, claims, territory, succession, remains, news — reads the community
-- row and the cognition profile, so a second people is data (DR-0026). The only code is where to put their grove.
--
-- WHERE THEY DIFFER, AND WHY IT IS THE POINT. V344's eligibility columns were read by nothing until #671 wired
-- them to each act. The grovebound are the first people to answer any of them NO:
--   * companionship_eligible FALSE — the grovebound do not leave their grove. Asking is refused in their terms.
--   * community_membership FALSE — you do not become grovebound; it is not a door anybody opens.
-- Trade, agreements and paid work are open to them. A people whose social life differs by data alone is exactly
-- what the classification was for.

INSERT INTO wildlife_species (species_key, kingdom_class, ecological_role, activity_cycle, movement_class, size_tier,
                              base_resistance, ambush_hunter, pack_hunter, territorial, tamability, biome_affinity,
                              toxic, venomous, temperament, needs_a_wallow)
VALUES ('grovebound', 'MAMMALIA', 'HERBIVORE', 'DIURNAL', 'TERRESTRIAL', 'MEDIUM',
        40, FALSE, FALSE, TRUE, 0, '',
        FALSE, FALSE, 'WARY', FALSE)
ON CONFLICT (species_key) DO NOTHING;

-- Declared in the same migration, as V344's trigger requires of every species.
INSERT INTO cognition_profile (species_key, cognition_class, communication_mode, symbolic_language, tool_culture,
                               individual_identity, kinship_model, community_membership, moral_agency, trade_eligible,
                               agreement_eligible, companionship_eligible, settlement_capable,
                               working_relationship_eligible, restraint_prohibited, domestication_prohibited,
                               remains_protected, social_risk_profile, review_note, classified_in)
VALUES ('grovebound', 'PEOPLE', 'LANGUAGE', TRUE, TRUE,
        TRUE, 'KIN_GROUP', FALSE, TRUE, TRUE,
        TRUE, FALSE, TRUE,
        TRUE, TRUE, TRUE,
        TRUE, 'COMMUNITY_RETALIATION',
        'the second native people (#115, #118): a rooted old-growth people of bark, mast and resin. They do not '
        'leave their grove with strangers and nobody becomes one of them, which is why companionship and membership '
        'are declared FALSE rather than merely discouraged.', 'V366')
ON CONFLICT (species_key) DO NOTHING;

-- The register's gate (#120): a community may only be founded for an ACTIVE candidacy, and ACTIVE requires all six
-- reviews. Every one of them is answerable now, which is the argument for activating them at all.
INSERT INTO native_candidate (candidate_key, display_name, proposed_tier, proposed_class, ground, home_form, note)
VALUES ('grovebound', 'Grovebound', 'III', 'PEOPLE', 'old-growth TEMPERATE_FOREST',
        'one grove, which is their home and their body',
        'Defined in #118 and the card in 12.1-Native-Peoples. Placed as the second culture once felling became the '
        'gravest encroachment (V365) and fire became a threat to a settlement (#111).')
ON CONFLICT (candidate_key) DO NOTHING;

UPDATE native_candidate SET status='ACTIVE', species_key='grovebound', activated_in='V366',
       tier_and_interactions=TRUE,   -- Tier III PEOPLE under #110, with every permitted interaction named above
       ecology=TRUE,                 -- mast, nuts and fungi on old-growth ground; one grove per world; no affinity
       body_and_materials=TRUE,      -- bark, resin and herbs, all catalogue items with their own chains
       home_and_footprint=TRUE,      -- a grove with a house and a store, and a territory of two chunks
       social_rules=TRUE,            -- every people system reads the community row; the two refusals are declared
       actions_and_tests=TRUE        -- ASecondPeopleIntegrationTest, plus every people test that now runs over two
 WHERE candidate_key='grovebound';

COMMENT ON COLUMN native_community.grave_encroachment_kind IS
  '#211: the disturbance kind that takes the thing this people''s life is made of. CANOPY_LOSS for both peoples so far: the reedkin lose their withies and cover, the grovebound lose the grove itself.';

-- What a people's own hands make, which is what it has to trade that is not food (#113). This was a Java constant
-- naming reed goods, so every people in the world made mats: the second people is what made it data.
ALTER TABLE native_community
    ADD COLUMN made_goods VARCHAR(80)[] NOT NULL DEFAULT ARRAY['reed_mat','fiber_cordage','woven_basket','fish_trap']::VARCHAR(80)[];

COMMENT ON COLUMN native_community.made_goods IS
  '#113: the goods this people makes, in the order its makers turn to them. Read by the making step and by trade, which values a thing made here differently from a thing brought in.';
