-- V69: workstations (DR-0022 Layer 4b) — benches and looms, derived from how real primitive craft works.
--
-- A bench or a loom does NOT gate a craft and does NOT decide its grade. Skilled hands with basic tools have
-- always made superior work — ancient joiners cut fine mortises, weavers made fine cloth, masons dressed stone
-- square — with the ground, their body, cord, and pegs for a fixture. So:
--
--   * QUALITY stays majorly the craftsman: skill (mastery) + care (the attempt) + materials. A reachable
--     workstation adds only a MINOR, bounded quality assist (a stable held surface aids precision at the
--     margin), applied in code as a one-step lift to the attempt that the materials ceiling still caps. It
--     never carries the result and never rescues poor work.
--   * EFFICIENCY is the workstation's real payoff: less waste (yield biased up), and — as a follow-up wiring —
--     less time and effort.
--   * NEVER a gate. Bare-handed always works, just rougher-yielding and slower.
--
-- station_kind names the bench/loom whose fixture realistically assists this operation. NULL = hand/ground
-- work a fixture does nothing for (gathering, cordage, basketry, food, fire, tanning). The stations are
-- ordinary buildable structures, reachable via the unified reachability model (Layer 1).

ALTER TABLE material_process ADD COLUMN station_kind VARCHAR(40) NULL;

-- Precision woodwork — a stable held surface assists fitting joints and truing/assembling large pieces.
UPDATE material_process SET station_kind = 'woodworking_bench'
 WHERE process_key IN ('cut_mortise','cut_tenon','dovetail_corner','lap_joint_planks','scarf_joint',
                       'edge_join_boards','assemble_frame','turn_dowels','shape_components');

-- Stonework — a firm bench steadies dressing and squaring stone and pressure-flaking tool stone.
UPDATE material_process SET station_kind = 'stoneworking_bench'
 WHERE process_key IN ('dress_foundation','dress_construction','knap_tool_stone');

-- Textiles — cloth is woven on a loom; the loom is the fixture that makes weaving efficient (a warp-weighted
-- or backstrap loom is itself a simple buildable). Spinning and felting stay hand work.
UPDATE material_process SET station_kind = 'loom'
 WHERE process_key IN ('weave_textile','weave_wool_cloth');

-- The workstations themselves, as buildable/carriable items (sited at a location like other furniture, then
-- reachable there). Skill still decides what they help you make; they only ease and speed the making.
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable) VALUES
 ('woodworking_bench', 'Woodworking bench',  'FURNITURE', 42000, 90000, FALSE, FALSE),
 ('stoneworking_bench','Stoneworking bench', 'FURNITURE', 60000, 90000, FALSE, FALSE),
 ('loom',              'Upright loom',       'FURNITURE', 18000, 70000, FALSE, FALSE)
ON CONFLICT (item_key) DO NOTHING;
