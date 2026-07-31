-- V54: Activity categories and the subject gate — M1/M2 of Sprint 003.
--
-- Four procedure simulations (cabin, fish preservation, bow, leather armor) all
-- failed the same way: a process matched because a word in the action text also
-- appeared in its keyword list, with nothing checking that the two were talking
-- about the same thing. "Weatherproof the shell" matched the weather domain.
-- "Flashing around the doors" matched gather_ash. "Split the fish" matched
-- split_planks. Matching was lexical and global where it needed to be semantic
-- and scoped.
--
-- The dangerous direction is the false COVERED: a wrong match does not throw, it
-- silently suppresses the Architect call the situation actually needed, and the
-- chronicle gets a confidently wrong result. That is worse than an honest gap.
--
-- Scoping needs two axes, not one. Category alone was the original plan, but it
-- only separates five of the eight recorded collisions -- "split the fish" and
-- "split the log" are both PROCESS, as are "shape the bow blank" and
-- shape_components, and gathering mushrooms and prospecting ore are both ACQUIRE.
-- So a process must also agree on its SUBJECT: it may only match when the action
-- text names a material it actually consumes or produces. Splitting a fish never
-- reaches split_planks because no timber word appears, and the gap is reported
-- honestly instead of being papered over.

CREATE TABLE activity_category (
    category_key VARCHAR(20)  PRIMARY KEY,
    display_name VARCHAR(60)  NOT NULL,
    description  TEXT         NOT NULL,
    -- Tie-break when two categories score equally on a phrase. Lower wins.
    -- Ordered so that the more physically committing reading is preferred: an
    -- ambiguous "set the trap" should be HUNT before it is CRAFT.
    precedence   SMALLINT     NOT NULL
);

INSERT INTO activity_category (category_key, display_name, description, precedence) VALUES
('HUNT',      'Hunting and fishing',   'Pursuing, trapping, killing, and butchering animals.',                       1),
('ACQUIRE',   'Acquisition',           'Taking raw material the world already offers: flora, minerals, water, ash.', 2),
('PROCESS',   'Material processing',   'Transforming raw stock into usable material. Input and output differ in kind.', 3),
('CRAFT',     'Crafting',              'Assembling processed material into a portable finished object.',             4),
('CONSTRUCT', 'Construction',          'Raising or extending a structure fixed to a site.',                          5),
('MAINTAIN',  'Maintenance',           'Restoring or preserving the condition of something that already exists.',    6),
('INHABIT',   'Inhabiting',            'Sleeping, eating, drinking, warming, cooking, storing — living in a place.', 7),
('RECORD',    'Recording',             'Writing, sketching, mapping, archiving — committing knowledge to a surface.',8),
('OBSERVE',   'Observation',           'Looking, listening, inspecting, surveying. Perception without alteration.',   9),
('MOVE',      'Movement',              'Travelling between places.',                                                10);

-- The vocabulary the classifier scores against. A term may legitimately belong to
-- several categories -- "dress" is PROCESS on a hide and CONSTRUCT on a stone --
-- so this is deliberately not a unique index on term. Weight separates a term
-- that all but decides the category (weight 3) from one that merely leans (1).
CREATE TABLE category_term (
    category_key VARCHAR(20) NOT NULL REFERENCES activity_category(category_key),
    term         VARCHAR(40) NOT NULL,
    weight       SMALLINT    NOT NULL DEFAULT 1,
    PRIMARY KEY (category_key, term),
    CONSTRAINT category_term_weight_check CHECK (weight BETWEEN 1 AND 3),
    -- Terms are matched whole-word against normalised text, so they must already
    -- be normalised themselves: lowercase, no punctuation, single spaces.
    CONSTRAINT category_term_normalised_check CHECK (term = lower(term) AND term !~ '[^a-z0-9 ]' AND term !~ '  ')
);
CREATE INDEX category_term_lookup_idx ON category_term(term);

INSERT INTO category_term (category_key, term, weight) VALUES
('ACQUIRE','gather',2),('ACQUIRE','collect',2),('ACQUIRE','forage',3),('ACQUIRE','pick',1),
('ACQUIRE','harvest',2),('ACQUIRE','dig',2),('ACQUIRE','quarry',3),('ACQUIRE','mine',3),
('ACQUIRE','prospect',3),('ACQUIRE','ore',2),('ACQUIRE','scavenge',2),('ACQUIRE','fetch',1),
('ACQUIRE','strip bark',3),('ACQUIRE','fell',3),('ACQUIRE','draw water',3),

