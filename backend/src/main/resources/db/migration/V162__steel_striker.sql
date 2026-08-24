-- V162: the steel striker — unlocking the iron-age fire (EPIC #180 heavy industry / #187 steel; and #49 fire).
--
-- V49 catalogued flint-and-steel as the iron-age way to make fire — a hardened steel edge shaved by flint throws a
-- shower of burning metal, reliable enough (difficulty 26) that it stayed in use for centuries, kinder than any
-- friction method and second only to carrying a live ember. It needs a flint and a steel striker, and the striker
-- had no way to be made: V51 recorded it as "Iron-age kit — no smelting exists yet, so flint_and_steel stays
-- unreachable until it does." Smelting now exists (V150/V151), so this closes the loop: a striker is a small bar of
-- iron case-hardened to steel, exactly as a steel axe or cuirass is, and it is reusable kit that lasts many fires.
--
-- Making the striker turns the whole flint-and-steel method live — the terminal payoff of the metal age for fire.

-- Case-harden a handful of strikers from one bloom. 'carburise' is a PROCESS term (V151) so it self-classifies; the
-- striker/fire subject keeps it distinct from carburise_iron_axe and carburise_iron_cuirass. No tool but the fire
-- and the charcoal bed — the same as the other carburising work.
INSERT INTO material_process (process_key, display_name, output_item_key, output_min, output_max, tool_class, requires_fire, requires_water, duration_minutes, domain_key, category_key, keywords, narration, review_state, reviewed_at) VALUES
('carburise_steel_striker', 'Case-harden steel strikers', 'steel_striker', 2,4, NULL, TRUE, FALSE, 90, 'items', 'PROCESS',
 'carburise a steel striker,case harden a steel striker,case harden a fire striker,steel a fire striker,harden a steel striker,pack a striker in charcoal',
 'You draw the iron into short flat bars, pack them in a bed of charcoal, and hold them at a red heat for hours; the metal takes up carbon and quenches hard, coming up as steel strikers that will shower sparks off a flint for years.', 'VERIFIED', now())
ON CONFLICT (process_key) DO NOTHING;

INSERT INTO material_process_input (process_key, item_key, quantity) VALUES
('carburise_steel_striker', 'iron_bloom', 1), ('carburise_steel_striker', 'charcoal', 2)
ON CONFLICT (process_key, item_key) DO NOTHING;

INSERT INTO process_subject (process_key, subject_term) VALUES
('carburise_steel_striker','steel'), ('carburise_steel_striker','striker'), ('carburise_steel_striker','fire')
ON CONFLICT DO NOTHING;
