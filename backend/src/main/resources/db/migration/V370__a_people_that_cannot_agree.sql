-- #121 (epic #109) — a people that cannot agree with itself.
--
-- #121's remaining scope, after the one a group follows (V363) and the queen and the matriarch (V367), is
-- "community disagreement as a state a people can be in". Until now the hardest decision a community ever made
-- was taken in a single line: at fourteen days of shortage, `lifecycle` went from SETTLED to MOVING, and eight
-- people abandoned the ground they were born on without a word passing between them.
--
-- WHAT A DISAGREEMENT IS. A question the community has put to itself and not yet answered. It is a row with a
-- life (DR-0026): it opens on a day, it has voices on each side, it stands until it is settled, and its settling
-- is written into the people's history like anything else that happened to them.
--
-- WHY LEAVING IS THE FIRST QUESTION. Because it is the one the world already asks them, and the one with two
-- honest sides. The elders hold to the ground — they have buried people in it — and everyone grown who is hungry
-- wants to go. Nobody asks a child. So the count is read off who is actually alive in the community that day,
-- not invented.
--
-- WHAT IT COSTS. While the argument stands they do not leave, and they will not put their name to a new
-- agreement (#113): a people arguing about whether to abandon its home does not take on a week's work. Hunger
-- goes on through all of it, which is the price of not deciding.
--
-- WHAT A CHRONICLE CAN DO ABOUT IT, without a new verb. Feed them. Ending the shortage settles the argument in
-- favour of staying, on the spot, because the thing they were arguing about has stopped being true. That is the
-- whole lever, and it is one a Chronicle already has — the rule from DR-0026 that what a people gives comes from
-- its own store cuts both ways: what a people is given, it has.

CREATE TABLE native_disagreement (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    community_id    UUID        NOT NULL REFERENCES native_community(id),
    question        VARCHAR(24) NOT NULL CHECK (question IN ('WHETHER_TO_LEAVE')),
    opened_at       TIMESTAMPTZ NOT NULL,
    voices_to_go    SMALLINT    NOT NULL CHECK (voices_to_go >= 0),
    voices_to_stay  SMALLINT    NOT NULL CHECK (voices_to_stay >= 0),
    settled_at      TIMESTAMPTZ,
    outcome         VARCHAR(12) CHECK (outcome IN ('WENT', 'STAYED')),
    CONSTRAINT settled_together CHECK ((settled_at IS NULL) = (outcome IS NULL)),
    CONSTRAINT an_argument_has_two_sides CHECK (voices_to_go + voices_to_stay > 0)
);

COMMENT ON TABLE native_disagreement IS
  '#121: a question a community has put to itself and not yet answered. Open while settled_at is NULL, and while one stands the community neither acts on the question nor enters a new agreement. Its settling is recorded in native_event like anything else that happens to them.';

-- One open argument to a community and a question. A people can be undecided about a thing; it cannot be
-- undecided about it twice.
CREATE UNIQUE INDEX native_one_open_argument
    ON native_disagreement (community_id, question) WHERE settled_at IS NULL;

CREATE INDEX native_disagreement_by_community ON native_disagreement (community_id, opened_at DESC);

-- Their history is theirs and cannot be rewritten — the same rule V345 put on native_event, for the same reason:
-- an argument that was had cannot be un-had once it is settled.
CREATE FUNCTION native_disagreement_is_history() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'native_disagreement is a community''s history and cannot be deleted (#121)';
    END IF;
    IF OLD.settled_at IS NOT NULL THEN
        RAISE EXCEPTION 'a settled disagreement cannot be reopened or rewritten (#121)';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER native_disagreement_append_only
    BEFORE UPDATE OR DELETE ON native_disagreement
    FOR EACH ROW EXECUTE FUNCTION native_disagreement_is_history();
