-- V277 — five recipes that could not be reached by their own words (#62/#73).
--
-- The matcher is two-axis: a phrase must classify to the SAME category the process declares, and it must carry
-- one of the process's keywords and one of its subject terms. These five declare a category that their own
-- canonical phrases never classify to, so the category axis rejected every sentence that would have named them:
--
--   carve a cage frame        -> CONSTRUCT, recipe declares CRAFT      -> nothing matches
--   shape a stone mortar      -> CONSTRUCT, recipe declares PROCESS    -> matches MIX_MORTAR instead
--   poison the spear          -> HUNT,      recipe declares PROCESS    -> nothing matches
--   lash a left snowshoe      -> CONSTRUCT, recipe declares CRAFT      -> nothing matches
--   lash a right snowshoe     -> CONSTRUCT, recipe declares CRAFT      -> nothing matches
--
-- Two of them are worse than silent: "shape a stone mortar" and "peck a mortar" resolve to MIX_MORTAR, so a
-- Chronicle asking for a stone bowl to grind in is handed builder's mortar. A homonym plus a category mismatch.
--
-- The causes are all in the term weights, and none of them is anyone's mistake:
--   * carve(CRAFT,2) vs frame(CONSTRUCT,2) — a TIE, and the tie-break is alphabetical, so CONSTRUCT wins.
--   * mortar is CONSTRUCT weight 3, which outweighs shape(PROCESS,2) outright.
--   * spear is HUNT weight 2, and poison/coat/venom carry no weight at all.
--   * lash is BOTH CONSTRUCT(2) and CRAFT(2) — another tie, and CONSTRUCT wins it again. That matters beyond
--     these two: 'lash' is the verb #96 chose precisely to dodge the CRAFT_GARMENT intent on 'make a shoe'.
--
-- The category is a routing bucket, not an ontology, so the fix is to declare the bucket the words actually
-- land in rather than to re-weight terms that hundreds of other recipes depend on. Each is checked below to
-- still satisfy the auditor's self-classify gate (a keyword must contain a term of its own category).

-- carve a cage frame: "cage frame" contains frame (CONSTRUCT) — gate satisfied.
UPDATE material_process SET category_key = 'CONSTRUCT' WHERE process_key = 'carve_cage_frame';

-- shape a stone mortar: "stone mortar" contains mortar (CONSTRUCT) — gate satisfied. Its keyword is also longer
-- than MIX_MORTAR's bare "mortar", so with the categories now agreeing the longest-keyword rule hands the stone
-- bowl to the right recipe.
UPDATE material_process SET category_key = 'CONSTRUCT' WHERE process_key = 'shape_stone_mortar';

-- poison the spear: every keyword names the spear (HUNT) — gate satisfied.
UPDATE material_process SET category_key = 'HUNT' WHERE process_key = 'coat_spear';

-- lash a snowshoe: every keyword leads with lash (CONSTRUCT) — gate satisfied.
UPDATE material_process SET category_key = 'CONSTRUCT' WHERE process_key IN ('make_snowshoe_left','make_snowshoe_right');

-- One consequence of aligning the mortar categories: SHAPE_STONE_MORTAR carries a bare "mortar" keyword, and so
-- does MIX_MORTAR. While the categories disagreed the mismatch kept them apart; now that they agree, a phrase
-- like "mix mortar for the wall" matches both on a six-character keyword and the winner is arbitrary. Drop the
-- bare one from the stone bowl — it is the ambiguous half of a homonym and the recipe does not need it. Its
-- remaining keywords are all longer and all distinctive, and every one still contains "mortar" (CONSTRUCT), so
-- the auditor's self-classify gate is still satisfied.
UPDATE material_process
SET keywords = 'stone mortar,shape a mortar,hollow a mortar,peck a mortar,grinding mortar'
WHERE process_key = 'shape_stone_mortar';
