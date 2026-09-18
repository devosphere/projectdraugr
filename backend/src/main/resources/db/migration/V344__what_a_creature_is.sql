-- #110 (epic #109) — what a creature is, declared before anything may treat it as anything.
--
-- THE RULE THIS INTRODUCES. Every creature in the world now carries an explicit cognition class, and the class decides
-- which kinds of act are even meaningful toward it:
--
--   PRIMAL     survival-driven organisms: hunt, flee, nest, defend, learn danger. No language, no contracts, no
--              trade, no consent. Dire wolves, wyverns, rocs, swarms, most of the wild.
--   SOCIAL     groups with roles, kin, alarm and learned behaviour — packs, herds, colonies — but no symbolic
--              economy or politics. They can be observed, managed and, rarely, trained; never bargained with.
--   PEOPLE     individuals with language, tools, kin, memory, settlements and voluntary agreements. PERSONS.
--   SOVEREIGN  rare, long-lived, historically aware beings with their own territory and strategy (a dragon).
--
-- The class is data, reviewed per species — never inferred from a name. A harpy is not a person because folklore
-- gives her a voice, and a cave troll is not a people because "troll" sounds like one; both are recorded below as
-- candidates for the sapient review #115 will do, and until it does they are what their behaviour shows.
--
-- WHY NOW, WITH NO PEOPLE IN THE WORLD YET. Because the protections have to exist before the first person does.
-- A PEOPLE or SOVEREIGN class makes domestication, restraint and harvest-for-parts impossible by construction:
-- the constraints below refuse a person whose data would let them be tamed, penned or butchered, and the code
-- (taming, the carcass harvest) reads these same columns. #115 adds a people into a world that already refuses
-- to treat them as livestock, rather than into one that has to be taught.
--
-- AND EVERY NEW SPECIES MUST DECLARE ITS CLASS. A deferred constraint trigger checks, at the end of the migration
-- that adds a species, that its cognition profile was added in the same transaction. The ticket's catalogue
-- expansion rule — "every new monster/native species must first declare its hierarchy tier" — is therefore not a
-- convention someone has to remember; a migration that forgets fails.

CREATE TABLE cognition_profile (
    species_key                     VARCHAR(80) PRIMARY KEY REFERENCES wildlife_species(species_key),
    cognition_class                 VARCHAR(12) NOT NULL CHECK (cognition_class IN ('PRIMAL','SOCIAL','PEOPLE','SOVEREIGN')),
    communication_mode              VARCHAR(12) NOT NULL CHECK (communication_mode IN ('NONE','SIGNAL','CALL','LANGUAGE')),
    symbolic_language               BOOLEAN NOT NULL DEFAULT FALSE,
    tool_culture                    BOOLEAN NOT NULL DEFAULT FALSE,
    individual_identity             BOOLEAN NOT NULL DEFAULT FALSE,
    kinship_model                   VARCHAR(12) NOT NULL CHECK (kinship_model IN ('NONE','BROOD','PACK','HERD','COLONY','KIN_GROUP','LINEAGE')),
    community_membership            BOOLEAN NOT NULL DEFAULT FALSE,
    moral_agency                    BOOLEAN NOT NULL DEFAULT FALSE,
    trade_eligible                  BOOLEAN NOT NULL DEFAULT FALSE,
    agreement_eligible              BOOLEAN NOT NULL DEFAULT FALSE,
    companionship_eligible          BOOLEAN NOT NULL DEFAULT FALSE,
    settlement_capable              BOOLEAN NOT NULL DEFAULT FALSE,
    working_relationship_eligible   BOOLEAN NOT NULL DEFAULT FALSE,
    restraint_prohibited            BOOLEAN NOT NULL DEFAULT FALSE,
    domestication_prohibited        BOOLEAN NOT NULL DEFAULT FALSE,
    -- Not in the ticket's list by name, but it is what "a Tier III/IV being cannot be reduced to a generic wildlife
    -- drop table after death" means in the schema: the remains of a person are not a harvest.
    remains_protected               BOOLEAN NOT NULL DEFAULT FALSE,
    social_risk_profile             VARCHAR(24) NOT NULL CHECK (social_risk_profile IN
                                        ('NONE','TERRITORIAL','GROUP_DEFENCE','COMMUNITY_RETALIATION','SOVEREIGN_GRIEVANCE')),
    review_note                     TEXT,
    classified_in                   VARCHAR(10) NOT NULL DEFAULT 'V344',

    -- An animal cannot trade, promise, settle, hire on or be held morally to account, whatever the prose says.
    CONSTRAINT animals_are_not_parties CHECK (cognition_class IN ('PEOPLE','SOVEREIGN') OR NOT (
        symbolic_language OR moral_agency OR trade_eligible OR agreement_eligible OR companionship_eligible
        OR settlement_capable OR working_relationship_eligible OR communication_mode = 'LANGUAGE')),
    -- A person is never livestock, never restrained like a beast, never a drop table.
    CONSTRAINT persons_are_not_property CHECK (cognition_class NOT IN ('PEOPLE','SOVEREIGN') OR (
        moral_agency AND individual_identity AND domestication_prohibited AND restraint_prohibited
        AND remains_protected AND communication_mode IN ('CALL','LANGUAGE')))
);

