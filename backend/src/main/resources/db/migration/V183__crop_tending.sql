-- V183 — crop tending (EPIC #162 / story #165 tending). A sown stand, once in the ground, needed no attention until
-- harvest — the season passed and the grain came in whether the Chronicle returned to the field or not. That made the
-- crop a set-and-forget button, against the EPIC's rule that a crop is a living population that must be tended across
-- time. This marks whether a stand has been weeded during its growing season: a stand worked clean fills out fuller,
-- for weeding frees the grain from the weeds that compete with it for the soil, the light, and the water, so a tended
-- stand carries an extra head over one left to itself — the reward for returning to the field to work it.
ALTER TABLE crop_stand ADD COLUMN weeded_at TIMESTAMPTZ;
