-- #106 — "How is the goat?"
--
-- Act sixteen swept the husbandry axis: fifty-six sentences a person who keeps animals would actually say.
-- THIRTY-SIX OF FIFTY-SIX REACHED NOTHING, and three of the twenty that answered answered wrongly — including the
-- gravest wrong answer this project has produced.
--
-- TIE UP THE GOAT was resolved as an act against the native community in reach: "You lay hands on Holm, who twists
-- free and shrieks. The rest are on you at once, and you are driven off the isle bruised and bleeding." So was
-- TIE UP THE BUNDLE, DRAG THE LOG, DRAG THE SLEDGE, CAPTURE THE DUCK, SEIZE THE ROPE and SEIZE THE BRANCH.
-- ConductService recognises an offence by its opening words — "tie up the", "drag the", "seize the", "steal the" —
-- and took whatever followed the article without ever reading it. Measured: 11 of 31 sentences about a THING were
-- answered as an act against people, and the other 20 were told "you put that to people, and there are none
-- within reach", which is the same mistake with nobody present to commit it against. The service's own class note
-- reads "Persons are not livestock"; the defect was the exact inverse of it, and it cost standing with a people
-- and a beating. Now the open-object phrases are held to their object, by one people vocabulary (PersonWords) and
-- by the names the world gave this community in native_individual.
--
-- THE GOAT HAD NO NAME. Tending, grooming and feeding each carried its OWN hand-kept roll of species, and the
-- three had drifted until they no longer agreed on what an animal was:
--
--   TEND_ANIMAL    goat, horse, cow, ox, sheep, fowl, reindeer, donkey, buffalo
--   GROOM_ANIMAL   no species at all — only animal/beast/stock/herd/flock
--   FEED_ANIMAL    ox, aurochs, deer, elk, reindeer, cattle, livestock — no goat, no sheep, no horse
--
-- So the beast you could tend you could not groom, and the one you could groom you could not feed. "groom the
-- goat" reached nothing; "feed the goat" was answered by TAME — "it lets you come nearer than last time" — the
-- approach to a WILD animal, offered to a keeper feeding their own. And namesAKeptAnimal, the data-driven escape
-- that exists to prevent exactly this, asked the catalogue for the WHOLE compound key: the species are
-- mountain_goat and bighorn_sheep, so the word a keeper says twice a day named nothing the table would own to.
-- One vocabulary now, and the head noun of each key — goat, sheep, goose, duck, buffalo, fowl, ox — counts.
--
-- AND NOW THE STOCK CAN BE ASKED AFTER. wildlife_bond carries hunger, thirst, fatigue and sickness; the tick moves
-- all four; haulage weighs three of them, breeding is gated on all four, and a thirsty beast gives less milk. Not
-- one sentence could ask. "How is the goat", "is the goat sick" and "check on the animals" reached nothing, and
-- "water the animals" was answered "none of your draft beasts is hungry" — appetite, in reply to thirst.
-- CHECK_STOCK reports the four and names what relieves each, because a keeper told their beast is thirsty and not
-- told that thirst falls on wet ground or at a watering station has been given a fact and no use for it.
--
-- Read-only, and no new gear: every column here was already maintained. This migration is the impact card and
-- the guards.

INSERT INTO activity_impact
 (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire, notes,
  labor_energy, labor_hygiene, capability_domain, duration_minutes)
VALUES
 ('CHECK_STOCK', NULL, 0, FALSE, FALSE,
  'Walking through the stock and looking them over moves nothing and takes nothing from them. The beasts are '
  'hungry, thirsty, tired or sick either way; this is only the keeper finding out which.',
  2, 0, 'ATTENTION', 10)
ON CONFLICT (intent_key) DO NOTHING;

