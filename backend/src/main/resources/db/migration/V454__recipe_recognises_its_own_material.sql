-- V454: a recipe that advertises a phrase must recognise its own material in it (#37, #99).
--
-- ProcessMatcher needs a keyword AND a subject term to both appear in the sentence. That second gate is what
-- tells "split the fish" from "split the log", and it is derived from each process's own inputs and outputs so
-- a recipe cannot drift out of agreement with itself. Thirteen keywords drifted anyway: the process advertises
-- a phrase that contains NONE of its own subject terms, so its own promise cannot pass its own gate, and some
-- other recipe sharing a word answers instead.
--
-- The sharpest case: spin_linen_thread advertises "spin flax", "spin the flax" and "spin line flax", and its
-- only subject term is "linen". Flax is what linen is spun FROM, and the recipe did not know the word. All
-- three phrases went to ret_flax_cordage, so a Chronicle asking to spin flax was answered about cordage.
--
-- Measured over all 2,661 declared material_process keywords before writing this (the shipped
-- ActivityClassifier.classify and ProcessMatcher.resolve, driven from a dump of this catalogue, not a
-- re-implementation): these rows change 12 answers and every one of them moves to the process that declares
-- the keyword. None moves away from its owner and none moves between two strangers.
--
-- A new subject term cannot widen a recipe's reach on its own, which is why this is safe: the keyword gate
-- still has to pass first, so the blast radius is only sentences that already matched this recipe's own
-- keywords and were then turned away at its subject gate.

INSERT INTO process_subject (process_key, subject_term) VALUES
    -- "spin flax" / "spin the flax" / "spin line flax" -> was reaching ret_flax_cordage
    ('spin_linen_thread', 'flax'),
    -- "weave flax" -> was reaching ret_flax_cordage
    ('weave_linen_cloth', 'flax'),
    -- "skin bottle" -> was reaching skin_fish, on the bare word "skin"
    ('make_waterskin', 'bottle'),
    -- "root cordage" -> was reaching twist_cordage; the subjects had "cord", never "cordage"
    ('twist_root_cordage', 'cordage'),
    -- "arrow point" -> was reaching assemble_arrows; the subjects had "points", never the singular
    ('knap_arrowheads', 'point'),
    -- "lay the floor" / "floor board" -> was reaching pack_earth_floor
    ('lay_floorboards', 'floor'),
    -- "point the wall" -> was reaching daub_panel
    ('lay_mortared_course', 'wall'),
    -- "lay the dressed stone" -> was reaching course_dry_stone
    ('lay_ashlar_course', 'dressed stone'),
    -- "armour scale" -> was reaching assemble_lamellar_armor; the subjects had "scales", never the singular
    ('cut_lamellae', 'scale'),
    -- "lace the lamellae" -> was reaching cut_lamellae; the subjects had "lamella", never the plural
    ('assemble_lamellar_armor', 'lamellae')
ON CONFLICT (process_key, subject_term) DO NOTHING;
