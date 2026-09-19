-- #113 (epic #109) — paid work for a people, and the promise it makes.
--
-- A Chronicle offers days of work for goods from a community's store. The community sets the number of days by
-- what the wage is worth to it, and the work is owed by a date. Worked in full, the wage is carried out of the store
-- into the Chronicle's hands; what the store cannot pay yet stays OWED and is paid by the daily step when it can.
-- Left undone past its date, or walked away from, it is BROKEN — a broken promise #114 remembers. RELEASED is an
-- honest ending asked for and granted. One open agreement between a Chronicle and a community at a time.

CREATE TABLE native_agreement (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    community_id        UUID NOT NULL REFERENCES native_community(id),
    chronicle_id        UUID NOT NULL REFERENCES chronicle(id),
    kind                VARCHAR(12) NOT NULL CHECK (kind IN ('PAID_WORK')),
    days_owed           INTEGER NOT NULL CHECK (days_owed BETWEEN 1 AND 7),
    days_done           INTEGER NOT NULL DEFAULT 0,
    wage_item_key       VARCHAR(80) NOT NULL REFERENCES item_definition(item_key),
    wage_count          INTEGER NOT NULL CHECK (wage_count BETWEEN 1 AND 12),
    wage_paid           INTEGER NOT NULL DEFAULT 0,
    status              VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','OWED','COMPLETED','BROKEN','RELEASED')),
    agreed_at           TIMESTAMPTZ NOT NULL,
    due_at              TIMESTAMPTZ NOT NULL,
    last_worked_at      TIMESTAMPTZ,
    settled_at          TIMESTAMPTZ,
    CONSTRAINT work_done_is_within_what_was_agreed CHECK (days_done BETWEEN 0 AND days_owed),
    CONSTRAINT wage_paid_is_within_what_was_agreed CHECK (wage_paid BETWEEN 0 AND wage_count),
    CONSTRAINT work_is_owed_after_it_is_agreed CHECK (due_at > agreed_at)
);
CREATE UNIQUE INDEX native_agreement_one_open ON native_agreement (community_id, chronicle_id) WHERE status = 'OPEN';

-- The ActivityImpactCard invariant: every intent says what it does to the ground, and what it costs the body.
INSERT INTO activity_impact (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire, notes, labor_energy, labor_hygiene, capability_domain, duration_minutes)
VALUES ('AGREE_WITH_PEOPLE', NULL, 0, FALSE, FALSE,
        'Agreeing terms at the landing is gesture, counting on fingers and pointing at the store: nothing underfoot changes.',
        2, 0, 'ATTENTION', 20),
       ('WORK_FOR_PEOPLE', NULL, 0, FALSE, FALSE,
        'A day at a people''s weirs and nets: hauling, gutting and carrying beside them. The catch goes to their store; the ground is their fishing water and is left as it was.',
        12, 8, 'LOAD', 360)
ON CONFLICT (intent_key) DO NOTHING;