COMMENT ON TABLE cognition_profile IS
  '#110: what each creature is (PRIMAL/SOCIAL/PEOPLE/SOVEREIGN) and which acts that permits. Read by taming and the carcass harvest; required for every species by a deferred trigger.';

-- ── The wild. ─────────────────────────────────────────────────────────────────────────────────────────────────────
-- SOCIAL where the animal lives by the group: pack hunters, the herding grazers, the colonial insects. Everything
-- else is PRIMAL. This is a first classification from behaviour the catalogue already records; each row carries
-- the rule that placed it, so a reviewer can see why and change one species without re-deriving the rest.
INSERT INTO cognition_profile (species_key, cognition_class, communication_mode, kinship_model, social_risk_profile, review_note)
SELECT s.species_key,
       CASE WHEN s.pack_hunter
              OR s.species_key ~ '(^|_)(ant|bee|wasp|hornet|termite)s?($|_)'
              OR (s.kingdom_class = 'MAMMALIA' AND s.ecological_role = 'HERBIVORE' AND s.size_tier IN ('MEDIUM','LARGE','HUGE'))
            THEN 'SOCIAL' ELSE 'PRIMAL' END,
       CASE WHEN s.kingdom_class IN ('INSECTA','ARACHNIDA','GASTROPODA','ANNELIDA','BIVALVIA') THEN 'SIGNAL'
            WHEN s.pack_hunter OR (s.kingdom_class = 'MAMMALIA' AND s.ecological_role = 'HERBIVORE' AND s.size_tier IN ('MEDIUM','LARGE','HUGE')) THEN 'CALL'
            WHEN s.kingdom_class IN ('MAMMALIA','AVES') THEN 'CALL'
            ELSE 'SIGNAL' END,
       CASE WHEN s.pack_hunter THEN 'PACK'
            WHEN s.species_key ~ '(^|_)(ant|bee|wasp|hornet|termite)s?($|_)' THEN 'COLONY'
            WHEN s.kingdom_class = 'MAMMALIA' AND s.ecological_role = 'HERBIVORE' AND s.size_tier IN ('MEDIUM','LARGE','HUGE') THEN 'HERD'
            WHEN s.kingdom_class IN ('MAMMALIA','AVES') THEN 'BROOD'
            ELSE 'NONE' END,
       CASE WHEN s.pack_hunter THEN 'GROUP_DEFENCE' WHEN s.territorial THEN 'TERRITORIAL' ELSE 'NONE' END,
       CASE WHEN s.pack_hunter THEN 'social: hunts as a pack'
            WHEN s.species_key ~ '(^|_)(ant|bee|wasp|hornet|termite)s?($|_)' THEN 'social: a colony'
            WHEN s.kingdom_class = 'MAMMALIA' AND s.ecological_role = 'HERBIVORE' AND s.size_tier IN ('MEDIUM','LARGE','HUGE') THEN 'social: a herding grazer'
            ELSE 'primal: lives by instinct, alone or in a brood' END
  FROM wildlife_species s
 WHERE s.kingdom_class <> 'MONSTRUM';

-- Where the rule is wrong, say so for the one species rather than bend the rule: a porcupine is a medium herbivore
-- and lives alone.
UPDATE cognition_profile
   SET cognition_class = 'PRIMAL', kinship_model = 'BROOD', review_note = 'primal: a solitary grazer; the herding rule does not fit it'
 WHERE species_key = 'porcupine';

