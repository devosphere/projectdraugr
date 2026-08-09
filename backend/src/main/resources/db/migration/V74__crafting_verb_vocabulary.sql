-- V74: activity vocabulary for the remaining crafting / transformation verbs (M1 #69).
--
-- Most #69 verbs (make/craft/carve/haft/bind/weave/twist/sew/shape/knap/plait/spin/temper/form/mould, and the
-- MAINTAIN verbs mend/patch/reinforce/sharpen) already classify. These few synonyms were missing, so alternate
-- wording classified to no category. Categories agree with the processes that already carry these words, so
-- nothing existing becomes unreachable:
--   * coil    -> CRAFT   (form_vessel already carries "coil")
--   * whittle -> PROCESS (shape_components already carries "whittle")
--   * flake   -> PROCESS (knapping; whole-word "flake", distinct from the tool name "flaker")
--   * cleave / abrade / interlace -> PROCESS (split / smooth / weave synonyms; not existing keywords)
-- Deliberately NOT added: `rive` (rive_shakes is CONSTRUCT while rive_bow_stave is PROCESS — leaving it
-- unclassified keeps both reachable, the same rule applied to bundle/peel in V73).

INSERT INTO category_term (category_key, term, weight) VALUES
    ('CRAFT',   'coil',      2),
    ('PROCESS', 'whittle',   2),
    ('PROCESS', 'flake',     2),
    ('PROCESS', 'cleave',    2),
    ('PROCESS', 'abrade',    1),
    ('PROCESS', 'interlace', 1)
ON CONFLICT (category_key, term) DO NOTHING;
