-- V44: Wildlife events and tracking.
--
-- Until now wildlife only ever acted when the chronicle chose to confront it.
-- This adds the other direction: a hunting predator in the same ground as a
-- chronicle who is busy gathering may reach them first. It also adds TRACK --
-- reading the ground for what passed over it, which is how a hunter finds game
-- without stumbling into it.

-- Every meaningful contact between a chronicle and a population, in either
-- direction. Append-only, like every other ledger in the world.
CREATE TABLE chronicle_wildlife_event (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    chronicle_id   UUID        NOT NULL REFERENCES chronicle(id),
    population_id  UUID        NOT NULL REFERENCES wildlife_population(id),
    chunk_id       UUID        NOT NULL REFERENCES world_chunk(id),
    event_kind     VARCHAR(30) NOT NULL,
    occurred_at    TIMESTAMPTZ NOT NULL,
    source_action_id UUID,
    payload        JSONB       NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT chronicle_wildlife_event_kind_check
        CHECK (event_kind IN ('PASSIVE_ATTACK','FLED','OBSERVED','TRACKED','APPROACHED','STALKED'))
);
CREATE INDEX chronicle_wildlife_event_chronicle_idx ON chronicle_wildlife_event(chronicle_id, occurred_at DESC);
CREATE INDEX chronicle_wildlife_event_chunk_idx ON chronicle_wildlife_event(chunk_id);

-- The ledger is history: it is written once and never revised.
CREATE OR REPLACE FUNCTION prevent_chronicle_wildlife_event_mutation() RETURNS trigger AS $$
BEGIN RAISE EXCEPTION 'chronicle_wildlife_event is immutable'; END; $$ LANGUAGE plpgsql;
CREATE TRIGGER chronicle_wildlife_event_is_immutable
    BEFORE UPDATE OR DELETE ON chronicle_wildlife_event
    FOR EACH ROW EXECUTE FUNCTION prevent_chronicle_wildlife_event_mutation();

-- What a species leaves on the ground, and how long it stays readable. TRACK
-- reads these; a fresh print in soft ground says more than an old one in stone.
CREATE TABLE wildlife_sign (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    species_key   VARCHAR(80)  NOT NULL REFERENCES wildlife_species(species_key),
    sign_kind     VARCHAR(30)  NOT NULL,
    readable_hours SMALLINT    NOT NULL DEFAULT 24,
    CONSTRAINT wildlife_sign_kind_check
        CHECK (sign_kind IN ('PRINTS','SCAT','FEATHERS','DISTURBED_GROUND','CARCASS_SCRAPS','DEN_MARKS','TERRITORIAL_SCRATCH'))
);
CREATE INDEX wildlife_sign_species_idx ON wildlife_sign(species_key);

-- Signs by the kind of creature that leaves them. Heavy animals print; predators
-- scratch and den; birds drop feathers; scavengers leave scraps behind them.
INSERT INTO wildlife_sign (species_key, sign_kind, readable_hours)
SELECT species_key, 'PRINTS', CASE WHEN size_tier IN ('LARGE','HUGE') THEN 48 WHEN size_tier='MEDIUM' THEN 24 ELSE 12 END
FROM wildlife_species WHERE movement_class IN ('TERRESTRIAL','AMPHIBIOUS');

INSERT INTO wildlife_sign (species_key, sign_kind, readable_hours)
SELECT species_key, 'SCAT', 36 FROM wildlife_species
WHERE movement_class IN ('TERRESTRIAL','AMPHIBIOUS') AND size_tier IN ('MEDIUM','LARGE','HUGE');

INSERT INTO wildlife_sign (species_key, sign_kind, readable_hours)
SELECT species_key, 'FEATHERS', 24 FROM wildlife_species WHERE kingdom_class='AVES';

INSERT INTO wildlife_sign (species_key, sign_kind, readable_hours)
SELECT species_key, 'TERRITORIAL_SCRATCH', 72 FROM wildlife_species WHERE territorial AND size_tier IN ('MEDIUM','LARGE','HUGE');

INSERT INTO wildlife_sign (species_key, sign_kind, readable_hours)
SELECT species_key, 'DEN_MARKS', 96 FROM wildlife_species
WHERE ecological_role='CARNIVORE' AND movement_class='TERRESTRIAL';

INSERT INTO wildlife_sign (species_key, sign_kind, readable_hours)
SELECT species_key, 'CARCASS_SCRAPS', 18 FROM wildlife_species WHERE ecological_role='SCAVENGER';

INSERT INTO wildlife_sign (species_key, sign_kind, readable_hours)
SELECT species_key, 'DISTURBED_GROUND', 24 FROM wildlife_species
WHERE ecological_role IN ('HERBIVORE','OMNIVORE') AND movement_class='TERRESTRIAL' AND size_tier IN ('MEDIUM','LARGE','HUGE');

INSERT INTO domain_registry (domain_key, display_name, description, introduced_in_migration, origin)
VALUES ('tracking', 'Tracking', 'Reading the ground for what passed over it -- prints, scat, feathers, den marks -- and the ledger of every contact between a chronicle and a population.', 'V44', 'PREBUILT')
ON CONFLICT (domain_key) DO NOTHING;