DO $$
DECLARE n INT; bad TEXT;
BEGIN
    SELECT COUNT(*) INTO n FROM activity_impact WHERE intent_key = 'CHECK_STOCK';
    IF n <> 1 THEN RAISE EXCEPTION 'V402: looking over the stock needs exactly one impact card, found %', n; END IF;

    -- Looking at an animal marks nothing. A footprint here would be a claim that noticing a sick goat fouls the
    -- ground it stands on, and that should be argued for rather than arrived at.
    SELECT COUNT(*) INTO n FROM activity_impact
     WHERE intent_key = 'CHECK_STOCK'
       AND (footprint_kind IS NOT NULL OR footprint_amount <> 0 OR drifts OR only_with_fire);
    IF n > 0 THEN RAISE EXCEPTION 'V402: looking over the stock may not leave a mark on the world'; END IF;

    -- The four columns the answer is made of. Each is maintained by the tick and was unaskable.
    SELECT string_agg(c, ', ') INTO bad FROM (
        VALUES ('draft_hunger'), ('draft_thirst'), ('draft_fatigue'), ('sickness')
    ) AS want(c)
    WHERE NOT EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_name = 'wildlife_bond' AND column_name = want.c);
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V402: the stock answer is made of columns that do not exist: %', bad; END IF;

    -- And the names it answers to. A head noun is the last word of a species key, which is what makes
    -- mountain_goat answer to "goat" — so a key whose head noun is also an ITEM's whole name would make a
    -- sentence about a thing into a sentence about a beast, which is the defect this migration exists to undo.
    SELECT string_agg(DISTINCT head, ', ') INTO bad FROM (
        SELECT regexp_replace(species_key, '^.*_', '') AS head
          FROM (SELECT species_key FROM draft_species UNION SELECT species_key FROM tamed_yield) s
         WHERE species_key LIKE '%\_%'
    ) h
    WHERE EXISTS (SELECT 1 FROM item_definition d WHERE replace(d.item_key, '_', ' ') = h.head);
    IF bad IS NOT NULL THEN
        RAISE EXCEPTION 'V402: these species head nouns are also whole item names, so a thing would read as a beast: %', bad;
    END IF;

    -- A people vocabulary is only as good as the people it knows. If a community is settled, its individuals
    -- must be named, or "tie up Holm" is the one offence the open-object guard would now refuse.
    SELECT COUNT(*) INTO n FROM native_community c
     WHERE EXISTS (SELECT 1 FROM native_individual i WHERE i.community_id = c.id)
       AND NOT EXISTS (SELECT 1 FROM native_individual i WHERE i.community_id = c.id AND i.given_name IS NOT NULL);
    IF n > 0 THEN RAISE EXCEPTION 'V402: % settled community/communities have individuals with no given name', n; END IF;

    -- And a name that is ALSO an animal's cannot settle an offence, because this world names its people after
    -- what grows and flies in it — Holm, Sedge, Rushe, Lark, Tern, WREN — and wren is a species. Taking such a
    -- name at face value would make "capture the wren" an assault on a person, which is the defect this whole
    -- migration undoes. The guard is that no community may have EVERY one of its names taken that way, or
    -- nobody in it could be wronged by name at all.
    SELECT string_agg(c.name, ', ') INTO bad FROM native_community c
     WHERE EXISTS (SELECT 1 FROM native_individual i WHERE i.community_id = c.id AND i.given_name IS NOT NULL)
       AND NOT EXISTS (
           SELECT 1 FROM native_individual i
            WHERE i.community_id = c.id AND i.given_name IS NOT NULL
              AND lower(i.given_name) NOT IN (
                  SELECT lower(species_key) FROM wildlife_species
                  UNION SELECT regexp_replace(lower(species_key), '^.*_', '') FROM wildlife_species
                  UNION SELECT replace(lower(item_key), '_', ' ') FROM item_definition));
    IF bad IS NOT NULL THEN
        RAISE EXCEPTION 'V402: every name in these communities is also an animal or an item, so none of their people can be named in an offence: %', bad;
    END IF;
END $$;
