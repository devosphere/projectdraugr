-- #111 (epic #109) — a people that persists.
--
-- A native community is world state, not a quest giver: individuals with a body and a place, families with a
-- house, a settlement with a store you can walk up to, standing with the Chronicle and with other communities, and
-- a history nothing can rewrite. It is compact on purpose (the ticket: "does not require an AI turn for every
-- individual"), and the daily step that moves it is deterministic (NativeCommunityService).
--
-- WHAT IS PHYSICAL. Settlement sites and individuals are world objects, so each has a location and a lifecycle the
-- Auditor already polices. The community's goods are ordinary item instances HELD BY the store's world object: food
-- in the store spoils on the same clock as food anywhere, is eaten one item at a time, and runs out. Nothing here is
-- an abstract infinite shop.
--
-- WHO MAY BE A MEMBER. Only a species V344 classifies as PEOPLE or SOVEREIGN. A trigger refuses a community of
-- anything else, so a wolf pack can never be given a store and a trade policy by a later migration that forgets.
--
-- NO PEOPLE ARE CREATED HERE. #115 decides the first one after its review; this is the ground they will stand on.

CREATE TABLE native_community (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    world_id                UUID NOT NULL REFERENCES world_genesis(world_id),
    species_key             VARCHAR(80) NOT NULL REFERENCES cognition_profile(species_key),
    name                    VARCHAR(120) NOT NULL,
    home_chunk_id           UUID NOT NULL REFERENCES world_chunk(id),
    territory_radius        INTEGER NOT NULL DEFAULT 1 CHECK (territory_radius BETWEEN 0 AND 6),
    governance              VARCHAR(12) NOT NULL CHECK (governance IN ('ELDERS','HEADSPERSON','COUNCIL')),
    -- What the community is when it is fed and unafraid, and what it is right now. Shortage and grievance move the
    -- current values; recovery returns them to the base, never past it.
    base_trade_policy       VARCHAR(10) NOT NULL CHECK (base_trade_policy IN ('OPEN','SELECTIVE','CLOSED')),
    trade_policy            VARCHAR(10) NOT NULL CHECK (trade_policy IN ('OPEN','SELECTIVE','CLOSED')),
    base_security_posture   VARCHAR(10) NOT NULL CHECK (base_security_posture IN ('AT_EASE','WARY','GUARDED','HOSTILE')),
    security_posture        VARCHAR(10) NOT NULL CHECK (security_posture IN ('AT_EASE','WARY','GUARDED','HOSTILE')),
    lifecycle               VARCHAR(10) NOT NULL DEFAULT 'SETTLED' CHECK (lifecycle IN ('SETTLED','MOVING','DISPERSED')),
    -- What it lives on, and how much of it one person eats in a day.
    staple_item_key         VARCHAR(80) NOT NULL REFERENCES item_definition(item_key),
    daily_ration            INTEGER NOT NULL DEFAULT 1 CHECK (daily_ration BETWEEN 1 AND 4),
    shortage_days           INTEGER NOT NULL DEFAULT 0 CHECK (shortage_days >= 0),
    founded_at              TIMESTAMPTZ NOT NULL,
    last_simulated_at       TIMESTAMPTZ NOT NULL
);

CREATE OR REPLACE FUNCTION native_community_must_be_a_people() RETURNS trigger AS $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM cognition_profile WHERE species_key = NEW.species_key AND cognition_class IN ('PEOPLE','SOVEREIGN')) THEN
    RAISE EXCEPTION 'a native community must be a people: % is not classified PEOPLE or SOVEREIGN (#110/#111)', NEW.species_key;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER native_community_is_a_people
  BEFORE INSERT OR UPDATE OF species_key ON native_community
  FOR EACH ROW EXECUTE FUNCTION native_community_must_be_a_people();

