-- V73: activity vocabulary for the gathering / land-working / raw-material preparation verbs (M1 #68).
--
-- The #68 action catalogue lists everyday prep verbs — chop, crack, mill, screen, sort, separate, trim, sever,
-- hack, scoop, excavate, pry, lever, wash, rinse, pan — that were absent from category_term, so alternate
-- wording classified to no category. Adding them lets the two-axis matcher recognise the KIND of work and
-- route to the process catalogue (resolving where a process exists, else recording a *classified* routing miss
-- for review — never silently unclassified). Categories are chosen to AGREE with the processes that already
-- carry these words, so nothing existing becomes unreachable:
--   * PROCESS  — working a material (chop/crack/mill/screen/sort/separate/trim/sever/hack, and material
--                washing wash/rinse/pan; WASH the body is a separate intent, scoped in the classifier).
--   * ACQUIRE  — winning stock from a source (scoop/excavate/pry/lever), matching dig/scoop already ACQUIRE.
-- Deliberately NOT added: `bundle` (bundle_thatch is CONSTRUCT) and `peel` (make_rush_light is CRAFT / skin_fish
-- is HUNT) — classifying them here would disagree with those processes' own categories and hide them.

INSERT INTO category_term (category_key, term, weight) VALUES
    ('PROCESS', 'chop',     2),
    ('PROCESS', 'crack',    2),
    ('PROCESS', 'mill',     2),
    ('PROCESS', 'screen',   2),
    ('PROCESS', 'hack',     2),
    ('PROCESS', 'sever',    2),
    ('PROCESS', 'sort',     1),
    ('PROCESS', 'separate', 1),
    ('PROCESS', 'trim',     1),
    ('PROCESS', 'wash',     1),
    ('PROCESS', 'rinse',    1),
    ('PROCESS', 'pan',      1),
    ('ACQUIRE', 'excavate', 2),
    ('ACQUIRE', 'scoop',    1),
    ('ACQUIRE', 'pry',      1),
    ('ACQUIRE', 'lever',    1)
ON CONFLICT (category_key, term) DO NOTHING;
