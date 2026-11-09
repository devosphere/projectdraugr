-- V462: a recipe advertises a phrase its own subject gate must refuse (#37, #99).
--
-- ProcessMatcher's rule is that THREE things must agree before a process runs: the classified category, one of
-- the process's keywords, and one of its SUBJECT TERMS -- all three whole-word in the sentence. So a keyword
-- whose own words name no subject term of its own process can never match on its own. It is a phrase the
-- catalogue promises an answer to and then turns away.
--
-- Audited over every VERIFIED process: of 2,773 declared keywords, 114 multi-word phrases name no subject term
-- of the process that declares them. Sixteen were measured live against a booted backend on a from-nothing
-- database, and fourteen of the sixteen reached NOTHING AT ALL:
--
--   leather boots      UNKNOWN        cuir bouilli       UNKNOWN        half lap           UNKNOWN
--   pressure flake     UNKNOWN        brain tan          UNKNOWN        box joint          UNKNOWN
--   saddle notch       UNKNOWN        leach tannin       UNKNOWN        tanning            UNKNOWN
--   reverse twist      UNKNOWN        winter ration      UNKNOWN        cooking stone      UNKNOWN
--   bind the fletching UNKNOWN        cut a mortise      UNKNOWN
--
-- while `fur mittens` and `bark bracers` -- the same shape of phrase, on processes that happen to carry the
-- subject term already -- worked. That contrast is the whole diagnosis: nothing is wrong with the phrasing or
-- the mechanic, only with the two halves of one recipe disagreeing about a word.
--
-- THE SAME DEFECT AS V459's `shape sling stones`, found eleven more times. There the keyword said the plural
-- and the subject said the singular. Here the keyword says `mortise` and the subject says `mortised`; the
-- keyword says `bind the fletching` and the subject says `fletched`; the keyword says `leach the bark` while
-- the only subject the process declares at all is `mordant`.
--
-- WHAT IS ADDED, BY RULE RATHER THAN BY TASTE: for each such keyword, its own content words become subject
-- terms of its own process -- excluding articles and prepositions, and excluding the making verbs, so a subject
-- term stays a word about the MATERIAL.
--
-- AND ONE EXCLUSION THAT MATTERS: a term that is also a bare one-word keyword of the same process is NOT added.
-- That pair would make the keyword satisfy its own subject gate and leave the gate vacuous -- `strike` is a
-- bare keyword of knap_tool_stone, so adding it as a subject would have let `strike a spark` reach the
-- knapping. `strike` and `flake` are both held back for exactly that reason.
--
-- A subject term can only ever cause a match when a keyword of the SAME process already matches the sentence,
-- so the reach of this file is bounded by those 114 phrases. Verified by replaying all 485 input-bearing
-- recipes before and after.

INSERT INTO process_subject (process_key, subject_term) VALUES
  ('assemble_arrows', 'point'),
  ('assemble_frame', 'draw'),
  ('carve_bone_points', 'barbed'),
  ('carve_bone_points', 'point'),
  ('carve_snare_trigger', 'bent'),
  ('carve_snare_trigger', 'stick'),
  ('carve_snare_trigger', 'trigger'),
  ('char_post', 'surface'),
  ('crack_walnut', 'walnuts'),
  ('dovetail_corner', 'box'),
  ('dovetail_corner', 'joint'),
  ('dress_ashlar', 'stone'),
  ('dress_stone_griddle', 'cooking'),
  ('dress_stone_griddle', 'stone'),
  ('edge_join_boards', 'edge'),
  ('fat_tan_hide', 'brain'),
  ('fire_clay_trinkets', 'beads'),
  ('fire_clay_trinkets', 'clay'),
  ('fire_clay_trinkets', 'harden'),
  ('fire_clay_trinkets', 'kiln'),
  ('fire_clay_trinkets', 'trinkets'),
  ('fire_vessel', 'pottery'),
  ('gather_ash', 'ashes'),
  ('harden_leather', 'bouilli'),
  ('harden_leather', 'cuir'),
  ('knap_arrowheads', 'pressure'),
  ('knap_tool_stone', 'flakes'),
  ('lap_joint_planks', 'cross'),
  ('lap_joint_planks', 'half'),
  ('lap_joint_planks', 'joint'),
  ('lap_joint_planks', 'lap'),
  ('lay_out_hide', 'cuts'),
  ('lay_out_hide', 'planning'),
  ('make_antler_flaker', 'flaking'),
  ('make_antler_flaker', 'tool'),
  ('make_bark_bracer_left', 'bark'),
  ('make_bark_bracer_left', 'bracers'),
  ('make_bark_bracer_right', 'bark'),
  ('make_bark_bracer_right', 'bracers'),
  ('make_bark_shin_guard_left', 'bark'),
  ('make_bark_shin_guard_left', 'guards'),
  ('make_bark_shin_guard_left', 'shin'),
  ('make_bark_shin_guard_right', 'bark'),
  ('make_bark_shin_guard_right', 'guards'),
  ('make_bark_shin_guard_right', 'shin'),
  ('make_boiling_stones', 'boiler'),
  ('make_boiling_stones', 'pot'),
  ('make_boiling_stones', 'stones'),
  ('make_digging_stick', 'harden'),
  ('make_digging_stick', 'point'),
  ('make_fibre_knee_wrap_left', 'fibre'),
  ('make_fibre_knee_wrap_left', 'knee'),
  ('make_fibre_knee_wrap_left', 'wraps'),
  ('make_fibre_knee_wrap_right', 'fibre'),
  ('make_fibre_knee_wrap_right', 'knee'),
  ('make_fibre_knee_wrap_right', 'wraps'),
  ('make_fur_boot_left', 'boots'),
  ('make_fur_boot_left', 'fur'),
  ('make_fur_boot_right', 'boots'),
  ('make_fur_boot_right', 'fur'),
  ('make_fur_mitten_left', 'fur'),
  ('make_fur_mitten_right', 'fur'),
  ('make_fur_sock_left', 'fur'),
  ('make_fur_sock_left', 'socks'),
  ('make_fur_sock_right', 'fur'),
  ('make_fur_sock_right', 'socks'),
  ('make_hide_boot_left', 'boots'),
  ('make_hide_boot_left', 'hide'),
  ('make_hide_boot_right', 'boots'),
  ('make_hide_boot_right', 'hide'),
  ('make_hide_glove_left', 'hide'),
  ('make_hide_glove_right', 'hide'),
  ('make_hide_sock_left', 'hide'),
  ('make_hide_sock_left', 'socks'),
  ('make_hide_sock_right', 'hide'),
  ('make_hide_sock_right', 'socks'),
  ('make_javelin', 'spear'),
  ('make_leather_boot_left', 'boots'),
  ('make_leather_boot_left', 'leather'),
  ('make_leather_boot_right', 'boots'),
  ('make_leather_boot_right', 'leather'),
  ('make_leather_bracer_left', 'bracers'),
  ('make_leather_bracer_left', 'leather'),
  ('make_leather_bracer_right', 'bracers'),
  ('make_leather_bracer_right', 'leather'),
  ('make_leather_elbow_guard_left', 'elbow'),
  ('make_leather_elbow_guard_left', 'guards'),
  ('make_leather_elbow_guard_left', 'leather'),
  ('make_leather_elbow_guard_right', 'elbow'),
  ('make_leather_elbow_guard_right', 'guards'),
  ('make_leather_elbow_guard_right', 'leather'),
  ('make_leather_gaiter_left', 'gaiters'),
  ('make_leather_gaiter_left', 'leather'),
  ('make_leather_gaiter_right', 'gaiters'),
  ('make_leather_gaiter_right', 'leather'),
  ('make_leather_glove_left', 'leather'),
  ('make_leather_glove_right', 'leather'),
  ('make_leather_knee_guard_left', 'guards'),
  ('make_leather_knee_guard_left', 'knee'),
  ('make_leather_knee_guard_left', 'leather'),
  ('make_leather_knee_guard_right', 'guards'),
  ('make_leather_knee_guard_right', 'knee'),
  ('make_leather_knee_guard_right', 'leather'),
  ('make_leather_pauldron_left', 'leather'),
  ('make_leather_pauldron_left', 'pauldrons'),
  ('make_leather_pauldron_right', 'leather'),
  ('make_leather_pauldron_right', 'pauldrons'),
  ('make_leather_shin_guard_left', 'guards'),
  ('make_leather_shin_guard_left', 'leather'),
  ('make_leather_shin_guard_left', 'shin'),
  ('make_leather_shin_guard_right', 'guards'),
  ('make_leather_shin_guard_right', 'leather'),
  ('make_leather_shin_guard_right', 'shin'),
  ('make_leather_thigh_guard_left', 'guards'),
  ('make_leather_thigh_guard_left', 'leather'),
  ('make_leather_thigh_guard_left', 'thigh'),
  ('make_leather_thigh_guard_right', 'guards'),
  ('make_leather_thigh_guard_right', 'leather'),
  ('make_leather_thigh_guard_right', 'thigh'),
  ('make_pack_saddle', 'frame'),
  ('make_pemmican', 'food'),
  ('make_pemmican', 'ration'),
  ('make_pemmican', 'travel'),
  ('make_pemmican', 'winter'),
  ('make_rawhide_bracer_left', 'bracers'),
  ('make_rawhide_bracer_left', 'rawhide'),
  ('make_rawhide_bracer_right', 'bracers'),
  ('make_rawhide_bracer_right', 'rawhide'),
  ('make_rawhide_knee_guard_left', 'guards'),
  ('make_rawhide_knee_guard_left', 'knee'),
  ('make_rawhide_knee_guard_left', 'rawhide'),
  ('make_rawhide_knee_guard_right', 'guards'),
  ('make_rawhide_knee_guard_right', 'knee'),
  ('make_rawhide_knee_guard_right', 'rawhide'),
  ('make_rawhide_shin_guard_left', 'guards'),
  ('make_rawhide_shin_guard_left', 'rawhide'),
  ('make_rawhide_shin_guard_left', 'shin'),
  ('make_rawhide_shin_guard_right', 'guards'),
  ('make_rawhide_shin_guard_right', 'rawhide'),
  ('make_rawhide_shin_guard_right', 'shin'),
  ('make_rawhide_thigh_guard_left', 'guards'),
  ('make_rawhide_thigh_guard_left', 'rawhide'),
  ('make_rawhide_thigh_guard_left', 'thigh'),
  ('make_rawhide_thigh_guard_right', 'guards'),
  ('make_rawhide_thigh_guard_right', 'rawhide'),
  ('make_rawhide_thigh_guard_right', 'thigh'),
  ('make_reed_bracer_left', 'bracers'),
  ('make_reed_bracer_left', 'reed'),
  ('make_reed_bracer_right', 'bracers'),
  ('make_reed_bracer_right', 'reed'),
  ('make_reed_knee_pad_left', 'knee'),
  ('make_reed_knee_pad_left', 'pads'),
  ('make_reed_knee_pad_left', 'reed'),
  ('make_reed_knee_pad_right', 'knee'),
  ('make_reed_knee_pad_right', 'pads'),
  ('make_reed_knee_pad_right', 'reed'),
  ('make_reed_sandal_pair', 'reed'),
  ('make_reed_sandal_pair', 'sandals'),
  ('notch_log', 'saddle'),
  ('prepare_tannin_mordant', 'bark'),
  ('prepare_tannin_mordant', 'tannin'),
  ('preserve_berries', 'berry'),
  ('press_clay_seal', 'clay'),
  ('press_clay_seal', 'seals'),
  ('reinforce_timber', 'planks'),
  ('scarf_joint', 'joint'),
  ('season_timber', 'sticker'),
  ('shape_clay_bead', 'beads'),
  ('shape_clay_bead', 'clay'),
  ('shell_hazelnut', 'hazelnuts'),
  ('split_fish', 'drying'),
  ('thread_trinket_cord', 'trinkets'),
  ('turn_dowels', 'stock'),
  ('twist_bowstring', 'reverse'),
  ('weave_burden_basket', 'carrying'),
  ('weave_burden_basket', 'frame'),
  ('whittle_shavings', 'stick')
-- Idempotent on the pair the table is keyed by, so a re-run adds nothing.
ON CONFLICT (process_key, subject_term) DO NOTHING;

-- AND FIVE THE RULE ABOVE HELD BACK AND SHOULD NOT HAVE. The exclusion exists to stop a bare keyword
-- satisfying its own subject gate, and for a generic VERB that is exactly right: `strike` is a bare keyword of
-- knap_tool_stone, and as a subject term it would have let any sentence that strikes anything at all reach the
-- knapping. But the rule cannot tell a verb from a noun, and it caught five specific NOUNS that name nothing
-- except the work itself -- so five of the phrases this file set out to fix would have stayed broken:
--
--   cut a mortise      needs `mortise`        cut a tenon      needs `tenon`
--   bind the fletching needs `fletching`      saddle notch     needs `notch`
--   steep in brine     needs `brine`
--
-- A sentence carrying `mortise` is about mortising; there is nothing else in this world it could be about.
--
-- The ones the exclusion rightly kept out need no help anyway, because another word of the same phrase already
-- carries them: `strike flakes` by `flakes`, `pressure flake` by `pressure`, `hide gloves` by `hide`,
-- `fur mittens` by `fur`, `berry preserve` by `berry`, `scarf joint` by `joint`. Two are left refused on
-- purpose: `preserve` would have taken `preserve the meat` away from the meat, and a `scarf` is a garment in
-- this catalogue as well as a joint in a beam.
INSERT INTO process_subject (process_key, subject_term) VALUES
  ('brine_fish', 'brine'),
  ('cut_mortise', 'mortise'),
  ('cut_tenon', 'tenon'),
  ('fletch_arrows', 'fletching'),
  ('notch_log', 'notch')
ON CONFLICT (process_key, subject_term) DO NOTHING;