('HUNT','hunt',3),('HUNT','stalk',3),('HUNT','ambush',2),('HUNT','snare',3),('HUNT','trap',2),
('HUNT','fish',3),('HUNT','angle',1),('HUNT','spear',2),('HUNT','shoot',2),('HUNT','butcher',3),
('HUNT','skin',2),('HUNT','bait',3),('HUNT','lure',2),('HUNT','track',2),('HUNT','kill',2),
('HUNT','slaughter',3),('HUNT','gut',2),

('PROCESS','tan',3),('PROCESS','ret',3),('PROCESS','split',2),('PROCESS','knap',3),
('PROCESS','shape',2),('PROCESS','dress',1),('PROCESS','render',2),('PROCESS','leach',3),
('PROCESS','twist',2),('PROCESS','weave',2),('PROCESS','smoke',2),('PROCESS','dry',2),
('PROCESS','cure',2),('PROCESS','grind',2),('PROCESS','soak',2),('PROCESS','boil',2),
('PROCESS','temper',2),('PROCESS','season',1),('PROCESS','flesh',2),('PROCESS','scrape',2),
('PROCESS','plane',2),('PROCESS','hew',3),('PROCESS','saw',2),('PROCESS','brine',3),
('PROCESS','salt',2),('PROCESS','fillet',3),('PROCESS','debark',3),

('CRAFT','craft',3),('CRAFT','make',1),('CRAFT','assemble',2),('CRAFT','carve',2),
('CRAFT','sew',3),('CRAFT','stitch',3),('CRAFT','haft',3),('CRAFT','fashion',2),
('CRAFT','fit',1),('CRAFT','string',2),('CRAFT','bind',1),('CRAFT','lash',2),
('CRAFT','glue',2),('CRAFT','fletch',3),('CRAFT','nock',3),

('CONSTRUCT','construct',3),('CONSTRUCT','erect',3),('CONSTRUCT','raise',2),('CONSTRUCT','frame',2),
('CONSTRUCT','roof',3),('CONSTRUCT','thatch',3),('CONSTRUCT','wall',2),('CONSTRUCT','floor',2),
('CONSTRUCT','foundation',3),('CONSTRUCT','weatherproof',3),('CONSTRUCT','flashing',2),
('CONSTRUCT','joinery',3),('CONSTRUCT','rafter',3),('CONSTRUCT','ridge',2),('CONSTRUCT','post',1),
('CONSTRUCT','beam',2),('CONSTRUCT','sill',2),('CONSTRUCT','stud',2),('CONSTRUCT','build',2),
('CONSTRUCT','pitch the roof',3),

('MAINTAIN','repair',3),('MAINTAIN','mend',3),('MAINTAIN','patch',2),('MAINTAIN','sharpen',3),
('MAINTAIN','maintain',3),('MAINTAIN','reinforce',2),('MAINTAIN','replace',2),('MAINTAIN','oil',1),

('INHABIT','sleep',3),('INHABIT','rest',2),('INHABIT','eat',3),('INHABIT','drink',3),
('INHABIT','cook',3),('INHABIT','warm',2),('INHABIT','shelter',1),('INHABIT','store',2),
('INHABIT','tend the fire',3),('INHABIT','add fuel',3),('INHABIT','light a fire',3),

('RECORD','write',3),('RECORD','sketch',3),('RECORD','map',2),('RECORD','record',2),
('RECORD','document',2),('RECORD','inscribe',3),('RECORD','archive',2),('RECORD','note',1),
('RECORD','draw a',2),

('OBSERVE','look',2),('OBSERVE','watch',2),('OBSERVE','observe',3),('OBSERVE','inspect',3),
('OBSERVE','examine',3),('OBSERVE','survey',3),('OBSERVE','listen',2),('OBSERVE','smell',2),
('OBSERVE','study',2),('OBSERVE','scan',2),

('MOVE','travel',3),('MOVE','walk',2),('MOVE','head',1),('MOVE','climb',2),
('MOVE','swim',2),('MOVE','cross',2),('MOVE','return',1),('MOVE','follow',1),('MOVE','journey',2);

