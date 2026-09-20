-- #113 (epic #109) — a place among them.
--
-- A Chronicle given a place in a community is not given a title: they are given a share of a store that can run
-- out, and they are counted among the mouths that store has to feed. The row is the whole of it — which community,
-- which Chronicle, from when, until when, and why it ended.

CREATE TABLE native_membership (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    community_id    UUID NOT NULL REFERENCES native_community(id),
    chronicle_id    UUID NOT NULL REFERENCES chronicle(id),
    joined_at       TIMESTAMPTZ NOT NULL,
    left_at         TIMESTAMPTZ,
    end_reason      VARCHAR(16) CHECK (end_reason IN ('LEFT','ASKED_TO_LEAVE','DIED','DISPERSED')),
    CONSTRAINT a_place_given_up_says_why CHECK ((left_at IS NULL) = (end_reason IS NULL)),
    CONSTRAINT a_place_ends_after_it_begins CHECK (left_at IS NULL OR left_at >= joined_at)
);
-- One place at a time in one community, and one people at a time: a person belongs somewhere, not everywhere.
CREATE UNIQUE INDEX native_membership_one_per_community ON native_membership (community_id, chronicle_id) WHERE left_at IS NULL;
CREATE UNIQUE INDEX native_membership_one_people ON native_membership (chronicle_id) WHERE left_at IS NULL;

INSERT INTO activity_impact (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire, notes, labor_energy, labor_hygiene, capability_domain, duration_minutes)
VALUES ('JOIN_PEOPLE', NULL, 0, FALSE, FALSE,
        'Asking for a place among a people, drawing a day''s share from their store, or giving the place up: words at the landing and a walk to the store house. Nothing underfoot changes.',
        2, 0, 'ATTENTION', 25)
ON CONFLICT (intent_key) DO NOTHING;