-- A place the community has built. The world object carries its location and lifecycle; the store is the site whose
-- object holds the community's goods.
CREATE TABLE native_settlement_site (
    object_id               UUID PRIMARY KEY REFERENCES world_object(id),
    community_id            UUID NOT NULL REFERENCES native_community(id),
    site_kind               VARCHAR(14) NOT NULL CHECK (site_kind IN ('VILLAGE','SEASONAL_CAMP','STORE_HOUSE','WORK_PLACE')),
    access_rule             VARCHAR(8) NOT NULL DEFAULT 'INVITED' CHECK (access_rule IN ('OPEN','INVITED','CLOSED')),
    holds_stores            BOOLEAN NOT NULL DEFAULT FALSE,
    condition_percent       INTEGER NOT NULL DEFAULT 100 CHECK (condition_percent BETWEEN 0 AND 100)
);
CREATE UNIQUE INDEX native_one_store_per_community ON native_settlement_site (community_id) WHERE holds_stores;

CREATE TABLE native_kin_group (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    community_id            UUID NOT NULL REFERENCES native_community(id),
    name                    VARCHAR(120) NOT NULL,
    housing_site_id         UUID REFERENCES native_settlement_site(object_id),
    obligations             TEXT
);

CREATE TABLE native_individual (
    object_id               UUID PRIMARY KEY REFERENCES world_object(id),
    community_id            UUID NOT NULL REFERENCES native_community(id),
    kin_group_id            UUID REFERENCES native_kin_group(id),
    given_name              VARCHAR(80) NOT NULL,
    role                    VARCHAR(12) NOT NULL CHECK (role IN ('HEADSPERSON','ELDER','FORAGER','FISHER','HUNTER','MAKER','GUARD','CHILD')),
    condition               VARCHAR(8) NOT NULL DEFAULT 'WELL' CHECK (condition IN ('WELL','HUNGRY','INJURED','SICK','DEAD')),
    skill                   VARCHAR(40),
    available               BOOLEAN NOT NULL DEFAULT TRUE
);

-- Standing between a community and exactly one other party: the Chronicle, or another community.
CREATE TABLE community_relation (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    community_id            UUID NOT NULL REFERENCES native_community(id),
    chronicle_id            UUID REFERENCES chronicle(id),
    other_community_id      UUID REFERENCES native_community(id),
    standing                INTEGER NOT NULL DEFAULT 0 CHECK (standing BETWEEN -100 AND 100),
    last_event_kind         VARCHAR(40),
    last_event_at           TIMESTAMPTZ,
    obligation              TEXT,
    CONSTRAINT relation_has_one_other_party CHECK (num_nonnulls(chronicle_id, other_community_id) = 1),
    CONSTRAINT relation_is_not_with_itself CHECK (other_community_id IS NULL OR other_community_id <> community_id)
);
CREATE UNIQUE INDEX community_relation_with_chronicle ON community_relation (community_id, chronicle_id) WHERE chronicle_id IS NOT NULL;
CREATE UNIQUE INDEX community_relation_with_community ON community_relation (community_id, other_community_id) WHERE other_community_id IS NOT NULL;

-- What happened to a community, in order. Append-only: the collective memory #114 reads cannot be edited.
CREATE TABLE native_event (
    id                      BIGSERIAL PRIMARY KEY,
    community_id            UUID NOT NULL REFERENCES native_community(id),
    occurred_at             TIMESTAMPTZ NOT NULL,
    event_kind              VARCHAR(40) NOT NULL,
    subject_id              UUID,
    payload                 JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX native_event_by_community ON native_event (community_id, occurred_at);

CREATE OR REPLACE FUNCTION native_event_is_history() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'native_event is a community''s history and cannot be % (#111)', lower(TG_OP);
END $$ LANGUAGE plpgsql;

CREATE TRIGGER native_event_append_only
  BEFORE UPDATE OR DELETE ON native_event
  FOR EACH ROW EXECUTE FUNCTION native_event_is_history();

COMMENT ON TABLE native_community IS '#111: a native people''s community. Moved by NativeCommunityService.advanceTo on the world clock.';
