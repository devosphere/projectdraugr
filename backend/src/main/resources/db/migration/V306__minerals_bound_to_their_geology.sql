-- #160 — a mineral that belongs to a place, not to a whole biome.
--
-- The ticket's coverage focus is one sentence: prove "obsidian, sulphur, kaolin, pumice, iron sand, ochre, and
-- lime cannot be gathered from unrelated ground", and that "no material is activated by a broad biome label
-- alone". Today every one of them can be. `mineral_definition.biome_affinity` is the only gate there is, so
-- obsidian is turned up on any mountain in the world, ochre on any river bank, lime on any highland. A rarity
-- roll decides how often, never where. That is a broad biome label doing exactly the job the ticket says it must
-- not do: the ground's name stands in for the ground's geology.
--
-- WHAT THIS ADDS. A province: the site a mineral is actually found at. A mineral with a province row can be dug
-- only on a chunk carrying that site, on top of its existing biome affinity — both conditions, not either. A
-- mineral without one keeps working exactly as it does now, which is what makes this additive rather than a
-- removal: flint, field stone, clay, sand, gravel, iron and copper are unchanged, and the chains that start from
-- them are untouched.
--
-- WHY BOTH CONDITIONS. The province alone would be enough to find the mineral, and would also quietly widen it:
-- a site can only stand on the ground the generator allows it, but a mineral's affinity is the statement of what
-- rock it belongs to, and dropping it would let a site carry a mineral into country that has no business holding
-- it. Keeping both means a province narrows and never widens. The guard below asserts the two can meet at all —
-- a province whose ground the mineral's affinity excludes would gate the mineral to nowhere, which reads in play
-- exactly like the mineral having been deleted.
--
-- WHY site_biomes IS STORED HERE. The sites themselves are placed by WorldGenesisService.MARKER_SPECIFICATIONS,
-- which is Java: their positions are salted by list index, so they cannot move into data without shifting every
-- existing site in the pinned world. Copying the ground each site stands on into this table is what lets the
-- guard below check the two halves agree, and MineralProvinceInvariantTest checks the copy against the Java list
-- in the other direction. A copy that nothing compares is how this codebase gets its declared-but-ignored bugs;
-- a copy with a check on both ends is a contract.
--
-- NOT GATED, deliberately, and asserted below so a later edit cannot do it quietly: the minerals every first
-- chain starts from. Flint, chert, field stone, surface clay, river sand and gravel, the cobbles, iron and
-- copper. Iron is the one worth naming twice — bog iron IS genuinely found across whole wetlands rather than at
-- a seam, so a province for it would be worse geology, not better, as well as breaking every smelt in play.

CREATE TABLE mineral_province (
    mineral_key  VARCHAR(100) NOT NULL REFERENCES mineral_definition(mineral_key),
    site_kind    VARCHAR(100) NOT NULL,
    site_biomes  VARCHAR(160) NOT NULL,
    notes        TEXT,
    PRIMARY KEY (mineral_key, site_kind)
);

COMMENT ON TABLE mineral_province IS
  'The site a mineral is actually found at. A mineral listed here is diggable only on a chunk carrying that '
  'site AND within its own biome_affinity. A mineral absent from this table is gated by affinity alone, as '
  'before. site_kind and site_biomes must match a RESOURCE entry in WorldGenesisService.MARKER_SPECIFICATIONS.';

INSERT INTO mineral_province (mineral_key, site_kind, site_biomes, notes) VALUES
  -- Volcanic glass and pumice are the ticket's own first example. Both are the leavings of one event, and a
  -- mountain that never erupted has neither, however mountainous it is.
  ('obsidian_shard', 'Obsidian field', 'MOUNTAIN',
   'Volcanic glass is the chilled skin of a flow. It exists where the flow was and nowhere else on the range.'),
  ('pumice_piece', 'Volcanic scree', 'MOUNTAIN',
   'Frothed rock from the same violence, gathered off the scree it fell as.'),
  -- Ochre is weathered iron in a cut bank, which is why it is a river and marsh find rather than a hill one.
  ('ochre_red', 'Ochre earth bank', 'RIVER_BANK,WETLAND',
   'Iron-stained earth exposed where water has cut into the bank. The red is the burnt end of the same seam.'),
  ('ochre_yellow', 'Ochre earth bank', 'RIVER_BANK,WETLAND',
   'The unburnt yellow of that seam, from the same cut bank.'),
  -- Lime. The quarry already exists in the world and has been standing there gating nothing.
  ('limestone_chunk', 'Limestone quarry', 'CAVE_MOUTH,HIGHLAND',
   'Lime comes out of a bed of it. The quarry and the karst mouth are the same rock read from two sides.'),
  -- The metals that occur as narrow veins rather than as country rock. Galena carries silver and lead together,
  -- which is why one exposure answers for both — that is the ore, not a convenience.
  ('tin_ore', 'Tin exposure', 'MOUNTAIN,HIGHLAND',
   'Cassiterite runs in narrow greisen veins. Tin scarcity is the reason bronze was ever worth trading for.'),
  ('silver_ore', 'Silver-lead exposure', 'MOUNTAIN,HIGHLAND',
   'Argentiferous galena: the silver is in the lead ore, and the same exposure gives both.'),
  ('lead_ore', 'Silver-lead exposure', 'MOUNTAIN,HIGHLAND',
   'The other half of that ore, and the commoner half.'),
  ('iron_pyrite', 'Pyrite exposure', 'MOUNTAIN,HIGHLAND',
   'Fire-striking pyrite comes out of specific sulphide exposures, not off any hillside.'),
  -- Stone chosen for what it can be worked into rather than for being stone.
  ('soapstone_piece', 'Soapstone outcrop', 'HIGHLAND,MOUNTAIN',
   'A talc body soft enough to carve a lamp or a mould from. Rare, and obvious once found.'),
  ('precision_tool_stone', 'Precision tool-stone exposure', 'MOUNTAIN,HIGHLAND',
   'The fine, even-grained stone a precise edge needs. Ordinary rock will not hold the work.'),
  ('lens_crystal', 'Crystal pocket', 'CAVE_MOUTH,MOUNTAIN',
   'Clear crystal grows in vughs, which open in caves and high crags. The pocket site already existed for it.'),
  -- And V305's fire clay, which came in a day ago on affinity alone and would otherwise be the newest example
  -- of the thing this migration exists to stop.
  ('fire_clay', 'Refractory clay bed', 'HIGHLAND,RIVER_BANK',
   'Refractory clay is a particular bed, not a quality of clay in general. Most clay slumps; this is the bed that does not.');

