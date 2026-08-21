-- V148: bronze recycling — re-melt a worn bronze tool back into an ingot (EPIC #180 / #185 recycling chains).
--
-- This is bronze's real advantage over stone, and it closes the whole metal loop: a knapped flint that shatters is
-- gone, but a bronze axe worn past use can be melted down and its metal recovered as an ingot to forge anew. No new
-- item — melt_down_bronze consumes a bronze axe over a hot fire and returns a bronze_ingot (the haft and a little
-- slag are lost). Completes the tin → smelt → alloy → forge → USE → recycle loop.

-- 'melt' is decisive PROCESS work (reducing a worked object back to raw metal); distinct whole word from 'smelt'.
INSERT INTO category_term (category_key, term, weight) VALUES
('PROCESS','melt',3)
ON CONFLICT (category_key, term) DO NOTHING;

INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('melt_down_bronze', 'Melt down bronze', 'bronze_ingot', 1,1, NULL, TRUE, FALSE, 45, 'items', 'PROCESS',
 'melt down the bronze axe,melt down bronze,melt the bronze axe,melt down a worn bronze axe,remelt the bronze axe,recover the bronze',
 'You set the worn axe-head in among the coals and force the fire until the bronze softens and runs, pouring off the good metal into an ingot to be made anew — the trick stone could never manage.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('melt_down_bronze', 'bronze_axe', 1)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('melt_down_bronze','bronze'), ('melt_down_bronze','axe')
ON CONFLICT DO NOTHING;
