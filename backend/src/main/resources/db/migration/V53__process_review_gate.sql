-- V53: The Auditor's review gate over foundation processes.
--
-- V52 made material processing declarative, which means a process is now data --
-- and data can be wrong in ways code review will not catch. A recipe that yields
-- more mass than it consumes creates matter from nothing. One whose output is
-- also its input loops forever. One built on an unobtainable input is dead on
-- arrival. None of these throw; they just quietly corrupt the world's economy.
--
-- So a process is not canon until it has been reviewed. The Auditor checks every
-- definition deterministically and cheaply; only what it cannot settle is passed
-- to the Architect for refinement, with the specific finding attached. Nothing in
-- NEEDS_REFINEMENT may execute, so a suspect recipe cannot write to a chronicle's
-- records at all -- the integrity problem is stopped before it becomes history,
-- which is the only point at which it is still cheap to fix.

ALTER TABLE material_process
    ADD COLUMN review_state VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN reviewed_at  TIMESTAMPTZ,
    ADD CONSTRAINT material_process_review_check
        CHECK (review_state IN ('DRAFT','VERIFIED','NEEDS_REFINEMENT','REJECTED'));

-- The Auditor's findings, and the queue the Architect works from. Append-only in
-- spirit: a finding is resolved by a later row, not by erasing the earlier one,
-- so the reasoning behind a definition stays legible.
CREATE TABLE process_review (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    process_key   VARCHAR(60)  NOT NULL REFERENCES material_process(process_key),
    finding_kind  VARCHAR(40)  NOT NULL,
    severity      VARCHAR(20)  NOT NULL,
    detail        TEXT         NOT NULL,
    raised_by     VARCHAR(20)  NOT NULL DEFAULT 'AUDITOR',
    raised_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resolved_at   TIMESTAMPTZ,
    resolution    TEXT,
    CONSTRAINT process_review_severity_check CHECK (severity IN ('BLOCKING','ADVISORY')),
    CONSTRAINT process_review_raiser_check CHECK (raised_by IN ('AUDITOR','ARCHITECT','HUMAN'))
);
CREATE INDEX process_review_open_idx ON process_review(process_key) WHERE resolved_at IS NULL;

-- Mass accounting per process, so conservation can be checked in one place rather
-- than recomputed everywhere. Output mass above input mass means the recipe makes
-- matter; well below means it silently destroys it. Some loss is real -- bark
-- liquor leaves the hide, water boils off pitch -- so the tolerance is generous
-- in the losing direction and strict in the gaining one.
CREATE VIEW process_mass_balance AS
SELECT mp.process_key,
       mp.output_item_key,
       (mp.output_max * d.unit_mass_grams) AS max_output_grams,
       COALESCE(fixed.g, 0) + COALESCE(grp.g, 0) AS min_input_grams
FROM material_process mp
JOIN item_definition d ON d.item_key = mp.output_item_key
LEFT JOIN (
    SELECT i.process_key, SUM(i.quantity * dd.unit_mass_grams) AS g
    FROM material_process_input i JOIN item_definition dd ON dd.item_key = i.item_key
    GROUP BY i.process_key) fixed ON fixed.process_key = mp.process_key
LEFT JOIN (
    -- A group contributes its lightest satisfying option: the worst case for conservation.
    SELECT process_key, SUM(min_g) AS g FROM (
        SELECT g.process_key, g.group_name, MIN(g.quantity * dd.unit_mass_grams) AS min_g
        FROM material_process_input_group g JOIN item_definition dd ON dd.item_key = g.item_key
        GROUP BY g.process_key, g.group_name) per_group
    GROUP BY process_key) grp ON grp.process_key = mp.process_key;

-- Some processes legitimately gain mass from something the item model does not
-- track. Leaching pulls alkali into water that is not itself an item; ash is
-- harvested out of a hearth rather than made from carried stock. These are gaps
-- in the model, not errors in the recipe, so they are exempted explicitly and
-- with a reason rather than by loosening the check for everyone.
ALTER TABLE material_process
    ADD COLUMN conservation_exempt BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN exempt_reason TEXT,
    ADD CONSTRAINT material_process_exempt_check
        CHECK (NOT conservation_exempt OR exempt_reason IS NOT NULL);

UPDATE material_process SET conservation_exempt = TRUE,
    exempt_reason = 'Water carries the leached alkali and is not modelled as a carried item, so output mass legitimately exceeds the ash consumed.'
WHERE process_key = 'leach_lye';
UPDATE material_process SET conservation_exempt = TRUE,
    exempt_reason = 'Ash is taken from a spent hearth rather than transformed from carried stock; the fire supplied the mass.'
WHERE process_key = 'gather_ash';

-- Corrections to V52's own numbers, which the review caught. These were real:
-- a log became more plank than log, and two field stones became five kilos of
-- dressed masonry. Fixed forward rather than by relaxing the invariant.
UPDATE item_definition SET unit_mass_grams = 1400, unit_volume_ml = 5200 WHERE item_key = 'timber_plank';
UPDATE item_definition SET unit_mass_grams = 5000, unit_volume_ml = 26000 WHERE item_key = 'timber_log';
UPDATE item_definition SET unit_mass_grams = 1500, unit_volume_ml = 2200 WHERE item_key = 'stone_axe';
UPDATE item_definition SET unit_mass_grams = 1000, unit_volume_ml = 30000 WHERE item_key = 'large_basket';

