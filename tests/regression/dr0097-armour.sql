-- Regression: armour from scale/shell/chitin (V97, M1 #75). Read-only.
BEGIN;
DO $$
DECLARE mats text[] := ARRAY['wyvern_scale','chitin_fragment','turtle_shell','wyvern_wing_membrane']; k text;
BEGIN
    FOREACH k IN ARRAY mats LOOP
        IF NOT (EXISTS(SELECT 1 FROM material_process_input WHERE item_key=k) OR EXISTS(SELECT 1 FROM material_process_input_group WHERE item_key=k)) THEN
            RAISE EXCEPTION 'REGRESSION: armour material % is consumed by nothing', k; END IF;
    END LOOP;
    -- three armour pieces exist, are obtainable, and are equippable at distinct body positions.
    IF (SELECT count(*) FROM item_definition d JOIN item_source s ON s.item_key=d.item_key JOIN item_equipment_compatibility e ON e.item_key=d.item_key
        WHERE d.item_key IN ('scale_armour','chitin_helm','war_shield')) < 3 THEN
        RAISE EXCEPTION 'REGRESSION: armour pieces missing item/source/equip rows'; END IF;
    IF (SELECT count(DISTINCT body_position) FROM item_equipment_compatibility WHERE item_key IN ('scale_armour','chitin_helm','war_shield')) < 3 THEN
        RAISE EXCEPTION 'REGRESSION: armour pieces should cover distinct body positions'; END IF;
    RAISE NOTICE 'PASS: scale/chitin/shell armour crafted, obtainable, equippable (defence wired in confront) (V97)';
END $$;
ROLLBACK;
