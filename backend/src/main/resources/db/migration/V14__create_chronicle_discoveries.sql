CREATE TABLE chronicle_discovery (
    chronicle_id UUID NOT NULL REFERENCES chronicle(id),
    discovery_key VARCHAR(100) NOT NULL,
    acquired_at TIMESTAMPTZ NOT NULL,
    source_action_id UUID NULL,
    PRIMARY KEY (chronicle_id, discovery_key)
);

CREATE INDEX idx_chronicle_discovery_timeline ON chronicle_discovery(chronicle_id, acquired_at);
