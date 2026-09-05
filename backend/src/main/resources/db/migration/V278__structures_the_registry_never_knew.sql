-- V278 — fifteen buildable structures the registry never knew about (#77/#219/#220).
--
-- `construction_kind` is the registry every structural capability is gated on: is_shelter decides whether a
-- build keeps the weather off you (#482), decays decides whether it wears and can be mended (#504), flammable
-- decides whether a roaring fire beside it can catch (#505), is_workstation decides whether it eases work.
--
-- Fifteen assemblies name a project_kind that has no row in it at all:
--
--   DRYING_RACK  SMOKE_RACK  WATTLE_AND_DAUB_HUT  EARTH_SHELTERED_HUT  RAISED_SLEEPING_PLATFORM
--   REED_SCREEN  CLAY_LINED_HEARTH  WOOD_STORE  RAINWATER_CATCHMENT  SPLIT_RAIL_FENCE
--   SIMPLE_GATE  FOOTBRIDGE  FISHING_LANDING  LOG_CABIN  TIMBER_BARN
--
-- There is no foreign key on construction_project.project_kind, so nothing ever failed loudly. They simply fell
-- through every registry-driven capability at once: a timber barn and a log cabin never wore out, a wood store
-- and a split-rail fence stood fireproof beside a roaring hearth, and a raised sleeping platform sheltered
-- nobody. Three of them — the two huts and the log cabin — are only sheltered at all because shelterInReach
-- still carries them as hardcoded exceptions from before the registry was read.
--
-- This is the cost of the fix being right: #504 and #505 made decay and fire read the registry rather than a
-- hand-written list, which is correct, and it means anything absent from the registry now quietly opts out of
-- both. Filling it in is the other half of that work.
INSERT INTO construction_kind (project_kind, display_name, domain_key, is_shelter, is_workstation, decays, proven_in) VALUES
    -- Roofed things a person can get under and out of the weather.
    ('WATTLE_AND_DAUB_HUT',      'Wattle-and-daub hut',      'construction', TRUE,  FALSE, TRUE, 'V278'),
    ('EARTH_SHELTERED_HUT',      'Earth-sheltered hut',      'construction', TRUE,  FALSE, TRUE, 'V278'),
    ('LOG_CABIN',                'Log cabin',                'construction', TRUE,  FALSE, TRUE, 'V278'),
    ('TIMBER_BARN',              'Timber barn',              'construction', TRUE,  FALSE, TRUE, 'V278'),
    ('RAISED_SLEEPING_PLATFORM', 'Raised sleeping platform', 'construction', TRUE,  FALSE, TRUE, 'V278'),
    -- Field structures: they wear, but nobody shelters under them.
    ('DRYING_RACK',              'Drying rack',              'construction', FALSE, FALSE, TRUE, 'V278'),
    ('SMOKE_RACK',               'Smoke rack',               'construction', FALSE, FALSE, TRUE, 'V278'),
    ('REED_SCREEN',              'Reed screen',              'construction', FALSE, FALSE, TRUE, 'V278'),
    ('CLAY_LINED_HEARTH',        'Clay-lined hearth',        'construction', FALSE, FALSE, TRUE, 'V278'),
    ('WOOD_STORE',               'Wood store',               'construction', FALSE, FALSE, TRUE, 'V278'),
    ('RAINWATER_CATCHMENT',      'Rainwater catchment',      'construction', FALSE, FALSE, TRUE, 'V278'),
    ('SPLIT_RAIL_FENCE',         'Split-rail fence',         'construction', FALSE, FALSE, TRUE, 'V278'),
    ('SIMPLE_GATE',              'Simple gate',              'construction', FALSE, FALSE, TRUE, 'V278'),
    ('FOOTBRIDGE',               'Footbridge',               'construction', FALSE, FALSE, TRUE, 'V278'),
    ('FISHING_LANDING',          'Fishing landing',          'construction', FALSE, FALSE, TRUE, 'V278')
ON CONFLICT (project_kind) DO NOTHING;

-- What burns, on the same reasoning as V274: plant matter, bark, hide and dry timber catch; earth, clay and
-- stone do not. A wattle-and-daub hut is earth over a frame and an earth-sheltered hut is a hole in a bank, so
-- neither carries flame; a clay-lined hearth is the thing you light the fire IN. A rainwater catchment is wet by
-- definition and is left alone.
UPDATE construction_kind SET flammable = TRUE WHERE project_kind IN (
    'DRYING_RACK','SMOKE_RACK','LOG_CABIN','TIMBER_BARN','RAISED_SLEEPING_PLATFORM','REED_SCREEN',
    'WOOD_STORE','SPLIT_RAIL_FENCE','SIMPLE_GATE','FOOTBRIDGE','FISHING_LANDING');

-- Two assemblies both answered to "bed platform" on a keyword of equal length, so which one a Chronicle got was
-- arbitrary. They are genuinely different builds — a one-stage lashed bed frame and a three-stage posted
-- sleeping platform — so the shared phrase goes to neither: the bed platform keeps it, and the sleeping platform
-- keeps its own four distinctive ones.
UPDATE assembly_definition
SET keywords = 'build a raised sleeping platform,raise a sleeping platform,raised sleeping platform,build a sleeping platform,work on the sleeping platform'
WHERE assembly_key = 'raised_sleeping_platform';
