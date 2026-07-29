CREATE TABLE item_definition (
    item_key VARCHAR(100) PRIMARY KEY,
    display_name VARCHAR(255) NOT NULL,
    category VARCHAR(40) NOT NULL,
    unit_mass_grams INTEGER NOT NULL CHECK (unit_mass_grams >= 0),
    unit_volume_ml INTEGER NOT NULL CHECK (unit_volume_ml >= 0),
    stackable BOOLEAN NOT NULL DEFAULT FALSE,
    equippable BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE item_instance (
    object_id UUID PRIMARY KEY REFERENCES world_object(id),
    item_key VARCHAR(100) NOT NULL REFERENCES item_definition(item_key),
    condition_state VARCHAR(30) NOT NULL DEFAULT 'SOUND',
    created_from_action_id UUID NULL,
    CHECK (condition_state IN ('SOUND', 'WORN', 'BROKEN', 'DESTROYED'))
);

CREATE TABLE container_properties (
    object_id UUID PRIMARY KEY REFERENCES item_instance(object_id),
    max_mass_grams INTEGER NOT NULL CHECK (max_mass_grams > 0),
    max_volume_ml INTEGER NOT NULL CHECK (max_volume_ml > 0),
    allows_nested_containers BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE item_containment (
    item_id UUID PRIMARY KEY REFERENCES item_instance(object_id),
    container_id UUID NOT NULL REFERENCES container_properties(object_id),
    placed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (item_id <> container_id)
);

CREATE TABLE equipment_attachment (
    item_id UUID PRIMARY KEY REFERENCES item_instance(object_id),
    chronicle_id UUID NOT NULL REFERENCES chronicle(id),
    body_position VARCHAR(40) NOT NULL,
    layer VARCHAR(30) NOT NULL,
    attached_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (body_position IN ('HEAD','FACE','NECK','SHOULDER_LEFT','SHOULDER_RIGHT','TORSO','WAIST','BACK','ARM_LEFT','ARM_RIGHT','HAND_LEFT','HAND_RIGHT','LEG_LEFT','LEG_RIGHT','FOOT_LEFT','FOOT_RIGHT','FINGER_LEFT_1','FINGER_LEFT_2','FINGER_LEFT_3','FINGER_LEFT_4','FINGER_LEFT_5','FINGER_RIGHT_1','FINGER_RIGHT_2','FINGER_RIGHT_3','FINGER_RIGHT_4','FINGER_RIGHT_5')),
    CHECK (layer IN ('UNDER','CLOTHING','OUTER','PROTECTION','ATTACHED','CARRIED')),
    UNIQUE (chronicle_id, body_position, layer)
);

CREATE TABLE object_transition (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    object_id UUID NOT NULL REFERENCES world_object(id),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    transition_type VARCHAR(50) NOT NULL,
    from_container_id UUID NULL REFERENCES container_properties(object_id),
    to_container_id UUID NULL REFERENCES container_properties(object_id),
    from_attachment VARCHAR(100) NULL,
    to_attachment VARCHAR(100) NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    CHECK (jsonb_typeof(payload) = 'object')
);

CREATE INDEX idx_item_containment_container ON item_containment(container_id);
CREATE INDEX idx_equipment_attachment_chronicle ON equipment_attachment(chronicle_id);
CREATE INDEX idx_object_transition_object ON object_transition(object_id, occurred_at);

CREATE OR REPLACE FUNCTION validate_item_containment() RETURNS trigger AS $$
DECLARE used_mass INTEGER; used_volume INTEGER; max_mass INTEGER; max_volume INTEGER; nested_allowed BOOLEAN;
BEGIN
    IF EXISTS (WITH RECURSIVE ancestors(id) AS (SELECT NEW.container_id UNION ALL SELECT c.container_id FROM item_containment c JOIN ancestors a ON c.item_id = a.id) SELECT 1 FROM ancestors WHERE id = NEW.item_id) THEN
        RAISE EXCEPTION 'container placement would create a containment cycle';
    END IF;
    SELECT cp.max_mass_grams, cp.max_volume_ml, cp.allows_nested_containers INTO max_mass, max_volume, nested_allowed FROM container_properties cp WHERE cp.object_id = NEW.container_id;
    IF NOT nested_allowed AND EXISTS (SELECT 1 FROM container_properties WHERE object_id = NEW.item_id) THEN RAISE EXCEPTION 'container does not permit nested containers'; END IF;
    WITH RECURSIVE contents(id) AS (SELECT item_id FROM item_containment WHERE container_id=NEW.container_id AND item_id<>NEW.item_id UNION ALL SELECT ic.item_id FROM item_containment ic JOIN contents c ON ic.container_id=c.id) SELECT COALESCE(SUM(d.unit_mass_grams),0), COALESCE(SUM(d.unit_volume_ml),0) INTO used_mass, used_volume FROM contents JOIN item_instance ii ON ii.object_id=contents.id JOIN item_definition d ON d.item_key=ii.item_key;
    WITH RECURSIVE incoming(id) AS (SELECT NEW.item_id UNION ALL SELECT ic.item_id FROM item_containment ic JOIN incoming i ON ic.container_id=i.id) SELECT used_mass + COALESCE(SUM(d.unit_mass_grams),0), used_volume + COALESCE(SUM(d.unit_volume_ml),0) INTO used_mass, used_volume FROM incoming JOIN item_instance ii ON ii.object_id=incoming.id JOIN item_definition d ON d.item_key=ii.item_key;
    IF used_mass > max_mass OR used_volume > max_volume THEN RAISE EXCEPTION 'container capacity exceeded'; END IF;
    RETURN NEW;
END; $$ LANGUAGE plpgsql;
CREATE TRIGGER item_containment_is_valid BEFORE INSERT OR UPDATE ON item_containment FOR EACH ROW EXECUTE FUNCTION validate_item_containment();

CREATE OR REPLACE FUNCTION prevent_object_transition_mutation() RETURNS trigger AS $$ BEGIN RAISE EXCEPTION 'object_transition is immutable'; END; $$ LANGUAGE plpgsql;
CREATE TRIGGER object_transition_is_immutable BEFORE UPDATE OR DELETE ON object_transition FOR EACH ROW EXECUTE FUNCTION prevent_object_transition_mutation();

INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable) VALUES
('woven_basket','Woven basket','CONTAINER',900,12000,FALSE,TRUE),
('field_stone','Field stone','MATERIAL',750,300,FALSE,FALSE),
('clay_lump','Clay lump','MATERIAL',1200,700,FALSE,FALSE)
ON CONFLICT (item_key) DO NOTHING;
