-- Regression: thick objects (V68, DR-0022 Layer 4) — a crafted thing carries features and a dated history.
--
-- object_attribute (key/value features) and object_modification (a dated log of how it changed) are additive
-- metadata on a world_object; examination reads them back so an object is a bespoke, evolving entity (the
-- Phase-0 "Utility Belt Revision III with a hammer holder"). Neither is ever an input to the physics gate.
-- Pins the round-trip the ExaminationService features()/modificationHistory() queries rely on.

BEGIN;

-- A placeholder object to hang attributes and modifications on (DESTROYED needs neither owner nor location).
INSERT INTO world_object (id, object_type, display_name, lifecycle_state)
 VALUES ('e1000000-0000-0000-0000-000000000000', 'ITEM', 'Utility belt', 'DESTROYED');

INSERT INTO object_attribute (object_id, attr_key, attr_value) VALUES
 ('e1000000-0000-0000-0000-000000000000', 'handle', 'true'),
 ('e1000000-0000-0000-0000-000000000000', 'length_cm', '28');

INSERT INTO object_modification (object_id, occurred_at, note) VALUES
 ('e1000000-0000-0000-0000-000000000000', now() - interval '2 days', 'Added a stone hatchet holder'),
 ('e1000000-0000-0000-0000-000000000000', now(),                    'Added a stone hammer holder');

DO $$
DECLARE feats int; latest text;
BEGIN
    SELECT count(*) INTO feats FROM object_attribute WHERE object_id = 'e1000000-0000-0000-0000-000000000000';
    SELECT note INTO latest FROM object_modification WHERE object_id = 'e1000000-0000-0000-0000-000000000000'
     ORDER BY occurred_at DESC LIMIT 1;

    IF feats <> 2 THEN RAISE EXCEPTION 'REGRESSION: object_attribute round-trip wrong (got % features)', feats; END IF;
    IF latest <> 'Added a stone hammer holder' THEN RAISE EXCEPTION 'REGRESSION: latest modification wrong (got %)', latest; END IF;
    RAISE NOTICE 'PASS: object features + dated modification history round-trip (V68 thick objects)';
END $$;

ROLLBACK;
