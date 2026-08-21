-- V143: garment water resistance (EPIC #171 textiles / #177 finishing — closing the waterproofing dead-read).
--
-- Worn garments were read for INSULATION (warmth) but not for shedding rain, so a waxed hide cloak soaked as fast
-- as bare skin — waterproofing, oiling, and hardening (tar/cuir bouilli) bought nothing. Now each garment carries
-- a water_resistance; ChroniclePhysiologyService sums it across what is worn and slows how fast rain wets the body
-- (capped, so no outfit stays bone-dry in a storm). Hide, fur, leather, bark, and reed shed rain; woven plant
-- cloth and grass soak, so they keep their default of 0 — dressing in rags is no protection from the wet.
ALTER TABLE item_definition ADD COLUMN water_resistance SMALLINT NOT NULL DEFAULT 0;

UPDATE item_definition SET water_resistance = 12 WHERE item_key = 'fur_cloak';                       -- full cloak, greasy fur sheds well
UPDATE item_definition SET water_resistance = 10 WHERE item_key = 'hide_coat';                       -- hide over the torso
UPDATE item_definition SET water_resistance =  8 WHERE item_key IN ('leather_armor', 'scale_armour');-- leather plates over the torso
UPDATE item_definition SET water_resistance =  6 WHERE item_key = 'hide_leggings';                   -- legs
UPDATE item_definition SET water_resistance =  3 WHERE item_key IN ('hide_boots', 'reed_hat', 'bark_hood'); -- feet dry; a hat/hood sheds off the head
UPDATE item_definition SET water_resistance =  2 WHERE item_key IN ('leather_bracer', 'leather_helm_cap', 'chitin_helm', 'fur_lining'); -- small coverage