-- Verbs the first positive-control pass proved were missing. "Form a vessel from
-- the clay" matched no term at all, and "process the resin to make pitch" was
-- pulled to CRAFT by "make" because no PROCESS verb in it was known. Both are the
-- false-negative direction: not a wrong answer, but a needless Architect call for
-- something the foundation already knows how to do, which is precisely the cost
-- this layer exists to avoid.
INSERT INTO category_term (category_key, term, weight) VALUES
('PROCESS','process',2),('PROCESS','refine',2),('PROCESS','reduce',1),('PROCESS','extract',2),
('PROCESS','strip',2),('PROCESS','pound',2),('PROCESS','crush',2),('PROCESS','sift',2),
('CRAFT','form',2),('CRAFT','mold',2),('CRAFT','mould',2),('CRAFT','shape into',2),
('CRAFT','weave into',2),('CRAFT','attach',1),('CRAFT','join',1),
-- "Cut" reads as acquisition ("cut reeds") but in practice almost always names a
-- processing step, and FELL and STRIP already carry the acquisition sense. Filed
-- under ACQUIRE it pulled "cut a leather cord from the tanned leather" away from
-- PROCESS and lost it.
('ACQUIRE','take',1),('PROCESS','cut',1),('CONSTRUCT','lay',1),('CONSTRUCT','fix',1);

-- Axis one: every matchable process declares the category it belongs to. Added
-- nullable, backfilled, then made NOT NULL in this same migration -- a process
-- missed by the backfill fails the migration here rather than silently ceasing
-- to match at runtime, which is the failure mode this whole change exists to end.
ALTER TABLE material_process ADD COLUMN category_key VARCHAR(20) REFERENCES activity_category(category_key);

UPDATE material_process SET category_key = 'PROCESS' WHERE process_key IN (
    'timber_from_log','split_planks','shape_components','reinforce_timber','dress_foundation',
    'dress_construction','knap_tool_stone','twist_cordage','weave_textile','ret_nettle',
    'tan_hide','cut_leather_cord','leach_lye','render_pitch','fire_vessel');
UPDATE material_process SET category_key = 'ACQUIRE' WHERE process_key IN ('gather_ash');
UPDATE material_process SET category_key = 'CRAFT' WHERE process_key IN (
    'carve_needle','form_vessel','weave_large_basket','haft_stone_axe');

ALTER TABLE material_process ALTER COLUMN category_key SET NOT NULL;

-- Axis two: the subject gate. A process may only match when the action text names
-- a material it actually handles. This is what separates the collisions that share
-- a category: splitting a fish and splitting a log are both PROCESS, and only the
-- subject tells them apart.
CREATE TABLE process_subject (
    process_key  VARCHAR(60) NOT NULL REFERENCES material_process(process_key),
    subject_term VARCHAR(40) NOT NULL,
    PRIMARY KEY (process_key, subject_term)
);
CREATE INDEX process_subject_term_idx ON process_subject(subject_term);

-- Derived rather than hand-listed, so a process cannot drift out of agreement with
-- its own inputs. Both the item key and the display name contribute words: the key
-- carries the canonical noun (timber_log -> timber, log) and the display name
-- carries how a person would actually say it.
INSERT INTO process_subject (process_key, subject_term)
SELECT DISTINCT x.process_key, w.word
FROM (
    SELECT process_key, item_key FROM material_process_input
    UNION SELECT process_key, item_key FROM material_process_input_group
    UNION SELECT process_key, output_item_key FROM material_process
) x
JOIN item_definition d ON d.item_key = x.item_key
CROSS JOIN LATERAL (
    SELECT regexp_split_to_table(x.item_key, '_') AS word
    UNION
    SELECT regexp_split_to_table(lower(d.display_name), '[^a-z]+')
) w
WHERE length(w.word) >= 3
  -- Words that describe every material equally identify none of them.
  AND w.word NOT IN ('the','and','for','raw','new','old','one','two','cut','set',
                     'made','from','with','into','unit','item','piece','small','large')
ON CONFLICT DO NOTHING;

-- A handful of synonyms a player would plausibly use that no item key contains.
INSERT INTO process_subject (process_key, subject_term) VALUES
('timber_from_log','tree'),('timber_from_log','trunk'),('timber_from_log','wood'),
('split_planks','wood'),('split_planks','board'),('split_planks','lumber'),
('shape_components','wood'),('shape_components','stave'),('shape_components','billet'),
('reinforce_timber','wood'),('reinforce_timber','beam'),
('dress_foundation','rock'),('dress_construction','rock'),('knap_tool_stone','rock'),
('knap_tool_stone','flint'),('knap_tool_stone','core'),
('twist_cordage','rope'),('twist_cordage','string'),('twist_cordage','twine'),
('weave_textile','cloth'),('weave_textile','fabric'),
('ret_nettle','nettle'),('ret_nettle','stalk'),
('tan_hide','skin'),('tan_hide','pelt'),('tan_hide','leather'),
('cut_leather_cord','leather'),('cut_leather_cord','thong'),
('gather_ash','hearth'),('gather_ash','fire'),
('leach_lye','ash'),('leach_lye','alkali'),
('render_pitch','resin'),('render_pitch','sap'),('render_pitch','tar'),
('carve_needle','bone'),('carve_needle','antler'),
('form_vessel','clay'),('form_vessel','pot'),('fire_vessel','clay'),('fire_vessel','pot'),
('weave_large_basket','basket'),('weave_large_basket','vine'),('weave_large_basket','withy'),
('haft_stone_axe','axe'),('haft_stone_axe','handle'),('haft_stone_axe','head')
ON CONFLICT DO NOTHING;

