ALTER TABLE chronicle_action
    ADD COLUMN narration TEXT;

ALTER TABLE chronicle_action
    ADD CONSTRAINT chronicle_action_narration_not_blank CHECK (narration IS NULL OR length(trim(narration)) > 0);

CREATE INDEX idx_chronicle_action_narration_timeline
    ON chronicle_action (chronicle_id, resolved_at DESC, id DESC)
    WHERE narration IS NOT NULL;
