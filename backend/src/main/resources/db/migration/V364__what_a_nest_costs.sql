-- #122 — what a nest costs.
--
-- The catalogue has had `bird_egg` since the beginning and exactly one way to get one: kill the bird and find eggs
-- among its drops. That is backwards, and it is the shape this project calls a catalogue token — an item that
-- exists without a way to come by it that makes sense.
--
-- Robbing a nest is the way that does. It is non-lethal, and the ticket's rule for non-lethal taking is that it
-- carries season, method and consequence. The consequence is this column: a clutch taken is this year's young
-- taken, so the birds stand where they stood and what would have been added is not added. The daily breeding pass
-- reads it; the encounter refuses a nest already emptied. Neither is a hidden cooldown — both are the same fact.

ALTER TABLE wildlife_population
    ADD COLUMN clutch_taken_at TIMESTAMPTZ;

COMMENT ON COLUMN wildlife_population.clutch_taken_at IS
  '#122: when this population''s clutch was last taken from the nest. For thirty days afterwards it does not breed, and the nest has nothing in it.';

INSERT INTO activity_impact (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire, notes, labor_energy, labor_hygiene, capability_domain, duration_minutes)
VALUES ('RAID_NEST', 'COMMOTION', 3, FALSE, FALSE,
        'Going over cover on hands and knees for a nest, and lifting a clutch out of it: the birds go up off the water and keep calling long after, and everything with ears on that ground knows where you are.',
        6, 3, 'FINE_MOTOR', 25)
ON CONFLICT (intent_key) DO NOTHING;
