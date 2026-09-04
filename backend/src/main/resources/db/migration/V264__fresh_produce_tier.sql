-- V264 — foraged food must keep, and then stop keeping. Every gathered plant food was created with no
-- food_preservation_state at all, so roughly 65 foraged items — berries, mushrooms, greens, roots, nuts — never
-- spoiled. Only animal food was ever registered (game meat and fish). Pick a handful of berries and they would still
-- be good a year later, which defeats the preservation system exactly the way the untracked dressed fish did.
--
-- Raw's 18 hours is a meat span and far too harsh for produce, while the dried tier is far too generous for a
-- mushroom. FRESH is added as the produce tier. Dry keepers — nuts, mast, grain — are registered on the existing
-- DRIED tier instead, since that is genuinely how they behave.
ALTER TABLE food_preservation_state DROP CONSTRAINT IF EXISTS food_preservation_state_preparation_kind_check;
ALTER TABLE food_preservation_state ADD CONSTRAINT food_preservation_state_preparation_kind_check
    CHECK (preparation_kind IN ('RAW','FRESH','COOKED','SALTED','DRIED','SMOKED'));
