CREATE TABLE food_preservation_state (
    object_id UUID PRIMARY KEY REFERENCES item_instance(object_id),
    preparation_kind VARCHAR(20) NOT NULL CHECK (preparation_kind IN ('RAW','COOKED')),
    safe_until TIMESTAMPTZ NOT NULL,
    spoiled_at TIMESTAMPTZ NULL
);

CREATE INDEX idx_food_preservation_freshness ON food_preservation_state (safe_until) WHERE spoiled_at IS NULL;
