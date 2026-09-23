-- #37 — "build a shelter" is a sentence, and the world had no answer to it.
--
-- Found by playing. Act two of an ordinary first day, in the rain, at night:
--
--     build a shelter  ->  UNKNOWN
--     "The stuff of it is willing enough; it is the working that will not come."
--
-- There are ten shelters in the catalogue. The Chronicle died of cold the same night.
--
-- WHY NOTHING ANSWERED. The Java lean-to path is reached only by the literal words "lean-to" or "lean to". The
-- staged lean_to assembly answers to "assemble a lean-to shelter in stages" and "staged lean-to". Every other
-- shelter answers to its own name — "debris hut", "bark shelter", "snow shelter". Not one of them answers to the
-- word a person actually uses when they are cold and have no name for what they are about to make.
--
-- WHY THE LEAN-TO AND NOT A QUESTION. "cook some food" (V375) is asked about because four ways of cooking supper
-- are equally apt and the choice is the player's. This is not that: a lean-to is the shelter you build when you
-- have nothing, out of branches and fibre, and every other shelter in the list wants bark, hide, snow, reed or
-- logs the Chronicle may not have. Asking a freezing person to choose between nine things they cannot build is
-- not respect for their agency, it is a menu.
--
-- A player who names a shelter still gets the one they named: these keywords are unqualified, and every other
-- assembly keeps its own. And the prose says what was begun, so nobody is left guessing what they built.

UPDATE assembly_definition
   SET keywords = keywords || ',build a shelter,make a shelter,put up a shelter,rig a shelter,build myself a shelter'
 WHERE assembly_key = 'lean_to'
   AND keywords NOT ILIKE '%build a shelter%';

DO $$
DECLARE n INT;
BEGIN
    SELECT COUNT(*) INTO n FROM assembly_definition
     WHERE keywords ILIKE '%build a shelter%' OR keywords ILIKE '%make a shelter%';
    -- Exactly one, or the generic words would make a cold night a multiple-choice question.
    IF n <> 1 THEN RAISE EXCEPTION 'V376: expected one shelter to answer the generic words, found %', n; END IF;

    SELECT COUNT(*) INTO n FROM assembly_definition WHERE assembly_key='lean_to' AND construction_kind='LEAN_TO';
    IF n <> 1 THEN RAISE EXCEPTION 'V376: the lean_to assembly is not where it was'; END IF;
END $$;
