-- V163: the charcoal clamp — a real fuel supply for the metal age (EPIC #180 heavy industry / #182 fuel chains).
--
-- Every smelt and every carburising (V144–V162) eats charcoal, but the only way to get charcoal was to rake a single
-- cold piece from a spent fire (PhysicalItemService.makeCharcoal) — free, and one at a time. That is a fair trickle
-- for a lone cook fire, but it is no basis for industry: a bloomery devours a heavy charge, and a smith who wants to
-- smelt should have to lay in fuel first. A charcoal clamp is how charcoal was actually made — cordwood stacked and
-- packed under earth and turf, lit and then starved of air so it chars slowly instead of burning to ash, yielding
-- roughly a quarter of its weight back as charcoal. This adds that bulk burn WITHOUT removing the raker's trickle:
-- "char the wood into charcoal" chars a stack of branches into a batch; "make/gather charcoal" still lifts one piece
-- from a dead fire (the MAKE_CHARCOAL intent yields to this process by actionMatchesProcess, so neither steals the
-- other).
--
-- 'char' is a PROCESS term (V57), so the burn self-classifies and routes by an ordinary sentence.

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('char_charcoal_clamp', 'Char a charcoal clamp', 'charcoal', 6,8, NULL, TRUE, FALSE, 480, 'items', 'PROCESS',
 'char the wood into charcoal,char wood into charcoal,char branches into charcoal,burn a charcoal clamp,burn a charcoal mound,char a charcoal clamp',
 'You stack the branches close, pack them under earth and turf, and light the pile from within, then choke the air until it no longer flames but only smoulders. A day later you rake out the cooled clamp: not ash, but a batch of light black charcoal, a fraction of the wood''s weight and worth far more to a fire.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('char_charcoal_clamp', 'dry_branch', 3)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('char_charcoal_clamp','charcoal'), ('char_charcoal_clamp','wood'), ('char_charcoal_clamp','clamp')
ON CONFLICT DO NOTHING;
