-- V128 — mass-balance correction, #245 part C (the one forced, no-guess case).
--
-- grind_bone_awl declared output_max=2 at 25g/awl from a >=48g bone splinter: 2 awls = 50g > 48g of
-- input, i.e. grinding two awls that together outweigh the bone they were ground from — physically
-- impossible (grinding only removes material). Cap the yield to one awl (25g <= 48g), which conserves
-- mass and is what a single splinter realistically yields. This needs no mass guess — conservation
-- forces output_max=1.
--
-- The other 14 mass-balance violations each require an intent decision (lower the output mass vs raise
-- an understated input mass) and several cascade through downstream chains (peel_root -> wash_root ->
-- cook_root_stew; grind_pigment -> dye_cloth; carve_point -> assemble_arrows). Fixing them by simply
-- lowering outputs would make tools/food unrealistically light, against the real-world-simulation
-- mandate. They are enumerated with per-process guidance in #245 for focused, realism-aware work.
UPDATE material_process SET output_max = 1
WHERE process_key = 'grind_bone_awl' AND output_max > 1;
