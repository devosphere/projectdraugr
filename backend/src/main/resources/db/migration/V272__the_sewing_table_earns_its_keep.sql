-- V272 — the sewing table is for sewing (#95/#96).
--
-- `sewing_table` is a fully dead catalogue entry, dead at both ends at once.
--
-- Unobtainable: its only `item_source` row points at `technique_definition`, and no material_process produces it
-- and no Java intent makes it. The three other benches — woodworking, stoneworking, loom — are all raised through
-- CRAFT_WORKSTATION; the sewing table was left out of that list, so there has never been a way to have one.
--
-- And useless if you had one: fifty-three sewing and leatherwork processes exist — sewing panels, pouches,
-- bracers, helm caps, quivers, sacks and the whole leather-garment run, plus cutting out soles, lamellae and
-- cord — and not one of them declared a station. A 17kg work table that nothing in the world asks for.
--
-- It slipped past the dead-end guard because that guard exempts FURNITURE, which is the right call for chairs and
-- shelves and exactly the wrong one for a workstation.
--
-- station_kind is a bonus, never a gate (see PhysicalItemService: efficiency plus a bounded quality assist), so
-- this asks nothing new of a Chronicle who works leather on their knee. It only means that building a proper
-- table finally repays the effort.

-- Sewing proper: every sew_* recipe.
UPDATE material_process SET station_kind = 'sewing_table'
WHERE process_key LIKE 'sew\_%' AND station_kind IS NULL;

-- The leather garment and armour run, which is the same work under another verb.
UPDATE material_process SET station_kind = 'sewing_table'
WHERE process_key LIKE 'make\_leather\_%' AND station_kind IS NULL;

-- Cutting out is table work too: soles, lamellae and cord are marked and cut flat, not held in the hand.
-- Hardening leather is deliberately excluded — that is a fire-and-water job, not a bench one.
UPDATE material_process SET station_kind = 'sewing_table'
WHERE process_key IN ('cut_boot_soles', 'cut_lamellae', 'cut_leather_cord', 'assemble_lamellar_armor')
  AND station_kind IS NULL;
