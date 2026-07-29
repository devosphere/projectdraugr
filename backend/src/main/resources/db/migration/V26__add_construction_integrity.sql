ALTER TABLE construction_project
    ADD COLUMN integrity_percent SMALLINT NOT NULL DEFAULT 100 CHECK (integrity_percent BETWEEN 0 AND 100),
    ADD COLUMN last_structural_update TIMESTAMPTZ NOT NULL DEFAULT now();
