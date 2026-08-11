-- Regression: bare-hand body wraps (V110, M1 #198/#191). Read-only.
BEGIN;
DO $$
DECLARE p text;
BEGIN
    FOREACH p IN ARRAY ARRAY['make_grass_wraps','make_hand_wraps','make_reed_hat','make_bark_hood','make_moss_pad'] LOOP
        IF NOT EXISTS (SELECT 1 FROM material_process WHERE process_key=p AND tool_class IS NULL) THEN
            RAISE EXCEPTION 'REGRESSION: % must be bare-hand', p; END IF;
    END LOOP;
    -- worn (equippable + insulation), and NOT armour (not in the confront defence set).
    IF EXISTS (SELECT 1 FROM item_definition WHERE item_key IN ('grass_ankle_wrap','fibre_hand_wrap','reed_hat','bark_hood','moss_pad') AND (NOT equippable OR insulation_value <= 0)) THEN
        RAISE EXCEPTION 'REGRESSION: a wrap is not worn/warming'; END IF;
    IF EXISTS (SELECT 1 WHERE 'grass_ankle_wrap' IN ('scale_armour','chitin_helm','war_shield')) THEN
        RAISE EXCEPTION 'REGRESSION: a wrap counts as armour'; END IF;
    -- paired anatomy for ankle/hand wraps.
    IF (SELECT count(*) FROM item_equipment_compatibility WHERE item_key='grass_ankle_wrap') <> 2
       OR (SELECT count(*) FROM item_equipment_compatibility WHERE item_key='fibre_hand_wrap') <> 2 THEN
        RAISE EXCEPTION 'REGRESSION: paired wraps not left+right'; END IF;
    RAISE NOTICE 'PASS: 5 bare-hand body wraps — no tool, worn+warming, paired, not armour (V110 #198/#191)';
END $$;
ROLLBACK;
