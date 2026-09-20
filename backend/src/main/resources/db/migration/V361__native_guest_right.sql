-- #113 (epic #109) — a roof for the night.
--
-- A people who know a Chronicle and are not shut up will put them under cover. Until now that was impossible to
-- express: shelter was something a Chronicle had built, so standing in the middle of a village in a storm was the
-- same as standing on open marsh. A guest right is leave to be under their roof, and it runs out.
--
-- Three nights in ten days is hospitality; more than that is living there, which is asked for differently (V357).

CREATE TABLE native_guest_right (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    community_id    UUID NOT NULL REFERENCES native_community(id),
    chronicle_id    UUID NOT NULL REFERENCES chronicle(id),
    granted_at      TIMESTAMPTZ NOT NULL,
    welcome_until   TIMESTAMPTZ NOT NULL,
    CONSTRAINT a_welcome_ends_after_it_begins CHECK (welcome_until > granted_at)
);
CREATE INDEX native_guest_right_by_guest ON native_guest_right (chronicle_id, welcome_until);

COMMENT ON TABLE native_guest_right IS
  '#113: leave to be under a people''s roof, granted for a night. Read by Shelters.lentByAPeople, so their house shelters a guest exactly as a built one shelters its owner.';
