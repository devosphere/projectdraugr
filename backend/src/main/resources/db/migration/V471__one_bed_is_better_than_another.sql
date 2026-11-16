-- V471: one bed is better than another (#919).
--
-- V470 made every catalogued bedding count. It left them all worth exactly the same: a groundsheet, a thick
-- reed pallet and a built sleeping bench each took 2.6 hours off the same night, so a Chronicle who went to
-- the trouble of raising a platform slept no better than one who threw down a bark sheet. The ticket asks
-- for the other half — "more comfortable bedding should restore energy faster than basic bedding".
ALTER TABLE construction_kind
    ADD COLUMN bedding_comfort SMALLINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN construction_kind.bedding_comfort IS
    'How good a night this is to lie on: 1 barely off the wet ground, 2 a thick dry pallet, 3 up off the '
    'ground altogether. Zero for anything that is not bedding, which the CHECK below holds.';

-- 1 — off the wet ground and no more. A bark sheet or a few broad leaves between you and the mud.
UPDATE construction_kind SET bedding_comfort = 1 WHERE project_kind = 'GROUNDSHEET';
-- 2 — a thick dry pallet of grass, reed or straw. Soft, and it holds its own warmth.
UPDATE construction_kind SET bedding_comfort = 2 WHERE project_kind = 'GROUND_BED';
-- 3 — up off the ground altogether, out of the damp and whatever moves through it at night. The three built
-- ones are one tier because they are the same idea built three ways.
UPDATE construction_kind SET bedding_comfort = 3
 WHERE project_kind IN ('RAISED_BED_PLATFORM', 'RAISED_SLEEPING_PLATFORM', 'SLEEPING_BENCH');

-- Somewhere to sleep is somewhere with a comfort, and nothing else has one. Two columns saying related
-- things is how they drift; this is the rule that keeps them from it.
ALTER TABLE construction_kind
    ADD CONSTRAINT bedding_has_a_comfort
    CHECK ((is_bedding AND bedding_comfort BETWEEN 1 AND 3) OR (NOT is_bedding AND bedding_comfort = 0));

DO $$
DECLARE
    tiers INTEGER;
    flat  TEXT;
BEGIN
    SELECT COUNT(DISTINCT bedding_comfort) INTO tiers FROM construction_kind WHERE is_bedding;
    IF tiers < 3 THEN
        RAISE EXCEPTION 'V471: bedding must come in more than one grade, found % distinct', tiers;
    END IF;

    SELECT string_agg(project_kind, ', ') INTO flat
      FROM construction_kind WHERE is_bedding AND bedding_comfort = 0;
    IF flat IS NOT NULL THEN
        RAISE EXCEPTION 'V471: these are bedding with no comfort grade: %', flat;
    END IF;
END $$;
