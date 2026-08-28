-- V187 — the travois as a load platform (EPIC #100, second draft-logistics slice). V186 gave a tamed draft beast the
-- power to add its haul to the handler's capacity, but that capacity had nowhere to go: a Chronicle still had to hold
-- everything on their own back and in their own pack. The whole point of a travois is that the load rides ON THE FRAME,
-- not on the body — a heap of grain, timber, or hides piled on the drag poles and dragged behind the beast.
--
-- This makes the travois a container. Every travois made now auto-wires container_properties from this default (the
-- process-output path, createCraftedItem, reads container_capacity_default), so a Chronicle can load a great heap onto
-- it. Coherent with V186: a loaded travois is only movable while a draft beast is hitched — the beast's haul (V186)
-- covers the piled load; without a beast the load falls on the Chronicle's own back and they cannot shift it far. The
-- capacity is generous (250 kg / 350 L) to match the aurochs' haul, so beast + frame together move a real cargo.
INSERT INTO container_capacity_default (item_key, max_mass_grams, max_volume_ml) VALUES
('travois', 250000, 350000)
ON CONFLICT (item_key) DO NOTHING;
