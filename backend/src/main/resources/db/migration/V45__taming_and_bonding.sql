-- V45: Taming and bonding.
--
-- Trust is built, never granted. A bond moves through stages as a chronicle
-- returns, approaches calmly, and offers food; it regresses when they approach
-- armed or press too hard. Tamability is intrinsic to the species (V41) -- a
-- river turtle can be won over, a lynx essentially cannot.
--
-- Per the chronicle-legacy rule, a bond belongs to the chronicle who built it.
-- When they die the bond dies with them; the animal remains in the world, wild
-- again to whoever comes next.

CREATE TABLE wildlife_bond (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    chronicle_id        UUID        NOT NULL REFERENCES chronicle(id),
    population_id       UUID        NOT NULL REFERENCES wildlife_population(id),
    bond_stage          VARCHAR(20) NOT NULL DEFAULT 'WILD',
    trust_level         SMALLINT    NOT NULL DEFAULT 0,
    interaction_count   SMALLINT    NOT NULL DEFAULT 0,
    last_interaction_at TIMESTAMPTZ NOT NULL,
    tamed_object_id     UUID        REFERENCES world_object(id),
    UNIQUE (chronicle_id, population_id),
    CONSTRAINT wildlife_bond_stage_check CHECK (bond_stage IN ('WILD','WARY','CAUTIOUS','TOLERANT','BONDED','TAMED')),
    CONSTRAINT wildlife_bond_trust_check CHECK (trust_level BETWEEN 0 AND 100)
);
CREATE INDEX wildlife_bond_chronicle_idx ON wildlife_bond(chronicle_id);

-- Produce from tamed livestock. A tamed goat gives milk, a tamed fowl eggs --
-- but only on a bond that has actually reached TAMED, and only on the clock.
CREATE TABLE tamed_production (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    bond_id        UUID        NOT NULL REFERENCES wildlife_bond(id),
    item_key       VARCHAR(100) NOT NULL REFERENCES item_definition(item_key),
    interval_hours SMALLINT    NOT NULL DEFAULT 24,
    last_yielded_at TIMESTAMPTZ
);

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable) VALUES
('goat_milk', 'Goat Milk', 'FOOD', 450, 450, TRUE, FALSE),
('fowl_egg',  'Fowl Egg',  'FOOD',  60,  60, TRUE, FALSE),
('wool_tuft', 'Wool Tuft', 'MATERIAL', 80, 400, TRUE, FALSE)
ON CONFLICT (item_key) DO NOTHING;

-- What a species gives once tamed, and how often. Keyed by species so the
-- production rules are declarative rather than buried in a switch.
CREATE TABLE tamed_yield (
    species_key    VARCHAR(80)  NOT NULL REFERENCES wildlife_species(species_key),
    item_key       VARCHAR(100) NOT NULL REFERENCES item_definition(item_key),
    interval_hours SMALLINT     NOT NULL DEFAULT 24,
    PRIMARY KEY (species_key, item_key)
);

INSERT INTO tamed_yield (species_key, item_key, interval_hours) VALUES
('mountain_goat', 'goat_milk', 24),
('mountain_goat', 'wool_tuft', 72),
('reindeer',      'goat_milk', 36),
('marsh_fowl',    'fowl_egg',  24),
('mallard_duck',  'fowl_egg',  30),
('wild_turkey',   'fowl_egg',  30),
('wood_pigeon',   'fowl_egg',  48)
ON CONFLICT (species_key, item_key) DO NOTHING;

INSERT INTO domain_registry (domain_key, display_name, description, introduced_in_migration, origin)
VALUES ('taming', 'Taming', 'Trust built over repeated calm approach -- bond stages, trust regression when approached armed, and produce from tamed livestock.', 'V45', 'PREBUILT')
ON CONFLICT (domain_key) DO NOTHING;
