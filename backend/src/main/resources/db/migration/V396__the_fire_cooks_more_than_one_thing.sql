-- #37 — "Cook the fish."
--
-- Two defects that meet on the same sentence, and the plainest survival act there is sits between them.
--
-- ONE. The fire cooked exactly one food. FireService.cookGameMeat was written against two string literals —
-- raw_game_meat in, cooked_game_meat out — so of the raw foods the catalogue holds (raw game meat, raw fish,
-- raw fowl meat, a gutted fish) only game meat could ever be cooked. Every one of them can be smoked, dried,
-- salted, brined, split and cured by a named process; only one could be put over a fire and eaten. There was
-- no cooked fish and no cooked fowl in the catalogue at all.
--
-- TWO. Asking to cook a fish did not reach the fire anyway. The FISH intent was gated on the NOUN — any
-- sentence containing "fish" that was not a process meant "go fishing" — so a Chronicle standing over the fish
-- they had just landed and asking to cook it was sent back to the water. So was "eat the fish".
--
-- This migration is the catalogue half: the two missing cooked foods, and a TABLE for what the fire turns into
-- what, so the next raw food is a row rather than another pair of string literals.
--
-- Each pair LOSES mass, because cooking drives off water. Nothing here may create matter, and a cooked output
-- heavier than its raw input would be exactly that.
--
-- fish_fillet and fish_side are deliberately absent: they are preservation cuts, made to be dried and salted,
-- and each weighs 150g against a cooked fish's 320. Pairing them here would have the fire make more fish than
-- it was given. Drying and salting already take them, which is what they are for.

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable)
VALUES
 ('cooked_fish', 'Cooked fish', 'FOOD', 320, 330, TRUE, FALSE),
 ('cooked_fowl_meat', 'Cooked fowl', 'FOOD', 270, 280, TRUE, FALSE)
ON CONFLICT (item_key) DO NOTHING;

CREATE TABLE IF NOT EXISTS fire_cooking (
    raw_item_key    VARCHAR(100) PRIMARY KEY REFERENCES item_definition(item_key),
    cooked_item_key VARCHAR(100) NOT NULL     REFERENCES item_definition(item_key),
    -- What a person CALLS this thing when they ask for it cooked. Kept here rather than derived from the key,
    -- because nobody says "cook the raw fowl meat": they say fowl, or bird. A first cut of the service derived
    -- the spoken form from the key and so matched nothing, and "cook the fowl" silently cooked game meat.
    keywords        TEXT NOT NULL,
    notes           TEXT NOT NULL
);

COMMENT ON TABLE fire_cooking IS
 'What the fire turns into what (#37). Read by FireService: a raw food with a row here can be cooked over a '
 'live fire, and one without cannot. Before this the pairing was two string literals and the fire cooked game '
 'meat alone. Every pair must lose mass, which the guard below enforces.';

INSERT INTO fire_cooking (raw_item_key, cooked_item_key, keywords, notes) VALUES
 ('raw_game_meat', 'cooked_game_meat', 'meat,game,venison',
  'The pair the fire already knew, now a row like the others.'),
 ('gutted_fish', 'cooked_fish', 'gutted fish,dressed fish,fish',
  'Dressed first, which is how anyone who has caught one actually cooks it — and taken before the whole fish, '
  'so a Chronicle who has gutted their catch cooks that rather than starting on another.'),
 ('raw_fish', 'cooked_fish', 'fish,whole fish',
  'A whole fish laid on the stones, or a stick through it and held over the heat.'),
 ('raw_fowl_meat', 'cooked_fowl_meat', 'fowl,bird,poultry',
  'A bird off the snare, plucked and set over the fire.')
ON CONFLICT (raw_item_key) DO NOTHING;

DO $$
DECLARE n INT; bad TEXT;
BEGIN
    SELECT COUNT(*) INTO n FROM fire_cooking;
    IF n < 4 THEN RAISE EXCEPTION 'V396: the fire must know at least the four pairs, found %', n; END IF;

    -- Both sides of every pair must be food, or the fire turns a thing into something nobody can eat.
    SELECT string_agg(k, ', ') INTO bad FROM (
        SELECT raw_item_key AS k FROM fire_cooking
        UNION SELECT cooked_item_key FROM fire_cooking
    ) s WHERE NOT EXISTS (SELECT 1 FROM item_definition d WHERE d.item_key=s.k AND d.category='FOOD');
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V396: not FOOD on one side of a cooking pair: %', bad; END IF;

    -- Cooking drives off water. A cooked output that outweighs its raw input is matter from nothing, which is
    -- the one thing this catalogue may never do — the Auditor fails some three hundred tests over it.
    SELECT string_agg(fc.raw_item_key || ' -> ' || fc.cooked_item_key, ', ') INTO bad
      FROM fire_cooking fc
      JOIN item_definition r ON r.item_key=fc.raw_item_key
      JOIN item_definition c ON c.item_key=fc.cooked_item_key
     WHERE c.unit_mass_grams >= r.unit_mass_grams;
    IF bad IS NOT NULL THEN
        RAISE EXCEPTION 'V396: cooking must lose mass, not make it: %', bad;
    END IF;

    -- Every pair must carry the words a person would use, or it can only ever be cooked by accident — whichever
    -- raw food happened to be first in the pack.
    SELECT string_agg(raw_item_key, ', ') INTO bad FROM fire_cooking WHERE trim(keywords) = '';
    IF bad IS NOT NULL THEN RAISE EXCEPTION 'V396: no spoken words for: %', bad; END IF;

    -- And every raw food the catalogue holds should either have a row here or a stated reason not to. This
    -- names the reason rather than leaving the gap to be rediscovered: the two fish cuts are for drying and
    -- salting, and raw water and raw honey are not cooked at all.
    SELECT string_agg(d.item_key, ', ') INTO bad FROM item_definition d
     WHERE d.category='FOOD' AND d.item_key LIKE 'raw_%'
       AND d.item_key NOT IN ('raw_water', 'raw_honey')
       AND NOT EXISTS (SELECT 1 FROM fire_cooking fc WHERE fc.raw_item_key=d.item_key);
    IF bad IS NOT NULL THEN
        RAISE EXCEPTION 'V396: these raw foods can still never be cooked, and no reason is recorded: %', bad;
    END IF;
END $$;
