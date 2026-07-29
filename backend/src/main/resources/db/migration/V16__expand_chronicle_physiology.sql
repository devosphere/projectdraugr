ALTER TABLE chronicle_physiology
    ADD COLUMN sleep_debt_hours NUMERIC(6, 2) NOT NULL DEFAULT 0 CHECK (sleep_debt_hours BETWEEN 0 AND 72),
    ADD COLUMN pain_level SMALLINT NOT NULL DEFAULT 0 CHECK (pain_level BETWEEN 0 AND 100),
    ADD COLUMN stress_level SMALLINT NOT NULL DEFAULT 10 CHECK (stress_level BETWEEN 0 AND 100),
    ADD COLUMN injury_severity SMALLINT NOT NULL DEFAULT 0 CHECK (injury_severity BETWEEN 0 AND 100),
    ADD COLUMN illness_severity SMALLINT NOT NULL DEFAULT 0 CHECK (illness_severity BETWEEN 0 AND 100),
    ADD COLUMN blood_loss_ml INTEGER NOT NULL DEFAULT 0 CHECK (blood_loss_ml BETWEEN 0 AND 7000);
