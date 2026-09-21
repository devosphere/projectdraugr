-- #211 / #118 — the standing wood.
--
-- Work in a people's territory has been an encroachment since V351: noticed, weighed a little, and answered with a
-- demand if it goes on. That is right for most work. It is wrong for the one kind of work that does not disturb the
-- ground but takes it: felling.
--
-- A people names the disturbance that removes the thing its life is made of. For a reed-isle people that is the
-- standing wood of the carr — their withies, their poles, their fuel, and the cover their water sits in. Cutting it
-- inside their ground is not somebody working nearby; it is the ground going. It is weighed six times an ordinary
-- notice, it is remembered for a month, and three of them make an enemy.
--
-- It is also the last system the grovebound were waiting on (12.1-Native-Peoples): a people whose home IS the trees
-- needs felling to be recognised as the gravest encroachment before it can be placed. This is that recognition,
-- written so that any people can name its own.

ALTER TABLE native_community
    ADD COLUMN grave_encroachment_kind VARCHAR(20);

COMMENT ON COLUMN native_community.grave_encroachment_kind IS
  '#211: the disturbance kind that takes the thing this people''s life is made of (CANOPY_LOSS for a people of the carr). Weighed far heavier than ordinary encroachment, not excused by leave to work the ground, and three within a month make an enemy.';

-- The reedkin answer the loss of the carr, which is where every pole, withy and hearth-stick they own comes from.
UPDATE native_community SET grave_encroachment_kind = 'CANOPY_LOSS' WHERE species_key = 'reedkin';
