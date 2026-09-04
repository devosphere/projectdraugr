-- V260 — story #59: model the greenware stage. Vessels were formed AND fired in a single step, so there was no
-- unfired intermediate — the acceptance criteria ask for one that is fragile and water-sensitive, fired only through
-- a credible hearth progression. This splits the chain into the two things that actually happen:
--
--   tempered_clay --form--> unfired_bowl/cup   (wet work, NO fire)
--   unfired_*     --fire--> fired_bowl/cup     (requires fire)
--
-- Safe to restructure: nothing depended on the one-step output — no test references fired_bowl/fired_cup or the form
-- recipes, and the only process consumer of fired_bowl (crush_ceramic_grog, V259) still gets it through the new
-- firing step.
--
-- Routing notes. 'fire' is NOT a category term, so a firing phrase classifies to NULL, which drops the category
-- condition and lets the longest keyword decide: 'fire the clay bowl' (18) beats form_clay_bowl's 'clay bowl' (9),
-- while 'form a clay bowl' still reaches the forming recipe. Each firing process also carries a 'bake ...' keyword
-- so it self-classifies to its own PROCESS category (the Auditor gate: 'bake' -> PROCESS, and 'fire' classifies to
-- nothing at all). The forming recipes keep 'form'/'make' -> CRAFT.
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable) VALUES
('unfired_bowl', 'Unfired bowl', 'MATERIAL', 1000, 1400, FALSE, FALSE),
('unfired_cup',  'Unfired cup',  'MATERIAL',  400,  500, FALSE, FALSE)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO item_source (item_key, source_kind, detail) VALUES
('unfired_bowl', 'TECHNIQUE', 'formed by hand from tempered clay, still soft'),
('unfired_cup',  'TECHNIQUE', 'formed by hand from tempered clay, still soft')
ON CONFLICT (item_key, source_kind) DO NOTHING;

-- Forming no longer fires. It yields greenware, and drops the 'fired'/'fire a ...' keywords, which now belong to
-- the firing step.
UPDATE material_process SET
    display_name = 'Form a clay bowl',
    output_item_key = 'unfired_bowl',
    requires_fire = FALSE,
    keywords = 'form a clay bowl,shape a clay bowl,make a clay bowl,clay bowl',
    narration = 'You work the tempered clay up into a bowl and set it aside to stiffen. It is still soft earth — it will hold nothing until it has been through a fire.'
WHERE process_key = 'form_clay_bowl';

UPDATE material_process SET
    display_name = 'Form a clay cup',
    output_item_key = 'unfired_cup',
    requires_fire = FALSE,
    keywords = 'form a clay cup,shape a clay cup,make a clay cup,clay cup,clay beaker',
    narration = 'You raise the tempered clay into a small cup and set it aside to stiffen — soft earth yet, and no use for drink until it is fired.'
WHERE process_key = 'form_clay_cup';

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('fire_clay_bowl','Fire a clay bowl','fired_bowl',1,1,NULL,TRUE,FALSE,45,'items','PROCESS',
 'fire the clay bowl,bake the clay bowl,fire the bowl,fire the greenware bowl',
 'You set the stiffened bowl in the fire and hold the heat on it until the clay turns hard and rings when struck — earth become stone, and watertight at last.',
 'VERIFIED', now()),
('fire_clay_cup','Fire a clay cup','fired_cup',1,1,NULL,TRUE,FALSE,40,'items','PROCESS',
 'fire the clay cup,bake the clay cup,fire the cup,fire the greenware cup',
 'You bed the little cup in the embers and bring it up to heat until it hardens through and will hold water without slumping.',
 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('fire_clay_bowl','unfired_bowl',1),
('fire_clay_cup','unfired_cup',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('fire_clay_bowl','bowl'),('fire_clay_bowl','greenware'),
('fire_clay_cup','cup'),('fire_clay_cup','beaker'),('fire_clay_cup','greenware')
ON CONFLICT DO NOTHING;
