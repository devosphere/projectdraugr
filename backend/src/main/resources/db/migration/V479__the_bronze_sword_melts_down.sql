-- V479: THE BRONZE SWORD MELTS DOWN (GitHub #966, under EPIC #964).
--
-- V477 left the bronze short sword out of melt_down_bronze, and said why: that recipe returns a whole 900 g ingot,
-- and one 760 g sword melted into it would create 140 g of bronze from nothing. So a worn-out bronze sword was a
-- dead end -- the one bronze object in the world whose metal could never be recovered.
--
-- The honest answer is the one V478 gave the iron and steel swords (two worn swords feed reforge_iron_scrap): it
-- takes TWO bronze swords to fill the crucible for one ingot. 2 x 760 = 1520 g in, 900 g out. The hilts burn away,
-- and the dross skimmed off a small melt of worn, much-sharpened metal is the rest of the loss. Nothing is created.
--
-- melt_down_bronze's inputs are already an either/or group ('bronze_object', V156/V158), so the sword joins it as
-- one more option, at quantity 2. process_mass_balance takes the CHEAPEST option of a group, and 1520 g is heavier
-- than every existing option, so the recipe's balance is unchanged.
--
-- ROUTING: "melt down the bronze sword", "melt the bronze sword" and "remelt the bronze" are already reached by
-- melt_down_bronze's own keywords (`melt down the bronze`, `melt the bronze`, `remelt the bronze`); these are
-- longer than the shared "bronze sword" family keyword V477 put on cast/edge/hilt, so they win on length. What
-- was missing is that the process did not answer to the sword as its subject.

INSERT INTO material_process_input_group (process_key, group_name, item_key, quantity) VALUES
 ('melt_down_bronze', 'bronze_object', 'bronze_short_sword', 2)
ON CONFLICT DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
 ('melt_down_bronze', 'sword'), ('melt_down_bronze', 'swords')
ON CONFLICT DO NOTHING;
