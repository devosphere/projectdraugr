-- #108 (epic #100) — a shelter holds the bodies that fit in it.
--
-- THE DEFECT. `construction_kind.shelters_stock` is read in five places and is species-blind, so every stock
-- shelter in the world shelters every animal in the world. Concretely, today:
--
--   * A POULTRY_COOP on the ground is what lets a keeper's AUROCHS conceive.
--   * A BROODER_SHELTER — a warm box for day-old chicks — counts as a birthing house, and delivers a WATER
--     BUFFALO calf with its perinatal loss set to zero.
--   * A FOALING_STALL, built stall-width for a mare, does the same for a hen.
--
-- #108 is explicit that this is the thing to get right: "a ranch, stable or coop is a physical collection of
-- structures, not a menu designation", and "species-appropriate" is the first requirement in the ticket. A flag
-- that means "animals, any of them" is the menu designation it warns against.
--
-- THE RULE. A shelter declares the largest body it can hold, and holds that body and anything smaller. A ceiling
-- rather than a list, because that is what is physically true: a byre roofs a hen perfectly well, and a coop
-- cannot roof an ox however many hens it was built for. The five tiers are wildlife_species.size_tier, so no
-- second vocabulary is invented — the same trap V313's wearable positions fell into.
--
-- WHAT THE RULE OPENS, AND THIS MIGRATION CLOSES. Honest ceilings leave exactly one hole: nothing in the
-- catalogue shelters the BIRTH of a HUGE animal. Aurochs and water buffalo are the two HUGE breeders in the
-- world, they carry 10% perinatal loss, and the three birthing houses top out at a sow and a mare. So the ox
-- shed goes in — which #108 asks for by name, with the wide entrances, bedding and yoke storage it lists, and it
-- is the only structure added here, because a catalogue entry that changes nothing is the defect, not the fix.

ALTER TABLE construction_kind ADD COLUMN shelters_up_to_size VARCHAR(10)
    CHECK (shelters_up_to_size IN ('TINY','SMALL','MEDIUM','LARGE','HUGE'));

COMMENT ON COLUMN construction_kind.shelters_up_to_size IS
  '#108: the largest wildlife_species.size_tier this structure can actually hold. A shelter holds this body and every smaller one. NULL only for structures that shelter no stock; the sized_for_the_stock_it_holds constraint requires it of the ones that do.';

/**
 * Where a body sits among the size tiers, so one can be compared with another in SQL. IMMUTABLE so it may be used
 * in an index or a constraint later; an unknown tier ranks above every shelter, which refuses rather than admits.
 */
CREATE FUNCTION body_size_rank(tier TEXT) RETURNS INT
    LANGUAGE sql IMMUTABLE STRICT AS $$
    SELECT COALESCE(array_position(ARRAY['TINY','SMALL','MEDIUM','LARGE','HUGE'], tier), 99);
$$;

COMMENT ON FUNCTION body_size_rank(TEXT) IS
  '#108: orders wildlife_species.size_tier so a shelter ceiling can be compared with a body. An unrecognised tier ranks above every shelter, so a species nobody sized is refused shelter rather than quietly given the best of it.';

-- The honest ceiling of every structure that shelters stock today.
UPDATE construction_kind SET shelters_up_to_size = CASE project_kind
    WHEN 'BROODER_SHELTER'     THEN 'SMALL'   -- chicks and small fowl under a warmed cover; nothing else fits
    WHEN 'POULTRY_COOP'        THEN 'SMALL'   -- a hen house, with perches and a hatch a fowl uses
    WHEN 'GOAT_FOLD'           THEN 'LARGE'   -- hurdles at sheep and goat height, which a hill sheep clears
    WHEN 'FARROWING_SHELTER'   THEN 'LARGE'   -- a sow and her litter, which is the largest thing it was built to
    WHEN 'FOALING_STALL'       THEN 'LARGE'   -- stall-width for a mare; an ox cannot turn in it
    WHEN 'PIG_STY'             THEN 'LARGE'   -- a low robust enclosure over rooted ground
    ELSE 'HUGE' END                           -- byre, barn, pen, yard, sick shelter, hitching post, tether line
 WHERE shelters_stock OR shelters_birth OR isolates_sick;

