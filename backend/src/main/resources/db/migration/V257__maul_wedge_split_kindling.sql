-- V257 — story #58 (close the primitive tool gaps): the last two #58 tools that were craftable but did no work.
-- stone_maul and wooden_wedge existed as equippable rows read by nothing. This gives both explicit, correct work:
--   * stone_maul gets a STRIKING tool_profile — it becomes a real percussor, satisfying every STRIKING-gated process
--     and the assembly STRIKING check, exactly like a stone_hammer.
--   * a new split_kindling process is the classic maul-and-wedge job: a STRIKING maul drives a wooden_wedge into a
--     timber_log and rives it into an armful of fuel. The wedge is consumed (a driven wooden wedge splinters and
--     wears), giving it honest work; the maul is the required STRIKING tool.
-- Routing: keyword 'split kindling' is longer than split_planks' bare 'split', and 'kindling' is a subject no other
-- process uses, so the two-axis matcher (PROCESS category via 'split') routes the phrase here. 'split' is not a
-- gather/forage verb, so the kindling gather/forage hard-intents (GATHER_BRANCHES, FORAGE_GROUND) do not fire.
INSERT INTO tool_profile (item_key, tool_class) VALUES ('stone_maul','STRIKING')
ON CONFLICT DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('split_kindling','Split kindling from a log','dry_branch',6,8,'STRIKING',FALSE,FALSE,20,'items','PROCESS',
 'split kindling,split kindling from the log,rive kindling,split the kindling',
 'You set the wooden wedge against the end grain and drive it home with the maul, riving the log along its grain into a clean armful of kindling for the fire.',
 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('split_kindling','timber_log',1),('split_kindling','wooden_wedge',1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('split_kindling','kindling')
ON CONFLICT DO NOTHING;
