-- V205 — weapon profile registry (story #93 enabler). WildlifeEncounterService.confront reads a Chronicle's combat
-- capability from HARDCODED item_key lists (hand_weapon IN (...), blunt IN (...), edge tiers, javelin='javelin',
-- bow='hunting_bow', arrows='hunting_arrow', stones='field_stone', sling='sling'). Every weapon not on those lists
-- is a craftable token that does nothing in a hunt — the #93 defect. This table is the single source of truth for
-- what a carried/equipped item IS as a weapon: its combat role and, for a hand weapon, its edge tier and whether it
-- is envenomed. confront is refactored to read it, so adding a new weapon is pure data — insert a row and it is
-- functional in the hunt. Seeded here to reproduce the current behaviour EXACTLY (the existing weapon integration
-- tests — bronze/copper/steel axe, venom, fire-edge — are the equivalence net).
--
--   combat_role: HAND (melee, +35, edge tier adds bite), BLUNT (club/maul, stuns), JAVELIN (thrown reach),
--                BOW (needs arrows), ARROW (ammunition), SLING (multiplies thrown stones), THROWN_STONE (bare cast)
--   edge_tier:   PLAIN < HARDENED < BRONZE < IRON < STEEL — a keener edge bites a little deeper (only for HAND)
CREATE TABLE weapon_profile (
    item_key    VARCHAR(80) PRIMARY KEY,
    combat_role VARCHAR(20) NOT NULL,
    edge_tier   VARCHAR(20) NOT NULL DEFAULT 'PLAIN',
    envenomed   BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT weapon_profile_role_check CHECK (combat_role IN ('HAND','BLUNT','JAVELIN','BOW','ARROW','SLING','THROWN_STONE')),
    CONSTRAINT weapon_profile_edge_check CHECK (edge_tier IN ('PLAIN','HARDENED','BRONZE','IRON','STEEL'))
);

INSERT INTO weapon_profile (item_key, combat_role, edge_tier, envenomed) VALUES
-- hand weapons (edge tier drives the extra bite; poisoned spear also envenoms)
('stone_axe',            'HAND', 'PLAIN',    FALSE),
('primitive_spear',      'HAND', 'PLAIN',    FALSE),
('poisoned_spear',       'HAND', 'PLAIN',    TRUE),
('fire_hardened_spear',  'HAND', 'HARDENED', FALSE),
('copper_axe',           'HAND', 'HARDENED', FALSE),
('bronze_spear',         'HAND', 'BRONZE',   FALSE),
('bronze_axe',           'HAND', 'BRONZE',   FALSE),
('iron_axe',             'HAND', 'IRON',     FALSE),
('steel_axe',            'HAND', 'STEEL',    FALSE),
-- blunt weapons
('wooden_club',          'BLUNT','PLAIN',    FALSE),
('stone_club',           'BLUNT','PLAIN',    FALSE),
('stone_maul',           'BLUNT','PLAIN',    FALSE),
('stone_hammer',         'BLUNT','PLAIN',    FALSE),
-- reach and projectile
('javelin',              'JAVELIN','PLAIN',  FALSE),
('hunting_bow',          'BOW',    'PLAIN',  FALSE),
('hunting_arrow',        'ARROW',  'PLAIN',  FALSE),
('sling',                'SLING',  'PLAIN',  FALSE),
('field_stone',          'THROWN_STONE','PLAIN', FALSE)
ON CONFLICT (item_key) DO NOTHING;
