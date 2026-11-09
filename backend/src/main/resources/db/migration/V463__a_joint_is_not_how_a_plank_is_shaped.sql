-- V463 — a joint is not how a plank is shaped (#37)
--
-- `shape the plank` is a sentence about working a plank, and shape_components is the recipe that does it:
-- it takes a timber_plank and answers to "shape". Measured through the shipped classify() + resolveForPlay():
--
--     shape the plank  ->  TIE[lap_joint_planks, split_planks]     (shape_components dropped)
--
-- Two defects meet in that line. The code half — split_planks answering to the bare material noun "plank" at
-- the same length as a work-word — is settled in ProcessMatcher.resolve. This is the data half.
--
-- V57 gave every joinery recipe the bare family words "cut" and "shape" as filler. They could not fire while the
-- recipes' only subject terms were their own plurals; V462 gave lap_joint_planks and dovetail_corner the subject
-- "plank" (correctly — a plank is what they consume), and from then on the bare "shape" let both claim any
-- sentence about shaping a plank. lap_joint_planks then WON it outright, because its output, lapped_plank, has
-- "plank" in its name and the tie-break credits a sentence for naming what a recipe makes. A Chronicle holding a
-- plank, a chisel and a bench was sent to cut a lap joint and told to find pegs.
--
-- A lap joint and a dovetail are CUT, and both recipes keep "cut" and every phrase of their own ("lap joint",
-- "half lap", "lap the planks", "cross lap"; "dovetail", "dovetail the corner", "box joint", "corner joint").
-- Only the bare "shape" goes. The beam joints (cut_mortise, cut_tenon, scarf_joint) keep theirs: their subjects
-- are beams, so they never contend for a plank, and nothing measured moves through them.
--
-- Removing a word is not removing a mechanic: both recipes stay exactly as runnable as before by every phrase
-- that names them.

UPDATE material_process
   SET keywords = array_to_string(array_remove(string_to_array(keywords, ','), 'shape'), ',')
 WHERE process_key IN ('lap_joint_planks', 'dovetail_corner')
   AND 'shape' = ANY (string_to_array(keywords, ','));