DO $$
DECLARE bad text;
BEGIN
  -- A province must be able to meet its mineral's affinity somewhere, or the mineral is gated to nowhere and
  -- reads in play exactly as if it had been deleted. This is the failure mode that matters most here.
  SELECT string_agg(p.mineral_key || ' (' || p.site_kind || ')', ', ') INTO bad
    FROM mineral_province p JOIN mineral_definition m USING (mineral_key)
   WHERE NOT EXISTS (
      SELECT 1 FROM unnest(string_to_array(p.site_biomes, ',')) sb
       WHERE btrim(sb) = ANY (string_to_array(m.biome_affinity, ',')));
  IF bad IS NOT NULL THEN
    RAISE EXCEPTION 'V306: these provinces stand on ground the mineral is not declared for, so the mineral would be findable nowhere: %', bad;
  END IF;

  -- site_kind is an exact marker label, matched by equality at runtime. A wildcard here would silently widen the
  -- gate to whatever else happened to be named similarly.
  SELECT string_agg(DISTINCT site_kind, ', ') INTO bad FROM mineral_province
   WHERE site_kind LIKE '%\%%' ESCAPE '\' OR site_kind LIKE '%\_%' ESCAPE '\' OR btrim(site_kind) <> site_kind;
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V306: a site kind must be an exact marker label: %', bad; END IF;

  -- The commons stay ungated. Gating any of these would strand the chains a chronicle's first week is made of,
  -- and it would be an easy thing to do by accident while adding a rare mineral later.
  SELECT string_agg(mineral_key, ', ') INTO bad FROM mineral_province
   WHERE mineral_key IN ('field_stone','flint_stone','chert_nodule','clay_lump_surface','river_sand','river_gravel',
                         'river_hammerstone','granite_cobble','basalt_cobble','quartzite_cobble','sandstone_piece',
                         'slate_shard','iron_ore','copper_ore','rock_salt','pine_resin','silt_bundle');
  IF bad IS NOT NULL THEN
    RAISE EXCEPTION 'V306: these are the minerals every early chain starts from and must stay findable by biome: %', bad;
  END IF;

  -- Every gated mineral must still be terminally useful, or the gate is only making a token harder to reach.
  --
  -- This guard fired on its first run and was right to: lens_crystal, iron_pyrite and pumice_piece are consumed
  -- by none of the three material_process routes. Two of them are consumed through a fourth I had not counted —
  -- fire_method_requirement, the ignition kit — which is a burning glass and a fire-striking pyrite, and is as
  -- real a consumer as any recipe. It is added below rather than exempted.
  --
  -- Pumice is the exception, and it is exempted by name rather than by widening the rule until it passes.
  -- Its consumer is real but it is a literal in Java: PhysicalItemService sharpening reads
  -- `hasAtLeast(chronicle,"pumice_piece",1)` in a hand-written list of abrasives alongside whetstone and
  -- sandstone. No table can see that, which makes it the same declared-in-code defect this project keeps
  -- finding, one layer further down. Recorded on #160 as its own slice rather than fixed in passing here.
  SELECT string_agg(p.mineral_key, ', ') INTO bad FROM (SELECT DISTINCT mineral_key FROM mineral_province) p
   WHERE p.mineral_key <> 'pumice_piece'
     AND NOT EXISTS (SELECT 1 FROM material_process_input i WHERE i.item_key = p.mineral_key)
     AND NOT EXISTS (SELECT 1 FROM material_process_input_group g WHERE g.item_key = p.mineral_key)
     AND NOT EXISTS (SELECT 1 FROM material_process mp WHERE mp.station_kind = p.mineral_key)
     AND NOT EXISTS (SELECT 1 FROM fire_method_requirement f WHERE f.item_key = p.mineral_key);
  IF bad IS NOT NULL THEN
    RAISE EXCEPTION 'V306: gating a mineral nothing consumes just makes the token harder to reach: %', bad;
  END IF;
END $$;
