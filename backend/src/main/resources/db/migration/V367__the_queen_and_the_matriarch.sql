-- #122 / #121 — the queen and the matriarch.
--
-- The ticket's Tier II rule: harvesting a colony queen, a matriarch, a nest or a clutch changes population, range,
-- defensive behaviour and future material availability. Three of those four now hold — a clutch taken costs a
-- year's young (V364), a pack that loses the one it follows scatters out of every hunting rule (V363), a nest
-- robbed twice gives nothing. The queen was the one left, and she is the clearest case of all: a colony without
-- its queen is not a depleted colony, it is a finished one.
--
-- A colony kind either has a queen or it does not, and that is a fact about the creature, not about the act: bees,
-- hornets, ants and termites are ruled by one; a mussel bed, a worm patch and a caddis shallows are not, so there
-- is nothing there to take and no way to make that mistake.

ALTER TABLE insect_colony_kind
    ADD COLUMN has_a_queen BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE insect_colony_kind SET has_a_queen = TRUE
 WHERE colony_kind IN ('honeybee_hive', 'hornet_nest', 'ant_colony');

COMMENT ON COLUMN insect_colony_kind.has_a_queen IS
  '#122: whether one creature holds this colony together. Only a colony with a queen can lose one, and losing her ends it rather than depleting it.';

ALTER TABLE insect_colony
    ADD COLUMN queen_taken_at TIMESTAMPTZ;

COMMENT ON COLUMN insect_colony.queen_taken_at IS
  '#122: when this colony''s queen was cut out of it. The colony is finished from that moment — health 0, nothing ready to take for a year, and no pollination off it — which is the difference between robbing a hive and destroying one.';
