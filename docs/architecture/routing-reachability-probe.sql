-- Routing reachability probe.
--
-- A SQL replica of the runtime resolution rule (ActivityClassifier.classify +
-- ProcessMatcher.resolve), used to check that every VERIFIED material process is
-- reachable by an ordinary sentence and that none collide. This is NOT the
-- authority -- ProcessMatcher.java is -- but it runs against the live vocabulary
-- and process tables without a JVM, so it catches routing regressions a new
-- migration introduces before they ship.
--
-- Run it against a database migrated to the head:
--   docker exec -i <container> psql -U postgres -d draugr < routing-reachability-probe.sql
--
-- The rule, mirrored (keep in step with V54's foot and ProcessMatcher):
--   1. normalise: space-pad, lowercase, non-alphanumeric -> space.
--   2. classify: sum category_term weights per category over whole-word matches;
--      highest sum wins, activity_category.precedence breaks ties; NULL if none.
--   3. resolve: a VERIFIED process matches when its category equals the classified
--      one (or classification is NULL, dropping that condition), one keyword
--      matches whole-word, and one subject term matches whole-word. Longest
--      keyword wins; ties fall to the lexically first process_key.
--
-- Extend the probes list whenever a migration adds processes: one representative
-- sentence per new process, plus anything that shares a verb with it.

CREATE OR REPLACE FUNCTION norm(t text) RETURNS text LANGUAGE sql IMMUTABLE AS $$
  SELECT ' ' || btrim(regexp_replace(lower(t), '[^a-z0-9]+', ' ', 'g')) || ' '; $$;
CREATE OR REPLACE FUNCTION whole(nrm text, term text) RETURNS boolean LANGUAGE sql IMMUTABLE AS $$
  SELECT nrm LIKE '%' || ' ' || term || ' ' || '%'; $$;
CREATE OR REPLACE FUNCTION classify(t text) RETURNS text LANGUAGE sql STABLE AS $$
  WITH n AS (SELECT norm(t) AS v),
  scored AS (SELECT ct.category_key, SUM(ct.weight) AS s FROM category_term ct, n WHERE whole(n.v, ct.term) GROUP BY ct.category_key)
  SELECT s.category_key FROM scored s JOIN activity_category ac ON ac.category_key=s.category_key ORDER BY s.s DESC, ac.precedence ASC LIMIT 1; $$;
CREATE OR REPLACE FUNCTION resolve(t text) RETURNS text LANGUAGE sql STABLE AS $$
  WITH n AS (SELECT norm(t) AS v), c AS (SELECT classify(t) AS cat),
  cand AS (SELECT mp.process_key, MAX(length(k.kw)) FILTER (WHERE whole((SELECT v FROM n), k.kw)) AS best_kw
    FROM material_process mp, c CROSS JOIN LATERAL unnest(string_to_array(mp.keywords, ',')) AS k(kw)
    WHERE mp.review_state='VERIFIED' AND (c.cat IS NULL OR mp.category_key=c.cat) GROUP BY mp.process_key),
  passed AS (SELECT cand.process_key, cand.best_kw FROM cand WHERE cand.best_kw IS NOT NULL
    AND EXISTS (SELECT 1 FROM process_subject s WHERE s.process_key=cand.process_key AND whole((SELECT v FROM n), s.subject_term)))
  SELECT process_key FROM passed ORDER BY best_kw DESC, process_key ASC LIMIT 1; $$;

CREATE TEMP TABLE probes(txt text, want text);
INSERT INTO probes VALUES
  -- V57 timber preservation
  ('season the oak timber for a year','season_timber'),('char the post before setting it in the ground','char_post'),
  ('render birch tar from the bark','render_birch_tar'),('pitch the timber against rot','pitch_timber'),
  ('coat the timber with pitch','pitch_timber'),('line the coat with fur','line_with_fur'),
  ('tar the cordage','tar_cordage'),('season the planks','season_plank'),
  -- V57 joinery
  ('cut a mortise into the beam','cut_mortise'),('cut a tenon on the end of the beam','cut_tenon'),
  ('notch the log for the corner','notch_log'),('scarf the two beams together','scarf_joint'),
  ('dovetail the corner joint','dovetail_corner'),('carve a batch of wooden pegs','carve_pegs'),
  -- V57 building layers
  ('weave a wattle panel from hazel rods','weave_wattle_panel'),('daub the panel with clay','daub_panel'),
  ('rive roof shakes from the block','rive_shakes'),('lay the floorboards and peg them down','lay_floorboards'),
  ('course the dry stone into a wall','course_dry_stone'),('lay a mortared course of stone','lay_mortared_course'),
  ('chink the wall with moss','chink_with_moss'),('build a plank door','build_door_blank'),
  -- V57 salt and preservation
  ('grind the rock salt fine','grind_salt'),('salt the fish for winter','salt_fish'),
  ('dry the fish on the rack','dry_fish'),('smoke the fish over the fire','smoke_fish'),
  ('salt the meat down','salt_meat'),('dry the meat into strips','dry_meat'),
  ('render tallow from the fat','render_tallow'),('pound the dried meat into pemmican','make_pemmican'),
  -- V57 fish processing
  ('gut the fish','gut_fish'),('fillet the fish','fillet_fish'),('split the fish for drying','split_fish'),
  -- V57 bow production
  ('rive a bow stave from the log','rive_bow_stave'),('tiller the bow limbs','tiller_bow_stave'),
  ('twist a bowstring from sinew','twist_bowstring'),('assemble the bow and string it','assemble_bow'),
  ('shave the arrow shafts straight','shave_arrow_shafts'),('fletch the arrows with feathers','fletch_arrows'),
  ('knap stone arrowheads','knap_arrowheads'),
  -- V57 leather and armour
  ('flesh the deer hide','flesh_hide'),('dehair the hide in lye','dehair_hide'),
  ('harden the leather over heat','harden_leather'),('cut the leather lamellae','cut_lamellae'),
  ('lace the lamellae into armour','assemble_lamellar_armor'),('sew a leather bracer','sew_bracer'),
  -- V57 textiles / tools / containers
  ('spin wool yarn','spin_wool_yarn'),('plait a withy rope','plait_withy_rope'),
  ('twist bark cordage from the bast','strip_bark_cordage'),('haft a stone adze','haft_stone_adze'),
  ('carve a wooden bowl','carve_wooden_bowl'),('sew a leather pouch','sew_leather_pouch'),
  ('weave a fish trap','weave_fish_trap'),('make tallow candles','make_tallow_candle'),
  -- V57 remaining fish byproducts, preservation, tools, containers
  ('smoke the fowl over green wood','smoke_fowl'),('brine the fish in salt water','brine_fish'),
  ('skin the fish carefully','skin_fish'),('cure the fish skin into leather','cure_fish_skin'),
  ('boil fish glue from the skins','boil_fish_glue'),('press fish oil from the trimmings','press_fish_oil'),
  ('carve a bone fish hook','carve_fish_hook'),('weave a burden basket','weave_burden_basket'),
  ('dry the mushrooms on a string','dry_mushrooms'),('preserve the berries in honey','preserve_berries'),
  ('dry the herbs in bunches','dry_herbs'),('make a waterskin from the hide','make_waterskin'),
  ('make rush lights','make_rush_light'),('smoke the meat high in the smoke','smoke_meat'),
  ('carve a wooden spoon','carve_wooden_spoon'),('grind a bone awl','grind_bone_awl'),
  ('grind a bone scraper','grind_bone_scraper'),('make a drop spindle','make_drop_spindle'),
  ('make a quiver from bark','weave_quiver'),('salt the deer hide','salt_hide'),
  ('make rawhide from the hide','make_rawhide'),('dehair the hide in lye','dehair_hide'),
  ('sew a leather helm cap','sew_helm_cap'),('cut boot soles from the rawhide','cut_boot_soles'),
  -- V60 cut-planning (multi-output)
  ('lay out the hide and cut the pieces','lay_out_hide'),
  -- Originals: must not regress
  ('split the log into planks with an axe','split_planks'),('tan the deer hide with oak bark','tan_hide'),
  ('weave a large basket from plant fiber','weave_large_basket'),('gather ash from the hearth','gather_ash'),
  ('form a vessel from the clay','form_vessel'),
  -- V144 copper metallurgy
  ('smelt the copper ore','smelt_copper'),('forge a copper chisel','forge_copper_chisel'),
  -- V145 copper axe
  ('forge a copper axe','forge_copper_axe'),
  -- V146 tin and bronze
  ('smelt the tin ore','smelt_tin'),('alloy the bronze from copper and tin','alloy_bronze'),
  ('forge a bronze spear','forge_bronze_spear'),
  -- V147 bronze axe
  ('forge a bronze axe','forge_bronze_axe'),
  -- V148 bronze recycling
  ('melt down the bronze axe','melt_down_bronze'),
  -- V149 bronze knife
  ('forge a bronze knife','forge_bronze_knife'),
  -- V150 iron
  ('smelt the iron ore','smelt_iron'),('forge an iron axe','forge_iron_axe'),
  -- V151 steel
  ('carburise the iron axe','carburise_iron_axe'),
  -- V152 bloomery furnace
  ('make a bloomery furnace','make_bloomery_furnace'),
  -- V153 bronze pickaxe
  ('forge a bronze pickaxe','forge_bronze_pickaxe'),
  -- V154 lead and sinker
  ('smelt the lead ore','smelt_lead'),('cast a lead sinker','cast_lead_sinker'),
  -- V155 silver and water
  ('smelt the silver ore','smelt_silver'),('cast a silver cup','cast_silver_cup'),
  -- V156 recycle more metal
  ('melt down the copper axe','melt_down_copper'),('melt down the bronze spear','melt_down_bronze'),
  -- V157 bronze fish hook
  ('forge a bronze fish hook','forge_bronze_fish_hook'),
  -- V158 bronze cuirass
  ('forge a bronze cuirass','forge_bronze_cuirass'),
  -- V159 iron cuirass
  ('forge an iron cuirass','forge_iron_cuirass'),
  -- V160 iron reforging
  ('reforge the iron scrap','reforge_iron_scrap'),('work the old iron down','reforge_iron_scrap'),
  -- V161 steel cuirass
  ('carburise the iron cuirass','carburise_iron_cuirass'),
  -- V162 steel striker
  ('carburise a steel striker','carburise_steel_striker'),('case-harden a fire striker','carburise_steel_striker');

\echo === MISSES (expect none) ===
SELECT want AS expected, COALESCE(resolve(txt),'<null>') AS got, txt
FROM probes WHERE resolve(txt) IS DISTINCT FROM want ORDER BY want;

\echo === TOTALS ===
SELECT count(*) FILTER (WHERE resolve(txt) IS NOT DISTINCT FROM want) AS ok,
       count(*) FILTER (WHERE resolve(txt) IS DISTINCT FROM want) AS miss FROM probes;

\echo === PER-CATEGORY VERIFIED PROCESSES (M5 coverage) ===
SELECT ac.category_key,
       (SELECT count(*) FROM material_process mp WHERE mp.category_key=ac.category_key AND mp.review_state='VERIFIED') AS verified
FROM activity_category ac ORDER BY ac.precedence;

-- The gate (M5): any miss is a coverage regression -- a step that no longer resolves,
-- or a false COVERED where it resolves to the WRONG process. Under ON_ERROR_STOP=1 this
-- aborts, so running the probe in the migration-validation workflow fails the build
-- rather than letting the gap surface in play.
DO $$
DECLARE miss int;
BEGIN
    SELECT count(*) INTO miss FROM probes WHERE resolve(txt) IS DISTINCT FROM want;
    IF miss > 0 THEN
        RAISE EXCEPTION 'Coverage regression: % probe step(s) did not resolve to their expected process (unreachable or false COVERED).', miss;
    END IF;
END $$;

DROP FUNCTION resolve(text); DROP FUNCTION classify(text); DROP FUNCTION whole(text,text); DROP FUNCTION norm(text);
