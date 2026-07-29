CREATE TABLE construction_project (
    object_id UUID PRIMARY KEY REFERENCES world_object(id),
    project_kind VARCHAR(100) NOT NULL,
    state VARCHAR(30) NOT NULL CHECK (state IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'ABANDONED', 'DESTROYED')),
    progress_percent SMALLINT NOT NULL DEFAULT 0 CHECK (progress_percent BETWEEN 0 AND 100),
    created_from_action_id UUID NULL,
    completed_at TIMESTAMPTZ NULL,
    CHECK ((state = 'COMPLETED' AND progress_percent = 100 AND completed_at IS NOT NULL) OR state <> 'COMPLETED')
);

CREATE INDEX idx_construction_project_state ON construction_project(state, project_kind);
