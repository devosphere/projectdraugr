-- Regression: AI narration must never mutate the append-only chronicle_action row.
--
-- Pins the contract behind the fix for the prevent_chronicle_action_mutation violation, where
-- the Simulation Agent's refined narration was written with `UPDATE chronicle_action` and the
-- immutability trigger (rightly) rejected it — a hard error that, on the old ordering, fired
-- AFTER a paid model call.
--
-- Self-contained: it reproduces the immutable-history trigger (V6) and the narration overlay
-- shape (V65) in TEMP tables, so it runs on any fresh Postgres without seeding the real
-- chronicle -> world_object -> world_genesis FK chain. The SAME behaviour against the REAL
-- migrated tables is exercised by NarrationOverlayIntegrationTest in CI. Any RAISE below exits
-- the script non-zero under ON_ERROR_STOP, failing the suite.

BEGIN;

-- --- Mirror of the immutable ledger + its trigger (V6) ----------------------------------------
CREATE TEMP TABLE ca (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), narration text NOT NULL) ON COMMIT DROP;
CREATE OR REPLACE FUNCTION ca_immutable() RETURNS trigger AS $fn$
BEGIN RAISE EXCEPTION 'chronicle_action and its effects are immutable history'; END;
$fn$ LANGUAGE plpgsql;
CREATE TRIGGER ca_is_immutable BEFORE UPDATE OR DELETE ON ca FOR EACH ROW EXECUTE FUNCTION ca_immutable();

-- --- Mirror of the narration overlay (V65) ---------------------------------------------------
CREATE TEMP TABLE can (action_id uuid PRIMARY KEY REFERENCES ca(id), narration text NOT NULL, model text) ON COMMIT DROP;

-- A resolved action, holding the deterministic prose (the durable source of truth).
INSERT INTO ca (id, narration) VALUES ('11111111-1111-1111-1111-111111111111', 'The oak gives way and falls.');

-- 1) THE OLD BUG: writing the AI narration back with UPDATE must be REJECTED by the trigger.
DO $$
BEGIN
    UPDATE ca SET narration = 'The oak gives way and falls. Sap beads at the wound.'
    WHERE id = '11111111-1111-1111-1111-111111111111';
    RAISE EXCEPTION 'REGRESSION: UPDATE on the immutable ledger was allowed — the bug is back';
EXCEPTION
    WHEN sqlstate 'P0001' THEN
        IF SQLERRM LIKE '%immutable history%' THEN RAISE NOTICE 'PASS: base row is immutable (UPDATE rejected)';
        ELSE RAISE; END IF;
END $$;

-- 2) THE FIX: the enriched narration is INSERTed into the overlay — no mutation of the base row.
INSERT INTO can (action_id, narration, model)
VALUES ('11111111-1111-1111-1111-111111111111', 'The oak gives way and falls. Sap beads at the raw wound.', 'claude-haiku-4-5');

-- 3) READ PATH: COALESCE(overlay, base) returns the enriched prose the player saw live, and the
--    base row is provably untouched.
DO $$
DECLARE shown text; base text; mdl text;
BEGIN
    SELECT COALESCE(can.narration, ca.narration), ca.narration, can.model INTO shown, base, mdl
    FROM ca LEFT JOIN can ON can.action_id = ca.id WHERE ca.id = '11111111-1111-1111-1111-111111111111';
    IF shown <> 'The oak gives way and falls. Sap beads at the raw wound.' THEN
        RAISE EXCEPTION 'REGRESSION: read path did not return the overlay prose (got: %)', shown; END IF;
    IF base <> 'The oak gives way and falls.' THEN
        RAISE EXCEPTION 'REGRESSION: the immutable base narration was altered (got: %)', base; END IF;
    IF mdl <> 'claude-haiku-4-5' THEN
        RAISE EXCEPTION 'REGRESSION: model id not recorded for the narration-quality review (got: %)', mdl; END IF;
    RAISE NOTICE 'PASS: overlay stored with model; COALESCEd on read; base prose unchanged';
END $$;

-- 4) An action with NO overlay falls back to the deterministic base prose.
INSERT INTO ca (id, narration) VALUES ('22222222-2222-2222-2222-222222222222', 'You scan the treeline.');
DO $$
DECLARE shown text;
BEGIN
    SELECT COALESCE(can.narration, ca.narration) INTO shown
    FROM ca LEFT JOIN can ON can.action_id = ca.id WHERE ca.id = '22222222-2222-2222-2222-222222222222';
    IF shown <> 'You scan the treeline.' THEN
        RAISE EXCEPTION 'REGRESSION: fallback to deterministic prose failed (got: %)', shown; END IF;
    RAISE NOTICE 'PASS: an action without an overlay falls back to deterministic prose';
END $$;

ROLLBACK;
