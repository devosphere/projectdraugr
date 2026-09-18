-- #112 (epic #109) — meeting a people.
--
-- Contact is gradual. A Chronicle learns a people's ways and words over encounters, and what they have learned is
-- a fact about the relation, not about the Chronicle alone: understanding the reedkin of one isle is not
-- understanding everyone. It is kept beside standing, never shown to the player as a number; narration reports only
-- what the Chronicle sees and hears (ContactService). Every act of contact is written to the community's
-- append-only history (V345), so what happened between them cannot be rewritten and later decisions (#113, #114)
-- read the same evidence.

ALTER TABLE community_relation
    ADD COLUMN understanding     INTEGER NOT NULL DEFAULT 0 CHECK (understanding BETWEEN 0 AND 100),
    ADD COLUMN first_contact_at  TIMESTAMPTZ;

COMMENT ON COLUMN community_relation.understanding IS
  '#112: how much of this community''s speech and custom the other party has come to follow, 0-100. Never shown as a number.';
