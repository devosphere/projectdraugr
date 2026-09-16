-- #77 — what the cellar actually keeps.
--
-- THE DEFECT, in two halves, both in one SQL predicate.
--
-- A root cellar and a cool food pit credit food its shelf life back while it sits by them (V255/#218's
-- counterpart). The predicate that finds that food reads:
--
--     JOIN world_object body ON body.id = food.current_owner_id ... body.current_location_id = <the cellar's chunk>
--
-- So the food must be owned by exactly one thing that itself stands on the ground the cellar stands on. That leaves
-- out the two most natural ways anybody has ever used a cellar:
--
--   * FOOD SET DOWN IN IT. Roots laid on the cellar floor are owned by nobody — `current_owner_id` is NULL — so the
--     join finds no row and the credit never applies. A cellar you actually put your harvest in kept it no better
--     than a hillside; only food a Chronicle STOOD THERE HOLDING was kept.
--   * FOOD IN A SACK INSIDE A CHEST. One level of nesting and the owner is the sack, which has no location of its
--     own because the chest owns it. The chain stops and the credit is lost.
--
-- WHAT THIS MIGRATION CHANGES. Only the second, smaller half: the two cold stores were named in Java as a literal
-- list of project kinds, so a third cold store could be built and would keep nothing. `keeps_food_cool` moves that
-- decision into the catalogue where every other structural capability lives. The walk up the ownership chain —
-- through containers, through bodies, to whatever ground the food finally rests on — is in FoodPreservationService.
--
-- WHAT IT DOES NOT CHANGE. Pests. #218 docks shelf life from food held by a KEEPER standing on fouled ground, and
-- that clause is left exactly as it was: making refuse reach into sacks and cellars would make the world harsher,
-- and that is a design decision to take deliberately and on its own, not a side effect of fixing a cellar.

ALTER TABLE construction_kind ADD COLUMN IF NOT EXISTS keeps_food_cool BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN construction_kind.keeps_food_cool IS
  'Cool, still air that holds a larder near-suspended: food resting on this ground keeps its remaining freshness '
  'while it stays. Credits shelf life, never creates it — food cannot un-spoil or gain more than it had.';

UPDATE construction_kind SET keeps_food_cool = TRUE WHERE project_kind IN ('ROOT_CELLAR','COOL_FOOD_PIT');

DO $$
DECLARE n int; bad text;
BEGIN
  SELECT count(*) INTO n FROM construction_kind WHERE keeps_food_cool;
  IF n <> 2 THEN RAISE EXCEPTION 'V334: expected the two cold stores to keep food cool, found %', n; END IF;

  SELECT string_agg(project_kind, ', ') INTO bad FROM construction_kind
   WHERE project_kind IN ('ROOT_CELLAR','COOL_FOOD_PIT') AND NOT keeps_food_cool;
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V334: a cold store that keeps nothing cool: %', bad; END IF;

  -- A place that holds fire is not a place that holds a larder. This is the guard that stops the column being
  -- spread over every structure the day somebody wants their food to keep a little longer.
  SELECT string_agg(project_kind, ', ') INTO bad FROM construction_kind WHERE keeps_food_cool AND holds_fire;
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V334: a hearth does not keep food cool: %', bad; END IF;
END $$;
