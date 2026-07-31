package com.devosphere.draugr.ecology;

import com.devosphere.draugr.chronicle.ChroniclePhysiologyService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.survival.FoodPreservationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        Encounter candidate=jdbc.query("SELECT wp.id,wp.species_key,wp.ecological_role,wp.behavior_state,wp.population_count,ws.movement_class,ws.base_resistance,ws.ambush_hunter FROM wildlife_population wp JOIN ecology_site es ON es.id=wp.site_id LEFT JOIN wildlife_species ws ON ws.species_key=wp.species_key WHERE es.chunk_id=? AND wp.population_count>0 ORDER BY CASE wp.ecological_role WHEN 'CARNIVORE' THEN 0 WHEN 'OMNIVORE' THEN 1 ELSE 2 END LIMIT 1 FOR UPDATE",rs->rs.next()?new Encounter(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4),rs.getInt(5),rs.getString(6),(Integer)rs.getObject(7),rs.getBoolean(8)):null,chunk);
        if(candidate==null)return new EncounterResult("FAILED","The ground answers only with rain and the small movements of the forest.");
        Combatant body=jdbc.query("SELECT p.energy_level,p.injury_severity,p.pain_level,COALESCE((SELECT COUNT(*) FROM equipment_attachment e JOIN item_instance i ON i.object_id=e.item_id WHERE e.chronicle_id=? AND e.body_position IN ('HAND_LEFT','HAND_RIGHT') AND i.item_key IN ('stone_axe','primitive_spear')),0),COALESCE((SELECT COUNT(*) FROM world_object w JOIN item_instance i ON i.object_id=w.id WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND i.item_key='field_stone'),0) FROM chronicle_physiology p WHERE p.chronicle_id=?",rs->rs.next()?new Combatant(rs.getInt(1),rs.getInt(2),rs.getInt(3),rs.getInt(4),rs.getInt(5)):new Combatant(0,100,100,0,0),chronicle,chronicle,chronicle);
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
    private record Combatant(int energy,int injury,int pain,int handWeapon,int stones){}
    private record Carcass(UUID id,String species,int meat,boolean hide){}
    public record EncounterResult(String outcome,String narration){}
    public record HarvestResult(String outcome,String narration){}
}
