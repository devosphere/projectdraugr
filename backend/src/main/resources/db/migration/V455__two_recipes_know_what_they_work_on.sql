-- V455: two recipes that advertise a phrase and do not recognise the material in it (#37, #99).
--
-- The companion to V454, found by the same method from the other end. V454 came from auditing which recipe
-- answered a phrase; this came from auditing the phrases NOTHING answers. Of the sixty-six keywords a Java
-- intent in ChronicleActionService claimed before the catalogue was asked, eight resolve to nothing even with
-- the whole catalogue in play, so handing those to the catalogue would trade a wrong answer for no answer.
-- Four of the eight are bare verbs ("fix", "dress", "carve", "reinforce") and correctly name no material.
-- Two of the remaining four are fixed here. The other two are over-promises and are explained at the bottom.
--
-- ProcessMatcher needs a keyword AND a subject term to both appear in the sentence, and that second gate is
-- derived from each process's own inputs and outputs so a recipe cannot drift out of agreement with itself.
-- These two drifted anyway:
--
--   dress_construction  advertises "dress rubble" and its subject terms are construction, field, medium,
--                       rock, stone. Rubble is the very thing being dressed, and the recipe did not know
--                       the word, so its own promise could not pass its own gate.
--   mordant_dye_cloth   advertises "fix the dye", "fast dye", "colourfast dye", "colorfast dye" and
--                       "set the dye fast" — FIVE phrases — and its only subject term was "mordant". Not one
--                       of those five names a mordant, so not one of them could ever reach the recipe. One
--                       missing word, five dead promises.
--
-- Measured over all 2,337 declared keywords before writing this, through the shipped ActivityClassifier.classify
-- and ProcessMatcher.resolveForPlay driven from a dump of this catalogue rather than a re-implementation of
-- their reasoning: these two rows change SIX answers, every one from nothing to the process that declares the
-- keyword. None moves away from its owner and none moves between two strangers.
--
-- A new subject term cannot widen a recipe's reach on its own, which is what makes this safe: the keyword gate
-- still has to pass first, so the blast radius is only sentences that already matched this recipe's own
-- keywords and were then turned away at its subject gate.

INSERT INTO process_subject (process_key, subject_term) VALUES
    -- "dress rubble" -> was reaching nothing; rubble is what a construction stone is dressed FROM
    ('dress_construction', 'rubble'),
    -- "fix the dye" / "fast dye" / "colourfast dye" / "colorfast dye" / "set the dye fast" -> all five dead
    ('mordant_dye_cloth', 'dye')
ON CONFLICT (process_key, subject_term) DO NOTHING;

-- TWO ARE NOT FIXED HERE, AND ON PURPOSE. Both are phrases the catalogue advertises that identify no material
-- the recipe works on, which is a different defect from a missing subject term and is not answerable with data.
-- Recorded so the next audit does not read their silence as this migration having missed them.
--
--   dry_meat     advertises "hang it to dry". A pronoun can never satisfy a subject gate, so no row added here
--                could make that phrase reachable. It wants the work-in-hand reading, not a subject term.
--
--   flesh_hide   advertises "take the flesh off", and its subjects carry "fleshed" but never "flesh". Adding
--                "flesh" WAS tried and then withdrawn on the measurement: "flesh" is also a bare KEYWORD of
--                flesh_hide, so the single word would satisfy both gates and any sentence naming flesh could
--                reach the recipe. Replayed through a running stack, "cut the flesh from the bone" and
--                "carve the flesh" both began answering "Flesh a hide turns on a cutting edge" — a butcher's
--                sentence answered about a hide. That trades no answer for a WRONG answer, which is the worse
--                of the two, so the row was dropped. The honest fix is a narrower keyword on flesh_hide, not
--                a broader subject.
