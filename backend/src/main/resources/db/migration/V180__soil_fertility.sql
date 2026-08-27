-- V180 — soil fertility: cropping the same ground exhausts it; rest and rotation restore it (EPIC #162 / story #164
-- soil, fertility, site suitability). A field yielded the same however many seasons it was cropped, which let one
-- patch feed a Chronicle forever. In truth each harvest takes from the soil, and ground cropped season after season
-- without rest gives thinner stands until it is left fallow to recover — the reason farmers rotate and rest their
-- fields. This tracks a field's fertility: a harvest draws it down, fallow time brings it back, and a worn field
-- gives less. Pristine ground (no row) reads as full, so nothing already growing changes; the cost falls on the
-- field that is worked without rest.
CREATE TABLE field_soil (
    chunk_id        UUID        PRIMARY KEY REFERENCES world_chunk(id),
    fertility       SMALLINT    NOT NULL DEFAULT 100 CHECK (fertility BETWEEN 0 AND 100),
    last_updated_at TIMESTAMPTZ NOT NULL
);