UPDATE item_definition SET unit_mass_grams = 2600, unit_volume_ml = 9600 WHERE item_key = 'structural_timber';
UPDATE material_process SET output_min = 1, output_max = 2 WHERE process_key = 'shape_components';
UPDATE material_process SET output_max = 4 WHERE process_key = 'split_planks';
UPDATE material_process SET output_min = 1, output_max = 2 WHERE process_key = 'dress_construction';
UPDATE material_process SET output_min = 1, output_max = 1 WHERE process_key = 'ret_nettle';

UPDATE material_process_input SET quantity = 6 WHERE process_key = 'dress_foundation' AND item_key = 'field_stone';
UPDATE material_process_input SET quantity = 5 WHERE process_key = 'dress_construction' AND item_key = 'field_stone';
UPDATE material_process_input SET quantity = 6 WHERE process_key = 'ret_nettle' AND item_key = 'nettle_fiber';
UPDATE material_process_input SET quantity = 8 WHERE process_key = 'weave_large_basket' AND item_key = 'vine';
UPDATE material_process_input SET quantity = 8 WHERE process_key = 'weave_large_basket' AND item_key = 'plant_fiber';

-- Run the deterministic review over everything V52 defined. Anything the checks
-- clear is VERIFIED and may execute; anything they catch is held and queued.
INSERT INTO process_review (process_key, finding_kind, severity, detail)
SELECT process_key, 'MASS_CREATION', 'BLOCKING',
       'Output mass (' || max_output_grams || 'g at maximum yield) exceeds minimum input mass ('
       || min_input_grams || 'g). The recipe would create matter.'
FROM process_mass_balance
WHERE min_input_grams > 0 AND max_output_grams > min_input_grams * 1.05
  AND process_key NOT IN (SELECT process_key FROM material_process WHERE conservation_exempt);

INSERT INTO process_review (process_key, finding_kind, severity, detail)
SELECT mp.process_key, 'NO_INPUTS', 'BLOCKING', 'Process declares no inputs of any kind.'
FROM material_process mp
WHERE NOT EXISTS (SELECT 1 FROM material_process_input i WHERE i.process_key = mp.process_key)
  AND NOT EXISTS (SELECT 1 FROM material_process_input_group g WHERE g.process_key = mp.process_key)
  AND NOT mp.conservation_exempt;

INSERT INTO process_review (process_key, finding_kind, severity, detail)
SELECT mp.process_key, 'SELF_LOOP', 'BLOCKING', 'Output ' || mp.output_item_key || ' is also one of its own inputs.'
FROM material_process mp
WHERE EXISTS (SELECT 1 FROM material_process_input i WHERE i.process_key = mp.process_key AND i.item_key = mp.output_item_key)
   OR EXISTS (SELECT 1 FROM material_process_input_group g WHERE g.process_key = mp.process_key AND g.item_key = mp.output_item_key);

INSERT INTO process_review (process_key, finding_kind, severity, detail)
SELECT DISTINCT x.process_key, 'UNOBTAINABLE_INPUT', 'BLOCKING',
       'Input ' || x.item_key || ' has no acquisition path in the world.'
FROM (SELECT process_key, item_key FROM material_process_input
      UNION SELECT process_key, item_key FROM material_process_input_group) x
WHERE NOT EXISTS (SELECT 1 FROM item_source s WHERE s.item_key = x.item_key);

INSERT INTO process_review (process_key, finding_kind, severity, detail)
SELECT process_key, 'IMPLAUSIBLE_DURATION', 'ADVISORY',
       'Duration of ' || duration_minutes || ' minutes looks wrong for this kind of work.'
FROM material_process WHERE duration_minutes < 5 OR duration_minutes > 600;

-- Fire and water claims must be consistent with what the process actually is.
INSERT INTO process_review (process_key, finding_kind, severity, detail)
SELECT process_key, 'MISSING_HEAT', 'ADVISORY',
       'Firing, rendering, or baking without requires_fire set.'
FROM material_process
WHERE NOT requires_fire AND (keywords ILIKE '%fire the%' OR keywords ILIKE '%bake%' OR keywords ILIKE '%render%' OR keywords ILIKE '%melt%');

-- Everything with no blocking finding is canon; everything else is held.
UPDATE material_process SET review_state = 'VERIFIED', reviewed_at = now()
WHERE NOT EXISTS (
    SELECT 1 FROM process_review r
    WHERE r.process_key = material_process.process_key AND r.severity = 'BLOCKING' AND r.resolved_at IS NULL);

UPDATE material_process SET review_state = 'NEEDS_REFINEMENT', reviewed_at = now()
WHERE EXISTS (
    SELECT 1 FROM process_review r
    WHERE r.process_key = material_process.process_key AND r.severity = 'BLOCKING' AND r.resolved_at IS NULL);

INSERT INTO domain_registry (domain_key, display_name, description, introduced_in_migration, origin)
VALUES ('process_review','Process Review','The Auditor''s standing review of foundation processes, and the refinement queue it hands to the Architect.','V53','PREBUILT')
ON CONFLICT (domain_key) DO NOTHING;
