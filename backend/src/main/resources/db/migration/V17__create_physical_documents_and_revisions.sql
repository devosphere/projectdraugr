INSERT INTO item_definition (item_key, display_name, category, unit_mass_grams, unit_volume_ml, stackable, equippable) VALUES
('field_journal','Field journal','LITERATURE',450,600,FALSE,FALSE),
('hand_drawn_map','Hand-drawn map','MAP',120,250,FALSE,FALSE)
ON CONFLICT (item_key) DO NOTHING;

CREATE TABLE literature_document (
    object_id UUID PRIMARY KEY REFERENCES item_instance(object_id),
    document_kind VARCHAR(20) NOT NULL CHECK (document_kind IN ('LITERATURE','MAP')),
    title VARCHAR(255) NOT NULL,
    current_revision_id UUID NULL
);

CREATE TABLE literature_revision (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES literature_document(object_id),
    revision_number INTEGER NOT NULL CHECK (revision_number > 0),
    parent_revision_id UUID NULL REFERENCES literature_revision(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by_chronicle_id UUID NULL REFERENCES chronicle(id),
    source_action_id UUID NULL,
    edit_kind VARCHAR(20) NOT NULL CHECK (edit_kind IN ('INITIAL','APPEND','INSERT','REPLACE')),
    content TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    UNIQUE(document_id, revision_number)
);

ALTER TABLE literature_document ADD CONSTRAINT literature_document_current_revision_fk FOREIGN KEY (current_revision_id) REFERENCES literature_revision(id);
CREATE INDEX idx_literature_revision_document ON literature_revision(document_id, revision_number DESC);

CREATE OR REPLACE FUNCTION prevent_literature_revision_mutation() RETURNS trigger AS $$ BEGIN RAISE EXCEPTION 'literature revisions are immutable'; END; $$ LANGUAGE plpgsql;
CREATE TRIGGER literature_revision_is_immutable BEFORE UPDATE OR DELETE ON literature_revision FOR EACH ROW EXECUTE FUNCTION prevent_literature_revision_mutation();
