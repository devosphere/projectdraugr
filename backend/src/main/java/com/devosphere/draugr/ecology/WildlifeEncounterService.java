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
        Encounter candidate=jdbc.query("SELECT wp.id,wp.species_key,wp.ecological_role,wp.behavior_state,wp.population_count FROM wildlife_population wp JOIN ecology_site es ON es.id=wp.site_id WHERE es.chunk_id=? AND wp.population_count>0 ORDER BY CASE wp.ecological_role WHEN 'CARNIVORE' THEN 0 WHEN 'OMNIVORE' THEN 1 ELSE 2 END LIMIT 1 FOR UPDATE",rs->rs.next()?new Encounter(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4),rs.getInt(5)):null,chunk);
        if(candidate==null)return new EncounterResult("FAILED","The ground answers only with rain and the small movements of the forest.");
        Combatant body=jdbc.query("SELECT p.energy_level,p.injury_severity,p.pain_level,COALESCE((SELECT COUNT(*) FROM equipment_attachment e JOIN item_instance i ON i.object_id=e.item_id WHERE e.chronicle_id=? AND e.body_position IN ('HAND_LEFT','HAND_RIGHT') AND i.item_key IN ('stone_axe','primitive_spear')),0),COALESCE((SELECT COUNT(*) FROM world_object w JOIN item_instance i ON i.object_id=w.id WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND i.item_key='field_stone'),0) FROM chronicle_physiology p WHERE p.chronicle_id=?",rs->rs.next()?new Combatant(rs.getInt(1),rs.getInt(2),rs.getInt(3),rs.getInt(4),rs.getInt(5)):new Combatant(0,100,100,0,0),chronicle,chronicle,chronicle);
        int capability = body.energy()/3 - body.injury()/2 - body.pain()/3 + body.handWeapon()*35 + Math.min(10,body.stones()*2) + tacticBonus;
        int resistance = "CARNIVORE".equals(candidate.role()) ? 85 : "OMNIVORE".equals(candidate.role()) ? 65 : 42;
        if("HUNTING".equals(candidate.behavior())) resistance += 10;
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
        int severity="CARNIVORE".equals(candidate.role())?22:"OMNIVORE".equals(candidate.role())?12:4;
        if("RESTING".equals(candidate.behavior()) || "SHELTERING".equals(candidate.behavior())) severity=Math.max(2,severity-5);
        physiology.applyInjury(chronicle,severity,action,at,"WILDLIFE_CONTACT");
        jdbc.update("UPDATE wildlife_population SET behavior_state='FLEEING' WHERE id=?",candidate.populationId());
        return new EncounterResult("PARTIAL","The " + display(candidate.species()) + " moves with sudden force. The encounter leaves its mark before the forest takes it back.");
    }

    @Transactional
    public HarvestResult harvest(UUID chronicle, UUID chunk, UUID action, Instant at) {
        Carcass carcass=jdbc.query("SELECT wc.object_id,wc.species_key,wc.remaining_meat_units,wc.hide_available FROM wildlife_carcass wc JOIN world_object w ON w.id=wc.object_id WHERE w.current_location_id=? AND w.lifecycle_state='ACTIVE' ORDER BY wc.died_at LIMIT 1 FOR UPDATE",rs->rs.next()?new Carcass(rs.getObject(1,UUID.class),rs.getString(2),rs.getInt(3),rs.getBoolean(4)):null,chunk);
        if(carcass==null)return new HarvestResult("FAILED","You search the ground carefully, then leave it as you found it.");
        if(carcass.meat()>0) { UUID meat=items.createCarriedItem(chronicle,"raw_game_meat","Raw game meat",at,"HARVESTED_FROM_CARCASS"); food.registerRaw(meat,at); jdbc.update("UPDATE wildlife_carcass SET remaining_meat_units=remaining_meat_units-1 WHERE object_id=?",carcass.id()); }
        else if(carcass.hide()) { items.createCarriedItem(chronicle,"animal_hide","Animal hide",at,"HARVESTED_FROM_CARCASS"); jdbc.update("UPDATE wildlife_carcass SET hide_available=false WHERE object_id=?",carcass.id()); }
        else return new HarvestResult("FAILED","The remains offer nothing more that you can carry away.");
        Integer remaining=jdbc.queryForObject("SELECT remaining_meat_units FROM wildlife_carcass WHERE object_id=?",Integer.class,carcass.id()); Boolean hide=jdbc.queryForObject("SELECT hide_available FROM wildlife_carcass WHERE object_id=?",Boolean.class,carcass.id());
        if((remaining==null||remaining==0) && Boolean.FALSE.equals(hide)) { Timestamp ts=Timestamp.from(at); jdbc.update("UPDATE world_object SET lifecycle_state='DESTROYED',destroyed_at=?,current_location_id=NULL WHERE id=?",ts,carcass.id()); jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CARCASS_EXHAUSTED','{}'::jsonb)",carcass.id(),ts); }
        return new HarvestResult("SUCCEEDED","You work carefully over the remains and take what you can carry.");
    }
    private int meatFor(String species) { return species.contains("bear") || species.contains("elk") ? 4 : species.contains("deer") || species.contains("boar") ? 3 : 1; }
    private String display(String species) { return species.replace('_',' '); }
    private record Encounter(UUID populationId,String species,String role,String behavior,int population){}
    private record Combatant(int energy,int injury,int pain,int handWeapon,int stones){}
    private record Carcass(UUID id,String species,int meat,boolean hide){}
    public record EncounterResult(String outcome,String narration){}
    public record HarvestResult(String outcome,String narration){}
}
