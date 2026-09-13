-- #161 — what makes each thing: the dependency axis of the catalogue matrix.
--
-- V51 made item_source the contract for how an item enters the world, and the Auditor holds "no item without a
-- source or a recorded reason". That catches an item with NO claim. It cannot catch a claim nobody honours: V51
-- derived a TECHNIQUE source for every technique_definition.produces_item, so a Phase-0 technique naming a thing is
-- enough to make it look obtainable whether or not anything in the world makes it.
--
-- Measured against a fully migrated database — process outputs and by-products, assemblies (by the item or the
-- construction they raise), declared code makers, and recorded gaps — the claims that nothing honours are exactly
-- these, and each is settled here by saying what is true:
--
--  * utility_belt  — made in code (PhysicalItemService.craftUtilityBelt, #35) and never declared so.
--  * sewing_table  — made in code by the workstation craft since V272, and never declared so.
--  * tool_shed     — raised in code (ConstructionService.buildToolShed) as the TOOL_SHED construction.
--  * woodworking_table, stoneworking_table, weaving_table — nothing makes them. The workstation craft makes a
--    woodworking bench, a stoneworking bench and a loom, and stationStructureFor maps each onto these construction
--    kinds; the table ITEMS are Phase-0 names for the same things. Recorded as superseded, as V51 recorded timber_log.
--  * whetstone     — nothing makes it; dress_whetstone makes stone_whetstone, and honing accepts either.
--
-- And the converse, a recorded gap that is no longer a gap: V51 wrote steel_striker off as "no smelting exists yet".
-- V162 has carburised it from an iron bloom ever since, and it was never given a source. It gets one, and the stale
-- reason goes.

INSERT INTO item_source (item_key, source_kind, detail) VALUES
  ('utility_belt',  'CODE',      'PhysicalItemService.craftUtilityBelt'),
  ('sewing_table',  'CODE',      'CRAFT_WORKSTATION'),
  ('tool_shed',     'CODE',      'ConstructionService.buildToolShed (the TOOL_SHED construction)'),
  ('steel_striker', 'TECHNIQUE', 'carburise_steel_striker')
ON CONFLICT (item_key, source_kind) DO NOTHING;

DELETE FROM item_unreachable_known WHERE item_key = 'steel_striker';

INSERT INTO item_unreachable_known (item_key, reason) VALUES
  ('woodworking_table',  'Phase 0 name. The workstation craft makes woodworking_bench, which stationStructureFor counts as the WOODWORKING_TABLE station.'),
  ('stoneworking_table', 'Phase 0 name. The workstation craft makes stoneworking_bench, which stationStructureFor counts as the STONEWORKING_TABLE station.'),
  ('weaving_table',      'Phase 0 name. The workstation craft makes loom, which stationStructureFor counts as the WEAVING_TABLE station.'),
  ('whetstone',          'Superseded by stone_whetstone (dress_whetstone); honing accepts either.')
ON CONFLICT (item_key) DO NOTHING;

DO $$
DECLARE bad text;
BEGIN
  -- Every TECHNIQUE claim has something that honours it, or a recorded reason it does not.
  SELECT string_agg(s.item_key, ', ' ORDER BY s.item_key) INTO bad FROM item_source s
   WHERE s.source_kind = 'TECHNIQUE'
     AND NOT EXISTS (SELECT 1 FROM material_process mp WHERE mp.output_item_key = s.item_key)
     AND NOT EXISTS (SELECT 1 FROM material_process_output o WHERE o.item_key = s.item_key)
     AND NOT EXISTS (SELECT 1 FROM assembly_definition a WHERE a.produces_item_key = s.item_key OR lower(a.construction_kind) = s.item_key)
     AND NOT EXISTS (SELECT 1 FROM item_source c WHERE c.item_key = s.item_key AND c.source_kind = 'CODE')
     AND NOT EXISTS (SELECT 1 FROM item_unreachable_known u WHERE u.item_key = s.item_key);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V319: technique claims nothing honours: %', bad; END IF;

  -- A recorded gap must not name something the world already makes.
  SELECT string_agg(u.item_key, ', ') INTO bad FROM item_unreachable_known u
   WHERE EXISTS (SELECT 1 FROM material_process mp WHERE mp.output_item_key = u.item_key AND mp.review_state = 'VERIFIED')
      OR EXISTS (SELECT 1 FROM material_process_output o WHERE o.item_key = u.item_key)
      OR EXISTS (SELECT 1 FROM assembly_definition a WHERE a.produces_item_key = u.item_key);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V319: recorded as unobtainable but made: %', bad; END IF;
END $$;
