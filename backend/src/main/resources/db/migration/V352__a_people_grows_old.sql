-- #121 (epic #109) — a people grows old: life stage as persistent, biological state.
--
-- A native person now has a birth date, and their stage of life follows from it: a child grows into an adult who
-- takes up work, an adult becomes an elder, an elder dies of age, and a fed community in spring has children. The
-- stage is kept as its own column (not inferred from role) because the ticket separates the layers: life stage is
-- biology, role is what the community needs of someone, and a child who comes of age changes stage first and then
-- takes up whatever work the isle is short of.
--
-- A grave (#211) is where the community's dead are laid, and it is a place, not a record: a world object on the
-- isle, kept with the name of who lies there.

ALTER TABLE native_individual
    ADD COLUMN born_on     DATE,
    ADD COLUMN life_stage  VARCHAR(8) NOT NULL DEFAULT 'ADULT' CHECK (life_stage IN ('CHILD','ADULT','ELDER'));

-- Everyone already living is given an age that fits the role they already hold, so nobody's stage contradicts what
-- they do. Ages are spread deterministically across plausible spans, not all the same.
UPDATE native_individual n
   SET life_stage = CASE n.role WHEN 'CHILD' THEN 'CHILD' WHEN 'ELDER' THEN 'ELDER' ELSE 'ADULT' END,
       born_on = (c.founded_at::date) - (CASE n.role
                    WHEN 'CHILD' THEN 5 + (abs(hashtext(n.object_id::text)) % 7)
                    WHEN 'ELDER' THEN 56 + (abs(hashtext(n.object_id::text)) % 12)
                    ELSE 18 + (abs(hashtext(n.object_id::text)) % 25) END) * 365
  FROM native_community c WHERE c.id = n.community_id AND n.born_on IS NULL;

-- A child is a child: the stage and the role agree for everyone.
ALTER TABLE native_individual ADD CONSTRAINT children_do_the_work_of_children
    CHECK ((life_stage = 'CHILD') = (role = 'CHILD'));

-- Anyone made without a birth date is given one that fits the role they are made into, and the stage that goes with
-- it, so every path that creates a person yields a consistent life without having to know this migration exists.
CREATE OR REPLACE FUNCTION native_individual_is_born() RETURNS trigger AS $$
DECLARE founded DATE;
BEGIN
  IF NEW.born_on IS NULL THEN
    NEW.life_stage := CASE NEW.role WHEN 'CHILD' THEN 'CHILD' WHEN 'ELDER' THEN 'ELDER' ELSE 'ADULT' END;
    SELECT founded_at::date INTO founded FROM native_community WHERE id = NEW.community_id;
    NEW.born_on := COALESCE(founded, CURRENT_DATE) - (CASE NEW.role
                     WHEN 'CHILD' THEN 5 + (abs(hashtext(NEW.object_id::text)) % 7)
                     WHEN 'ELDER' THEN 56 + (abs(hashtext(NEW.object_id::text)) % 12)
                     ELSE 18 + (abs(hashtext(NEW.object_id::text)) % 25) END) * 365;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER native_individual_born
  BEFORE INSERT ON native_individual
  FOR EACH ROW EXECUTE FUNCTION native_individual_is_born();

ALTER TABLE native_individual ALTER COLUMN born_on SET NOT NULL;
