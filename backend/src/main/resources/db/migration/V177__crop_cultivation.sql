-- V177 — cultivated grain: the first slice of physical agriculture (EPIC #162 seed → soil → crop → harvest → renewal).
--
-- Grain existed only as WILD forage: a Chronicle could find a grain head, thresh it, grind it — but never sow a seed
-- and reap a stand. That is the whole of farming missing. This opens it with the core loop: seed grain worked into
-- open ground comes up, over a season, as a crop stand that yields far more grain than was sown — the multiplication
-- that makes cultivation worth the labour. It feeds straight into the chain that already exists (harvest wild grain
-- heads → thresh → grain → grind → flour), so the sown crop is functional end-to-end from its first day.
--
-- One stand per worked ground at a time, timestamped so its ripening is elapsed real growing-time (not a use count),
-- and marked when reaped so the ground reads as spent until it is sown afresh. Growth stages, tending, water, soil
-- fertility, and crop stress are later slices (#164-#170); this is the seed-to-harvest spine they hang on.
CREATE TABLE crop_stand (
    id            UUID PRIMARY KEY,
    chunk_id      UUID        NOT NULL REFERENCES world_chunk(id),
    crop_key      VARCHAR(60) NOT NULL,
    sown_at       TIMESTAMPTZ NOT NULL,
    maturity_days INTEGER     NOT NULL CHECK (maturity_days > 0),
    harvested     BOOLEAN     NOT NULL DEFAULT FALSE,
    harvested_at  TIMESTAMPTZ
);

CREATE INDEX idx_crop_stand_chunk ON crop_stand (chunk_id) WHERE harvested = FALSE;
