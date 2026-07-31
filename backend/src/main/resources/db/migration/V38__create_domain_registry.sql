-- The Persistent State Architect's ledger of what the world knows how to be. Per the
-- monotonic-schema decision (DR-0013), when a domain is invented for the first time
-- anywhere in the world's history, the Architect adds its tables ONCE and records the
-- domain here. Every future chronicle then inherits a world where that domain's schema
-- exists — though not the dead chronicle's skill in it. This table is how the Architect
-- knows whether a domain (pottery, smithing, agriculture) has been invented yet, so it
-- adds each domain's schema exactly once and never twice.
--
-- origin distinguishes the domains the developer pre-migrated to seed a playable world
-- (PREBUILT) from those a living civilization reaches and the Architect adds during play
-- (INVENTED). Nothing is ever removed: a domain, once known, is known forever.
CREATE TABLE domain_registry (
    domain_key VARCHAR(60) PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL,
    description TEXT NOT NULL,
    introduced_in_migration VARCHAR(20) NOT NULL,
    origin VARCHAR(20) NOT NULL DEFAULT 'PREBUILT' CHECK (origin IN ('PREBUILT','INVENTED')),
    introduced_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The domains that already exist in the world as of this migration, each attributed to
-- the migration that first gave it schema. These are the foundation a first chronicle
-- awakens into; later domains (pottery, smithing, textiles, agriculture, medicine) are
-- absent until invented, and their INSERT here is part of the migration that adds them.
INSERT INTO domain_registry (domain_key, display_name, description, introduced_in_migration) VALUES
('world_core','World Core','Persistent world, world_object identity, and the immutable transition and event ledgers.','V1'),
('geography','Geography','The grid of chunks the world is laid out across.','V2'),
('ecology','Ecology','Ecological sites that seed living things into the land.','V3'),
('chronicle_lifecycle','Chronicle Lifecycle','Awakening, permanent death, and the death snapshot a life leaves behind.','V4'),
('physiology','Physiology','Hunger, thirst, health, temperature, injury, illness, and the rest of the body state.','V5'),
('action_ledger','Action Ledger','The immutable record of every action a chronicle resolves.','V6'),
('wildlife','Wildlife','Deterministic animal populations, their behaviour, and the carcasses a kill leaves.','V7'),
('items','Items','Physical items, containers, and the provenance every object carries.','V8'),
('equipment','Equipment','What a chronicle wears and wields, attached to the body.','V8'),
('carry_capacity','Carry Capacity','The mass and volume a body can bear at once.','V10'),
('capability','Capability Adaptation','Per-chronicle practiced familiarity that makes terse veteran actions succeed.','V11'),
('construction','Construction','Shelters and structures raised in place, with integrity and decay.','V13'),
('discovery','Discovery','The ground a chronicle has personally found and can draw upon.','V14'),
('survival','Survival Consumables','Food and water a body takes in.','V15'),
('literature','Literature','Written documents and maps and their immutable revision chains.','V17'),
('fire','Fire','Fire pits, the ignition chain, and fuel that burns down over time.','V19'),
('weather','Weather','World weather and the exposure it presses onto the body.','V20'),
('food_preservation','Food Preservation','Spoilage and the changing state of stored food.','V25'),
('writing_materials','Writing Materials','Bark, charcoal, and clay — the means to set words down.','V31'),
('settlement','Settlement','Places a chronicle names and makes its own within the land.','V34'),
('furniture','Furniture','Workbenches, seats, and shelves crafted and set down at a place.','V35'),
('navigation','Navigation','Markers, route memory, and the wayfinding that finds a place again.','V36');
