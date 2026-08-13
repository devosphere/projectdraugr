-- Regression: the bow is a functional ranged weapon (M1 #132 / #123 cat.7, code). Read-only.
--
-- The whole archery chain already existed (stave -> tiller -> back-with-sinew -> assemble_bow -> hunting_bow, a
-- bowstring, and fletched/assembled arrows) but the bow was inert in a fight — confront counted only spear/axe.
-- V126's confront now reads a hunting_bow WITH hunting_arrow as the strongest ranged option (+40, opens the AERIAL
-- gate). This pins that the chain the wiring depends on is intact and reachable, so the bow cannot silently lose
-- its makers or its ammo.

BEGIN;

DO $$
BEGIN
    -- The end items the confront hook reads.
    IF NOT EXISTS (SELECT 1 FROM item_definition WHERE item_key='hunting_bow') THEN RAISE EXCEPTION 'ARCHERY: hunting_bow missing'; END IF;
    IF NOT EXISTS (SELECT 1 FROM item_definition WHERE item_key='hunting_arrow') THEN RAISE EXCEPTION 'ARCHERY: hunting_arrow missing'; END IF;
    -- The chain that produces them is intact and VERIFIED.
    IF NOT EXISTS (SELECT 1 FROM material_process WHERE process_key='assemble_bow' AND output_item_key='hunting_bow' AND review_state='VERIFIED') THEN
        RAISE EXCEPTION 'ARCHERY: assemble_bow does not produce a VERIFIED hunting_bow'; END IF;
    IF NOT EXISTS (SELECT 1 FROM material_process WHERE output_item_key='hunting_arrow' AND review_state='VERIFIED') THEN
        RAISE EXCEPTION 'ARCHERY: no VERIFIED maker of hunting_arrow'; END IF;
    IF NOT EXISTS (SELECT 1 FROM material_process WHERE output_item_key='bowstring' AND review_state='VERIFIED') THEN
        RAISE EXCEPTION 'ARCHERY: no VERIFIED maker of bowstring'; END IF;
    IF NOT EXISTS (SELECT 1 FROM item_definition WHERE item_key='bow_stave') THEN RAISE EXCEPTION 'ARCHERY: bow_stave missing'; END IF;
    -- assemble_bow must actually consume a stave and a string (the bow is a real build, not a bare item).
    IF NOT EXISTS (SELECT 1 FROM material_process_input i WHERE i.process_key='assemble_bow' AND i.item_key IN ('bow_stave','tillered_bow_stave','backed_bow_stave'))
       AND NOT EXISTS (SELECT 1 FROM material_process_input_group g WHERE g.process_key='assemble_bow' AND g.item_key IN ('bow_stave','tillered_bow_stave','backed_bow_stave')) THEN
        RAISE EXCEPTION 'ARCHERY: assemble_bow does not consume a bow stave'; END IF;
    IF NOT EXISTS (SELECT 1 FROM material_process_input WHERE process_key='assemble_bow' AND item_key='bowstring')
       AND NOT EXISTS (SELECT 1 FROM material_process_input_group WHERE process_key='assemble_bow' AND item_key='bowstring') THEN
        RAISE EXCEPTION 'ARCHERY: assemble_bow does not consume a bowstring'; END IF;
    RAISE NOTICE 'PASS: the bow/arrow/string/stave chain is intact and VERIFIED — the bow the confront hook reads is buildable (#132)';
END $$;

ROLLBACK;
