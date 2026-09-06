-- V277 — recipes that could not be reached by their own words (#62/#73).
--
-- The matcher is two-axis (ProcessMatcher.resolve): a phrase must classify to the SAME category the process
-- declares, and carry one of its keywords and one of its subject terms. ActivityClassifier decides the category
-- by summing category_term weights over whole-word matches and taking the highest score — breaking a TIE by
-- activity_category.precedence, LOWEST first: HUNT 1, ACQUIRE 2, PROCESS 3, CRAFT 4, CONSTRUCT 5, and so on.
--
-- Two recipes declare a category their own canonical phrases never classify to, so the category axis rejected
-- every sentence that would have named them:
--
--   "shape a stone mortar"  -> CONSTRUCT (mortar carries weight 3, outweighing shape's PROCESS 2 outright)
--                              while the recipe declares PROCESS. Worse than silent: it matched MIX_MORTAR, so a
--                              Chronicle asking for a stone bowl to grind in was handed builder's mortar.
--   "poison the spear"      -> HUNT (spear is HUNT weight 2; poison, coat and venom carry no weight at all)
--                              while the recipe declares PROCESS. Nothing matched.
--
-- The category is a routing bucket, not an ontology, so each declares the bucket its words actually land in
-- rather than re-weighting terms that hundreds of other recipes depend on. Both still satisfy the auditor's
-- self-classify gate: "stone mortar" contains mortar (CONSTRUCT), and every coat_spear keyword names the spear.
UPDATE material_process SET category_key = 'CONSTRUCT' WHERE process_key = 'shape_stone_mortar';
UPDATE material_process SET category_key = 'HUNT'      WHERE process_key = 'coat_spear';

-- With the mortar categories now agreeing, SHAPE_STONE_MORTAR's bare "mortar" keyword would tie MIX_MORTAR's on
-- six characters and the winner would be arbitrary. Drop the ambiguous half of the homonym from the stone bowl;
-- its remaining keywords are all longer and all distinctive, and every one still contains "mortar" (CONSTRUCT).
UPDATE material_process
SET keywords = 'stone mortar,shape a mortar,hollow a mortar,peck a mortar,grinding mortar'
WHERE process_key = 'shape_stone_mortar';

-- Four keywords in a related shape, found by asking a different question: not "can this recipe be reached at
-- all" but "does its OWN advertised phrase reach it". These four ARE reachable by their primary phrase, but each
-- advertises a second phrase that lands on a different recipe, which is worse than not advertising it.
--
--   "tiller a long/recurve/short ... bow" -> PROCESS (tiller, scrape) -> TILLER_BOW_STAVE, not the CRAFT assembly
--   "grind a bone knife"                  -> PROCESS (grind)          -> GRIND_BONE_AWL. Ask for a knife, get an awl.
--
-- In each case the classifier is RIGHT and the keyword is wrong: tillering genuinely IS the stave process and
-- grinding genuinely IS the awl's verb. Dropping the misleading phrase leaves each recipe reachable by what it
-- actually is — "assemble a long self bow", "carve a bone knife".
UPDATE material_process SET keywords = 'assemble a long self bow,long self bow,long bow'
WHERE process_key = 'assemble_long_self_bow';

UPDATE material_process SET keywords = 'assemble a recurve wood bow,recurve wood bow,recurve bow'
WHERE process_key = 'assemble_recurve_wood_bow';

UPDATE material_process SET keywords = 'assemble a short self bow,short self bow,short bow'
WHERE process_key = 'assemble_short_self_bow';

UPDATE material_process SET keywords = 'carve a bone knife,bone knife,bone blade'
WHERE process_key = 'carve_bone_knife';