-- Every matchable process must be reachable through the subject gate. One with no
-- subject terms can never match anything, which is the same silent-death failure
-- the NOT NULL above guards against on the other axis.
DO $$
DECLARE bare INT;
BEGIN
    SELECT COUNT(*) INTO bare FROM material_process mp
    WHERE NOT EXISTS (SELECT 1 FROM process_subject s WHERE s.process_key = mp.process_key);
    IF bare > 0 THEN
        RAISE EXCEPTION 'V54: % process(es) have no subject terms and could never match.', bare;
    END IF;
END $$;

-- With the subject gate in place, keywords no longer have to carry the noun, and
-- they should not: V52 wrote them as adjacent phrases ("form pot", "make a
-- vessel", "cut the log"), so "form a vessel from the clay" matched nothing at all
-- because the player put a word between the verb and its object. Fifty-odd of the
-- keywords across all twenty processes had this shape. That is a false negative on
-- ordinary phrasing -- an Architect call spent on something already built.
--
-- The phrases are kept, since an exact phrase is still the strongest signal, and a
-- bare verb is added alongside each. The verb alone is safe now in a way it was not
-- before, because category and subject must also agree: "cut" belongs to both
-- split_planks and cut_leather_cord, and only the subject decides which.
UPDATE material_process SET keywords = keywords || ',' || v.verbs FROM (VALUES
 ('timber_from_log',   'square,hew,dress'),
 ('split_planks',      'split,rive,saw,cut'),
 ('shape_components',  'carve,trim,shape,plane,taper'),
 ('reinforce_timber',  'lash,bind,reinforce'),
 ('dress_foundation',  'dress,square'),
 ('dress_construction','dress,break,course'),
 ('knap_tool_stone',   'knap,strike,flake'),
 ('twist_cordage',     'twist,spin,ply'),
 ('weave_textile',     'weave'),
 ('ret_nettle',        'ret,soak,strip'),
 ('tan_hide',          'tan,cure,soak'),
 ('cut_leather_cord',  'cut,slice'),
 ('gather_ash',        'gather,scoop,rake,collect'),
 ('leach_lye',         'leach'),
 ('render_pitch',      'render,melt,boil,process'),
 ('carve_needle',      'carve,whittle'),
 ('form_vessel',       'form,coil,shape,make'),
 ('fire_vessel',       'fire,bake,harden'),
 ('weave_large_basket','weave,plait'),
 ('haft_stone_axe',    'haft,fit,mount,make')
) AS v(pk, verbs) WHERE material_process.process_key = v.pk;

-- The resolution rule this table set is built for, recorded here because the
-- Java side must not drift from it:
--
--   1. Classify the text: sum category_term weights per category, highest wins,
--      activity_category.precedence breaks ties.
--   2. A process may match only if ALL of: its category equals the classified
--      category; one of its keywords appears whole-word in the text; one of its
--      subject terms appears whole-word in the text.
--   3. If NO category term matches the text at all, the classification is NULL and
--      step 2 drops the category condition rather than matching nothing. An
--      unrecognised verb is ignorance of the vocabulary, not evidence that the
--      action belongs to no category, and silently matching nothing there would
--      spend an Architect call on something the foundation already covers.
--
-- Whole-word means space-padded comparison on normalised text (' '||text||' '
-- LIKE '% '||term||' %'), which is what keeps "forest" from matching "ore".

INSERT INTO domain_registry (domain_key, display_name, description, introduced_in_migration, origin)
VALUES ('activity_routing','Activity Routing','Categories and subject terms that scope which internal process an action may resolve to, so a lexical coincidence cannot stand in for a real match.','V54','PREBUILT')
ON CONFLICT (domain_key) DO NOTHING;
