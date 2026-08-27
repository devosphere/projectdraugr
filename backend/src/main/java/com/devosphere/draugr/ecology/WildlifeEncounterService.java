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
        // One pass over what is worn and one over what is carried, aggregated conditionally — the combat-relevant
        // tally the encounter needs, replacing the dozen correlated COUNT subqueries this grew from. Column order
        // is the Combatant record's: energy, injury, pain, handWeapon, stones, armour, poison, lightShield,
        // rawhideShield, blunt, sling, javelin, bow, arrows. Worn items count from equipment_attachment; thrown/
        // held stock (stones, sling, javelin, bow, arrows) counts from what the chronicle owns and carries.
        Combatant body=jdbc.query(
            "SELECT p.energy_level,p.injury_severity,p.pain_level,eq.hand_weapon,own.stones,eq.armour,eq.poison," +
            "eq.light_shield,eq.rawhide_shield,eq.blunt,own.sling,own.javelin,own.bow,own.arrows,eq.soft_armour,eq.hardened,eq.bronze,eq.iron,eq.steel,eq.plate " +
            "FROM chronicle_physiology p " +
            "LEFT JOIN LATERAL (SELECT " +
            "  COALESCE(SUM(CASE WHEN e.body_position IN ('HAND_LEFT','HAND_RIGHT') AND i.item_key IN ('stone_axe','primitive_spear','poisoned_spear','fire_hardened_spear','copper_axe','bronze_spear','bronze_axe','iron_axe','steel_axe') THEN 1 ELSE 0 END),0) hand_weapon," +
            "  COALESCE(SUM(CASE WHEN i.item_key IN ('scale_armour','chitin_helm','war_shield') THEN 1 ELSE 0 END),0) armour," +
            "  COALESCE(SUM(CASE WHEN i.item_key='poisoned_spear' THEN 1 ELSE 0 END),0) poison," +
            "  COALESCE(SUM(CASE WHEN i.item_key IN ('bark_shield','woven_reed_shield') THEN 1 ELSE 0 END),0) light_shield," +
            "  COALESCE(SUM(CASE WHEN i.item_key='rawhide_shield' THEN 1 ELSE 0 END),0) rawhide_shield," +
            "  COALESCE(SUM(CASE WHEN e.body_position IN ('HAND_LEFT','HAND_RIGHT') AND i.item_key IN ('wooden_club','stone_club','stone_maul','stone_hammer') THEN 1 ELSE 0 END),0) blunt," +
            "  COALESCE(SUM(CASE WHEN i.item_key IN ('leather_armor','leather_helm_cap','leather_bracer') THEN 1 ELSE 0 END),0) soft_armour," +
            "  COALESCE(SUM(CASE WHEN e.body_position IN ('HAND_LEFT','HAND_RIGHT') AND i.item_key IN ('fire_hardened_spear','copper_axe') THEN 1 ELSE 0 END),0) hardened," +
            "  COALESCE(SUM(CASE WHEN e.body_position IN ('HAND_LEFT','HAND_RIGHT') AND i.item_key IN ('bronze_spear','bronze_axe') THEN 1 ELSE 0 END),0) bronze," +
            "  COALESCE(SUM(CASE WHEN e.body_position IN ('HAND_LEFT','HAND_RIGHT') AND i.item_key='iron_axe' THEN 1 ELSE 0 END),0) iron," +
            "  COALESCE(SUM(CASE WHEN e.body_position IN ('HAND_LEFT','HAND_RIGHT') AND i.item_key='steel_axe' THEN 1 ELSE 0 END),0) steel," +
            "  COALESCE(SUM(CASE WHEN i.item_key='bronze_cuirass' THEN 12 WHEN i.item_key='iron_cuirass' THEN 20 WHEN i.item_key='steel_cuirass' THEN 28 ELSE 0 END),0) plate" +
            "  FROM equipment_attachment e JOIN item_instance i ON i.object_id=e.item_id WHERE e.chronicle_id=p.chronicle_id) eq ON true " +
            "LEFT JOIN LATERAL (SELECT " +
            "  COALESCE(SUM(CASE WHEN i.item_key='field_stone' THEN 1 ELSE 0 END),0) stones," +
            "  COALESCE(SUM(CASE WHEN i.item_key='sling' THEN 1 ELSE 0 END),0) sling," +
            "  COALESCE(SUM(CASE WHEN i.item_key='javelin' THEN 1 ELSE 0 END),0) javelin," +
            "  COALESCE(SUM(CASE WHEN i.item_key='hunting_bow' THEN 1 ELSE 0 END),0) bow," +
            "  COALESCE(SUM(CASE WHEN i.item_key='hunting_arrow' THEN 1 ELSE 0 END),0) arrows" +
            "  FROM world_object w JOIN item_instance i ON i.object_id=w.id WHERE w.current_owner_id=p.chronicle_id AND w.lifecycle_state='ACTIVE') own ON true " +
            "WHERE p.chronicle_id=?",
            rs->rs.next()?new Combatant(rs.getInt(1),rs.getInt(2),rs.getInt(3),rs.getInt(4),rs.getInt(5),rs.getInt(6),rs.getInt(7),rs.getInt(8),rs.getInt(9),rs.getInt(10),rs.getInt(11),rs.getInt(12),rs.getInt(13),rs.getInt(14),rs.getInt(15),rs.getInt(16),rs.getInt(17),rs.getInt(18),rs.getInt(19),rs.getInt(20)):new Combatant(0,100,100,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0),chronicle);
        // A creature on the wing cannot be reached by a hand weapon. Throwing stones
        // is the only contact a chronicle has with it. The narrator witnesses the
        // futility without naming what would be needed.
        boolean archery = body.bow()>0 && body.arrows()>0; // a bow is only a weapon with arrows to loose
        if("AERIAL".equals(candidate.movementClass()) && body.stones()==0 && body.javelin()==0 && !archery)
            return new EncounterResult("FAILED","It stays above you, well out of reach. Whatever you do with your hands, the air between you does not close.");
        // A venom-tipped spear does part of the killing itself: the animal is envenomed by the first wound and
        // fails faster, so a poisoned weapon reaches the kill where a plain one would only wound (#75).
        // A sling turns thrown stones from a lob into a real cast: the stone term climbs from up-to-10 to up-to-24.
        int thrownStones = body.sling()>0 ? Math.min(24,body.stones()*5) : Math.min(10,body.stones()*2);
        // A bow with arrows is the strongest reach a first-era Chronicle has — it strikes hard from a distance,
        // but only ever with an arrow nocked (no arrows, no shot).
        // Fire in the fight (#126): the same flame a predator will not ambush through tells in the close too.
        // A lit fire at hand, or a brand raised from the carried stock, cows the animal — it presses less and
        // breaks off sooner, so the Chronicle's effort counts for more. A creature that breathes fire is, of
        // course, unmoved by it. Weaker than a real weapon; it never makes a bare-handed stand a sure thing.
        int fireEdge = 0;
        boolean fireBreather = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM monster_profile WHERE species_key=? AND special_mechanic='FIRE_BREATH')", Boolean.class, candidate.species()));
        if (!fireBreather) {
            boolean fireAtHand = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object w ON w.id=cp.object_id JOIN fire_state fs ON fs.construction_id=cp.object_id WHERE w.current_location_id=? AND fs.active=true AND w.lifecycle_state='ACTIVE')", Boolean.class, chunk));
            boolean brandInHand = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM world_object w JOIN item_instance i ON i.object_id=w.id WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND i.item_key='resin_torch')", Boolean.class, chronicle));
            fireEdge = fireAtHand ? 15 : (brandInHand ? 10 : 0);
        }
        // A keen, edge-holding weapon bites deeper than a plain or knapped one (#75/#77/#180): a fire-hardened
        // spear point, or a forged copper axe. Each counts as a hand weapon (+35) and bites a little deeper on top
        // (+10), so a copper axe out-fights a stone one and a hardened spear a green one — the ordering runs
        // primitive < fire-hardened, and stone axe < copper axe < poisoned. Closes the fire_hardened_spear dead-read
        // (hardening once DISARMED the Chronicle) and gives the smelted copper axe its terminal edge in a fight.
        // The metal ladder in the fight (#180/#184-187): each harder metal keeps a keener edge and bites deeper on
        // top of the hand-weapon count — bronze +18 over copper, iron +25, steel +35. So the ordering runs
        // stone(+35) < copper(+45) < bronze(+53) < iron(+60) < steel(+70), the terminal payoff of the extraction chain.
        int capability = body.energy()/3 - body.injury()/2 - body.pain()/3 + body.handWeapon()*35 + (body.hardened()>0?10:0) + (body.bronze()>0?18:0) + (body.iron()>0?25:0) + (body.steel()>0?35:0) + (body.blunt()>0?22:0) + (body.javelin()>0?25:0) + (archery?40:0) + thrownStones + Math.min(20,body.poison()*20) + tacticBonus + fireEdge;
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
        // Armour and shields take part of the blow, graded by what they are (#75, #126, #257): worn scale/shell/
        // war shield turn the most aside; a rawhide shield rather less; worn soft leather (armour, bracers, a helm
        // cap) and a bark or woven-reed shield least of all — leather was craftable but turned no blow at all
        // until now. An improvised guard is better than a bare arm, but no cover makes a body untouchable, so the
        // worst a mauling can do is only blunted, never erased.
        // Metal plate — a forged bronze or iron cuirass — turns a blow far better than knapped scale or boiled
        // leather: it is the defensive counterpart to the metal edge, so the metals a Chronicle smelts protect as
        // well as they kill (#180). The plate tier mirrors the edge ladder: a bronze cuirass turns +12, a harder iron
        // one +20, and a case-hardened steel one +28 — all above knapped scale/shell/war-shield (+7) and soft leather
        // (+4). The `plate` column already carries these blunting points (bronze 12 / iron 20 / steel 28), so it adds
        // in directly, not as a count.
        int blunted = body.plate() + body.armour()*7 + body.rawhideShield()*6 + body.lightShield()*4 + body.softArmour()*4;
        if (blunted > 0) severity = Math.max(1, severity - blunted);
        physiology.applyInjury(chronicle,severity,action,at,"WILDLIFE_CONTACT");
        // A venomous ordinary animal — the common adder among wildlife (#V174) — does more than wound: its bite
        // carries venom that works inward after the snake is gone. This is the ordinary-wildlife counterpart of a
        // monster's VENOM_WOUND, so a real viper envenoms the way the giant hornet's sting does — a sick, spreading
        // illness on top of the bite. Armour blunts the wound but not the venom, which reaches through any scratch it
        // opens. Only an actual strike reaches here (a fled or killed animal never does), so only a real bite envenoms.
        if (isVenomousSpecies(candidate.species())) {
            physiology.applyIllness(chronicle, 10, action, at, "VENOMOUS_BITE");
            if (monsterMark == null) monsterMark = "The bite itself is almost nothing — two small marks, quickly made. Then it is not nothing: a cold heat spreads out from them, climbing where no blow ever reached.";
        }
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
        boolean toxicFlesh=false;
        if(carcass.meat()>0) {
            // A toxic species (fire salamander, toad, newt — #V173) is stripped but its flesh is left: it is poison,
            // no food for anyone. The carcass still depletes — the meat is worked off, just discarded, not carried.
            if(isToxicSpecies(carcass.species())) { toxicFlesh=true; }
            else { UUID meat=items.createCarriedItem(chronicle,"raw_game_meat","Raw game meat",at,"HARVESTED_FROM_CARCASS"); food.registerRaw(meat,at); }
            jdbc.update("UPDATE wildlife_carcass SET remaining_meat_units=remaining_meat_units-1 WHERE object_id=?",carcass.id());
        }
        // The meat is off; what remains is the species' own yield — a wolf's pelt and
        // fangs, a deer's antler and sinew — drawn from wildlife_drop (V42). The legacy
        // generic hide stands in for species not yet catalogued.
        else if(carcass.hide()) { int taken=takeSpeciesDrops(chronicle,carcass.species(),at); if(taken==0) items.createCarriedItem(chronicle,"animal_hide","Animal hide",at,"HARVESTED_FROM_CARCASS"); jdbc.update("UPDATE wildlife_carcass SET hide_available=false WHERE object_id=?",carcass.id()); }
        else return new HarvestResult("FAILED","The remains offer nothing more that you can carry away.");
        Integer remaining=jdbc.queryForObject("SELECT remaining_meat_units FROM wildlife_carcass WHERE object_id=?",Integer.class,carcass.id()); Boolean hide=jdbc.queryForObject("SELECT hide_available FROM wildlife_carcass WHERE object_id=?",Boolean.class,carcass.id());
        if((remaining==null||remaining==0) && Boolean.FALSE.equals(hide)) { Timestamp ts=Timestamp.from(at); jdbc.update("UPDATE world_object SET lifecycle_state='DESTROYED',destroyed_at=?,destroyed_location_id=current_location_id,destroyed_cause='CARCASS_EXHAUSTED',current_location_id=NULL WHERE id=?",ts,carcass.id()); jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CARCASS_EXHAUSTED','{}'::jsonb)",carcass.id(),ts); }
        return new HarvestResult("SUCCEEDED", toxicFlesh
            ? "You work over the " + display(carcass.species()) + ", but you know its flesh for what it is — toxic, no food for anyone — and leave it. You carry nothing edible away."
            : "You work carefully over the remains and take what you can carry.");
    }
    /** Whether a species' flesh is toxic to eat (#V173) — the fauna counterpart of a poisonous plant. */
    private boolean isToxicSpecies(String speciesKey) {
        return Boolean.TRUE.equals(jdbc.query("SELECT toxic FROM wildlife_species WHERE species_key=?",
            rs -> rs.next() ? rs.getBoolean(1) : Boolean.FALSE, speciesKey));
    }
    /** Whether a species' bite or sting is venomous (#V174) — envenoms the loser of a confront, distinct from toxic flesh. */
    private boolean isVenomousSpecies(String speciesKey) {
        return Boolean.TRUE.equals(jdbc.query("SELECT venomous FROM wildlife_species WHERE species_key=?",
            rs -> rs.next() ? rs.getBoolean(1) : Boolean.FALSE, speciesKey));
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
        return passiveEncounter(chronicle, chunk, action, at, attention, false, false);
    }

    /**
     * As above, but told whether the Chronicle is actively breaking contact this turn (#126 escape). A
     * deliberate disengage is the difference between usually getting clear and being run down: fleeing puts
     * ground between them, and going to ground breaks the predator's line entirely. It is never certain — the
     * roll still stands — so a bad break can still be caught, but escape is now a real, mechanical choice
     * rather than narration over an unchanged ambush chance.
     */
    @Transactional
    public String passiveEncounter(UUID chronicle, UUID chunk, UUID action, Instant at, String attention, boolean breakingContact, boolean concealed) {
        Threat threat = jdbc.query(
            "SELECT wp.id,wp.species_key,wp.behavior_state,ws.base_resistance,ws.ambush_hunter,ws.size_tier " +
            "FROM wildlife_population wp JOIN ecology_site es ON es.id=wp.site_id " +
            "LEFT JOIN wildlife_species ws ON ws.species_key=wp.species_key " +
            "WHERE es.chunk_id=? AND wp.population_count>0 AND wp.ecological_role IN ('CARNIVORE','OMNIVORE') " +
            "AND wp.behavior_state IN ('HUNTING','PACK_HUNT','STALKING','TERRITORIAL','ALERT') " +
            "ORDER BY CASE wp.behavior_state WHEN 'PACK_HUNT' THEN 0 WHEN 'HUNTING' THEN 1 WHEN 'STALKING' THEN 2 ELSE 3 END LIMIT 1",
            rs -> rs.next() ? new Threat(rs.getObject(1,UUID.class), rs.getString(2), rs.getString(3), (Integer)rs.getObject(4), rs.getBoolean(5), rs.getString(6)) : null, chunk);
        boolean pursued = false;
        if (threat == null) {
            // Pursuit (#210) — a monster roused to its utmost (PACK_HUNT) at its lair follows the intruder onto the
            // next ground rather than letting them walk away. A Chronicle who heavily provokes a lair and then
            // steps to a neighbouring chunk is still hunted — from a chunk away, with a beat to react (a capped
            // chance below), and every defensive lever still applies.
            threat = jdbc.query(
                "SELECT wp.id,wp.species_key,wp.behavior_state,ws.base_resistance,ws.ambush_hunter,ws.size_tier " +
                "FROM wildlife_population wp JOIN ecology_site es ON es.id=wp.site_id JOIN world_chunk ec ON ec.id=es.chunk_id " +
                "JOIN world_chunk here ON here.id=? LEFT JOIN wildlife_species ws ON ws.species_key=wp.species_key " +
                "WHERE es.site_category='MONSTER' AND wp.behavior_state='PACK_HUNT' AND wp.population_count>0 " +
                "AND ec.world_id=here.world_id AND abs(ec.grid_x-here.grid_x)+abs(ec.grid_y-here.grid_y)=1 LIMIT 1",
                rs -> rs.next() ? new Threat(rs.getObject(1,UUID.class), rs.getString(2), rs.getString(3), (Integer)rs.getObject(4), rs.getBoolean(5), rs.getString(6)) : null, chunk);
            pursued = (threat != null);
        }
        if (threat == null) return null;

        // How likely it is to reach the chronicle. A pack that is coordinating is the
        // worst case; a merely alert animal is the least. A chronicle who is paying
        // attention sees it coming and it does not close; one heads-down does not.
        int chance = switch (threat.behavior()) { case "PACK_HUNT" -> 35; case "HUNTING" -> 25; case "STALKING" -> 20; case "TERRITORIAL" -> 15; default -> 8; };
        if (threat.ambushHunter()) chance += 10;
        if (pursued) chance -= 13; // it is closing from the next ground, not already upon you — a beat to react.
        if ("HIGH".equals(attention)) chance -= 12;
        else if ("MODERATE".equals(attention)) chance -= 5;
        // Actively breaking contact this turn robs the hunt of its close: going to ground breaks the
        // predator's line of sight outright, a run puts distance in the way. This is what makes flee/hide a
        // real escape rather than words — but it is a reduction, not immunity; a bad break is still caught.
        if (concealed) chance -= 30;
        else if (breakingContact) chance -= 18;
        // A camp alarm (#126) — a trip-line strung with anything that clatters — robs an ambush of its surprise:
        // nothing crosses the perimeter unheard, so even a heads-down Chronicle is not caught wholly unaware.
        boolean alarmed = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind='CAMP_ALARM' AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND w.lifecycle_state='ACTIVE')", Boolean.class, chunk));
        if (alarmed) chance -= 15;
        // A perimeter fence (#127) is a barrier, not a warning: a predator must breach it to reach the camp, and
        // many turn aside rather than try. A woven wattle wall stands stronger than a piled brush one. This is
        // the barrier layer of the defence catalogue, layered with the alarm (warning) and escape (flight), not
        // folded into them.
        Integer fence = jdbc.queryForObject(
            "SELECT MAX(CASE cp.project_kind WHEN 'WATTLE_FENCE' THEN 22 WHEN 'BRUSH_FENCE' THEN 12 ELSE 0 END) " +
            "FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
            "WHERE w.current_location_id=? AND cp.project_kind IN ('BRUSH_FENCE','WATTLE_FENCE') AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND w.lifecycle_state='ACTIVE'",
            Integer.class, chunk);
        if (fence != null) chance -= fence;
        // A fire-brand to hand (#126) — a resin torch the Chronicle can raise and wave — reads to a predator
        // as the fire it fears: it presses a rush far less readily against someone carrying flame. Weaker than
        // a whole camp's fire (which the ecology's fire-fear cascade already answers), but real, and carried.
        boolean hasBrand = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM world_object w JOIN item_instance i ON i.object_id=w.id WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND i.item_key='resin_torch')", Boolean.class, chronicle));
        if (hasBrand) chance -= 12;
        // A fresh kill carried on the body (#123/#127) is blood and scent on the wind — it draws a hungry predator
        // in where it might otherwise have passed. Cook, store, or cache the meat and the draw is gone; carry a raw
        // carcass through predator ground and you are the bait. A built camp store on this ground (#207 heritage
        // STORAGE_AREA) is a larder to set the kill down in — home ground with a store is somewhere a carcass can
        // be brought back to without turning it into an ambush, so the draw does not follow you there.
        boolean freshKill = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM world_object w JOIN item_instance i ON i.object_id=w.id WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND i.item_key IN ('raw_game_meat','raw_fish'))", Boolean.class, chronicle));
        if (freshKill && !hasStorageArea(chunk)) chance += 10;
        // A camp choked with refuse (#218) carries the scent of rot on the wind and draws hungry animals in the
        // way a fresh kill does — scavengers and opportunists both, come to see what a filthy ground offers. A
        // latrine/refuse pit that keeps the camp clean takes the draw away with the filth (it drains the refuse in
        // the world tick). Zero on a clean ground, or one with no refuse row at all.
        Integer refuse = jdbc.queryForObject("SELECT COALESCE((SELECT refuse_level FROM chunk_refuse WHERE chunk_id=?),0)", Integer.class, chunk);
        if (refuse != null && refuse >= 50) chance += 8;
        if (Math.floorMod(action.hashCode() >>> 8, 100) >= Math.max(0, chance)) return null;

        int resistance = threat.baseResistance() != null ? threat.baseResistance() : 50;
        int severity = Math.max(4, resistance / 4 + (threat.ambushHunter() ? 8 : 0));
        physiology.applyInjury(chronicle, severity, action, at, "PASSIVE_WILDLIFE_ATTACK");
        record(chronicle, threat.populationId(), chunk, "PASSIVE_ATTACK", at, action);
        jdbc.update("UPDATE wildlife_population SET behavior_state='ALERT' WHERE id=?", threat.populationId());
        String witness = threat.ambushHunter()
            ? "It is on you before there is anything to see — weight and teeth out of the cover, and the ground coming up to meet you."
            : "The " + display(threat.species()) + " does not wait to be found. It closes while your hands are busy, and the work you were doing is over.";
        // A venomous animal (#V174) envenoms however the bite lands — the ambush strike is no exception. This is the
        // passive-attack counterpart of the venom applied on a losing confront: an adder that reaches a heads-down
        // Chronicle leaves the same climbing sickness, not a plain scratch. Kept in step with confront so the venom
        // is a property of the animal, not of which code path met it.
        if (isVenomousSpecies(threat.species())) {
            physiology.applyIllness(chronicle, 10, action, at, "VENOMOUS_BITE");
            witness += " And the wound it leaves is small and wrong — a cold heat is already spreading out from it, climbing where no strike reached.";
        }
        return witness;
    }

    /** Whether a completed, standing resource store keeps this ground — the larder that ends the fresh-kill draw. */
    private boolean hasStorageArea(UUID chunk) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
            "WHERE w.current_location_id=? AND cp.project_kind='STORAGE_AREA' AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND w.lifecycle_state='ACTIVE')",
            Boolean.class, chunk));
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
     * Scan the boundary of the current ground toward each cardinal direction for the sign of a predator in the
     * next chunk — a scent riding the wind, boundary trees scored deep, prey gone silent — so a Chronicle can
     * choose a route away from danger before walking into it (#128/#123: the world gives grounded evidence
     * before forced contact, and at least one route leads away from hostile territory). It reads only what a
     * careful scan of the immediate surroundings would give: one tile out, directional, never a map, never a
     * count. A carnivore population next door is the danger; its behaviour sets how pressing the warning reads.
     *
     * <p>A raised lookout (#127) at this ground lifts the eye above the near treeline: from it the read is always
     * clear (no glance-versus-practised gap) and reaches a second chunk out along each way, so danger two tiles
     * off is seen while there is still room to plan around it.
     */
    @Transactional(readOnly = true)
    public EncounterResult scoutBoundary(UUID chronicle, UUID chunk, String attention, double familiarity) {
        java.util.Map<String,Object> here = jdbc.queryForMap("SELECT world_id, grid_x, grid_y FROM world_chunk WHERE id=?", chunk);
        UUID world = (UUID) here.get("world_id"); int gx = (int) here.get("grid_x"); int gy = (int) here.get("grid_y");
        boolean hasLookout = Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
            "WHERE w.current_location_id=? AND cp.project_kind='LOOKOUT' AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND w.lifecycle_state='ACTIVE')",
            Boolean.class, chunk));
        // North, east, south, west — the four ways a Chronicle could step off this ground.
        int[][] offs = {{0,-1},{1,0},{0,1},{-1,0}};
        String[] names = {"to the north","to the east","to the south","to the west"};
        boolean reads = hasLookout || "HIGH".equals(attention) || familiarity > 0.15;
        java.util.List<String> danger = new java.util.ArrayList<>();
        java.util.List<String> far = new java.util.ArrayList<>();
        java.util.List<String> clearWays = new java.util.ArrayList<>();
        for (int i = 0; i < offs.length; i++) {
            int nx = gx + offs[i][0], ny = gy + offs[i][1];
            if (!chunkAt(world, nx, ny)) continue; // the edge of the world — no ground that way to read
            boolean nearClear;
            java.util.Map<String,Object> pred = threatAt(world, nx, ny);
            if (pred == null) {
                nearClear = true;
            } else {
                nearClear = false;
                boolean monster = Boolean.TRUE.equals(pred.get("monster"));
                String sign = threatSign(chunk, nx, ny, monster);
                danger.add(reads
                    ? names[i] + ", " + sign + " — " + (monster ? "the lair of a " : "a ") + display((String) pred.get("species")) + urgencyOf((String) pred.get("behavior"))
                    : names[i] + ", " + sign + " — " + (monster ? "something keeps a lair that way" : "something large keeps that ground"));
            }
            // From a lookout the eye reaches a second chunk out along the same line — whether or not the near
            // ground carried danger, so a far threat is seen even when the way immediately ahead reads clear.
            boolean farThreat = false;
            if (hasLookout) {
                java.util.Map<String,Object> beyond = threatAt(world, gx + offs[i][0] * 2, gy + offs[i][1] * 2);
                if (beyond != null) { farThreat = true; boolean m = Boolean.TRUE.equals(beyond.get("monster"));
                    far.add("further " + names[i] + ", " + (m ? "the lair of a " : "a ") + display((String) beyond.get("species")) + urgencyOf((String) beyond.get("behavior"))); }
            }
            if (nearClear && !farThreat) clearWays.add(names[i]);
        }
        StringBuilder s = new StringBuilder();
        if (danger.isEmpty() && far.isEmpty()) {
            s.append(hasLookout
                ? "From the lookout you read the ground on every side, near and far — no scent on the wind, no scoring on the trees, no silence where there should be birdsong. The ways off look clear."
                : "You read the boundary on every side — no scent on the wind, no scoring on the trees, no silence where there should be birdsong. The ways off this ground look clear.");
        } else {
            s.append(hasLookout ? "From the lookout you read the ground far and near. " : "You read the boundary. ");
            for (String d : danger) s.append(Character.toUpperCase(d.charAt(0))).append(d.substring(1)).append(". ");
            for (String d : far) s.append(Character.toUpperCase(d.charAt(0))).append(d.substring(1)).append(". ");
            if (!clearWays.isEmpty()) s.append("The ground ").append(String.join(" and ", clearWays)).append(" reads clear.");
            else s.append("No way off reads clear.");
        }
        return new EncounterResult("SUCCEEDED", s.toString());
    }

    /** True when a chunk exists at these grid coordinates in the world. */
    private boolean chunkAt(UUID world, int x, int y) {
        return Boolean.TRUE.equals(jdbc.query("SELECT 1 FROM world_chunk WHERE world_id=? AND grid_x=? AND grid_y=?",
            java.sql.ResultSet::next, world, x, y));
    }

    /** The most pressing threat at a chunk — a hunting carnivore or a monster's lair (a MONSTER-category site,
     *  whatever its ecological role): species + behaviour + whether it is a lair. Null if the ground is clear.
     *  Lairs are reported ahead of ordinary predators; #123 names goblin caves and monster lairs among the
     *  dangers a Chronicle must be able to sense before forced contact. */
    private java.util.Map<String,Object> threatAt(UUID world, int x, int y) {
        return jdbc.query(
            "SELECT wp.species_key, wp.behavior_state, (es.site_category='MONSTER') AS monster FROM world_chunk c " +
            "JOIN ecology_site es ON es.chunk_id=c.id JOIN wildlife_population wp ON wp.site_id=es.id " +
            "WHERE c.world_id=? AND c.grid_x=? AND c.grid_y=? AND (wp.ecological_role='CARNIVORE' OR es.site_category='MONSTER') AND wp.population_count>0 " +
            "ORDER BY (es.site_category='MONSTER') DESC, CASE wp.behavior_state WHEN 'HUNTING' THEN 0 WHEN 'PACK_HUNT' THEN 0 WHEN 'STALKING' THEN 1 " +
            "WHEN 'AGGRESSIVE' THEN 1 WHEN 'ALERT' THEN 2 WHEN 'TERRITORIAL' THEN 2 ELSE 3 END LIMIT 1",
            rs -> rs.next() ? java.util.Map.of("species", rs.getString(1), "behavior", rs.getString(2) == null ? "" : rs.getString(2), "monster", rs.getBoolean(3)) : null,
            world, x, y);
    }

    /** A grounded sensory sign of a threat read from the boundary — fouler and stiller for a monster's lair. */
    private static String threatSign(UUID chunk, int nx, int ny, boolean monster) {
        int r = Math.floorMod(chunk.hashCode() + nx * 31 + ny, 3);
        return monster
            ? switch (r) {
                case 0 -> "the ground is fouled and the air hangs wrong";
                case 1 -> "bones lie cracked and scattered, more than any beast leaves";
                default -> "nothing living moves or makes a sound";
            }
            : switch (r) {
                case 0 -> "a rank animal smell rides the wind";
                case 1 -> "the boundary trees are scored deep, higher than a man reaches";
                default -> "the small birds have fallen silent";
            };
    }

    /** How pressing a predator's presence reads, from its behaviour — appended to a scout's report. */
    private static String urgencyOf(String behavior) {
        return switch (behavior == null ? "" : behavior) {
            case "HUNTING", "PACK_HUNT" -> ", and it is hunting";
            case "STALKING", "AGGRESSIVE" -> ", and it is roused";
            case "TERRITORIAL", "ALERT" -> ", and it is not far off";
            default -> ", at its ease for now";
        };
    }

    /**
     * Record local habitat disturbance from a physical act (#207/#208): a fight or kill, a felled tree, work that
     * marks the ground. It raises the chunk's disturbance level (capped) and logs an immutable event of what
     * caused it. WildlifeSimulationService reads the level and the wildlife that live there grow wary and quit
     * the ground while it stays disturbed; the level decays over time, so a place left alone grows quiet again.
     * This is measurement + accrual only — the response and decay live in the ecology tick.
     */
    @Transactional
    public void recordDisturbance(UUID chunk, String sourceKind, int amount, Instant at) {
        if (chunk == null || amount <= 0) return;
        Timestamp ts = Timestamp.from(at);
        jdbc.update("INSERT INTO chunk_disturbance (chunk_id, disturbance_level, last_updated_at) VALUES (?, LEAST(100,?), ?) " +
            "ON CONFLICT (chunk_id) DO UPDATE SET disturbance_level = LEAST(100, chunk_disturbance.disturbance_level + ?), last_updated_at = ?",
            chunk, amount, ts, amount, ts);
        jdbc.update("INSERT INTO chunk_disturbance_event (id, chunk_id, source_kind, amount, occurred_at) VALUES (?,?,?,?,?)",
            UUID.randomUUID(), chunk, sourceKind, amount, ts);
    }

    /**
     * A disturbance that emanates and carries — a plume of woodsmoke, but also the crash of a felled tree or the
     * commotion of a fight — does not stop at the ground that made it: it reaches the neighbouring country too
     * (#208 noise / #219 hazard footprints; the propagation the point-source {@link #recordDisturbance} lacks). This
     * records the full disturbance at the source AND a lesser one — it thins with distance — at each orthogonally-
     * adjacent chunk of the same world, so the wildlife of the ring around a loud or smoky working grow wary too, not
     * only those standing over it. Bounded to the immediate neighbours (it reaches only the connected reachable
     * places) and logged as its own {@code <kind>_DRIFT} provenance at each, distinct from the source.
     */
    @Transactional
    public void recordEmissionDrift(UUID chunk, String sourceKind, int amount, Instant at) {
        if (chunk == null || amount <= 0) return;
        recordDisturbance(chunk, sourceKind, amount, at);
        int drift = amount / 2;
        if (drift <= 0) return;
        for (UUID neighbour : jdbc.queryForList(
            "SELECT n.id FROM world_chunk here JOIN world_chunk n ON n.world_id=here.world_id " +
            "AND abs(n.grid_x-here.grid_x)+abs(n.grid_y-here.grid_y)=1 WHERE here.id=?", UUID.class, chunk)) {
            recordDisturbance(neighbour, sourceKind + "_DRIFT", drift, at);
        }
    }

    /**
     * Record refuse left on this ground (#218): the waste of work done at a camp — butchery offal now, more sources
     * later — that piles up and, left unclean, breeds illness ({@link com.devosphere.draugr.chronicle.ChroniclePhysiologyService}
     * reads it) and later draws pests. A built latrine/refuse pit disposes of it, and it breaks down slowly on its
     * own (both in {@link WildlifeSimulationService}'s tick). Mirrors {@link #recordDisturbance}; a no-op with no
     * chunk or no amount, so a camp only grows foul when it is actually fouled.
     */
    @Transactional
    public void recordRefuse(UUID chunk, int amount, Instant at) {
        if (chunk == null || amount <= 0) return;
        Timestamp ts = Timestamp.from(at);
        jdbc.update("INSERT INTO chunk_refuse (chunk_id, refuse_level, last_updated_at) VALUES (?, LEAST(100,?), ?) " +
            "ON CONFLICT (chunk_id) DO UPDATE SET refuse_level = LEAST(100, chunk_refuse.refuse_level + ?), last_updated_at = ?",
            chunk, amount, ts, amount, ts);
    }

    /**
     * Restore disturbed ground (#207/#213): the Chronicle's counter-play to the disturbance they cause. Clearing
     * the churned earth, scattering seed, setting things back toward order lowers the chunk's disturbance level
     * now, so the land grows quiet — and the wildlife return to it — sooner than time and decay alone would
     * bring. It is honest work with a real, logged effect; it does not conjure back what has already gone, only
     * speeds the recovery of the ground. Fails grounded on ground that is already quiet.
     */
    @Transactional
    public String[] restoreHabitat(UUID chunk, int amount, Instant at) {
        Integer level = jdbc.query("SELECT disturbance_level FROM chunk_disturbance WHERE chunk_id=?",
            rs -> rs.next() ? rs.getInt(1) : null, chunk);
        if (level == null || level <= 0)
            return new String[]{"FAILED", "The ground here is already quiet — churned by nothing, there is nothing to mend."};
        Timestamp ts = Timestamp.from(at);
        jdbc.update("UPDATE chunk_disturbance SET disturbance_level = GREATEST(0, disturbance_level - ?), last_updated_at = ? WHERE chunk_id=?",
            Math.max(1, amount), ts, chunk);
        jdbc.update("INSERT INTO chunk_disturbance_event (id, chunk_id, source_kind, amount, occurred_at) VALUES (?,?,?,?,?)",
            UUID.randomUUID(), chunk, "RESTORATION", Math.max(1, amount), ts);
        return new String[]{"SUCCEEDED", "You clear the worst of the churned ground, scatter seed, and set what was disturbed back toward order. Given a little quiet now, the wild will find its way back the sooner."};
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
        // Explicit trap/spear/line intent is honoured first; otherwise the Chronicle reaches for the best
        // fishing gear it carries, most effective first. A woven fish trap or cast/gill net (#36/#43) is a real
        // tool — the whole reason to make one — and a bone hook turns bare grabbing into angling. Each of these
        // was craftable but read by nothing, so it caught no better than empty hands until now.
        if (v.contains("trap") || v.contains("basket") || v.contains("weir")) { method="TRAP"; chance=75; }
        else if (v.contains("spear") && items.hasAtLeast(chronicle,"primitive_spear",1)) { method="SPEAR"; chance=55; }
        else if (v.contains("line") || v.contains("hook")) { method="LINE"; chance=45; }
        else if (items.hasAtLeast(chronicle,"fish_trap",1)) { method="TRAP"; chance=75; }
        else if (items.hasAtLeast(chronicle,"fishing_net",1)) { method="NET"; chance=72; }
        else if (items.hasAtLeast(chronicle,"landing_net",1)) { method="NET"; chance=50; }
        else if (items.hasAtLeast(chronicle,"bone_fish_hook",1) || items.hasAtLeast(chronicle,"bronze_fish_hook",1) || items.hasAtLeast(chronicle,"iron_fish_hook",1)) { method="LINE"; chance=45; }
        else { method="BARE_HAND"; chance=20; }
        // A forged metal fish hook holds a fish that a soft bone hook would straighten and lose, so a line fishes
        // better with one (#184) — it stacks with a lead sinker (a hook that catches, a weight that carries it deep).
        if (method.equals("LINE") && (items.hasAtLeast(chronicle,"bronze_fish_hook",1) || items.hasAtLeast(chronicle,"iron_fish_hook",1))) chance = Math.min(90, chance + 10);
        // A lead sinker weights a hand-line so the baited hook carries down to the deeper fish and holds against
        // the current, where a bare line only drifts on the top — a real lift to angling that the soft, dense
        // smelted metal is uniquely good for (#188). Line methods only; a trap or net is not weighted this way.
        if (method.equals("LINE") && items.hasAtLeast(chronicle,"lead_sinker",1)) chance = Math.min(90, chance + 15);
        // Bait (#75): a worm on the hook, in the trap, or in the hand draws fish that clear water would not —
        // it is spent whether or not the fish takes. Bare hands and a sweeping net are the methods a worm does
        // not help — one grabs, the other encircles.
        boolean baited = !method.equals("BARE_HAND") && !method.equals("NET") && items.hasAtLeast(chronicle,"earthworm",1) && items.consumeOne(chronicle,"earthworm",at);
        if (baited) chance = Math.min(90, chance + 20);
        java.util.List<String> species = jdbc.queryForList("SELECT species_key FROM wildlife_species WHERE movement_class='AQUATIC' AND biome_affinity ILIKE ? ORDER BY species_key", String.class, "%"+biome+"%");
        if (species.isEmpty()) return new EncounterResult("FAILED","You watch the ground a while. There is no water here that holds anything worth taking.");
        // #181/#36 finite water: a stretch fished relentlessly thins until it is fished out here, and needs rest to
        // come back. A stretch not yet worked is lazily full, so ordinary angling never meets the ceiling.
        if (fishRemaining(chunk, at) <= 0)
            return new EncounterResult("FAILED","The water here is fished out — it needs time to come back before it will give up anything more. Try fresh water.");
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
        drawFishStock(chunk, got, at); // the catch draws the stretch down (#181/#36)
        return new EncounterResult("SUCCEEDED","The "+display(caught)+" comes up out of the water, cold and heavy and still fighting.");
    }

    private static final int FISH_REGEN_HOURS = 2;
    /** How many fish a stretch of water holds when full (#181/#36) — not a flat figure but a stretch with its own
     *  richness: a broad, slow reach teems where a thin one holds little. Deterministic per ground, so the same water
     *  always reads the same but neighbouring water differs. Generous overall, so only sustained over-fishing exhausts
     *  one; a floor keeps even a poor stretch worth working. */
    public static int fishStockSeedFor(UUID chunk) {
        int h = Math.floorMod((chunk.toString() + ":fish").hashCode(), 100); // 0..99, fixed for this water
        double richness = 0.6 + (h / 100.0) * 0.8;                            // 0.6 (thin) .. ~1.4 (teeming)
        return Math.max(180, (int) Math.round(350 * richness));               // ~210 .. 490
    }
    /** Current fish remaining at a chunk, after natural restocking since it was last fished; a stretch never worked
     *  is lazily full (not yet recorded). Public so a survey can read how well-stocked the water is (#181/#36). */
    public int fishRemaining(UUID chunk, Instant at) {
        int full = fishStockSeedFor(chunk);
        java.util.Map<String,Object> row = jdbc.query(
            "SELECT remaining_units, last_fished_at FROM fish_stock WHERE chunk_id=?",
            rs -> rs.next() ? java.util.Map.of("r", rs.getInt(1), "t", rs.getTimestamp(2).toInstant()) : null, chunk);
        if (row == null) return full;
        int remaining = (int) row.get("r");
        long hours = java.time.Duration.between((Instant) row.get("t"), at).toHours();
        int regen = hours > 0 ? (int) (hours / FISH_REGEN_HOURS) : 0;
        return Math.min(full, remaining + regen);
    }
    /** Draw a stretch down by the catch, restocking first, and reset its clock so recovery accrues from now. */
    private void drawFishStock(UUID chunk, int got, Instant at) {
        int now = Math.max(0, fishRemaining(chunk, at) - got);
        Timestamp ts = Timestamp.from(at);
        jdbc.update("INSERT INTO fish_stock (chunk_id, remaining_units, last_fished_at) VALUES (?,?,?) " +
            "ON CONFLICT (chunk_id) DO UPDATE SET remaining_units=?, last_fished_at=?", chunk, now, ts, now, ts);
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
        // A toxic catch (#V173) is left in the cord — its flesh is poison, not food.
        if (isToxicSpecies(caught))
            return new EncounterResult("SUCCEEDED","The snare has held — a "+display(caught)+" hangs in the cord, but you know it for toxic and leave the flesh where it is. No food from this one.");
        UUID meat = items.createCarriedItem(chronicle,"raw_game_meat","Raw game meat",at,"TAKEN_FROM_SNARE"); food.registerRaw(meat,at);
        return new EncounterResult("SUCCEEDED","The snare has held. A "+display(caught)+" hangs in the cord, long still by the time you reach it."+(got>0?"":""));
    }

    private int meatFor(String species) { return species.contains("bear") || species.contains("elk") ? 4 : species.contains("deer") || species.contains("boar") ? 3 : 1; }
    private String display(String species) { return species.replace('_',' '); }
    private record Encounter(UUID populationId,String species,String role,String behavior,int population,String movementClass,Integer baseResistance,boolean ambushHunter){}
    private record Combatant(int energy,int injury,int pain,int handWeapon,int stones,int armour,int poison,int lightShield,int rawhideShield,int blunt,int sling,int javelin,int bow,int arrows,int softArmour,int hardened,int bronze,int iron,int steel,int plate){}
    private record Carcass(UUID id,String species,int meat,boolean hide){}
    public record EncounterResult(String outcome,String narration){}
    public record HarvestResult(String outcome,String narration){}
}
