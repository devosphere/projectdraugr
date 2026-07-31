-- V49: The many ways a fire actually gets started.
--
-- Until now ignition was one hardcoded path: hearth board plus spindle. In
-- reality fire-making is a family of techniques, each with its own kit, its own
-- difficulty, and its own conditions. Making fire in the wild is genuinely hard
-- — a survival skill most of humanity has lost — and that difficulty is the
-- point. But it is only fair if the player has the whole real menu to reach for,
-- and the world recognises the one they describe.
--
-- Difficulty is the base chance the attempt fails on technique alone, before
-- the three-layer model applies. A bow drill is far kinder than a hand drill;
-- flint on pyrite is kinder still; carrying an ember from a fire that already
-- burns is easiest of all, and is how most of prehistory actually kept fire.

CREATE TABLE fire_method (
    method_key        VARCHAR(40)  PRIMARY KEY,
    display_name      VARCHAR(80)  NOT NULL,
    difficulty        SMALLINT     NOT NULL CHECK (difficulty BETWEEN 0 AND 100),
    requires_daylight BOOLEAN      NOT NULL DEFAULT FALSE,
    requires_dry      BOOLEAN      NOT NULL DEFAULT TRUE,
    era               VARCHAR(20)  NOT NULL,
    principle         TEXT         NOT NULL
);

CREATE TABLE fire_method_requirement (
    method_key VARCHAR(40)  NOT NULL REFERENCES fire_method(method_key),
    item_key   VARCHAR(100) NOT NULL REFERENCES item_definition(item_key),
    quantity   SMALLINT     NOT NULL DEFAULT 1,
    consumed   BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (method_key, item_key)
);

-- Kit the existing world did not yet have. birch_polypore (V39) is already
-- amadou — the true tinder fungus — so it needs no new definition, only a use.
INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable) VALUES
('fire_bow',      'Fire bow',         'TOOL',     280,  900, FALSE, FALSE),
('fire_socket',   'Bearing block',    'TOOL',     180,  260, FALSE, FALSE),
('plough_board',  'Fire plough board','TOOL',     420,  950, FALSE, FALSE),
('fire_saw_set',  'Fire saw set',     'TOOL',     260,  620, FALSE, FALSE),
('flint_stone',   'Flint',            'MATERIAL', 190,   90, TRUE,  FALSE),
('iron_pyrite',   'Iron pyrite',      'MATERIAL', 210,   80, TRUE,  FALSE),
('steel_striker', 'Steel striker',    'TOOL',     140,   70, FALSE, FALSE),
('lens_crystal',  'Clear crystal',    'MATERIAL', 160,   70, FALSE, FALSE),
('char_tinder',   'Charred tinder',   'MATERIAL',  15,   60, TRUE,  FALSE),
('ember_bundle',  'Ember bundle',     'MATERIAL', 120,  400, FALSE, FALSE)
ON CONFLICT (item_key) DO NOTHING;

INSERT INTO fire_method (method_key, display_name, difficulty, requires_daylight, requires_dry, era, principle) VALUES
('hand_drill',       'Hand drill',        88, FALSE, TRUE,  'PALEOLITHIC', 'A spindle spun between bare palms. The oldest method and the most punishing — it needs speed, downward force, and hands that will blister before it smokes.'),
('bow_drill',        'Bow drill',         55, FALSE, TRUE,  'PALEOLITHIC', 'A bow drives the spindle far faster than palms can, and a bearing block lets the whole weight of the shoulder press down. The workhorse of friction fire.'),
('fire_plough',      'Fire plough',       76, FALSE, TRUE,  'PALEOLITHIC', 'A hardwood point ploughed hard along a grooved baseboard, piling its own dust ahead of it until the dust catches.'),
('fire_saw',         'Fire saw',          72, FALSE, TRUE,  'PALEOLITHIC', 'Split wood sawn crosswise against a fixed edge; the dust falls into its own notch and smoulders there.'),
('flint_and_pyrite', 'Flint and pyrite',  46, FALSE, TRUE,  'NEOLITHIC',   'Flint struck against iron pyrite throws real sparks. This is what people actually carried before steel, and it works cold and wet-handed where friction will not.'),
('flint_and_steel',  'Flint and steel',   26, FALSE, TRUE,  'IRON_AGE',    'A hardened steel edge shaved by flint throws a shower of burning metal. Reliable enough that it stayed in use for centuries.'),
('solar_lens',       'Burning lens',      30, TRUE,  TRUE,  'NEOLITHIC',   'Sunlight focused to a point. Effortless when the sun is out and useless the moment it is not.'),
('fire_piston',      'Fire piston',       42, FALSE, TRUE,  'NEOLITHIC',   'Air driven down a sealed cylinder fast enough that the compression alone lights the tinder.'),
('ember_transfer',   'Carried ember',     14, FALSE, FALSE, 'PALEOLITHIC', 'An ember carried from a fire that already burns, nursed in a bundle. This is how fire was actually kept across most of prehistory: not relit each time, but never allowed to die.')
ON CONFLICT (method_key) DO NOTHING;

INSERT INTO fire_method_requirement (method_key, item_key, quantity, consumed) VALUES
('hand_drill',       'hearth_board',  1, FALSE), ('hand_drill',       'fire_spindle', 1, FALSE),
('bow_drill',        'hearth_board',  1, FALSE), ('bow_drill',        'fire_spindle', 1, FALSE),
('bow_drill',        'fire_bow',      1, FALSE), ('bow_drill',        'fire_socket',  1, FALSE),
('fire_plough',      'plough_board',  1, FALSE),
('fire_saw',         'fire_saw_set',  1, FALSE),
('flint_and_pyrite', 'flint_stone',   1, FALSE), ('flint_and_pyrite', 'iron_pyrite',  1, FALSE),
('flint_and_steel',  'flint_stone',   1, FALSE), ('flint_and_steel',  'steel_striker',1, FALSE),
('solar_lens',       'lens_crystal',  1, FALSE),
('ember_transfer',   'ember_bundle',  1, TRUE)
ON CONFLICT DO NOTHING;

-- fire_piston is catalogued but has no craftable kit yet, so it carries no
-- requirement rows and stays unreachable in play. It is recorded rather than
-- omitted so the Architect knows the method has been reckoned with, and
-- whoever wires its kit later has the entry waiting.

-- Techniques, catalogued alongside the rest so the Architect never re-derives them.
INSERT INTO technique_definition (technique_key, display_name, domain_key, difficulty, produces_item, requires_tool, proven_in, principle)
SELECT 'ignite_' || method_key, display_name || ' fire-lighting', 'fire',
       CASE WHEN difficulty >= 70 THEN 'ADVANCED' WHEN difficulty >= 40 THEN 'INTERMEDIATE' ELSE 'PRIMITIVE' END,
       NULL, NULL, 'V49', principle
FROM fire_method
ON CONFLICT (technique_key) DO NOTHING;
