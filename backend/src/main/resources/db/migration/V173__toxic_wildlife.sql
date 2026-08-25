-- V173: toxic wildlife — some animals are not food (EPIC real-world simulation; the creature half of #45/#75).
--
-- Flora already carries is_poisonous, and eating a poisonous plant sickens (a death cap nourishes nothing). Fauna had
-- no such property, so a fire salamander, a toad, or a great crested newt — all genuinely toxic, their skin and flesh
-- laced with samandarin, bufotoxin, tetrodotoxin — could be snared, harvested, cooked, and eaten as ordinary game
-- with no consequence. That is a catalogue creature: attainable but not honestly workable. This marks the toxic
-- species, so a Chronicle who works over the remains recognises the flesh for what it is and takes nothing edible —
-- the animal is real down to the reason you leave it alone.

ALTER TABLE wildlife_species ADD COLUMN toxic BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN wildlife_species.toxic IS
    'This animal''s flesh is toxic to eat (the fauna counterpart of flora_definition.is_poisonous). Harvesting or snaring a toxic species yields no edible meat — WildlifeEncounterService recognises it and takes nothing.';

UPDATE wildlife_species SET toxic = TRUE WHERE species_key IN ('fire_salamander', 'common_toad', 'great_crested_newt');
