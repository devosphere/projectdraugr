-- V236 — story #149 (action-composer vocabulary for primitive material preparation). Of the ten named prep verbs,
-- eight already route (strip -> strip_bark_cordage/STRIP_BARK; split -> split_*; dry -> dry_*; twist -> twist_*;
-- scrape -> flesh_hide/knap_scraper/grind_bone_scraper; knap -> knap_*; sharpen -> REPAIR_ITEM; fire-harden ->
-- fire_harden_*). The two gaps are 'braid' and 'abrade'. 'abrade' is added to the REPAIR_ITEM trigger in Java
-- (abrading an edge sharpens it). 'braid' is added here as a twist_cordage keyword: braiding fibre lays up cordage,
-- so "braid a cord / braid the fibre" resolves to the same cordage-making process as twist/ply (subjects cord/rope/
-- cordage/fibre already present). No new item or process — this only widens recognised phrasing.
UPDATE material_process
SET keywords = keywords || ',braid,braid a cord,braid the fibre,braid cordage'
WHERE process_key = 'twist_cordage'
  AND position('braid' in keywords) = 0;

INSERT INTO process_subject (process_key, subject_term) VALUES ('twist_cordage','braid')
ON CONFLICT DO NOTHING;
