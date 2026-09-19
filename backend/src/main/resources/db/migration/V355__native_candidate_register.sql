-- #120 (epic #109) — the candidate register: every native creature and people the world MIGHT hold, and the gate
-- between a candidate and the world.
--
-- The ticket's rule is that the register may grow freely while the active world stays deliberately small. Until now
-- the register was a list in an issue, and "active" meant only that someone had written a community for it. This
-- makes the gate a fact the database keeps:
--
--   * Every candidate has a proposed tier and cognition class (#110), the ground it belongs to, and the form its home
--     would take. A name decides nothing: a candidate's class is what review says it is.
--   * Six activation reviews, one column each, taken from the ticket's activation requirements. A candidate may be
--     ACTIVE only when all six are done and it names the catalogue species it became.
--   * A community may be founded only for a species whose candidate is ACTIVE. The reedkin (DR-0024) are the one
--     active people, recorded here with the reviews their delivery under #115 carried out.
--
-- The ticket's other limit, "no more than two additional candidates in a release", is kept by NativeCandidateRegister
-- IntegrationTest, which counts activations per migration; a constraint cannot see across rows of different releases.

CREATE TABLE native_candidate (
    candidate_key           VARCHAR(40) PRIMARY KEY,
    display_name            VARCHAR(80) NOT NULL,
    proposed_tier           VARCHAR(4) NOT NULL CHECK (proposed_tier IN ('I','I/II','II','IIb','III','IV')),
    proposed_class          VARCHAR(12) NOT NULL CHECK (proposed_class IN ('PRIMAL','SOCIAL','PEOPLE','SOVEREIGN')),
    ground                  VARCHAR(60) NOT NULL,
    home_form               TEXT NOT NULL,
    status                  VARCHAR(12) NOT NULL DEFAULT 'CANDIDATE' CHECK (status IN ('CANDIDATE','UNDER_REVIEW','ACTIVE','REJECTED')),
    species_key             VARCHAR(80) REFERENCES wildlife_species(species_key),
    -- The six activation reviews (#120).
    tier_and_interactions   BOOLEAN NOT NULL DEFAULT FALSE,   -- 1. tier under #110 and permitted interactions under #117
    ecology                 BOOLEAN NOT NULL DEFAULT FALSE,   -- 2. distribution, cap, diet, water, life stage, waste, disease, predator/prey
    body_and_materials      BOOLEAN NOT NULL DEFAULT FALSE,   -- 3. anatomy, mobility, carrying, harvest ethics, objects
    home_and_footprint      BOOLEAN NOT NULL DEFAULT FALSE,   -- 4. settlement/home/range form and resource footprint
    social_rules            BOOLEAN NOT NULL DEFAULT FALSE,   -- 5. individual/community/relationship rules (Tier IIb-IV)
    actions_and_tests       BOOLEAN NOT NULL DEFAULT FALSE,   -- 6. materials, structures, actions, conflict rules and tests
    activated_in            VARCHAR(10),
    note                    TEXT NOT NULL,
    CONSTRAINT active_means_reviewed CHECK (status <> 'ACTIVE' OR (
        tier_and_interactions AND ecology AND body_and_materials AND home_and_footprint AND social_rules AND actions_and_tests
        AND species_key IS NOT NULL AND activated_in IS NOT NULL)),
    CONSTRAINT only_active_names_a_species CHECK (status = 'ACTIVE' OR species_key IS NULL)
);
CREATE UNIQUE INDEX native_candidate_one_per_species ON native_candidate (species_key) WHERE species_key IS NOT NULL;

INSERT INTO native_candidate (candidate_key, display_name, proposed_tier, proposed_class, ground, home_form, note) VALUES
-- Forest and old growth.
('kobold_warrens',      'Kobold warrens',        'IIb', 'PEOPLE',    'TEMPERATE_FOREST, CAVE', 'small cave and root-burrow communities with compact physical stores', 'Cunning folk; scavenging, mining, fibre and wood culture; vulnerable to smoke and flooding.'),
('caprine_folk',        'Caprine folk',          'III', 'PEOPLE',    'TEMPERATE_FOREST edge, HIGHLAND meadow', 'mobile seasonal camps', 'Foragers and herders; music and ritual as culture, never a magic shortcut; goat-compatible ecology.'),
('mycelial_collective', 'Mycelial collective',   'IIb', 'PEOPLE',    'CAVE, old growth', 'physical fruiting and growing sites', 'Moisture, air, spore and waste constraints; slow communication; no remote telepathy unless later proven.'),
('thornback_griffin',   'Thornback griffin',     'I',   'PRIMAL',    'TEMPERATE_FOREST, HIGHLAND', 'nest and hunting territory', 'Apex; not an aerial taxi or a speaking ally by default.'),
('forest_giant',        'Forest giant lineage',  'IV',  'SOVEREIGN', 'TEMPERATE_FOREST', 'ancient territory held by a family', 'Very rare; large calorie, space and material needs; limited interactions.'),
-- Grassland, scrub and open routes.
('centaur_confederacy', 'Centaur confederacies', 'III', 'PEOPLE',    'GRASSLAND', 'territory camps along open routes', 'Defined in #118.'),
('orc_clans',           'Orc clans',             'III', 'PEOPLE',    'GRASSLAND, HIGHLAND', 'clan holds', 'Defined in #119.'),
('goblin_bands',        'Goblin bands',          'IIb', 'PEOPLE',    'GRASSLAND, TEMPERATE_FOREST', 'band camps', 'Defined in #119.'),
('minotaur_homesteads', 'Minotaur homesteads',   'III', 'PEOPLE',    'GRASSLAND, ruin edge', 'homesteads with grazing, farming and storage', 'Large-bodied; no automatic maze or dungeon trope.'),
('griffin_prides',      'Griffin prides',        'II',  'SOCIAL',    'HIGHLAND, GRASSLAND', 'coordinated nesting grounds', 'Group hunters; no default trade or personhood.'),
('basilisk',            'Basilisk',              'I',   'PRIMAL',    'rocky scrub', 'ambush ground', 'Rare; sight, venom and territory physically bounded; no arbitrary petrification.'),
-- Wetland, river, lake and warm slope.
('reedkin',             'Reedkin',               'III', 'PEOPLE',    'WETLAND, RIVER_BANK', 'raised reed isles with a store house', 'Defined in #118; the first people placed (#115, DR-0024).'),
('saurian_kin',         'Saurian kin',           'III', 'PEOPLE',    'WETLAND, warm slope', 'basking and nesting grounds', 'Defined in #118.'),
('naga_river_clans',    'Naga river clans',      'III', 'PEOPLE',    'RIVER_BANK, WETLAND', 'river and wetland settlements', 'Semi-aquatic and ectothermic; temperature and nesting constraints; no hypnosis or water breathing.'),
('moss_troll_families', 'Moss troll families',   'IIb', 'PEOPLE',    'WETLAND, TEMPERATE_FOREST', 'family holds', 'Distinct from hostile bog wraiths; only if diet, disease and footprint are coherent.'),
('giant_beaver_colony', 'Giant beaver colony',   'II',  'SOCIAL',    'RIVER_BANK, WETLAND', 'dams and lodges', 'Habitat engineers; not a merchant society unless personhood review approves it.'),
('marsh_leviathan',     'Marsh leviathan',       'I',   'PRIMAL',    'deep WETLAND water', 'signs, territory and migration', 'Rare deep-water apex; no routine combat spawn.'),
-- Highland, mountain, cliff and cave.
('stonekin_enclaves',   'Stonekin enclaves',     'III', 'PEOPLE',    'MOUNTAIN, CAVE', 'enclaves in the rock', 'Defined in #118.'),
('cyclops_households',  'Cyclops households',    'III', 'PEOPLE',    'HIGHLAND, MOUNTAIN', 'households', 'Defined in #119.'),
('harpy_aerie_clans',   'Harpy aerie clans',     'III', 'PEOPLE',    'HIGHLAND cliff', 'aeries', 'Defined in #118. The harpy now in the catalogue is classified by behaviour (V344), not by this candidacy.'),
('dwarrow_deepfolk',    'Dwarrow deepfolk',      'III', 'PEOPLE',    'CAVE, MOUNTAIN', 'deep halls with ventilation and water', 'Only if differentiated materially and ecologically from stonekin.'),
('cave_giant',          'Cave giant lineage',    'IV',  'SOVEREIGN', 'CAVE, MOUNTAIN', 'sparse family holds', 'Extreme resource footprint; strong territory requirements.'),
('crag_drake',          'Crag drake',            'I/II','PRIMAL',    'MOUNTAIN', 'clutch ground', 'Mountain predator or social clutch organism; never conflated with Tier IV dragons.'),
('deepstone_oracle',    'Deepstone oracle',      'IV',  'SOVEREIGN', 'CAVE', 'a single chamber', 'Defined in #118; individually authored and non-omniscient.'),
-- Ruins and across the land.
('gargoyle_roost',      'Gargoyle roost',        'I/II','PRIMAL',    'ANCIENT_RUIN', 'ruin roosts', 'Ruin-adapted nocturnal organism; stone, heat and shelter needs defined physically; no animate-statue magic.'),
('ruin_scavenger_folk', 'Ruin scavenger folk',   'IIb', 'PEOPLE',    'ANCIENT_RUIN', 'bands in existing ruins', 'Salvage and repair economy; no claim they built the lost civilisation.'),
('elder_groveheart',    'Elder groveheart',      'IV',  'SOVEREIGN', 'TEMPERATE_FOREST', 'rooted physical territory', 'Defined in #118; one or a few sovereign forest beings.'),
('dragon',              'Dragon',                'IV',  'SOVEREIGN', 'MOUNTAIN', 'a hoard lair', 'Defined in #117.');

-- The reedkin were delivered under #115 with every review the ticket asks for: tier and interactions (V344, V346),
-- ecology and a capped population (V345, NativeCommunityService), body and protected remains (V344), isles and stores
-- (V345), social rules (contact, trade, conduct, territory, agreements) and their tests.
UPDATE native_candidate SET status='ACTIVE', species_key='reedkin', activated_in='V346',
       tier_and_interactions=TRUE, ecology=TRUE, body_and_materials=TRUE, home_and_footprint=TRUE, social_rules=TRUE, actions_and_tests=TRUE
 WHERE candidate_key='reedkin';

-- The gate itself: no community for a species whose candidacy has not passed.
CREATE OR REPLACE FUNCTION native_community_must_be_an_active_candidate() RETURNS trigger AS $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM native_candidate WHERE species_key = NEW.species_key AND status = 'ACTIVE') THEN
    RAISE EXCEPTION 'no community may be founded for % until its candidacy is ACTIVE in native_candidate (#120)', NEW.species_key;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER native_community_must_be_an_active_candidate
  BEFORE INSERT OR UPDATE OF species_key ON native_community
  FOR EACH ROW EXECUTE FUNCTION native_community_must_be_an_active_candidate();

-- And the register agrees with the world it guards: every community already founded is of an active candidate.
DO $$
DECLARE stray text;
BEGIN
  SELECT string_agg(DISTINCT n.species_key, ', ') INTO stray FROM native_community n
   WHERE NOT EXISTS (SELECT 1 FROM native_candidate c WHERE c.species_key = n.species_key AND c.status = 'ACTIVE');
  IF stray IS NOT NULL THEN RAISE EXCEPTION 'V355: communities exist for species with no active candidacy: %', stray; END IF;
END $$;

COMMENT ON TABLE native_candidate IS
  '#120: every native creature and people the world might hold. ACTIVE only with all six reviews; a community may be founded only for an ACTIVE candidate.';
