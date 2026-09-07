-- What a predator smells on a Chronicle (#123/#127).
--
-- Carrying a fresh kill draws a hungry predator in, and which items count as a fresh kill was a two-key list in
-- the Java: raw_game_meat and raw_fish. The world also yields raw_fowl_meat (from four species) and crayfish_meat,
-- and neither drew anything — so a Chronicle could walk through predator ground with a duck or a goose over their
-- shoulder and be no more interesting than one carrying firewood.
--
-- Declared here rather than fixed in place, because the list was never the code's business. It is a fact about the
-- catalogue, the catalogue is where facts about items live, and this is the same move #93 made for weapons and
-- tools: data declares, code reads, and a test can then ask whether the two still agree.
--
-- Deliberately NOT included:
--   raw_honey  -- a bear will come to honey, but this rule is written about blood and carcass scent, and sweeping
--                honey into it would change a mechanic under cover of fixing a list. Worth its own decision.
--   bird_egg   -- an egg in a pouch is not a carcass on the shoulder.
CREATE TABLE carcass_scent (
    item_key VARCHAR(100) PRIMARY KEY REFERENCES item_definition(item_key)
);

INSERT INTO carcass_scent (item_key)
SELECT k FROM (VALUES ('raw_game_meat'), ('raw_fish'), ('raw_fowl_meat'), ('crayfish_meat')) AS v(k)
 WHERE EXISTS (SELECT 1 FROM item_definition d WHERE d.item_key = v.k)
ON CONFLICT (item_key) DO NOTHING;
