-- V174: venomous wildlife — an ordinary venomous animal envenoms, not just wounds (real-world simulation; #75/#126).
--
-- V173 gave fauna a `toxic` flag: some animals are poison to EAT. Venom is the other half and a different thing —
-- injected by a bite or sting, it harms the one who loses the fight, not the one who eats the loser. The monster tier
-- already models this: a giant hornet's sting carries VENOM_WOUND (monster_profile), so losing to it adds a spreading
-- illness on top of the wound. But an ordinary venomous animal had no such property. The common adder — a true viper,
-- the one snake here whose bite is genuinely venomous — could strike a Chronicle in a losing confront and leave only
-- an ordinary scratch, no different from a harmless grass snake. That is a catalogue creature: real down to its fangs,
-- but the fangs did nothing. This flags the ordinary venomous species so a strike from one envenoms — a sick, climbing
-- illness beyond the bite — exactly as the hornet's sting does, closing the gap for wildlife that are not monsters.
--
-- Only the common adder is flagged: the grass snake and the constrictor are non-venomous in life, and the giant hornet
-- already carries its venom through monster_profile (flagging it here too would apply the venom twice).

ALTER TABLE wildlife_species ADD COLUMN venomous BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN wildlife_species.venomous IS
    'This animal''s bite or sting is venomous (distinct from toxic, which is about eating it). Losing a confront to a venomous species envenoms the Chronicle — WildlifeEncounterService adds a spreading illness on top of the wound, the ordinary-wildlife counterpart of a monster''s VENOM_WOUND mechanic.';

UPDATE wildlife_species SET venomous = TRUE WHERE species_key = 'common_adder';
