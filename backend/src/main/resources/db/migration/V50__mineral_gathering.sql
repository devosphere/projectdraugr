-- V50: Minerals a chronicle can actually find.
--
-- V49 introduced flint, iron pyrite, and clear crystal as fire-making kit but
-- gave them no acquisition path, which made the striking methods unreachable in
-- play — the same flaw the hide garments had. A definition without a way to
-- obtain it is scenery, not a mechanic.
--
-- Minerals occur where geology puts them: flint in chalk and limestone country,
-- pyrite and quartz in exposed rock and old streambeds. Finding them is not
-- guaranteed — searching stone for a nodule is patient work, and rarity is the
-- chance a given search turns something up.

CREATE TABLE mineral_definition (
    mineral_key    VARCHAR(100) PRIMARY KEY REFERENCES item_definition(item_key),
    display_name   VARCHAR(120) NOT NULL,
    biome_affinity VARCHAR(160) NOT NULL,
    rarity         NUMERIC(3,2) NOT NULL DEFAULT 0.5 CHECK (rarity > 0 AND rarity <= 1),
    tool_required  VARCHAR(40),
    yield_min      SMALLINT     NOT NULL DEFAULT 1,
    yield_max      SMALLINT     NOT NULL DEFAULT 1,
    notes          TEXT
);

INSERT INTO mineral_definition (mineral_key, display_name, biome_affinity, rarity, tool_required, yield_min, yield_max, notes) VALUES
('flint_stone',  'Flint',         'HIGHLAND,GRASSLAND,MOUNTAIN', 0.55, NULL,        1, 3, 'Nodules weather out of chalk and limestone; they ring differently underfoot than the stone around them.'),
('iron_pyrite',  'Iron pyrite',   'MOUNTAIN,HIGHLAND',           0.35, 'STRIKING',  1, 2, 'Brassy cubes in exposed rock and old streambeds. Struck against flint it throws real sparks, which is why it was carried.'),
('lens_crystal', 'Clear crystal', 'MOUNTAIN',                    0.20, 'STRIKING',  1, 1, 'Water-clear quartz, rare enough that finding one worth focusing light through is a genuine event.'),
('field_stone',  'Field stone',   'HIGHLAND,GRASSLAND,MOUNTAIN,TEMPERATE_FOREST', 0.95, NULL, 1, 3, 'Loose stone, everywhere the soil is thin.'),
('precision_tool_stone','Precision tool stone','MOUNTAIN,HIGHLAND', 0.40, 'STRIKING', 1, 2, 'Fine-grained stone that holds an edge — the difference between a blade and a rock.')
ON CONFLICT (mineral_key) DO NOTHING;

INSERT INTO technique_definition (technique_key, display_name, domain_key, difficulty, produces_item, requires_tool, proven_in, principle) VALUES
('mineral_prospecting', 'Mineral prospecting', 'stoneworking', 'PRIMITIVE', NULL, NULL, 'V50',
 'Stone is not uniform. Knowing which rock carries flint, which carries pyrite, and where each weathers out is the difference between an hour wasted and a fire tonight.')
ON CONFLICT (technique_key) DO NOTHING;

INSERT INTO domain_registry (domain_key, display_name, description, introduced_in_migration, origin)
VALUES ('minerals', 'Minerals', 'Flint, pyrite, quartz, and tool stone — where they occur and how they are found.', 'V50', 'PREBUILT')
ON CONFLICT (domain_key) DO NOTHING;
