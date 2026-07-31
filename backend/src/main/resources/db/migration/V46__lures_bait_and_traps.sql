-- V46: Lures, bait, and placed traps.
--
-- The last of the ecology migrations. Where SNARE (V42) resolves immediately,
-- a placed trap is a physical thing left standing in the world that catches in
-- its own time -- the chronicle sets it, walks away, and finds out later. Bait
-- draws populations toward a chunk rather than catching anything itself.

CREATE TABLE placed_trap (
    object_id     UUID        PRIMARY KEY REFERENCES world_object(id),
    chunk_id      UUID        NOT NULL REFERENCES world_chunk(id),
    trap_kind     VARCHAR(30) NOT NULL,
    set_by        UUID        NOT NULL REFERENCES chronicle(id),
    set_at        TIMESTAMPTZ NOT NULL,
    baited_with   VARCHAR(100) REFERENCES item_definition(item_key),
    checked_at    TIMESTAMPTZ,
    caught_species VARCHAR(80) REFERENCES wildlife_species(species_key),
    sprung        BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT placed_trap_kind_check CHECK (trap_kind IN ('SNARE','DEADFALL','PIT','FISH_TRAP','CAGE'))
);
CREATE INDEX placed_trap_chunk_idx ON placed_trap(chunk_id) WHERE NOT sprung;

-- A lure placed on the ground, drawing a class of animal toward it while it lasts.
CREATE TABLE placed_lure (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    chunk_id    UUID        NOT NULL REFERENCES world_chunk(id),
    chronicle_id UUID       NOT NULL REFERENCES chronicle(id),
    bait_item_key VARCHAR(100) NOT NULL REFERENCES item_definition(item_key),
    draws_role  VARCHAR(20) NOT NULL,
    placed_at   TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT placed_lure_role_check CHECK (draws_role IN ('CARNIVORE','HERBIVORE','OMNIVORE','SCAVENGER'))
);
CREATE INDEX placed_lure_chunk_idx ON placed_lure(chunk_id, expires_at);

-- Which bait draws which kind of animal, and for how long. Meat brings predators
-- and scavengers; fruit and greens bring browsers.
CREATE TABLE bait_profile (
    item_key    VARCHAR(100) PRIMARY KEY REFERENCES item_definition(item_key),
    draws_role  VARCHAR(20)  NOT NULL,
    potency     SMALLINT     NOT NULL DEFAULT 10,
    hours_active SMALLINT    NOT NULL DEFAULT 6,
    CONSTRAINT bait_profile_role_check CHECK (draws_role IN ('CARNIVORE','HERBIVORE','OMNIVORE','SCAVENGER'))
);

INSERT INTO bait_profile (item_key, draws_role, potency, hours_active) VALUES
('raw_game_meat',    'CARNIVORE', 25, 8),
('raw_fish',         'CARNIVORE', 20, 6),
('raw_fowl_meat',    'CARNIVORE', 18, 6),
('wild_berries',     'HERBIVORE', 15, 8),
('blackberry',       'HERBIVORE', 15, 8),
('elderberry',       'HERBIVORE', 12, 8),
('dandelion',        'HERBIVORE', 10, 6),
('nettle_leaf',      'HERBIVORE', 10, 6),
('watercress_bundle','HERBIVORE', 12, 6),
('earthworm',        'OMNIVORE',  14, 5),
('raw_honey',        'OMNIVORE',  22, 8),
('dried_grasshopper','OMNIVORE',  12, 5)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO domain_registry (domain_key, display_name, description, introduced_in_migration, origin)
VALUES ('lure_trap', 'Lures and Traps', 'Placed traps that catch in their own time, and bait that draws animals toward ground the chronicle has chosen.', 'V46', 'PREBUILT')
ON CONFLICT (domain_key) DO NOTHING;
