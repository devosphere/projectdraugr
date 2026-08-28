-- V193 — forage: a kept beast must be fed (EPIC #100 / #104 forage & water logistics). A tamed beast tired and
-- rested and hardened to the work, but it never ate — it could be kept on bare rock forever and pull as well as one
-- on good pasture. An animal must be fed: left hungry it weakens and pulls poorly, just as a spent one does. This
-- gives each bond a draft-hunger. It rises as the world turns, UNLESS the beast is on grassland — there it grazes the
-- pasture and stays fed; on bare or wooded ground its keeper must bring it cut fodder (a dry grass bundle) and feed
-- it. A hungry beast's haul falls the same way a tired one's does (loadState now takes the WORSE of hunger and
-- fatigue, eased by conditioning), so keeping a draft team means finding it pasture or cutting it hay — real forage
-- logistics, the cost of an animal that works.
ALTER TABLE wildlife_bond ADD COLUMN draft_hunger SMALLINT NOT NULL DEFAULT 0 CHECK (draft_hunger BETWEEN 0 AND 100);
