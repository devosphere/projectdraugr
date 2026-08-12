-- V117: bare-hand handwork mass-conservation fixes (M1 #199, EPIC #191).
--
-- The #199 anti-duplication invariant (dr0119) proves no bare-hand result outweighs the material put into it. Two
-- feeders failed it: a single 25 g shed-bark curl cannot flatten into a 120 g bark sheet, and two 40 g loose bark
-- strips (80 g) cannot ret into 110 g of soft bast. Raise the input counts so the output is covered by its inputs.
UPDATE material_process_input SET quantity = 5 WHERE process_key = 'flatten_bark'  AND item_key = 'birch_bark_shed';
UPDATE material_process_input SET quantity = 3 WHERE process_key = 'ret_bark_strip' AND item_key = 'loose_bark_strip';
