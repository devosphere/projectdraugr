-- Regression: bare-hand handwork limits & integrity (M1 #199, EPIC #191). Read-only.
--
-- The #199 proof suite over the whole bare-hand corpus (#192-#198). It pins the acceptance invariants as data:
--   (A) no tool-required action routes through bare hands — every handwork process is tool_class NULL, except the
--       one deliberately tool-gated step (crack_walnut, a walnut's shell needs a STRIKING stone), which must stay
--       gated;
--   (B) material selection is mandatory — no handwork process creates from nothing (>=1 input);
--   (C) no result exceeds the material put in — output total mass <= the heaviest total input mass (anti-duplication);
--   (D) improvised carriers have a finite, positive capacity — none can be overloaded without limit.
-- Persistence/no-remote-access/Auditor-clean are guaranteed for these objects by construction: every handwork
-- output is an ordinary world_object/item_instance under the world_object location-XOR-owner CHECK, the UUID primary
-- key (no duplication), and object_transition immutable history — the same invariants the Persistent State Auditor
-- enforces at launch and on schedule; there is no handwork-specific persistence path to escape them.

BEGIN;

DO $$
DECLARE
    -- The bare-hand handwork processes across #192-#197 (carrying, food handling, clay, cordage, raw-material feeders).
    procs text[] := ARRAY[
        'make_leaf_wrap','fold_bark_cup','fold_bark_container','make_grass_sling','weave_reed_pouch',
        'fold_bark_scoop','tie_reed_sheaf','knot_cordage_loop','weave_grass_mat','tie_forage_bundle',
        'shell_hazelnut','peel_root','wash_root','crack_walnut',
        'shape_clay_bead','press_clay_seal','fire_clay_trinkets','thread_trinket_cord',
        'dry_grass','ready_reed_shaft','ret_bark_strip','flatten_bark','dress_feathers','clean_surface_clay','temper_clay_with_silt',
        'twist_bast_cordage','twist_flexible_root_cordage','twist_milkweed_cordage','twist_root_cordage','make_smoke_wrap'];
    pk text; bad text;
BEGIN
    -- (A) Tool boundary.
    FOREACH pk IN ARRAY procs LOOP
        IF pk = 'crack_walnut' THEN
            IF NOT EXISTS (SELECT 1 FROM material_process WHERE process_key = pk AND tool_class = 'STRIKING') THEN
                RAISE EXCEPTION 'LIMITS: crack_walnut must stay STRIKING-gated (a walnut shell will not open bare-handed)'; END IF;
        ELSE
            IF EXISTS (SELECT 1 FROM material_process WHERE process_key = pk AND tool_class IS NOT NULL) THEN
                RAISE EXCEPTION 'LIMITS: bare-hand process % wrongly requires a tool', pk; END IF;
        END IF;
        -- (B) Material selection: no creation from nothing.
        IF NOT EXISTS (SELECT 1 FROM material_process_input WHERE process_key = pk)
           AND NOT EXISTS (SELECT 1 FROM material_process_input_group WHERE process_key = pk) THEN
            RAISE EXCEPTION 'LIMITS: handwork process % has no inputs (creates from nothing)', pk; END IF;
    END LOOP;

    -- (C) Anti-duplication: output total mass <= heaviest possible total input mass.
    SELECT string_agg(x.process_key, ', ') INTO bad FROM (
        SELECT mp.process_key
        FROM material_process mp
        JOIN item_definition od ON od.item_key = mp.output_item_key
        LEFT JOIN (SELECT process_key, SUM(quantity*d.unit_mass_grams) m
                     FROM material_process_input i JOIN item_definition d ON d.item_key = i.item_key GROUP BY 1) s
               ON s.process_key = mp.process_key
        LEFT JOIN (SELECT process_key, SUM(gm) m FROM (
                     SELECT g.process_key, g.group_name, MAX(g.quantity*d.unit_mass_grams) gm
                       FROM material_process_input_group g JOIN item_definition d ON d.item_key = g.item_key
                      GROUP BY 1,2) t GROUP BY 1) gs
               ON gs.process_key = mp.process_key
        WHERE mp.process_key = ANY(procs)
          AND od.unit_mass_grams * mp.output_max > COALESCE(s.m,0) + COALESCE(gs.m,0)
    ) x;
    IF bad IS NOT NULL THEN
        RAISE EXCEPTION 'LIMITS: handwork result exceeds its material (mass gain) in: %', bad; END IF;

    -- (D) Carriers cannot be overloaded without limit: every CONTAINER produced has a positive, finite capacity.
    SELECT string_agg(mp.output_item_key, ', ') INTO bad
    FROM material_process mp JOIN item_definition od ON od.item_key = mp.output_item_key AND od.category = 'CONTAINER'
    WHERE mp.process_key = ANY(procs)
      AND NOT EXISTS (SELECT 1 FROM container_capacity_default c WHERE c.item_key = mp.output_item_key AND c.max_mass_grams > 0 AND c.max_volume_ml > 0);
    IF bad IS NOT NULL THEN
        RAISE EXCEPTION 'LIMITS: improvised carrier(s) have no finite capacity: %', bad; END IF;

    RAISE NOTICE 'PASS: bare-hand handwork within limits — no tool routes bare-handed (crack_walnut gated), no creation from nothing, no mass gain, carriers finite (#199)';
END $$;

ROLLBACK;
