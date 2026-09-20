-- #122 (epic #109) — the dead of a people keep what was theirs.
--
-- Two things were missing for a native person to be more than a body on the ground. First, they owned nothing: a
-- fisher had no hook, a maker no awl, so "recover belongings" had nothing to recover and "their possessions remain
-- owned objects" was a rule about an empty set. Second, nothing said what happens to those objects when they die.
--
-- This gives every living adult of a people one real thing of their own, by the work they do — the same item
-- definitions anyone else in the world uses, owned by their own world object, with a transition that says where it
-- came from. Death does not make it ownerless: the community's daily step has their kin gather it up after a few
-- days (NativeCommunityService), and until then it is still theirs, so taking it is robbing the dead (#114).

INSERT INTO world_object (id, object_type, display_name, current_owner_id)
SELECT gen_random_uuid(), 'ITEM',
       CASE n.role WHEN 'FISHER' THEN 'Bone fish hook' WHEN 'MAKER' THEN 'Bone awl'
                   WHEN 'FORAGER' THEN 'Woven basket' WHEN 'HUNTER' THEN 'Bone knife'
                   WHEN 'HEADSPERSON' THEN 'Bone knife' ELSE 'Bone needle' END,
       n.object_id
  FROM native_individual n JOIN world_object w ON w.id = n.object_id
 WHERE n.condition <> 'DEAD' AND n.life_stage <> 'CHILD' AND w.lifecycle_state = 'ACTIVE'
   AND NOT EXISTS (SELECT 1 FROM world_object o WHERE o.current_owner_id = n.object_id);

INSERT INTO item_instance (object_id, item_key)
SELECT o.id, CASE o.display_name WHEN 'Bone fish hook' THEN 'bone_fish_hook' WHEN 'Bone awl' THEN 'bone_awl'
                                 WHEN 'Woven basket' THEN 'woven_basket' WHEN 'Bone knife' THEN 'bone_knife'
                                 ELSE 'bone_needle' END
  FROM world_object o JOIN native_individual n ON n.object_id = o.current_owner_id
 WHERE o.object_type = 'ITEM' AND NOT EXISTS (SELECT 1 FROM item_instance i WHERE i.object_id = o.id);

INSERT INTO object_transition (object_id, occurred_at, transition_type, payload)
SELECT o.id, now(), 'MADE_BY_THEIR_OWN_HANDS', jsonb_build_object('individual', n.object_id::text)
  FROM world_object o JOIN native_individual n ON n.object_id = o.current_owner_id
 WHERE o.object_type = 'ITEM' AND NOT EXISTS (SELECT 1 FROM object_transition t WHERE t.object_id = o.id);
