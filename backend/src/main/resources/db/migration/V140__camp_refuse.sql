-- V140: camp refuse / sanitation state (EPIC #215 story #218 — waste, contamination, sanitation, pests).
--
-- Waste-generating work at a camp — butchery now, food processing / sustained occupation in later slices — leaves
-- refuse that, left to pile up, breeds illness (this slice) and draws pests/scavengers (a later slice). A built
-- latrine / refuse pit disposes of it (giving the LATRINE its second, long-described function beyond personal
-- hygiene), and it also breaks down slowly on its own when a camp is left. Mirrors the chunk_disturbance state
-- model (#207/#208). Defaults empty, so a camp only grows foul when it is actually worked and left unclean — every
-- world that does not accumulate refuse behaves exactly as before.
CREATE TABLE chunk_refuse (
    chunk_id        UUID PRIMARY KEY REFERENCES world_chunk(id) ON DELETE CASCADE,
    refuse_level    INTEGER NOT NULL DEFAULT 0 CHECK (refuse_level BETWEEN 0 AND 100),
    last_updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
