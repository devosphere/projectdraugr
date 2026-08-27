-- V182 — crop depredation: a ripe, unguarded crop is grazed down by the animals that reach it (EPIC #162 / story
-- #166 crop stress; ties to the #127 perimeter fence). A ripe stand standing open on ground that grazing animals
-- range — deer, hare, boar, the birds — is eaten and trampled unless it is kept. Nothing threatened the crop: it
-- ripened and waited, safe, whatever lived around it. This marks a stand grazed when it stands ripe on ground a
-- herbivore reaches with no fence to keep it out, so the reward for fencing the field (or reaping before the animals
-- find it) is a fuller harvest. A wattle or brush fence, or simply no grazers near, keeps the stand whole.
ALTER TABLE crop_stand ADD COLUMN grazed BOOLEAN NOT NULL DEFAULT FALSE;
