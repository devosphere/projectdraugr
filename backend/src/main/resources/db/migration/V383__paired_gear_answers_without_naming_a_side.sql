-- #37 — asking for gloves offers you a waterskin.
--
-- Found by turning the hand-found defect of V382 into a sweep. Snowshoes were not one case; they were one of
-- TWENTY-NINE. Every piece of paired gear is made by two processes, make_X_left and make_X_right, and every
-- keyword either of them carries names a SIDE. So the thing a person actually says reaches the wrong work:
--
--     make leather gloves   -> "Those words fit more than one piece of work — make a waterskin, or weave a
--                               quiver ..."
--     make leather bracers  -> the same waterskin
--     make fur mittens      -> "Line a garment with fur turns on a cutting edge ..."
--     make fur socks        -> the same fur lining
--     make a pair of gloves -> UNKNOWN
--     make a shin guard     -> UNKNOWN
--
-- These are not gaps. A gap says nothing; these confidently offered a DIFFERENT piece of work, because with no
-- keyword naming the gear the longest match fell to the material word — "leather", "fur" — and ran away with
-- it. That is the worst way for an answer to be wrong, and it is the state of twenty-four families.
--
-- Each pair gets the side-less name, singular and plural, derived from its own output key rather than written
-- out, so a paired process added later inherits nothing and a renamed item cannot leave a stale literal behind.
-- Both sides get it, which makes a tie -- and a tie is the right answer, because gear is made one side at a
-- time: the resolver asks which, and prefers whichever side has its materials in reach (#38/#593).
--
-- FIVE FAMILIES ARE DELIBERATELY EXCLUDED, by the query rather than by a list. bark_sandal, fibre_hand_wrap,
-- grass_ankle_wrap and bark_splint already have a process that answers to the side-less name -- make_bark_sandals
-- makes the PAIR, which is the right answer for "make bark sandals" and must not become a question -- and
-- snowshoe got its plural in V382. Anything already claimed is left exactly as it is.

WITH pairs AS (
    SELECT mp.process_key,
           replace(regexp_replace(mp.output_item_key, '_(left|right)$', ''), '_', ' ') AS spoken
    FROM material_process mp
    WHERE mp.process_key ~ '_(left|right)$'
),
free AS (
    SELECT p.process_key, p.spoken FROM pairs p
    WHERE NOT EXISTS (
        SELECT 1 FROM material_process m
        WHERE ',' || m.keywords || ',' LIKE '%,' || p.spoken || ',%'
           OR ',' || m.keywords || ',' LIKE '%,' || p.spoken || 's,%')
)
UPDATE material_process mp
   SET keywords = mp.keywords || ',' || f.spoken || ',' || f.spoken || 's'
  FROM free f
 WHERE mp.process_key = f.process_key;

-- All three of category, keyword and subject must agree, so the side-less noun is a subject too. The LAST word
-- of the name carries it ("glove", "bracer", "shin guard" -> "guard"), alongside the full name.
INSERT INTO process_subject (process_key, subject_term)
SELECT mp.process_key, replace(regexp_replace(mp.output_item_key, '_(left|right)$', ''), '_', ' ')
  FROM material_process mp
 WHERE mp.process_key ~ '_(left|right)$'
   AND ',' || mp.keywords || ',' LIKE '%,' || replace(regexp_replace(mp.output_item_key, '_(left|right)$', ''), '_', ' ') || ',%'
ON CONFLICT (process_key, subject_term) DO NOTHING;

DO $$
DECLARE n INT;
BEGIN
    -- Both sides of every family that took the name must have taken it, or one side is unreachable by it and
    -- the "tie" is really a silent single answer.
    SELECT COUNT(*) INTO n FROM (
        SELECT regexp_replace(process_key, '_(left|right)$', '') AS pair,
               COUNT(*) FILTER (WHERE ',' || keywords || ',' LIKE
                   '%,' || replace(regexp_replace(output_item_key, '_(left|right)$', ''), '_', ' ') || ',%') AS named
        FROM material_process WHERE process_key ~ '_(left|right)$'
        GROUP BY 1 HAVING COUNT(*) FILTER (WHERE ',' || keywords || ',' LIKE
                   '%,' || replace(regexp_replace(output_item_key, '_(left|right)$', ''), '_', ' ') || ',%') = 1) q;
    IF n > 0 THEN RAISE EXCEPTION 'V383: % paired famil(ies) took the side-less name on one side only', n; END IF;

    -- At least twenty families must now answer to their own name, or the sweep found nothing and this migration
    -- is inert.
    SELECT COUNT(DISTINCT regexp_replace(process_key, '_(left|right)$', '')) INTO n
      FROM material_process WHERE process_key ~ '_(left|right)$'
       AND ',' || keywords || ',' LIKE '%,' || replace(regexp_replace(output_item_key, '_(left|right)$', ''), '_', ' ') || ',%';
    IF n < 20 THEN RAISE EXCEPTION 'V383: only % paired families answer to their own name', n; END IF;

    -- Naming the side must still settle it outright, which is what keeps the tie reachable only by the
    -- side-less phrase.
    SELECT COUNT(*) INTO n FROM material_process mp
     WHERE mp.process_key ~ '_(left|right)$'
       AND NOT EXISTS (SELECT 1 FROM unnest(string_to_array(mp.keywords, ',')) k
                       WHERE length(trim(k)) > length(replace(regexp_replace(mp.output_item_key, '_(left|right)$', ''), '_', ' ')) + 1);
    IF n > 0 THEN RAISE EXCEPTION 'V383: % paired process(es) cannot be named more precisely than the pair', n; END IF;

    -- And the pair-making processes keep the phrases they already owned. make_bark_sandals makes the PAIR and
    -- is the right answer for "make bark sandals"; it must not have become a question.
    SELECT COUNT(*) INTO n FROM material_process
     WHERE process_key = 'make_bark_sandals' AND ',' || keywords || ',' LIKE '%,bark sandals,%';
    IF n <> 1 THEN RAISE EXCEPTION 'V383: the sandal pair has lost its own name'; END IF;
    SELECT COUNT(*) INTO n FROM material_process
     WHERE process_key ~ 'make_bark_sandal_(left|right)' AND ',' || keywords || ',' LIKE '%,bark sandals,%';
    IF n <> 0 THEN RAISE EXCEPTION 'V383: a single sandal now answers to the pair''s name'; END IF;
END $$;
