-- #113 (epic #109) — trade with a people.
--
-- Every exchange is between real objects. An offer names things the Chronicle is carrying and things the
-- community's store holds; a completed trade moves both sets of world objects at once, each with a transition on
-- its own history, and this row records which objects went which way. There is no currency and no infinite stock:
-- a mat traded away is a mat the isle no longer has.
--
-- A trade the community answers with a counter-offer waits here until it is accepted, declined, or overtaken by a
-- new offer; only one offer is open between a Chronicle and a community at a time.

CREATE TABLE native_trade (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    community_id        UUID NOT NULL REFERENCES native_community(id),
    chronicle_id        UUID NOT NULL REFERENCES chronicle(id),
    status              VARCHAR(10) NOT NULL CHECK (status IN ('COUNTERED','COMPLETED','DECLINED','WITHDRAWN')),
    offered_item_ids    UUID[] NOT NULL,
    wanted_item_key     VARCHAR(80) NOT NULL REFERENCES item_definition(item_key),
    wanted_count        INTEGER NOT NULL CHECK (wanted_count BETWEEN 1 AND 12),
    received_item_ids   UUID[],
    offered_at          TIMESTAMPTZ NOT NULL,
    settled_at          TIMESTAMPTZ
);
CREATE UNIQUE INDEX native_trade_one_open_offer ON native_trade (community_id, chronicle_id) WHERE status = 'COUNTERED';
