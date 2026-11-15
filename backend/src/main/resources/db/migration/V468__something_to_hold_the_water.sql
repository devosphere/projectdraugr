-- V468: vessels that can actually hold a measure of water (#915, #917, #918).
--
-- V465-V467 are taken by work in flight on other branches; this is the next free number, and Flyway runs
-- out-of-order here by design (see application.yml).
--
-- ONE MEASURE OF WATER IS 500 ML in this world — raw_water, filtered_water and clean_water all are, and
-- every reader of water counts measures: drinking, boiling, filtering, cooking. A vessel that cannot hold
-- one measure can therefore hold no water at all, whatever its catalogue capacity says.
--
-- The folded bark cup was catalogued at 300 ml. Its own source note says it "holds water before there is any
-- pottery to hold it in", and #918 was reported against exactly that cup: collect water at a source, and
-- Storage showed 0.0 L in it while Load listed the water carried loose. Half of that was code — the water was
-- never put IN anything — and half was this: 300 ml cannot take a 500 ml measure, so even once the code put
-- water into vessels, the cup would have stayed empty for ever.
--
-- A folded bark cup holds a cup of water. In this world a cup of water is a measure.
UPDATE container_capacity_default SET max_volume_ml = 500, max_mass_grams = 520
 WHERE item_key = 'folded_bark_cup' AND max_volume_ml < 500;

-- Instances already folded in a live playthrough carry their own capacity row, written when they were made.
UPDATE container_properties cp SET max_volume_ml = 500, max_mass_grams = 520
  FROM item_instance i
 WHERE i.object_id = cp.object_id AND i.item_key = 'folded_bark_cup' AND cp.max_volume_ml < 500;

-- THE LADLE IS A VESSEL (#915). It was catalogued as a TOOL with no container row at all, so a Chronicle who
-- carved one, equipped it, and stood at the marsh was told "nothing you carry will hold it; what you scoop up
-- runs out between your fingers" — while `drink water` in the same place described dipping that same ladle and
-- drinking from it. The world knew the ladle held water when it suited one action and not the other.
INSERT INTO container_capacity_default (item_key, max_mass_grams, max_volume_ml) VALUES
('water_ladle', 520, 500)
ON CONFLICT (item_key) DO NOTHING;

-- ...and ladles already carved get the capacity they should always have had, or #915 stays broken for exactly
-- the player who reported it.
INSERT INTO container_properties (object_id, max_mass_grams, max_volume_ml, allows_nested_containers)
SELECT i.object_id, 520, 500, FALSE
  FROM item_instance i
  LEFT JOIN container_properties cp ON cp.object_id = i.object_id
 WHERE i.item_key = 'water_ladle' AND cp.object_id IS NULL;

-- A BOWL PECKED OUT OF A FIELD STONE (#917).
--
-- The soapstone bowl (V106) wants soapstone, which is a highland and mountain mineral; the wooden bowl wants a
-- processed wooden component. A Chronicle who arrives anywhere else has no bowl at all until one of those
-- supply chains opens, and a bowl is what holds water, food and everything else.
--
-- TOOL CLASS: STRIKING, and deliberately not "either CUTTING or STRIKING" as #917 asked. `material_process`
-- holds ONE tool class and its CHECK constraint (V52) allows one of three values; four readers consult it,
-- including the assembly stages and the cure-time rules. Widening it is a real change and not this one. It is
-- also the truer answer: a field stone is pecked out with a hammerstone, not cut with a blade — carving
-- granite with a flint knife is not a thing a person can do. Say so if you would rather have both.
--
-- CAPACITY is fixed at 700 ml rather than bounded by the stone, because field stones are all one size in the
-- catalogue today; the size-bounded capacity #917 asks for waits on #916, which is what gives a field stone a
-- size at all.
--
-- THREE FIELD STONES, AND MASS IS WHY. A field stone is 750 g and the bowl is 1800 g, so a one-stone recipe
-- would have the Auditor's conservation rule right: it creates matter from nothing. (It caught this on the
-- first CI round and failed 447 tests doing it, which is what that gate is for.) Three is also the truthful
-- number: pecking out a stone bowl wears out the hammerstones as surely as it hollows the blank, and this
-- catalogue has exactly one word for a loose stone. One becomes the bowl; two are spent pounding it.
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable, insulation_value) VALUES
('field_stone_bowl', 'Field stone bowl', 'CONTAINER', 1800, 900, FALSE, TRUE, 0)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO container_capacity_default (item_key, max_mass_grams, max_volume_ml) VALUES
('field_stone_bowl', 750, 700)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('field_stone_bowl', 'TECHNIQUE', 'pecked out of a field stone with a hammerstone')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- Keywords deliberately avoid "carve a bowl", "carve a stone bowl" and "hollow a bowl", which the soapstone
-- bowl already owns word for word. Where a phrase does reach both, V318 settles it by which inputs are in
-- reach — a Chronicle holding field stones and no soapstone gets the bowl they can actually make.
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('peck_field_stone_bowl', 'Peck out a field stone bowl', 'field_stone_bowl', 1, 1, 'STRIKING', FALSE, FALSE, 120, 'items', 'CRAFT',
 'field stone bowl,stone bowl,peck a bowl,peck out a bowl,peck a stone bowl,peck out a stone bowl,pound out a bowl,make a field stone bowl,make a stone bowl',
 'You pick out the heaviest stone for the blank and set it between your feet, pecking at its face with the others, hour after hour. The hammerstones blunt and go to gravel; the hollow deepens into a bowl that will hold water.',
 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('peck_field_stone_bowl', 'field_stone', 3)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('peck_field_stone_bowl', 'bowl'), ('peck_field_stone_bowl', 'stone')
ON CONFLICT DO NOTHING;

DO $$
BEGIN
    IF (SELECT max_volume_ml FROM container_capacity_default WHERE item_key='folded_bark_cup') < 500
    THEN RAISE EXCEPTION 'V468: a bark cup still cannot hold a measure of water'; END IF;
    IF NOT EXISTS (SELECT 1 FROM container_capacity_default WHERE item_key='water_ladle')
    THEN RAISE EXCEPTION 'V468: the ladle is still not a vessel'; END IF;
    IF NOT EXISTS (SELECT 1 FROM material_process WHERE process_key='peck_field_stone_bowl')
    THEN RAISE EXCEPTION 'V468: the field stone bowl has no recipe'; END IF;
    IF NOT EXISTS (SELECT 1 FROM container_capacity_default WHERE item_key='field_stone_bowl')
    THEN RAISE EXCEPTION 'V468: the field stone bowl holds nothing'; END IF;
    -- The conservation rule the Auditor applies, asserted here so a bad number never reaches a CI round.
    IF EXISTS (SELECT 1 FROM process_mass_balance WHERE process_key='peck_field_stone_bowl'
                 AND min_input_grams > 0 AND max_output_grams > min_input_grams * 1.05)
    THEN RAISE EXCEPTION 'V468: pecking a bowl would create matter from nothing'; END IF;
END $$;