-- And the guard, so the next stock shelter anyone adds has to answer the question. Written against all three
-- flags: a birthing house that shelters no stock still has a body standing in it, and an isolation shelter that
-- left the question unanswered would silently stop isolating — body_size_rank refuses rather than admits, which
-- is the right direction for a roof and the wrong one for a quarantine.
ALTER TABLE construction_kind ADD CONSTRAINT sized_for_the_stock_it_holds
    CHECK (NOT (shelters_stock OR shelters_birth OR isolates_sick) OR shelters_up_to_size IS NOT NULL);

-- ---------------------------------------------------------------------------------------------------------------
-- The ox shed (#108): the hole the rule opens, closed in the same migration that opens it.
--
-- #108 asks for "robust dry shelter, pair handling, wide entrances, bedding, troughs, yoke storage". Each of those
-- is a flag it already has a meaning for: it shelters the heaviest stock, it is where they calve, and the yoke
-- storage is `shelters_gear` — the same column that keeps a harness out of the weather (V300). It is deliberately
-- expensive: this is the building a keeper puts up after the byre, not instead of it.
-- ---------------------------------------------------------------------------------------------------------------
INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays,
                               proven_in, flammable, encloses, is_barrier, holds_fire, retained_heat_minutes,
                               shelters_stock, barrier_strength, shelters_birth, isolates_sick, shelters_gear,
                               gives_shade, shelters_up_to_size)
VALUES ('OX_SHED', 'Ox shed', 'construction', TRUE, FALSE, TRUE,
        'V369', TRUE, TRUE, TRUE, FALSE, 0,
        TRUE, 25, TRUE, FALSE, TRUE,
        TRUE, 'HUGE');

INSERT INTO assembly_definition (assembly_key, subject_kind, display_name, portable, construction_kind, domain_key,
                                 keywords, subjects, narration, review_state, reviewed_at)
VALUES ('ox_shed', 'STRUCTURE', 'Ox shed', FALSE, 'OX_SHED', 'construction',
        'build an ox shed,raise an ox shed,build a calving shed,build an ox house,build a draught shed,ox shed,calving shed,ox house',
        'ox shed,calving shed,ox house,draught shed',
        'A heavy shed wide enough to lead a yoked pair through, bedded deep enough to calve in, with the yokes and harness hung dry along the back wall.',
        'VERIFIED', now());

INSERT INTO assembly_stage (stage_key, assembly_key, stage_order, name, prerequisite_stage_key, cure_minutes, tool_class, requires_fire, narration)
VALUES
 ('ox_shed_frame', 'ox_shed', 1, 'Raise the shed frame', NULL, 0, 'CUTTING', FALSE,
  'You set the heaviest posts you have in two wide lines and carry a ridge the length of them. The gap you leave at the end is wide enough to lead a yoked pair through without either beast touching a post.'),
 ('ox_shed_walls', 'ox_shed', 2, 'Wall and roof the shed', 'ox_shed_frame', 0, NULL, FALSE,
  'You close three sides and thatch the whole of it over. Inside, out of the wind, the sound of your own work comes back to you off the roof.'),
 ('ox_shed_fitting', 'ox_shed', 3, 'Bed it and hang the gear', 'ox_shed_walls', 0, NULL, FALSE,
  'You bed the floor deep against the cold of the ground and drive pegs along the back wall for the yokes and the harness. It smells of dry straw and cut wood, and it is ready for what it was built for.');

INSERT INTO assembly_stage_requirement (stage_key, item_key, quantity) VALUES
 ('ox_shed_frame',   'timber_log',        6),
 ('ox_shed_frame',   'fiber_cordage',     4),
 ('ox_shed_walls',   'hazel_rod',         8),
 ('ox_shed_walls',   'thatch_bundle',     6),
 ('ox_shed_fitting', 'dry_grass_bundle',  4),
 ('ox_shed_fitting', 'wooden_peg',        4);
