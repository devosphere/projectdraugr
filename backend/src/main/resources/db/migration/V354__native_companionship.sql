-- #113 (epic #109) — travelling together.
--
-- One of a people who has chosen to walk with a Chronicle for a while. The row is the whole of it: who, with whom,
-- from when, until when, and why it ended. The companion stays a person of their community — their body is their
-- own world object, their food what they carried from the store — and the community's daily step lives their day:
-- eating, going hungry, growing homesick, and coming home.

CREATE TABLE native_companionship (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    individual_id       UUID NOT NULL REFERENCES native_individual(object_id),
    community_id        UUID NOT NULL REFERENCES native_community(id),
    chronicle_id        UUID NOT NULL REFERENCES chronicle(id),
    started_at          TIMESTAMPTZ NOT NULL,
    hungry_days         INTEGER NOT NULL DEFAULT 0 CHECK (hungry_days >= 0),
    ended_at            TIMESTAMPTZ,
    end_reason          VARCHAR(20) CHECK (end_reason IN ('PARTED','WENT_HOME_HUNGRY','HOMESICK','LEFT_ALONE','DIED')),
    CONSTRAINT an_ending_has_a_reason CHECK ((ended_at IS NULL) = (end_reason IS NULL)),
    CONSTRAINT it_ends_after_it_begins CHECK (ended_at IS NULL OR ended_at >= started_at)
);
-- One person walks with one Chronicle at a time, and a Chronicle has one companion at a time.
CREATE UNIQUE INDEX native_companionship_one_per_person ON native_companionship (individual_id) WHERE ended_at IS NULL;
CREATE UNIQUE INDEX native_companionship_one_per_chronicle ON native_companionship (chronicle_id) WHERE ended_at IS NULL;

-- A companion is always one of the community they are recorded against.
CREATE OR REPLACE FUNCTION native_companion_is_of_their_community() RETURNS trigger AS $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM native_individual WHERE object_id = NEW.individual_id AND community_id = NEW.community_id) THEN
    RAISE EXCEPTION 'a companion must belong to the community recorded with them (#113)';
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER native_companion_is_of_their_community
  BEFORE INSERT OR UPDATE OF individual_id, community_id ON native_companionship
  FOR EACH ROW EXECUTE FUNCTION native_companion_is_of_their_community();

INSERT INTO activity_impact (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire, notes, labor_energy, labor_hygiene, capability_domain, duration_minutes)
VALUES ('COMPANION_PEOPLE', NULL, 0, FALSE, FALSE,
        'Asking someone to walk with you, or parting from them, is words and gesture at the landing or on the road: nothing underfoot changes.',
        2, 0, 'ATTENTION', 15)
ON CONFLICT (intent_key) DO NOTHING;
