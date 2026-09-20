-- #211 (epic #207) — what they ask in return.
--
-- A people who have been robbed, burnt out or bereaved did not, until now, ask for anything. Their standing fell,
-- they closed their store and that was the whole of it: there was no way for a Chronicle to answer a wrong except
-- by waiting for a month of good behaviour. The ticket asks for negotiation and compensation, and a claim is what
-- both of those rest on — a price, in goods, that the wrong is reckoned to be worth.
--
-- One claim stands at a time between a community and a Chronicle; a further wrong adds to it rather than starting
-- a second, which is how a people actually keeps a score. It ends PAID, or UNANSWERED a month later, or WAIVED.

CREATE TABLE native_claim (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    community_id    UUID NOT NULL REFERENCES native_community(id),
    chronicle_id    UUID NOT NULL REFERENCES chronicle(id),
    for_offence     VARCHAR(40) NOT NULL,
    price           INTEGER NOT NULL CHECK (price > 0),
    paid            INTEGER NOT NULL DEFAULT 0 CHECK (paid >= 0),
    status          VARCHAR(12) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','PAID','WAIVED','UNANSWERED')),
    demanded_at     TIMESTAMPTZ NOT NULL,
    settled_at      TIMESTAMPTZ,
    CONSTRAINT a_settled_claim_says_when CHECK ((status = 'OPEN') = (settled_at IS NULL)),
    CONSTRAINT a_paid_claim_was_paid CHECK (status <> 'PAID' OR paid >= price)
);
CREATE UNIQUE INDEX native_claim_one_standing ON native_claim (community_id, chronicle_id) WHERE status = 'OPEN';

INSERT INTO activity_impact (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire, notes, labor_energy, labor_hygiene, capability_domain, duration_minutes)
VALUES ('SETTLE_CLAIM', NULL, 0, FALSE, FALSE,
        'Asking what would put a wrong right, or carrying goods to the landing and setting them down: the ground is walked over and nothing on it changes.',
        6, 3, 'LOAD', 30)
ON CONFLICT (intent_key) DO NOTHING;

COMMENT ON TABLE native_claim IS
  '#211: what a community asks in compensation for a wrong, and whether it was paid. One standing claim per Chronicle.';
