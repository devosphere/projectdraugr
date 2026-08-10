package com.devosphere.draugr.ecology;

import com.devosphere.draugr.chronicle.ChroniclePhysiologyService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.survival.FoodPreservationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;

/** Resolves local wildlife contact deterministically; creatures exist as aggregates until an encounter kills one. */
@Service
public class WildlifeEncounterService {
    private final JdbcTemplate jdbc; private final ChroniclePhysiologyService physiology; private final PhysicalItemService items; private final FoodPreservationService food;
    public WildlifeEncounterService(JdbcTemplate jdbc, ChroniclePhysiologyService physiology, PhysicalItemService items, FoodPreservationService food) { this.jdbc=jdbc; this.physiology=physiology; this.items=items; this.food=food; }

    @Transactional
    public EncounterResult confront(UUID chronicle, UUID chunk, UUID action, Instant at) {
        return confront(chronicle, chunk, action, at, 0);
    }

    /**
     * @param tacticBonus added to the chronicle's effective capability — the caller's
     * measure of how well the attempt was described (naming a target, a weapon, an
     * approach) and how practiced the chronicle is at the hunt. A bare "kill the boar"
     * carries a bonus of zero and rests on raw body state and whatever is in hand.
     */
    @Transactional
    public EncounterResult confront(UUID chronicle, UUID chunk, UUID action, Instant at, int tacticBonus) {
        // The species registry (V41) supplies movement class, intrinsic resistance,
        // and the ambush flag; it is LEFT JOINed so a population whose species is not
        // yet catalogued still resolves on its role alone.
        // FOR UPDATE OF wp, not a bare FOR UPDATE: the species registry is a LEFT JOIN so
        // uncatalogued populations still resolve, and Postgres refuses to lock the nullable
        // side of an outer join. Only the population row needs locking — the registry is
        // immutable reference data.
        Encounter candidate=jdbc.query("SELECT wp.id,wp.species_key,wp.ecological_role,wp.behavior_state,wp.population_count,ws.movement_class,ws.base_resistance,ws.ambush_hunter FROM wildlife_population wp JOIN ecology_site es ON es.id=wp.site_id LEFT JOIN wildlife_species ws ON ws.species_key=wp.species_key WHERE es.chunk_id=? AND wp.population_count>0 ORDER BY CASE wp.ecological_role WHEN 'CARNIVORE' THEN 0 WHEN 'OMNIVORE' THEN 1 ELSE 2 END LIMIT 1 FOR UPDATE OF wp",rs->rs.next()?new Encounter(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4),rs.getInt(5),rs.getString(6),(Integer)rs.getObject(7),rs.getBoolean(8)):null,chunk);
        if(candidate==null)return new EncounterResult("FAILED","The ground answers only with rain and the small movements of the forest.");
        Combatant body=jdbc.query("SELECT p.energy_level,p.injury_severity,p.pain_level,COALESCE((SELECT COUNT(*) FROM equipment_attachment e JOIN item_instance i ON i.object_id=e.item_id WHERE e.chronicle_id=? AND e.body_position IN ('HAND_LEFT','HAND_RIGHT') AND i.item_key IN ('stone_axe','primitive_spear','poisoned_spear')),0),COALESCE((SELECT COUNT(*) FROM world_object w JOIN item_instance i ON i.object_id=w.id WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND i.item_key='field_stone'),0),COALESCE((SELECT COUNT(*) FROM equipment_attachment e JOIN item_instance i ON i.object_id=e.item_id WHERE e.chronicle_id=? AND i.item_key IN ('scale_armour','chitin_helm','war_shield')),0) FROM chronicle_physiology p WHERE p.chronicle_id=?",rs->rs.next()?new Combatant(rs.getInt(1),rs.getInt(2),rs.getInt(3),rs.getInt(4),rs.getInt(5),rs.getInt(6)):new Combatant(0,100,100,0,0,0),chronicle,chronicle,chronicle,chronicle);
        // A creature on the wing cannot be reached by a hand weapon. Throwing stones
        // is the only contact a chronicle has with it. The narrator witnesses the
        // futility without naming what would be needed.
        if("AERIAL".equals(candidate.movementClass()) && body.stones()==0)
            return new EncounterResult("FAILED","It stays above you, well out of reach. Whatever you do with your hands, the air between you does not close.");
        int capability = body.energy()/3 - body.injury()/2 - body.pain()/3 + body.handWeapon()*35 + Math.min(10,body.stones()*2) + tacticBonus;
        // Registry resistance is authoritative when catalogued; the old role-based
        // bands remain the fallback. Registry values are species-scaled, so they are
        // lifted onto the same footing as the bands they replace.
        int resistance = candidate.baseResistance()!=null ? 30 + candidate.baseResistance()/2
            : "CARNIVORE".equals(candidate.role()) ? 85 : "OMNIVORE".equals(candidate.role()) ? 65 : 42;
        if("HUNTING".equals(candidate.behavior())||"PACK_HUNT".equals(candidate.behavior())) resistance += 10;
        if("SHELTERING".equals(candidate.behavior())) resistance -= 12;
        int roll = Math.floorMod(action.hashCode(), 100);
        if (capability + roll >= resistance + 40) {
            UUID carcass=UUID.randomUUID(); int meat=meatFor(candidate.species());
            jdbc.update("UPDATE wildlife_population SET population_count=population_count-1,behavior_state='ALERT' WHERE id=?",candidate.populationId());
            jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CARCASS',?,?)",carcass,display(candidate.species())+" carcass",chunk);
            Timestamp ts=Timestamp.from(at);
            jdbc.update("INSERT INTO wildlife_carcass (object_id,source_population_id,species_key,remaining_meat_units,hide_available,killed_by_action_id,died_at) VALUES (?,?,?,?,true,?,?)",carcass,candidate.populationId(),candidate.species(),meat,action,ts);
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'WILDLIFE_KILLED',jsonb_build_object('species',?,'sourcePopulationId',?::text))",carcass,ts,candidate.species(),candidate.populationId().toString());
            return new EncounterResult("SUCCEEDED","The struggle ends in the wet earth. The still form remains where it fell.");
        }
        if (capability + roll >= resistance) {
            jdbc.update("UPDATE wildlife_population SET behavior_state='FLEEING' WHERE id=?",candidate.populationId());
            return new EncounterResult("PARTIAL","The animal breaks away through the cover before either of you can close the distance again.");
        }
        // A worse mismatch means a worse wound. A defenceless rush at a hunting
        // predator can be mortal; a near-won struggle only draws blood. The deficit
        // between the chronicle's effort and the animal's resistance sets the gravity,
        // so a bare-handed charge at a bear is punished the way it would be in life.
        int deficit = Math.max(0, resistance - (capability + roll));
        int severity=("CARNIVORE".equals(candidate.role())?22:"OMNIVORE".equals(candidate.role())?12:4) + deficit;
        if("RESTING".equals(candidate.behavior()) || "SHELTERING".equals(candidate.behavior())) severity=Math.max(2,severity-5);
        // An ambush hunter strikes from cover with no warning; the first blow lands
        // on a body that had no chance to set itself against it.
        if(candidate.ambushHunter()) severity += 8;
        // A monster's special mechanic shapes what losing to it means. These are
        // biological traits, not scripted set pieces: a wyvern breathes fire, a roc
        // carries what it catches, a harpy takes what it can lift, a wraith's wounds
        // go bad. The narrator witnesses the result and explains nothing.
        String mechanic = jdbc.query("SELECT special_mechanic FROM monster_profile WHERE species_key=?", rs->rs.next()?rs.getString(1):null, candidate.species());
        String monsterMark = null;
        if (mechanic != null) switch (mechanic) {
            case "FIRE_BREATH" -> { severity += 25; monsterMark = "Heat arrives before anything else does. The air itself turns against you, and what it leaves behind goes on burning after the shape above has wheeled away."; }
            case "GRAB_AND_CARRY" -> { severity += 20; monsterMark = "You are lifted. The ground drops away, tilts, and then returns all at once, and the returning is the part that breaks something."; }
            case "SWARM_WOUNDS" -> { severity += 10; monsterMark = "There is no single blow to brace against — only many, from every direction at once, until the air is nothing but wings and small teeth."; }
            case "DISEASE_WOUND" -> { physiology.applyIllness(chronicle, 15, action, at, "BOG_WRAITH_WOUND"); monsterMark = "The wound it opens is cold, and something in it does not stop at the skin."; }
            case "VENOM_WOUND" -> { physiology.applyIllness(chronicle, 12, action, at, "GIANT_HORNET_VENOM"); monsterMark = "The sting goes deep, and a slow heat spreads out from it that has nothing to do with the wound itself."; }
            case "ITEM_THEFT" -> { UUID stolen = jdbc.query("SELECT w.id FROM world_object w JOIN item_instance i ON i.object_id=w.id WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' ORDER BY random() LIMIT 1", rs->rs.next()?rs.getObject(1,UUID.class):null, chronicle);
                if (stolen != null) { String name = jdbc.queryForObject("SELECT display_name FROM world_object WHERE id=?", String.class, stolen);
                    jdbc.update("UPDATE world_object SET current_owner_id=NULL,current_location_id=? WHERE id=?", chunk, stolen);
                    jdbc.update("DELETE FROM equipment_attachment WHERE item_id=?", stolen);
                    jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'STOLEN',jsonb_build_object('by',?))", stolen, Timestamp.from(at), candidate.species());
                    monsterMark = "It comes in fast and close, and goes out again heavier. The " + name.toLowerCase() + " is no longer yours."; } }
            default -> { }
        }
        // Armour worn takes part of the blow: scale, shell, and a shield each turn some of the wound aside
        // (#75). It never makes a body untouchable — the worst a mauling can do is only blunted, not erased.
        if (body.armour() > 0) severity = Math.max(1, severity - body.armour()*7);
        physiology.applyInjury(chronicle,severity,action,at,"WILDLIFE_CONTACT");
        jdbc.update("UPDATE wildlife_population SET behavior_state='FLEEING' WHERE id=?",candidate.populationId());
        String mark = monsterMark != null ? monsterMark
            : severity >= 70
            ? "The " + display(candidate.species()) + " closes with its whole weight, and for a moment there is only force and tearing before it wheels away into the trees."
            : severity >= 35
            ? "The " + display(candidate.species()) + " drives into you hard, raking deep before it breaks off through the cover."
            : "The " + display(candidate.species()) + " moves with sudden force. The encounter leaves its mark before the forest takes it back.";
        return new EncounterResult("PARTIAL", mark);
    }

    @Transactional
    public HarvestResult harvest(UUID chronicle, UUID chunk, UUID action, Instant at) {
        Carcass carcass=jdbc.query("SELECT wc.object_id,wc.species_key,wc.remaining_meat_units,wc.hide_available FROM wildlife_carcass wc JOIN world_object w ON w.id=wc.object_id WHERE w.current_location_id=? AND w.lifecycle_state='ACTIVE' ORDER BY wc.died_at LIMIT 1 FOR UPDATE",rs->rs.next()?new Carcass(rs.getObject(1,UUID.class),rs.getString(2),rs.getInt(3),rs.getBoolean(4)):null,chunk);
        if(carcass==null)return new HarvestResult("FAILED","You search the ground carefully, then leave it as you found it.");
        if(carcass.meat()>0) { UUID meat=items.createCarriedItem(chronicle,"raw_game_meat","Raw game meat",at,"HARVESTED_FROM_CARCASS"); food.registerRaw(meat,at); jdbc.update("UPDATE wildlife_carcass SET remaining_meat_units=remaining_meat_units-1 WHERE object_id=?",carcass.id()); }
        // The meat is off; what remains is the species' own yield — a wolf's pelt and
        // fangs, a deer's antler and sinew — drawn from wildlife_drop (V42). The legacy
        // generic hide stands in for species not yet catalogued.
        else if(carcass.hide()) { int taken=takeSpeciesDrops(chronicle,carcass.species(),at); if(taken==0) items.createCarriedItem(chronicle,"animal_hide","Animal hide",at,"HARVESTED_FROM_CARCASS"); jdbc.update("UPDATE wildlife_carcass SET hide_available=false WHERE object_id=?",carcass.id()); }
        else return new HarvestResult("FAILED","The remains offer nothing more that you can carry away.");
        Integer remaining=jdbc.queryForObject("SELECT remaining_meat_units FROM wildlife_carcass WHERE object_id=?",Integer.class,carcass.id()); Boolean hide=jdbc.queryForObject("SELECT hide_available FROM wildlife_carcass WHERE object_id=?",Boolean.class,carcass.id());
        if((remaining==null||remaining==0) && Boolean.FALSE.equals(hide)) { Timestamp ts=Timestamp.from(at); jdbc.update("UPDATE world_object SET lifecycle_state='DESTROYED',destroyed_at=?,destroyed_location_id=current_location_id,destroyed_cause='CARCASS_EXHAUSTED',current_location_id=NULL WHERE id=?",ts,carcass.id()); jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CARCASS_EXHAUSTED','{}'::jsonb)",carcass.id(),ts); }
        return new HarvestResult("SUCCEEDED","You work carefully over the remains and take what you can carry.");
    }
    /**
     * The world's turn. A chronicle busy with a task in ground where something is
     * actively hunting may be reached before they ever choose to look up. This runs
     * after ordinary actions (travel, gathering) — it is the reason not looking
     * around is dangerous, and it never announces itself beforehand.
     *
     * @return a witness line if something happened, or null if the ground stayed quiet.
     */
    @Transactional
    public String passiveEncounter(UUID chronicle, UUID chunk, UUID action, Instant at, String attention) {
        Threat threat = jdbc.query(
            "SELECT wp.id,wp.species_key,wp.behavior_state,ws.base_resistance,ws.ambush_hunter,ws.size_tier " +
            "FROM wildlife_population wp JOIN ecology_site es ON es.id=wp.site_id " +
            "LEFT JOIN wildlife_species ws ON ws.species_key=wp.species_key " +
            "WHERE es.chunk_id=? AND wp.population_count>0 AND wp.ecological_role IN ('CARNIVORE','OMNIVORE') " +
            "AND wp.behavior_state IN ('HUNTING','PACK_HUNT','STALKING','TERRITORIAL','ALERT') " +
            "ORDER BY CASE wp.behavior_state WHEN 'PACK_HUNT' THEN 0 WHEN 'HUNTING' THEN 1 WHEN 'STALKING' THEN 2 ELSE 3 END LIMIT 1",
            rs -> rs.next() ? new Threat(rs.getObject(1,UUID.class), rs.getString(2), rs.getString(3), (Integer)rs.getObject(4), rs.getBoolean(5), rs.getString(6)) : null, chunk);
        if (threat == null) return null;

        // How likely it is to reach the chronicle. A pack that is coordinating is the
        // worst case; a merely alert animal is the least. A chronicle who is paying
        // attention sees it coming and it does not close; one heads-down does not.
        int chance = switch (threat.behavior()) { case "PACK_HUNT" -> 35; case "HUNTING" -> 25; case "STALKING" -> 20; case "TERRITORIAL" -> 15; default -> 8; };
        if (threat.ambushHunter()) chance += 10;
        if ("HIGH".equals(attention)) chance -= 12;
        else if ("MODERATE".equals(attention)) chance -= 5;
        if (Math.floorMod(action.hashCode() >>> 8, 100) >= Math.max(0, chance)) return null;

        int resistance = threat.baseResistance() != null ? threat.baseResistance() : 50;
        int severity = Math.max(4, resistance / 4 + (threat.ambushHunter() ? 8 : 0));
        physiology.applyInjury(chronicle, severity, action, at, "PASSIVE_WILDLIFE_ATTACK");
        record(chronicle, threat.populationId(), chunk, "PASSIVE_ATTACK", at, action);
        jdbc.update("UPDATE wildlife_population SET behavior_state='ALERT' WHERE id=?", threat.populationId());
        return threat.ambushHunter()
            ? "It is on you before there is anything to see — weight and teeth out of the cover, and the ground coming up to meet you."
            : "The " + display(threat.species()) + " does not wait to be found. It closes while your hands are busy, and the work you were doing is over.";
    }

    /**
     * Read the ground for what has passed over it. Tracking finds sign, not animals:
     * prints, scat, feathers, den marks. What the chronicle can read from them scales
     * with how carefully they looked and how practiced they are.
     */
    @Transactional
    public EncounterResult track(UUID chronicle, UUID chunk, UUID action, Instant at, String attention, double familiarity) {
        java.util.List<java.util.Map<String,Object>> sign = jdbc.queryForList(
            "SELECT wp.id AS population_id, wp.species_key, wp.behavior_state, g.sign_kind, g.readable_hours " +
            "FROM wildlife_population wp JOIN ecology_site es ON es.id=wp.site_id " +
            "JOIN wildlife_sign g ON g.species_key=wp.species_key " +
            "WHERE es.chunk_id=? AND wp.population_count>0 ORDER BY g.readable_hours DESC", chunk);
        if (sign.isEmpty()) return new EncounterResult("FAILED","You go over the ground carefully, but it holds nothing that anything has left behind.");
        java.util.Map<String,Object> found = sign.get(Math.floorMod(action.hashCode(), sign.size()));
        String species = (String) found.get("species_key");
        String kind = (String) found.get("sign_kind");
        UUID population = (UUID) found.get("population_id");
        record(chronicle, population, chunk, "TRACKED", at, action);

        String what = switch (kind) {
            case "PRINTS" -> "a line of prints pressed into the softer ground";
            case "SCAT" -> "droppings, not old";
            case "FEATHERS" -> "feathers caught in the low growth";
            case "DISTURBED_GROUND" -> "ground turned over and rooted through";
            case "CARCASS_SCRAPS" -> "scraps and bone fragments pulled away from something larger";
            case "DEN_MARKS" -> "a worn track running to a gap under the roots";
            case "TERRITORIAL_SCRATCH" -> "deep scoring in the bark, higher than your shoulder";
            default -> "sign";
        };
        StringBuilder s = new StringBuilder("You find ").append(what).append(". ");
        // A careful, practiced reading gets the animal and what it was doing. A
        // glance gets only that something was here.
        boolean reads = "HIGH".equals(attention) || familiarity > 0.15;
        if (reads) {
            s.append("A ").append(display(species)).append(", by the look of it");
            String behavior = (String) found.get("behavior_state");
            String doing = switch (behavior == null ? "" : behavior) {
                case "HUNTING", "PACK_HUNT", "STALKING" -> ", and moving with purpose";
                case "FEEDING", "FORAGING" -> ", and in no hurry";
                case "FLEEING" -> ", and going fast";
                case "TERRITORIAL" -> ", and not far off";
                default -> "";
            };
            s.append(doing).append(".");
        } else {
            s.append("Something was here. What it was, and how long ago, you cannot say.");
        }
        return new EncounterResult("SUCCEEDED", s.toString());
    }

    /**
     * Approach an animal calmly and try to build trust. Trust is earned across many
     * returns, not won in one: each calm approach moves it a little, food moves it
     * more, and approaching with a weapon in hand moves it back. Species tamability
     * (V41) sets the ceiling — a turtle yields readily, a lynx almost never.
     *
     * <p>The bond belongs to this chronicle. It is not inherited: when they die the
     * animal is wild again to whoever finds it next.
     */
    @Transactional
    public EncounterResult tame(UUID chronicle, UUID chunk, UUID action, Instant at, String actionText) {
        Tamable t = jdbc.query(
            "SELECT wp.id,wp.species_key,ws.tamability,wp.behavior_state FROM wildlife_population wp " +
            "JOIN ecology_site es ON es.id=wp.site_id JOIN wildlife_species ws ON ws.species_key=wp.species_key " +
            "WHERE es.chunk_id=? AND wp.population_count>0 AND ws.tamability>0 " +
            "ORDER BY ws.tamability DESC LIMIT 1 FOR UPDATE OF wp",
            rs -> rs.next() ? new Tamable(rs.getObject(1,UUID.class), rs.getString(2), rs.getInt(3), rs.getString(4)) : null, chunk);
        if (t == null) return new EncounterResult("FAILED","You stand still a long while, but there is nothing here that would let you near it.");

        String v = actionText.toLowerCase(java.util.Locale.ROOT);
        boolean armed = Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM equipment_attachment e JOIN item_instance i ON i.object_id=e.item_id " +
            "WHERE e.chronicle_id=? AND e.body_position IN ('HAND_LEFT','HAND_RIGHT') " +
            "AND i.item_key IN ('primitive_spear','stone_axe','stone_hatchet','stone_knife','stone_hammer','primitive_pickaxe'))",
            Boolean.class, chronicle));
        // Offering food is the strongest single thing a chronicle can do, and it costs
        // real food from their own stores.
        boolean offersFood = (v.contains("offer")||v.contains("feed")||v.contains("hold out")||v.contains("food")||v.contains("bait"))
            && (items.consumeOne(chronicle,"wild_berries",at) || items.consumeOne(chronicle,"blackberry",at) || items.consumeOne(chronicle,"raw_game_meat",at) || items.consumeOne(chronicle,"dandelion",at));

        java.util.Map<String,Object> existing = jdbc.query(
            "SELECT id,trust_level,interaction_count,bond_stage FROM wildlife_bond WHERE chronicle_id=? AND population_id=? FOR UPDATE",
            rs -> rs.next() ? java.util.Map.of("id",rs.getObject(1,UUID.class),"trust",rs.getInt(2),"count",rs.getInt(3),"stage",rs.getString(4)) : null,
            chronicle, t.populationId());
        int trust = existing == null ? 0 : (Integer) existing.get("trust");
        int count = existing == null ? 0 : (Integer) existing.get("count");

        // Trust moves by species willingness, tempered by how the chronicle carried
        // themselves. A weapon in hand reads as threat whatever the intent.
        int delta;
        String note;
        if (armed) { delta = -12; note = "It will not let you close with that in your hand. Whatever ground you had is gone."; }
        else {
            delta = Math.max(2, t.tamability() / 10) + (offersFood ? 8 : 0);
            if ("FLEEING".equals(t.behavior()) || "ALERT".equals(t.behavior())) delta = Math.max(1, delta / 2);
            note = offersFood ? "It takes what you offer, and does not move away afterwards."
                 : "It lets you come nearer than last time, and holds there, watching.";
        }
        int newTrust = Math.max(0, Math.min(100, trust + delta));
        String stage = newTrust >= 95 ? "TAMED" : newTrust >= 75 ? "BONDED" : newTrust >= 50 ? "TOLERANT" : newTrust >= 25 ? "CAUTIOUS" : newTrust > 0 ? "WARY" : "WILD";

        UUID bondId;
        if (existing == null) {
            bondId = UUID.randomUUID();
            jdbc.update("INSERT INTO wildlife_bond (id,chronicle_id,population_id,bond_stage,trust_level,interaction_count,last_interaction_at) VALUES (?,?,?,?,?,?,?)",
                bondId, chronicle, t.populationId(), stage, newTrust, 1, Timestamp.from(at));
        } else {
            bondId = (UUID) existing.get("id");
            jdbc.update("UPDATE wildlife_bond SET bond_stage=?,trust_level=?,interaction_count=?,last_interaction_at=? WHERE id=?",
                stage, newTrust, count + 1, Timestamp.from(at), bondId);
        }
        record(chronicle, t.populationId(), chunk, "APPROACHED", at, action);

        // Reaching TAMED makes the animal a physical thing in the world, owned like
        // any other object, and starts whatever it produces on its clock.
        if ("TAMED".equals(stage) && (existing == null || !"TAMED".equals(existing.get("stage")))) {
            UUID beast = UUID.randomUUID();
            jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'CREATURE',?,?)", beast, display(t.species()), chronicle);
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'TAMED',jsonb_build_object('species',?))", beast, Timestamp.from(at), t.species());
            jdbc.update("UPDATE wildlife_bond SET tamed_object_id=? WHERE id=?", beast, bondId);
            jdbc.update("UPDATE wildlife_population SET population_count=GREATEST(0,population_count-1) WHERE id=?", t.populationId());
            for (java.util.Map<String,Object> y : jdbc.queryForList("SELECT item_key,interval_hours FROM tamed_yield WHERE species_key=?", t.species()))
                jdbc.update("INSERT INTO tamed_production (bond_id,item_key,interval_hours,last_yielded_at) VALUES (?,?,?,?)", bondId, y.get("item_key"), y.get("interval_hours"), Timestamp.from(at));
            return new EncounterResult("SUCCEEDED","It comes to you without being called, and stays when you turn away. Whatever it was before, it is yours now.");
        }
        return new EncounterResult(armed ? "PARTIAL" : "SUCCEEDED", note);
    }

    private record Tamable(UUID populationId, String species, int tamability, String behavior) { }

    /**
     * Leave bait on the ground to draw animals toward it. What comes depends on what
     * was left: meat brings predators and scavengers, fruit and greens bring browsers.
     * The bait is really consumed and the lure really expires, so this is a decision
     * with a cost rather than a free summons.
     */
    @Transactional
    public EncounterResult lure(UUID chronicle, UUID chunk, Instant at, String actionText) {
        String v = actionText.toLowerCase(java.util.Locale.ROOT);
        // Find a bait the chronicle actually carries; prefer one they named.
        java.util.List<java.util.Map<String,Object>> baits = jdbc.queryForList(
            "SELECT b.item_key,b.draws_role,b.potency,b.hours_active,d.display_name FROM bait_profile b " +
            "JOIN item_definition d ON d.item_key=b.item_key ORDER BY b.potency DESC");
        java.util.Map<String,Object> chosen = null;
        for (java.util.Map<String,Object> b : baits) {
            String key = (String) b.get("item_key");
            if (!items.hasAtLeast(chronicle, key, 1)) continue;
            if (v.contains(key.replace('_',' '))) { chosen = b; break; }
            if (chosen == null) chosen = b;
        }
        if (chosen == null) return new EncounterResult("FAILED","You look through what you carry for something worth leaving out, and find nothing that would draw anything in.");

        String itemKey = (String) chosen.get("item_key");
        items.consumeOne(chronicle, itemKey, at);
        int hours = ((Number) chosen.get("hours_active")).intValue();
        jdbc.update("INSERT INTO placed_lure (chunk_id,chronicle_id,bait_item_key,draws_role,placed_at,expires_at) VALUES (?,?,?,?,?,?)",
            chunk, chronicle, itemKey, chosen.get("draws_role"), Timestamp.from(at), Timestamp.from(at.plus(Duration.ofHours(hours))));
        // Bait shifts what the drawn class of animal is doing in this ground.
        jdbc.update("UPDATE wildlife_population wp SET behavior_state='FORAGING' FROM ecology_site es " +
            "WHERE es.id=wp.site_id AND es.chunk_id=? AND wp.ecological_role=? AND wp.behavior_state IN ('RESTING','SHELTERING')",
            chunk, chosen.get("draws_role"));
        return new EncounterResult("SUCCEEDED","You set the " + ((String)chosen.get("display_name")).toLowerCase() + " down in the open and withdraw from it. The ground smells of it now, and will for a while.");
    }

    /**
     * Build a trap and leave it standing. Unlike a snare worked by hand, a placed
     * trap is a physical object in the world that catches in its own time — the
     * chronicle sets it, goes away, and learns later what walked into it.
     */
    @Transactional
    public EncounterResult setTrap(UUID chronicle, UUID chunk, Instant at, String actionText) {
        String v = actionText.toLowerCase(java.util.Locale.ROOT);
        String kind = v.contains("deadfall") || v.contains("falling") ? "DEADFALL"
                    : v.contains("pit") ? "PIT"
                    : v.contains("fish") || v.contains("weir") ? "FISH_TRAP"
                    : v.contains("cage") || v.contains("box") ? "CAGE" : "SNARE";
        // Every trap costs real material to build.
        boolean built = switch (kind) {
            case "DEADFALL" -> items.hasAtLeast(chronicle,"field_stone",1) && items.hasAtLeast(chronicle,"dry_branch",1)
                && items.consumeOne(chronicle,"field_stone",at) && items.consumeOne(chronicle,"dry_branch",at);
            case "FISH_TRAP", "CAGE" -> items.hasAtLeast(chronicle,"hazel_rod",2)
                && items.consumeOne(chronicle,"hazel_rod",at) && items.consumeOne(chronicle,"hazel_rod",at);
            case "PIT" -> items.hasAtLeast(chronicle,"dry_branch",2)
                && items.consumeOne(chronicle,"dry_branch",at) && items.consumeOne(chronicle,"dry_branch",at);
            default -> items.hasAtLeast(chronicle,"plant_fiber",2)
                && items.consumeOne(chronicle,"plant_fiber",at) && items.consumeOne(chronicle,"plant_fiber",at);
        };
        if (!built) return new EncounterResult("FAILED","You work at it for a while, but you do not have what a trap of that kind needs.");

        // Bait it too, if the chronicle carries something and says so.
        String bait = null;
        if (v.contains("bait") || v.contains("bait it") || v.contains("with meat") || v.contains("with berries")) {
            for (java.util.Map<String,Object> b : jdbc.queryForList("SELECT item_key FROM bait_profile ORDER BY potency DESC")) {
                String key = (String) b.get("item_key");
                if (items.hasAtLeast(chronicle,key,1) && items.consumeOne(chronicle,key,at)) { bait = key; break; }
            }
        }
        UUID trap = UUID.randomUUID();
        String label = switch (kind) { case "DEADFALL" -> "Deadfall trap"; case "PIT" -> "Covered pit"; case "FISH_TRAP" -> "Woven fish trap"; case "CAGE" -> "Woven cage trap"; default -> "Set snare"; };
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'TRAP',?,?)", trap, label, chunk);
        jdbc.update("INSERT INTO placed_trap (object_id,chunk_id,trap_kind,set_by,set_at,baited_with) VALUES (?,?,?,?,?,?)",
            trap, chunk, kind, chronicle, Timestamp.from(at), bait);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'TRAP_SET',jsonb_build_object('kind',?))", trap, Timestamp.from(at), kind);
        return new EncounterResult("SUCCEEDED","You build the " + label.toLowerCase() + " and set it across the run" + (bait != null ? ", baited" : "") + ". It stands there after you have gone. What comes to it is not yours to decide.");
    }

    /**
     * Check a trap the chronicle has left standing. A trap that has been out long
     * enough, in ground where something suitable moves, may have caught.
     */
    @Transactional
    public EncounterResult checkTrap(UUID chronicle, UUID chunk, UUID action, Instant at) {
        java.util.Map<String,Object> trap = jdbc.query(
            "SELECT object_id,trap_kind,set_at,baited_with FROM placed_trap WHERE chunk_id=? AND NOT sprung ORDER BY set_at LIMIT 1 FOR UPDATE",
            rs -> rs.next() ? java.util.Map.of("id",rs.getObject(1,UUID.class),"kind",rs.getString(2),"set_at",rs.getTimestamp(3).toInstant()) : null, chunk);
        if (trap == null) return new EncounterResult("FAILED","There is nothing of yours standing here to check.");
        Instant setAt = (Instant) trap.get("set_at");
        UUID trapId = (UUID) trap.get("id");
        long hoursOut = Duration.between(setAt, at).toHours();
        if (hoursOut < 2) return new EncounterResult("PARTIAL","The trap stands as you left it. Not enough time has passed for anything to have found it.");

        String kind = (String) trap.get("kind");
        boolean aquatic = "FISH_TRAP".equals(kind);
        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, chunk);
        java.util.List<String> prey = jdbc.queryForList(
            aquatic ? "SELECT species_key FROM wildlife_species WHERE movement_class='AQUATIC' AND biome_affinity ILIKE ? ORDER BY species_key"
                    : "SELECT species_key FROM wildlife_species WHERE size_tier IN ('TINY','SMALL','MEDIUM') AND movement_class IN ('TERRESTRIAL','AERIAL') AND biome_affinity ILIKE ? ORDER BY species_key",
            String.class, "%"+biome+"%");
        if (prey.isEmpty()) return new EncounterResult("PARTIAL","The trap is untouched. Nothing that uses this ground has come near it.");
        // The longer it has stood, the likelier it has caught — up to a point.
        int chance = (int) Math.min(70, 15 + hoursOut * 6);
        if (Math.floorMod(action.hashCode(), 100) >= chance) {
            jdbc.update("UPDATE placed_trap SET checked_at=? WHERE object_id=?", Timestamp.from(at), trapId);
            return new EncounterResult("PARTIAL","The trap is exactly as you set it. Nothing has come to it yet.");
        }
        String caught = prey.get(Math.floorMod(action.hashCode() >>> 4, prey.size()));
        takeSpeciesDrops(chronicle, caught, at);
        if (aquatic) { UUID fish = items.createCarriedItem(chronicle,"raw_fish","Raw fish",at,"TAKEN_FROM_TRAP"); food.registerRaw(fish,at); }
        else { UUID meat = items.createCarriedItem(chronicle,"raw_game_meat","Raw game meat",at,"TAKEN_FROM_TRAP"); food.registerRaw(meat,at); }
        jdbc.update("UPDATE placed_trap SET sprung=true,checked_at=?,caught_species=? WHERE object_id=?", Timestamp.from(at), caught, trapId);
        jdbc.update("UPDATE world_object SET lifecycle_state='DESTROYED',destroyed_at=?,destroyed_location_id=current_location_id,destroyed_cause='TRAP_SPRUNG',current_location_id=NULL WHERE id=?", Timestamp.from(at), trapId);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'TRAP_SPRUNG',jsonb_build_object('species',?))", trapId, Timestamp.from(at), caught);
        return new EncounterResult("SUCCEEDED","The trap has done its work. A " + display(caught) + " lies in it, long still by the time you reach it.");
    }

    /** Append a contact to the immutable wildlife-event ledger. */
    private void record(UUID chronicle, UUID population, UUID chunk, String kind, Instant at, UUID action) {
        jdbc.update("INSERT INTO chronicle_wildlife_event (chronicle_id,population_id,chunk_id,event_kind,occurred_at,source_action_id) VALUES (?,?,?,?,?,?)",
            chronicle, population, chunk, kind, Timestamp.from(at), action);
    }

    private record Threat(UUID populationId, String species, String behavior, Integer baseResistance, boolean ambushHunter, String sizeTier) { }

    /** Take a species' catalogued yields from a carcass. Returns how many items came away. */
    private int takeSpeciesDrops(UUID chronicle, String species, Instant at) {
        java.util.List<java.util.Map<String,Object>> drops = jdbc.queryForList("SELECT item_key,yield_min,yield_max,rarity FROM wildlife_drop WHERE species_key=? ORDER BY rarity DESC", species);
        int taken = 0;
        for (java.util.Map<String,Object> d : drops) {
            if (Math.random() > ((Number)d.get("rarity")).doubleValue()) continue;
            String itemKey = (String) d.get("item_key");
            int lo = ((Number)d.get("yield_min")).intValue(), hi = ((Number)d.get("yield_max")).intValue();
            int want = lo + (hi > lo ? (int)(Math.random()*(hi-lo+1)) : 0);
            String name = jdbc.queryForObject("SELECT display_name FROM item_definition WHERE item_key=?", String.class, itemKey);
            for (int i = 0; i < want; i++) { items.createCarriedItem(chronicle, itemKey, name, at, "HARVESTED_FROM_CARCASS"); taken++; }
        }
        return taken;
    }

    /**
     * Take a fish from the water. Method decides the odds: bare hands rarely work,
     * a spear is a real tool for it, a woven trap works patiently and well. The
     * fish species present are those the registry places in this biome.
     */
    @Transactional
    public EncounterResult fish(UUID chronicle, UUID chunk, UUID action, Instant at, String actionText) {
        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, chunk);
        String v = actionText.toLowerCase(java.util.Locale.ROOT);
        String method; int chance;
        if (v.contains("trap") || v.contains("basket") || v.contains("weir")) { method="TRAP"; chance=75; }
        else if (v.contains("spear") && items.hasAtLeast(chronicle,"primitive_spear",1)) { method="SPEAR"; chance=55; }
        else if (v.contains("line") || v.contains("hook")) { method="LINE"; chance=45; }
        else { method="BARE_HAND"; chance=20; }
        // Bait (#75): a worm on the hook, in the trap, or in the hand draws fish that clear water would not —
        // it is spent whether or not the fish takes. Bare-hand grabbing is the one method a worm does not help.
        boolean baited = !method.equals("BARE_HAND") && items.hasAtLeast(chronicle,"earthworm",1) && items.consumeOne(chronicle,"earthworm",at);
        if (baited) chance = Math.min(90, chance + 20);
        java.util.List<String> species = jdbc.queryForList("SELECT species_key FROM wildlife_species WHERE movement_class='AQUATIC' AND biome_affinity ILIKE ? ORDER BY species_key", String.class, "%"+biome+"%");
        if (species.isEmpty()) return new EncounterResult("FAILED","You watch the ground a while. There is no water here that holds anything worth taking.");
        if (Math.floorMod(action.hashCode(),100) >= chance)
            return new EncounterResult("FAILED", method.equals("BARE_HAND")
                ? "You stand in the cold water with your hands open, and whatever moves past is gone before you close them."
                : "You work the water patiently, and it gives up nothing this time.");
        String caught = species.get(Math.floorMod(action.hashCode(), species.size()));
        int got = 0;
        for (java.util.Map<String,Object> d : jdbc.queryForList("SELECT item_key,yield_min,yield_max,rarity FROM wildlife_drop WHERE species_key=?", caught)) {
            if (Math.random() > ((Number)d.get("rarity")).doubleValue()) continue;
            String itemKey=(String)d.get("item_key");
            int lo=((Number)d.get("yield_min")).intValue(), hi=((Number)d.get("yield_max")).intValue();
            int want = lo + (hi>lo ? (int)(Math.random()*(hi-lo+1)) : 0);
            String name = jdbc.queryForObject("SELECT display_name FROM item_definition WHERE item_key=?", String.class, itemKey);
            for (int i=0;i<want;i++) { UUID id=items.createCarriedItem(chronicle,itemKey,name,at,"CAUGHT_FROM_WATER"); if("raw_fish".equals(itemKey)){ food.registerRaw(id,at); jdbc.update("INSERT INTO aquatic_catch (object_id,species_key,method_used,caught_at) VALUES (?,?,?,?)",id,caught,method,Timestamp.from(at)); } got++; }
        }
        if (got==0) return new EncounterResult("FAILED","Something takes and slips free again, and the water closes over it.");
        return new EncounterResult("SUCCEEDED","The "+display(caught)+" comes up out of the water, cold and heavy and still fighting.");
    }

    /**
     * Set a snare for small ground game and low-flying birds. The catch is not
     * immediate — the snare is placed, and what walks into it does so in its own
     * time. Requires cordage to build.
     */
    @Transactional
    public EncounterResult snare(UUID chronicle, UUID chunk, UUID action, Instant at) {
        if (!items.hasAtLeast(chronicle,"plant_fiber",2) && !items.hasAtLeast(chronicle,"hazel_rod",1))
            return new EncounterResult("FAILED","You crouch over the run and find you have nothing to build a snare from.");
        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, chunk);
        java.util.List<String> prey = jdbc.queryForList("SELECT species_key FROM wildlife_species WHERE size_tier IN ('TINY','SMALL') AND movement_class IN ('TERRESTRIAL','AERIAL') AND biome_affinity ILIKE ? ORDER BY species_key", String.class, "%"+biome+"%");
        if (prey.isEmpty()) return new EncounterResult("FAILED","You set a snare across the run, but nothing here uses this ground.");
        if (items.hasAtLeast(chronicle,"plant_fiber",2)) { items.consumeOne(chronicle,"plant_fiber",at); items.consumeOne(chronicle,"plant_fiber",at); }
        else items.consumeOne(chronicle,"hazel_rod",at);
        if (Math.floorMod(action.hashCode(),100) >= 45)
            return new EncounterResult("PARTIAL","You set the snare across a run and leave it standing. Whether anything comes to it is not yours to decide.");
        String caught = prey.get(Math.floorMod(action.hashCode(), prey.size()));
        int got = takeSpeciesDrops(chronicle, caught, at);
        UUID meat = items.createCarriedItem(chronicle,"raw_game_meat","Raw game meat",at,"TAKEN_FROM_SNARE"); food.registerRaw(meat,at);
        return new EncounterResult("SUCCEEDED","The snare has held. A "+display(caught)+" hangs in the cord, long still by the time you reach it."+(got>0?"":""));
    }

    private int meatFor(String species) { return species.contains("bear") || species.contains("elk") ? 4 : species.contains("deer") || species.contains("boar") ? 3 : 1; }
    private String display(String species) { return species.replace('_',' '); }
    private record Encounter(UUID populationId,String species,String role,String behavior,int population,String movementClass,Integer baseResistance,boolean ambushHunter){}
    private record Combatant(int energy,int injury,int pain,int handWeapon,int stones,int armour){}
    private record Carcass(UUID id,String species,int meat,boolean hide){}
    public record EncounterResult(String outcome,String narration){}
    public record HarvestResult(String outcome,String narration){}
}
