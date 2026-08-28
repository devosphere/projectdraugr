-- V191 — the animal pen (EPIC #100 / #108 husbandry infrastructure, #104 keeping). A tamed beast had nowhere to be
-- kept: it followed its handler as an owned thing, and rested only when the handler themselves stopped to rest. A
-- settled people's answer was the pen — a ring of posts and lashed rails that holds the stock, so the beasts rest
-- easy behind the rails while their keeper is about other work. This lays the pen as a buildable field structure, and
-- ties it to draft welfare (V190): a beast whose keeper has a completed pen at hand recovers its draft-fatigue each
-- turn of the world, even as the keeper works — so a pen keeps the draft team fresh and ready, the reward for the
-- labour of building it. A field structure like a fence: it is neither shelter nor workstation, and it decays untended.
INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
('ANIMAL_PEN', 'Animal pen', 'construction', FALSE, FALSE, TRUE, 'PREBUILT')
ON CONFLICT (project_kind) DO NOTHING;
