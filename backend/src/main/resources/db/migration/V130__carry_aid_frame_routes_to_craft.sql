-- V130 — let a carrying-aid "frame" read as CRAFT, not CONSTRUCT (#246 / #57).
--
-- lash_burden_frame is a CRAFT process, but the player's natural words for it — "lash a burden frame",
-- "make a burden frame", "lash a pack frame" — all contain "frame", which is a CONSTRUCT term (building
-- framing). With only "lash" (CRAFT 2) opposing "frame" (CONSTRUCT 2) the activity classifier ties and
-- precedence breaks it to CONSTRUCT, so the CRAFT process can never match and the action dead-ends in the
-- generic "no way to make that comes to you" line even though the recipe, tools, and inputs are all present.
--
-- The process passed the Auditor/routing probe only because ITS OTHER keywords (e.g. "carrying frame")
-- happen to classify to CRAFT; the specific, first-class "burden frame" phrasing did not. "lash" is itself
-- both a CRAFT and a CONSTRUCT term, so it cancels and the real decider is the carry-aid noun against
-- "frame" (CONSTRUCT, weight 2). Add the carry-aid nouns at weight 3 so those phrasings tip decisively to
-- CRAFT. "burden" occurs only in carry-aid actions, and the multi-word "pack frame" / "carrying frame"
-- cannot collide with anything else, so no other process's routing shifts.
INSERT INTO category_term (category_key, term, weight) VALUES
    ('CRAFT', 'burden',         3),
    ('CRAFT', 'pack frame',     3),
    ('CRAFT', 'carrying frame', 3)
ON CONFLICT (category_key, term) DO NOTHING;
