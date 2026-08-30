-- V206 — tool profile registry (story #93 enabler, sibling of V205's weapon_profile). PhysicalItemService.
-- soundestToolOfClass picks the tool a process uses (CUTTING / STRIKING / AXE) from HARDCODED item_key lists. So a
-- newly crafted knife, hammer, or axe does nothing for a knapping/butchery/felling process unless its key is added
-- to that Java switch — the same token-gate weapon_profile just removed for combat. This table is the single source
-- of truth for what tool CLASS an item can serve as (an item may serve more than one — a stone hatchet is both
-- CUTTING and AXE — so the key is (item_key, tool_class)). soundestToolOfClass reads it, so adding a tool is pure
-- data. Seeded to reproduce the current switch EXACTLY (the existing felling/butchery/knapping tests are the net).
CREATE TABLE tool_profile (
    item_key   VARCHAR(80) NOT NULL,
    tool_class VARCHAR(20) NOT NULL,
    PRIMARY KEY (item_key, tool_class),
    CONSTRAINT tool_profile_class_check CHECK (tool_class IN ('CUTTING','STRIKING','AXE'))
);

INSERT INTO tool_profile (item_key, tool_class) VALUES
-- CUTTING (soundestToolOfClass 'CUTTING')
('stone_knife','CUTTING'),('stone_hatchet','CUTTING'),('stone_flake','CUTTING'),('stone_adze','CUTTING'),
('stone_chisel','CUTTING'),('flint_burin','CUTTING'),('flint_scraper','CUTTING'),('bone_scraper','CUTTING'),
('bronze_knife','CUTTING'),
-- STRIKING
('stone_hammer','STRIKING'),('primitive_pickaxe','STRIKING'),('field_stone','STRIKING'),
('granite_cobble','STRIKING'),('basalt_cobble','STRIKING'),
-- AXE
('stone_axe','AXE'),('stone_hatchet','AXE'),('copper_axe','AXE'),('bronze_axe','AXE'),
('iron_axe','AXE'),('steel_axe','AXE')
ON CONFLICT (item_key, tool_class) DO NOTHING;
