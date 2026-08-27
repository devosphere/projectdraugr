-- V184 — land clearing (EPIC #162 / story #165 land-clearing). Cultivation could only begin on open grassland: a
-- Chronicle standing in forest had no way to turn wooded ground into a field, so farming was fixed to wherever the
-- world seed happened to leave open ground. That is against the sector's intent, where a Chronicle wins arable land
-- by labour — felling the trees, cutting back the brush, grubbing the roots — the oldest way a settled people made a
-- field. This marks a wooded tile cleared: once the timber and brush are taken off it with an axe, the ground lies
-- open and arable, and tilling and sowing (which wanted grassland) will take it. Clearing is permanent — the trees do
-- not grow back over a season — so the labour buys a lasting field. One row per cleared chunk.
CREATE TABLE cleared_ground (
    chunk_id   UUID PRIMARY KEY REFERENCES world_chunk(id),
    cleared_at TIMESTAMPTZ NOT NULL
);