-- ── The monsters: each one decided, not swept. ────────────────────────────────────────────────────────────────────
-- The ticket names its Tier I examples outright — dire wolves, wyverns, rocs, giant hornet queens, locust swarms,
-- giant bat swarms — and a dire wolf stays PRIMAL although it runs in a pack, because the ticket's own reading is
-- that a pack of dire wolves is an apex predator's hunt, not a society. The four SOCIAL monsters are the ones whose
-- recorded behaviour is a group's: a jackal pack, the pale wolves' pack, a widow colony's shared web, and the harpy
-- flock. The harpy and the cave troll are marked for the #115 sapient review and are NOT people by this migration.
INSERT INTO cognition_profile (species_key, cognition_class, communication_mode, kinship_model, social_risk_profile, review_note)
SELECT s.species_key,
       CASE WHEN s.species_key IN ('cinder_jackal_pack','pale_wolf','widow_spider_colony','harpy') THEN 'SOCIAL' ELSE 'PRIMAL' END,
       CASE WHEN s.species_key IN ('harpy','cinder_jackal_pack','pale_wolf','dire_wolf','cave_troll') THEN 'CALL' ELSE 'SIGNAL' END,
       CASE WHEN s.species_key IN ('cinder_jackal_pack','pale_wolf','dire_wolf') THEN 'PACK'
            WHEN s.species_key IN ('widow_spider_colony','giant_hornet_queen','locust_swarm','giant_bat_swarm','mire_leech_cluster','willow_wisp_swarm','storm_moth_cloud') THEN 'COLONY'
            WHEN s.species_key = 'harpy' THEN 'BROOD'
            ELSE 'NONE' END,
       CASE WHEN s.pack_hunter THEN 'GROUP_DEFENCE' WHEN s.territorial THEN 'TERRITORIAL' ELSE 'NONE' END,
       CASE s.species_key
            WHEN 'harpy'      THEN 'social flock; CANDIDATE for the #115 sapient review. Stealing and striking are not evidence of personhood.'
            WHEN 'cave_troll' THEN 'solitary territorial organism; CANDIDATE for #115 — a stonekin people would be a distinct, explicitly specified culture, not every troll.'
            WHEN 'bog_wraith' THEN 'primal and non-social pending an ontology decision: a disease-wounding hostile cannot casually be a trade partner.'
            WHEN 'dire_wolf'  THEN 'primal per #110: an apex predator that hunts in company, not a society.'
            ELSE CASE WHEN s.species_key IN ('cinder_jackal_pack','pale_wolf','widow_spider_colony') THEN 'social: lives and hunts as a group'
                      ELSE 'primal monster: survival-driven, no language or society' END END
  FROM wildlife_species s
 WHERE s.kingdom_class = 'MONSTRUM';

-- ── Every future species declares its class in the same migration that adds it. ──────────────────────────────────
CREATE OR REPLACE FUNCTION species_must_declare_cognition() RETURNS trigger AS $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM cognition_profile WHERE species_key = NEW.species_key) THEN
    RAISE EXCEPTION 'species % has no cognition_profile: every creature must declare what it is (PRIMAL/SOCIAL/PEOPLE/SOVEREIGN) in the migration that adds it (#110)', NEW.species_key;
  END IF;
  RETURN NULL;
END $$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER species_declares_cognition
  AFTER INSERT ON wildlife_species
  DEFERRABLE INITIALLY DEFERRED
  FOR EACH ROW EXECUTE FUNCTION species_must_declare_cognition();

DO $$
DECLARE bad text; n int;
BEGIN
  SELECT string_agg(species_key, ', ') INTO bad
    FROM wildlife_species s WHERE NOT EXISTS (SELECT 1 FROM cognition_profile c WHERE c.species_key = s.species_key);
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V344: undeclared creatures: %', bad; END IF;

  -- The ticket's named Tier I monsters are exactly that.
  SELECT string_agg(species_key, ', ') INTO bad FROM cognition_profile
   WHERE species_key IN ('dire_wolf','wyvern','roc','giant_hornet_queen','locust_swarm','giant_bat_swarm') AND cognition_class <> 'PRIMAL';
  IF bad IS NOT NULL THEN RAISE EXCEPTION 'V344: named Tier I creatures classified otherwise: %', bad; END IF;

  -- Nobody is a person yet; that is #115's reviewed decision, not a side effect of this one.
  SELECT count(*) INTO n FROM cognition_profile WHERE cognition_class IN ('PEOPLE','SOVEREIGN');
  IF n <> 0 THEN RAISE EXCEPTION 'V344: % creatures were made persons without the #115 review', n; END IF;

  -- The classes are not decoration: the wild is not all one thing.
  SELECT count(DISTINCT cognition_class) INTO n FROM cognition_profile;
  IF n < 2 THEN RAISE EXCEPTION 'V344: every creature landed in one class; the classification says nothing'; END IF;
END $$;
