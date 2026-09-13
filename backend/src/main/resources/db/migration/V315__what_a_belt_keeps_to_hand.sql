-- #134 — what a belt keeps to hand.
--
-- V248 made two leather girdles: a tool girdle "fitted with a row of tool loops" and a pouch girdle "hung with small
-- pouches". Both were craftable and wearable, and neither did what it said.
--
--  * The tool girdle. ChronicleActionService.wearsUtilityBelt names three keys — utility_belt, cordage_tool_belt,
--    reed_tool_loop — as the tool carriers whose loops shorten bench work. The leather one, the best of them, was not
--    among them: a second list the catalogue grew past.
--  * The pouch girdle. A made container gets its capacity from container_capacity_default when it is made; the
--    single leather pouch has a row there, the girdle hung with several of them did not, so it held nothing at all.
--
-- tool_carrier declares what keeps tools to hand, and wearsUtilityBelt reads it. The pouch girdle gets capacity.

CREATE TABLE tool_carrier (
    item_key  VARCHAR(100) PRIMARY KEY REFERENCES item_definition(item_key),
    notes     TEXT NOT NULL
);

COMMENT ON TABLE tool_carrier IS
  'Worn gear that keeps tools to hand, so bench work loses less time to setting tools down and finding them (#57, #134). '
  'Read by ChronicleActionService.wearsUtilityBelt.';

INSERT INTO tool_carrier (item_key, notes) VALUES
  ('utility_belt',         'The first tool belt (#57).'),
  ('cordage_tool_belt',    'Cordage loops for the tools in use.'),
  ('reed_tool_loop',       'A reed loop that holds a tool or two.'),
  ('leather_utility_belt', 'A leather girdle with a row of tool loops (V248) — the best of them, and never read.');

-- A girdle hung with several small pouches holds a little more than one leather pouch (2500 g / 1800 ml),
-- and far less than a pack.
INSERT INTO container_capacity_default (item_key, max_mass_grams, max_volume_ml)
VALUES ('leather_pouch_belt', 3000, 2400)
ON CONFLICT (item_key) DO NOTHING;

DO $$
DECLARE bad text;
BEGIN
  -- A tool carrier that cannot be worn at the waist would keep nothing to hand.
  SELECT string_agg(tc.item_key, ', ') INTO bad FROM tool_carrier tc
   WHERE NOT EXISTS (SELECT 1 FROM item_equipment_compatibility c WHERE c.item_key = tc.item_key AND c.body_position = 'WAIST');
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V315: tool carriers not wearable at the waist: %', bad; END IF;

  -- Nor one nobody can make.
  SELECT string_agg(tc.item_key, ', ') INTO bad FROM tool_carrier tc
   WHERE NOT EXISTS (SELECT 1 FROM item_source s WHERE s.item_key = tc.item_key);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V315: tool carriers nobody can obtain: %', bad; END IF;

  -- Anything named a pouch or pouch belt, apart from the unworn bait pouch, must hold something.
  SELECT string_agg(d.item_key, ', ') INTO bad FROM item_definition d
   WHERE d.item_key ~ 'pouch' AND d.item_key <> 'bait_pouch'
     AND EXISTS (SELECT 1 FROM item_source s WHERE s.item_key = d.item_key)
     AND NOT EXISTS (SELECT 1 FROM container_capacity_default x WHERE x.item_key = d.item_key);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V315: pouches that hold nothing: %', bad; END IF;
END $$;
