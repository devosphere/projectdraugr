-- #121 (epic #109) — a role is someone holding it, from a day to a day.
--
-- Until now the one who spoke for an isle was a value in native_individual.role: a title with no start, no end and
-- no history, so a speaker's death left a people with no record that anyone had ever spoken for them, and nothing
-- to say who came next or when. A tenure is the holding itself: who, which office, from when, until when, and why
-- it ended. native_event keeps the telling (SPEAKER_LOST, SUCCESSION); this keeps the facts, and is append-only
-- except for closing an open tenure once.

CREATE TABLE native_role_tenure (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    individual_id   UUID NOT NULL REFERENCES native_individual(object_id),
    community_id    UUID NOT NULL REFERENCES native_community(id),
    role            VARCHAR(12) NOT NULL CHECK (role IN ('HEADSPERSON','ELDER','GUARD')),
    began_at        TIMESTAMPTZ NOT NULL,
    ended_at        TIMESTAMPTZ,
    end_reason      VARCHAR(12) CHECK (end_reason IN ('DIED','STEPPED_DOWN','REPLACED','LEFT')),
    CONSTRAINT a_tenure_ends_with_a_reason CHECK ((ended_at IS NULL) = (end_reason IS NULL)),
    CONSTRAINT a_tenure_ends_after_it_begins CHECK (ended_at IS NULL OR ended_at >= began_at)
);
-- One speaker at a time for a community, and one open tenure of an office per person.
CREATE UNIQUE INDEX native_one_speaker ON native_role_tenure (community_id) WHERE role = 'HEADSPERSON' AND ended_at IS NULL;
CREATE UNIQUE INDEX native_one_open_tenure ON native_role_tenure (individual_id, role) WHERE ended_at IS NULL;

CREATE OR REPLACE FUNCTION native_role_tenure_is_history() RETURNS trigger AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'a tenure is history and cannot be deleted (#121)'; END IF;
  IF OLD.ended_at IS NOT NULL
     OR NEW.individual_id <> OLD.individual_id OR NEW.community_id <> OLD.community_id
     OR NEW.role <> OLD.role OR NEW.began_at <> OLD.began_at THEN
    RAISE EXCEPTION 'a tenure may only be closed, once (#121)';
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER native_role_tenure_is_history
  BEFORE UPDATE OR DELETE ON native_role_tenure
  FOR EACH ROW EXECUTE FUNCTION native_role_tenure_is_history();

-- Everyone who holds an office now holds it from the day their community was founded.
INSERT INTO native_role_tenure (individual_id, community_id, role, began_at)
SELECT n.object_id, n.community_id, n.role, c.founded_at
  FROM native_individual n JOIN native_community c ON c.id = n.community_id
 WHERE n.role IN ('HEADSPERSON','GUARD') AND n.condition <> 'DEAD';

INSERT INTO activity_impact (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire, notes, labor_energy, labor_hygiene, capability_domain, duration_minutes)
VALUES ('ADDRESS_PEOPLE', NULL, 0, FALSE, FALSE,
        'Asking who speaks for a people, who does what, or standing with them in mourning is words and waiting: nothing underfoot changes.',
        2, 0, 'ATTENTION', 20)
ON CONFLICT (intent_key) DO NOTHING;
