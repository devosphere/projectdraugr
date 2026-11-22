-- V473: SOMETHING TO LASH IT WITH (GitHub #899).
--
-- Five tool recipes -- the spear, the knife, the hammer, the pickaxe and the hatchet -- each asked the same
-- question, "have I got something to bind this with", and each asked it the same wrong way:
--
--     items.hasAtLeast(chronicle, "field_stone", 1) && items.hasAtLeast(chronicle, "plant_fiber", 1)
--
-- One item key, named as a literal in Java. So a Chronicle who had retted a loose bark strip into soft bast
-- -- which the catalogue makes through ret_bark_strip, and which is the historically documented hafting
-- binding #899 cites -- could haft NOTHING. Not a knife, not a spear, not an axe. They were holding a
-- better lashing than the one the code would accept and the game told them "the stone and loose fiber never
-- settle into a usable edge", a sentence about fiber they did not have and were not using.
--
-- THE SHAPE OF THE ANSWER is lining_material, which already exists and already solves exactly this for
-- stuffing a garment: a small catalogue of what counts, a number saying how good it is, and a note saying
-- why. One table, one reader, and a new binding joins by being inserted rather than by someone remembering
-- to add a literal in five places.
--
-- EVERY ENTRY IS ATTAINABLE, verified against this catalogue rather than assumed:
--
--     plant_fiber      GATHER_FIBER
--     soft_bast_strip  ret_bark_strip
--     fiber_cordage    fourteen processes (twist_cordage, ret_nettle, twist_willow_cordage, ...)
--     animal_sinew     butchering a carcass
--     withy_rope       plait_withy_rope
--     tarred_cordage   tar_cordage
--
-- WHAT IS DELIBERATELY NOT HERE. `simple_cordage_loop` ("Cordage carry loop") and `rawhide` are both
-- flexible and both in reach, and neither is a lashing: the first is a finished carry strap and the second
-- is a hide. Consuming either to haft an axe would be the kind of silent wrong thing this project keeps
-- finding in itself, so they are left out on purpose and said so here.

CREATE TABLE binding_material (
    item_key     VARCHAR(80) PRIMARY KEY REFERENCES item_definition(item_key),
    holds_fast   SMALLINT    NOT NULL,
    notes        TEXT        NOT NULL,
    CONSTRAINT binding_holds_fast_band CHECK (holds_fast BETWEEN 1 AND 10),
    CONSTRAINT binding_needs_a_reason  CHECK (length(btrim(notes)) >= 40)
);

INSERT INTO binding_material (item_key, holds_fast, notes) VALUES
 ('plant_fiber', 3,
  'Loose fibre straight off the stem. It will hold a stone in a haft if it is wound tight enough, and it is the first lashing anyone has.'),
 ('soft_bast_strip', 5,
  'Inner bark retted until it is pliable, then used in long strips. The documented hafting binding: Wola toolmakers seated stone flakes in split handles and bound them with bast.'),
 ('fiber_cordage', 7,
  'Fibre twisted into cordage, which is stronger than the fibre that went into it and does not work loose. The right answer when a haft must survive being struck.'),
 ('animal_sinew', 9,
  'Sinew wrapped wet and left to dry shrinks onto the joint and sets hard. Nothing primitive holds a stone head better.'),
 ('withy_rope', 6,
  'Green withies plaited into rope. Stiffer than cordage and better at holding a bulky head than a fine one.'),
 ('tarred_cordage', 8,
  'Cordage run through tar, which keeps the wet out of the lashing and stops it rotting where it sits against the stone.');
