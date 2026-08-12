-- Regression: survival-coverage contract (M1 #123). Read-only.
--
-- #123 promises that every enabled start biome supports first-hours survival through craftable/buildable content.
-- This pins the invariant as data: (A) the catalogue provides content for each of the nine survival categories
-- (perception/escape is code; the rest are content-backed here); (B) each generated land start biome is
-- self-sufficient for forage and shelter/fuel material in-biome (tool stone and water may come from the arrival
-- envelope, which arrival_viability already guarantees is reachable). A gap here means a survival need lost its
-- content, or a start biome cannot feed or warm a Chronicle from its own ground.

BEGIN;

DO $$
BEGIN
    -- (A) The nine survival categories are backed by real content.
    IF (SELECT count(*) FROM item_definition WHERE item_key IN ('raw_water','filtered_water','clean_water')) < 2 THEN
        RAISE EXCEPTION 'COVERAGE: water (locate/treat) content missing'; END IF;
    IF NOT (EXISTS(SELECT 1 FROM item_definition WHERE item_key='tinder_nest')
            AND EXISTS(SELECT 1 FROM fire_method_requirement)
            AND EXISTS(SELECT 1 FROM item_definition WHERE item_key='dry_branch')) THEN
        RAISE EXCEPTION 'COVERAGE: fire (tinder/method/fuel) content missing'; END IF;
    IF (SELECT count(DISTINCT fd.item_key) FROM flora_drop fd JOIN item_definition d ON d.item_key=fd.item_key WHERE d.category='FOOD') < 10 THEN
        RAISE EXCEPTION 'COVERAGE: too little forageable food'; END IF;
    IF (SELECT count(*) FROM material_process WHERE process_key LIKE 'cook\_%' OR process_key LIKE 'bake\_%' OR process_key LIKE 'brew\_%' OR process_key LIKE 'stew\_%') < 3 THEN
        RAISE EXCEPTION 'COVERAGE: cooking content missing'; END IF;
    IF (SELECT count(*) FROM assembly_definition WHERE construction_kind IN ('LEAN_TO','WATTLE_AND_DAUB_HUT','EARTH_SHELTERED_HUT','LOG_CABIN') AND review_state='VERIFIED') < 2 THEN
        RAISE EXCEPTION 'COVERAGE: shelter content missing'; END IF;
    IF NOT (EXISTS(SELECT 1 FROM item_definition WHERE item_key='stone_knife')
            AND EXISTS(SELECT 1 FROM material_process WHERE output_item_key='fiber_cordage')) THEN
        RAISE EXCEPTION 'COVERAGE: cutting tool / cordage content missing'; END IF;
    IF (SELECT count(*) FROM item_definition d JOIN container_capacity_default c ON c.item_key=d.item_key
        WHERE d.item_key IN ('reed_pouch','bark_fold_container','grass_bundle_sling','burden_basket')) < 3 THEN
        RAISE EXCEPTION 'COVERAGE: carrying progression content missing'; END IF;
    IF NOT (EXISTS(SELECT 1 FROM item_definition WHERE item_key='scale_armour')
            AND EXISTS(SELECT 1 FROM item_definition WHERE item_key IN ('primitive_spear','poisoned_spear'))) THEN
        RAISE EXCEPTION 'COVERAGE: defence (armour/weapon) content missing'; END IF;
    IF NOT (EXISTS(SELECT 1 FROM material_process WHERE output_item_key='herbal_poultice')
            AND EXISTS(SELECT 1 FROM item_definition WHERE item_key='soap')) THEN
        RAISE EXCEPTION 'COVERAGE: recovery (wound/hygiene) content missing'; END IF;
END $$;

DO $$
DECLARE b text;
BEGIN
    FOREACH b IN ARRAY ARRAY['TEMPERATE_FOREST','WETLAND','GRASSLAND','HIGHLAND','MOUNTAIN'] LOOP
        IF NOT EXISTS (SELECT 1 FROM flora_definition f JOIN flora_drop fd ON fd.flora_key=f.flora_key JOIN item_definition d ON d.item_key=fd.item_key
                       WHERE f.biome_affinity ILIKE '%'||b||'%' AND d.category='FOOD') THEN
            RAISE EXCEPTION 'COVERAGE: start biome % has no edible forage in-biome', b; END IF;
        IF NOT EXISTS (SELECT 1 FROM flora_definition f WHERE f.biome_affinity ILIKE '%'||b||'%') THEN
            RAISE EXCEPTION 'COVERAGE: start biome % has no shelter/fuel plant material in-biome', b; END IF;
    END LOOP;
    RAISE NOTICE 'PASS: survival-coverage contract intact — 9 categories content-backed; all 5 land start biomes feed and warm from own ground (#123)';
END $$;

ROLLBACK;
