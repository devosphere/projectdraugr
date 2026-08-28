-- V190 — draft welfare: a working beast tires (EPIC #100 / #101 welfare). The draft vehicles (V186–V189) let a tamed
-- beast haul far beyond a Chronicle's back, but the beast paid nothing for it — an aurochs could drag a full cart up
-- and down the world forever, its haul undiminished. A working animal tires: it can pull hard for a while, then it
-- flags, and it must be let rest before it will pull full again. This gives each bond a draft-fatigue level. Working
-- the beast (moving or travelling with a vehicle hitched) tires it; resting or sleeping lets it recover. Its haul in
-- PhysicalItemService.loadState is scaled by how fresh it is, so an exhausted beast adds nothing until it is rested —
-- the honest limit that makes a draft animal a resource to husband, not a free engine.
ALTER TABLE wildlife_bond ADD COLUMN draft_fatigue SMALLINT NOT NULL DEFAULT 0 CHECK (draft_fatigue BETWEEN 0 AND 100);
