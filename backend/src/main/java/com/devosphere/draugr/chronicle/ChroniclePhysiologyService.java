package com.devosphere.draugr.chronicle;

import com.devosphere.draugr.item.PhysicalItemService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Authoritative physiology runtime. The HUD receives only qualitative states. */
@Service
public class ChroniclePhysiologyService {
    private static final double STARVATION_DEATH_HOURS = 721.0;
    private static final double DEHYDRATION_DEATH_HOURS = 73.0;
    private final JdbcTemplate jdbc;
    public ChroniclePhysiologyService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public BodyHudSnapshot activeBody() {
        return jdbc.query("SELECT b.condition_summary, p.hours_without_food, p.hours_without_water, p.energy_level, p.core_temperature_c, p.wetness_level, p.bladder_level, p.bowel_level, p.hygiene_level, p.sleep_debt_hours, p.pain_level, p.stress_level, p.injury_severity, p.illness_severity, p.blood_loss_ml FROM chronicle c JOIN chronicle_body b ON b.chronicle_id = c.id JOIN chronicle_physiology p ON p.chronicle_id = c.id WHERE c.life_state = 'LIVING'", rs -> rs.next() ? snapshot(health(rs.getInt(13), rs.getInt(14), rs.getInt(15)), condition(rs.getString(1), rs.getInt(11), rs.getInt(12), rs.getBigDecimal(10).doubleValue()), rs.getBigDecimal(2).doubleValue(), rs.getBigDecimal(3).doubleValue(), rs.getInt(4), rs.getBigDecimal(5).doubleValue(), rs.getInt(6), rs.getInt(7), rs.getInt(8), rs.getInt(9)) : null);
    }

    @Transactional
    public void advanceTo(Instant now) {
        jdbc.query("SELECT c.id, p.last_metabolic_update, p.hours_without_food, p.hours_without_water, p.energy_level, p.core_temperature_c, p.wetness_level, p.bladder_level, p.bowel_level, p.hygiene_level, b.health, b.condition_summary, p.sleep_debt_hours, p.pain_level, p.stress_level, p.injury_severity, p.illness_severity, p.blood_loss_ml FROM chronicle c JOIN chronicle_physiology p ON p.chronicle_id = c.id JOIN chronicle_body b ON b.chronicle_id = c.id WHERE c.life_state = 'LIVING' FOR UPDATE", rs -> {
            if (!rs.next()) return null;
            UUID id = rs.getObject(1, UUID.class);
            Timestamp ts = Timestamp.from(now);
            Instant last = rs.getTimestamp(2).toInstant();
            double hours = Math.max(0, Duration.between(last, now).toSeconds() / 3600.0);
            double food = rs.getBigDecimal(3).doubleValue() + hours;
            double water = rs.getBigDecimal(4).doubleValue() + hours;
            int energy = clamp((int) Math.round(rs.getInt(5) - hours * 0.5));
            // Passive baselines only. Actions, food, water, weather, illness, and sanitation
            // will apply additional physiological consequences through the simulation agent.
            int wetness = clamp((int) Math.round(rs.getInt(7) - hours * 4));
            int bladder = clamp((int) Math.round(rs.getInt(8) + hours * 8));
            int bowel = clamp((int) Math.round(rs.getInt(9) + hours * 1.5));
            // A camp latrine and refuse pit (#127/#218) keep filth away from where a Chronicle lives, so
            // grubbiness gathers more slowly — the passive hygiene loss is halved while one stands at the site.
            // This feeds the existing low-hygiene illness pressure below, so a clean camp is a healthier one.
            boolean latrine = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object lt ON lt.id=cp.object_id " +
                "JOIN world_object body ON body.current_location_id=lt.current_location_id " +
                "WHERE body.id=? AND cp.project_kind='LATRINE' AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND lt.lifecycle_state='ACTIVE')",
                Boolean.class, id));
            int hygiene = clamp((int) Math.round(rs.getInt(10) - hours * (latrine ? .125 : .25)));
            double sleepDebt = Math.min(72, rs.getBigDecimal(13).doubleValue() + hours);
            int pain = rs.getInt(14); int stress = rs.getInt(15); int injury = rs.getInt(16); int illness = rs.getInt(17); int bloodLoss = rs.getInt(18);
            Environment environment = jdbc.query("SELECT ww.weather_kind,ww.intensity,ww.ambient_temperature_c,ww.wind_speed_kph,COALESCE((SELECT fs.fuel_minutes FROM construction_project cp JOIN fire_state fs ON fs.construction_id=cp.object_id JOIN world_object pit ON pit.id=cp.object_id JOIN world_object body ON body.current_location_id=pit.current_location_id WHERE body.id=c.id AND fs.active=true ORDER BY fs.fuel_minutes DESC LIMIT 1),0),EXISTS(SELECT 1 FROM construction_project cp JOIN world_object shelter ON shelter.id=cp.object_id JOIN world_object body ON body.current_location_id=shelter.current_location_id WHERE body.id=c.id AND cp.project_kind='LEAN_TO' AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND shelter.lifecycle_state='ACTIVE')" +
                ",EXISTS(SELECT 1 FROM construction_project cp JOIN world_object wb ON wb.id=cp.object_id JOIN world_object body ON body.current_location_id=wb.current_location_id WHERE body.id=c.id AND cp.project_kind='WINDBREAK' AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND wb.lifecycle_state='ACTIVE')" +
                ",EXISTS(SELECT 1 FROM construction_project cp JOIN world_object rc ON rc.id=cp.object_id JOIN world_object body ON body.current_location_id=rc.current_location_id WHERE body.id=c.id AND cp.project_kind='RAIN_COVER' AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND rc.lifecycle_state='ACTIVE')" +
                ",EXISTS(SELECT 1 FROM construction_project cp JOIN world_object ss ON ss.id=cp.object_id JOIN world_object body ON body.current_location_id=ss.current_location_id WHERE body.id=c.id AND cp.project_kind='SUNSHADE' AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND ss.lifecycle_state='ACTIVE')" +
                ",EXISTS(SELECT 1 FROM construction_project cp JOIN world_object en ON en.id=cp.object_id JOIN world_object body ON body.current_location_id=en.current_location_id WHERE body.id=c.id AND cp.project_kind IN ('LEAN_TO','WATTLE_AND_DAUB_HUT','EARTH_SHELTERED_HUT','LOG_CABIN') AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND en.lifecycle_state='ACTIVE')" +
                ",EXISTS(SELECT 1 FROM item_instance ii JOIN world_object hw ON hw.id=ii.object_id JOIN world_object body ON body.current_location_id=hw.current_location_id WHERE body.id=c.id AND ii.item_key='smoke_hood' AND hw.lifecycle_state='ACTIVE')" +
                ",EXISTS(SELECT 1 FROM equipment_attachment e JOIN item_instance ii ON ii.object_id=e.item_id WHERE e.chronicle_id=c.id AND ii.item_key='smoke_face_wrap')" +
                " FROM chronicle c JOIN world_weather ww ON ww.world_id=c.world_id WHERE c.id=?", result -> result.next() ? new Environment(result.getString(1),result.getInt(2),result.getBigDecimal(3).doubleValue(),result.getInt(4),result.getInt(5),result.getBoolean(6),result.getBoolean(7),result.getBoolean(8),result.getBoolean(9),result.getBoolean(10),result.getBoolean(11),result.getBoolean(12)) : new Environment("CLEAR",0,18,0,0,false,false,false,false,false,false,false), id);
            double core = rs.getBigDecimal(6).doubleValue();
            // Full shelter cuts the wind most; a bare-hand windbreak (#195) cuts it partly, but it is not shelter.
            double effectiveWind = environment.shelter() ? environment.wind() * .25 : (environment.windbreak() ? environment.wind() * .5 : environment.wind());
            // What the chronicle is wearing finally counts. Summed insulation across worn
            // garments is capped at 80%, so no stack of rags makes a body invulnerable —
            // clothing buys time against the cold, it does not repeal it.
            Integer worn = jdbc.queryForObject(
                "SELECT COALESCE(SUM(d.insulation_value),0) FROM equipment_attachment e " +
                "JOIN item_instance i ON i.object_id=e.item_id " +
                "JOIN item_definition d ON d.item_key=i.item_key " +
                "JOIN world_object w ON w.id=e.item_id AND w.lifecycle_state='ACTIVE' " +
                "WHERE e.chronicle_id=?", Integer.class, id);
            double retained = 1.0 - Math.min(0.80, (worn == null ? 0 : worn) / 100.0);
            // Wet clothing insulates far worse than dry — the classic way people die of
            // cold in weather that is not, in itself, lethal.
            if (wetness >= 60) retained = Math.min(1.0, retained * 1.6);
            double envExposure = ((environment.ambient() - core) * Math.min(.22, hours * .04) - effectiveWind * hours * .004) * retained;
            // A leaf sunshade (#195) cuts the radiant heat load when the air is hotter than the body — partial
            // relief from a hot day, not a cool room. It does nothing in the cold (envExposure is then negative).
            if (environment.sunShade() && environment.ambient() > core) envExposure *= 0.6;
            double exposure = envExposure;
            // A living body makes its own heat, and that is what actually holds core
            // temperature at 37C in air far colder than 37C. Without this term the model
            // had nothing opposing environmental drain, so core decayed toward ambient
            // and any ambient below 28C was eventually fatal regardless of preparation.
            // Fuel matters: a starving or exhausted body thermoregulates poorly, which
            // is why cold takes the hungry first — and why food, fire, and clothing all
            // have to fail together before a chronicle freezes.
            double metabolicVigour = (food > STARVATION_DEATH_HOURS * .6 || energy < 15) ? .35 : 1.0;
            exposure += (37.0 - core) * Math.min(.40, hours * .07) * metabolicVigour;
            // Fire warming scales with fuel level: dying embers (1-30 min) = low, steady (31-90) = moderate, well-fed (91+) = good.
            // A primitive open fire pit is less effective than an enclosed hearth — capped accordingly.
            if (environment.fuelMinutes() > 0) {
                double fireRate = environment.fuelMinutes() <= 30 ? 0.6 : environment.fuelMinutes() <= 90 ? 1.4 : 2.0;
                exposure += (37.0 - core) * Math.min(0.5, hours * fireRate);
            }
            core = core + exposure;
            // A body cannot be chilled below the air around it. Wind and wet carry heat
            // away faster, but they cannot take a body past equilibrium with its
            // surroundings — without this floor the uncapped wind term drove core
            // temperature below ambient without limit, and every chronicle froze to
            // death on a mild day. Cold air still kills; a 20C day no longer does.
            if (environment.fuelMinutes() == 0 && environment.ambient() < 37.0)
                core = Math.max(Math.min(environment.ambient(), 36.6), core);
            core = Math.max(20, Math.min(45, core));
            // Rain wets the body less under a roof; a leant rain cover (#195) sheds some of it, but far less than
            // true shelter — it has no walls and turns only the worst of a shower.
            if ("RAIN".equals(environment.kind()) || "STORM".equals(environment.kind())) wetness = clamp((int)Math.round(wetness + hours * (environment.intensity() / (environment.shelter() ? 28.0 : (environment.rainCover() ? 15.0 : 7.0)))));
            if (environment.fuelMinutes() > 0) wetness = clamp((int)Math.round(wetness - hours * 14));
            // Woodsmoke in an enclosed shelter with an unvented fire fouls the air (#198): a small, non-lethal
            // pressure toward illness. A smoke hood at the hearth vents it away entirely; a worn smoke face wrap
            // only cuts what is breathed, so it eases but does not end the exposure — the real fix is the vent.
            boolean smoke = environment.enclosed() && environment.fuelMinutes() > 0 && !environment.vented();
            int smokePressure = smoke ? (environment.smokeWrap() ? (int) Math.ceil(hours * .15) : (int) Math.ceil(hours * .4)) : 0;
            int illnessPressure = (hygiene <= 10 ? (int) Math.ceil(hours * .4) : 0) + (wetness >= 70 && core < 36.0 ? (int) Math.ceil(hours * .6) : 0) + (injury >= 60 ? (int) Math.ceil(hours * .15) : 0) + smokePressure;
            illness = clamp(illness + illnessPressure);
            // Every physiological death vector terminates here. Trauma (injury_severity
            // at its ceiling) is the path a grievous mauling or an accumulation of
            // untreated wounds takes: without it, a chronicle savaged to the limit
            // would live on indefinitely, since a single wound rarely bleeds past the
            // exsanguination threshold on its own.
            if (food > STARVATION_DEATH_HOURS || water > DEHYDRATION_DEATH_HOURS || bloodLoss > 3500 || injury >= 100 || illness >= 100 || core < 28.0 || core > 42.0) {
                String cause = water > DEHYDRATION_DEATH_HOURS ? "Critical Dehydration" : food > STARVATION_DEATH_HOURS ? "Critical Starvation" : bloodLoss > 3500 ? "Critical Blood Loss" : injury >= 100 ? "Fatal Trauma" : core < 28.0 ? "Severe Hypothermia" : core > 42.0 ? "Severe Hyperthermia" : "Systemic Illness";
                UUID deathLocation = jdbc.query("SELECT current_location_id FROM world_object WHERE id=?", result -> result.next() ? result.getObject(1, UUID.class) : null, id);
                relocatePossessions(id, deathLocation, now);
                jdbc.update("INSERT INTO chronicle_death_snapshot (chronicle_id,died_at,death_location_id,cause,body_snapshot) VALUES (?,?,?,?,jsonb_build_object('health',?,'condition',?,'hunger',?,'thirst',?,'energy',?,'temperature',?,'wetness',?,'bladder',?,'bowel',?,'hygiene',?))",id,ts,deathLocation,cause,health(injury,illness,bloodLoss),condition(rs.getString(12),pain,stress,sleepDebt),hunger(food),thirst(water),energy(energy),temperature(core),wetness(wetness),bladder(bladder),bowel(bowel),hygiene(hygiene));
                jdbc.update("UPDATE chronicle SET life_state = 'DEAD', died_at = ?, death_cause = ? WHERE id = ?", ts, cause, id);
                jdbc.update("UPDATE chronicle_body SET health = 'Dying', condition_summary = ? WHERE chronicle_id = ?", cause, id);
                jdbc.update("INSERT INTO chronicle_event (chronicle_id, occurred_at, event_type, payload) VALUES (?, ?, 'CHRONICLE_DIED', jsonb_build_object('cause', ?))", id, ts, cause);
                jdbc.update("INSERT INTO world_event (occurred_at, event_type, aggregate_id, payload) VALUES (?, 'CHRONICLE_DIED', ?, jsonb_build_object('cause', ?))", ts, id, cause);
            } else {
                jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update = ?, hours_without_food = ?, hours_without_water = ?, energy_level = ?, core_temperature_c=?, wetness_level = ?, bladder_level = ?, bowel_level = ?, hygiene_level = ?, sleep_debt_hours=?, pain_level=?, stress_level=?, injury_severity=?, illness_severity=?, blood_loss_ml=? WHERE chronicle_id = ?", ts, BigDecimal.valueOf(food), BigDecimal.valueOf(water), energy, BigDecimal.valueOf(core), wetness, bladder, bowel, hygiene, BigDecimal.valueOf(sleepDebt), pain, stress, injury, illness, bloodLoss, id);
                BodyHudSnapshot body = snapshot(health(injury, illness, bloodLoss), condition(rs.getString(12), pain, stress, sleepDebt), food, water, energy, core, wetness, bladder, bowel, hygiene);
                jdbc.update("UPDATE chronicle_body SET hunger = ?, thirst = ?, energy = ?, temperature = ?, wetness = ?, bladder = ?, bowel = ?, hygiene = ? WHERE chronicle_id = ?", body.hunger(), body.thirst(), body.energy(), body.temperature(), body.wetness(), body.bladder(), body.bowel(), body.hygiene(), id);
            }
            return null;
        });
    }

    @Transactional
    public void applyRelief(UUID chronicleId, boolean bowel) {
        String column = bowel ? "bowel_level" : "bladder_level";
        jdbc.update("UPDATE chronicle_physiology SET " + column + " = 0 WHERE chronicle_id = ?", chronicleId);
        jdbc.update("UPDATE chronicle_body SET " + (bowel ? "bowel" : "bladder") + " = 'Empty' WHERE chronicle_id = ?", chronicleId);
    }

    /**
     * The physiological cost of a personal physical act — moderate exertion with
     * no nourishment. Energy falls, the body burns through food and water faster,
     * hygiene degrades, the bladder fills. The tick makes no exception for why the
     * body is spent: repeated without eating, drinking, or resting, this depletes
     * toward exhaustion and dehydration like any other drain. The world applies
     * physics, not judgment. See DR-0019.
     */
    @Transactional
    public void applyPersonalActExertion(UUID chronicleId) {
        jdbc.update("UPDATE chronicle_physiology SET energy_level=GREATEST(0,energy_level-15), hours_without_food=hours_without_food+2, hours_without_water=hours_without_water+2, hygiene_level=GREATEST(0,hygiene_level-12), bladder_level=LEAST(100,bladder_level+5) WHERE chronicle_id=?", chronicleId);
        refreshBody(chronicleId);
    }
    /** A trivial exertion — venting frustration at scenery costs a little energy and nothing else. */
    @Transactional
    public void applyMinorExertion(UUID chronicleId, int energyCost) {
        jdbc.update("UPDATE chronicle_physiology SET energy_level=GREATEST(0,energy_level-?) WHERE chronicle_id=?", Math.max(0, energyCost), chronicleId);
        refreshBody(chronicleId);
    }

    /**
     * The physical cost of doing work, on top of the passive metabolic tick — hard labour tires and
     * dirties the body, so felling a tree or hauling stone is not free the way standing still is
     * (GitHub #27). Energy and hygiene fall by the intensity of the act; the survival loop then has to
     * answer for it with food, rest, and washing. No effect when both costs are zero.
     */
    @Transactional
    public void applyLabor(UUID chronicleId, int energyCost, int hygieneCost) {
        if (energyCost <= 0 && hygieneCost <= 0) return;
        // #217 — heavy exertion taxes the body beyond the energy it spends. Hard work (the Labor(12,·) heavy tier;
        // a failed half-effort passes a smaller cost and does neither) sweats the body — it leaves it damp, and that
        // damp then chills through the SAME wetness→cold→illness path as rain does unless dried at a fire or shelter
        // — and it dries the body from within, so a labouring Chronicle thirsts sooner than a resting one and must
        // drink more to keep up. New exposure/hydration vectors: the passive metabolism only ever dampened the body
        // from rain and only ever thirsted it with the passage of time, never from the labour itself.
        int sweat = energyCost >= 12 ? 3 : 0;
        double exertionThirst = energyCost >= 12 ? 0.5 : 0.0;
        jdbc.update("UPDATE chronicle_physiology SET energy_level=GREATEST(0,energy_level-?), hygiene_level=GREATEST(0,hygiene_level-?), wetness_level=LEAST(100,wetness_level+?), hours_without_water=hours_without_water+? WHERE chronicle_id=?",
            Math.max(0, energyCost), Math.max(0, hygieneCost), sweat, exertionThirst, chronicleId);
        refreshBody(chronicleId);
    }
    @Transactional
    public void eat(UUID chronicleId) { eat(chronicleId, com.devosphere.draugr.quality.QualityGrade.SOUND); }
    /** A food's workmanship grade scales how much it nourishes — bounded, so even a poor meal still staves off
     *  hunger and a fine one is only modestly better (#271); grade is a benefit, never a gate. */
    @Transactional
    public void eat(UUID chronicleId, com.devosphere.draugr.quality.QualityGrade grade) {
        double m = nourishmentFactor(grade);
        int food = (int)Math.round(8*m), energy = (int)Math.round(6*m);
        jdbc.update("UPDATE chronicle_physiology SET hours_without_food=GREATEST(0,hours_without_food-?), energy_level=LEAST(100,energy_level+?) WHERE chronicle_id=?", food, energy, chronicleId);
        refreshBody(chronicleId);
    }
    public void eatCookedMeal(UUID chronicleId) { eatCookedMeal(chronicleId, com.devosphere.draugr.quality.QualityGrade.SOUND); }
    @Transactional
    public void eatCookedMeal(UUID chronicleId, com.devosphere.draugr.quality.QualityGrade grade) {
        double m = nourishmentFactor(grade);
        int food = (int)Math.round(16*m), energy = (int)Math.round(14*m);
        jdbc.update("UPDATE chronicle_physiology SET hours_without_food=GREATEST(0,hours_without_food-?), energy_level=LEAST(100,energy_level+?),stress_level=GREATEST(0,stress_level-2) WHERE chronicle_id=?", food, energy, chronicleId);
        refreshBody(chronicleId);
    }
    /** SOUND is the baseline (×1.0); a defective meal nourishes a little less, a fine one a little more. */
    private static double nourishmentFactor(com.devosphere.draugr.quality.QualityGrade grade) {
        return switch (grade == null ? com.devosphere.draugr.quality.QualityGrade.SOUND : grade) {
            case DEFECTIVE -> 0.75; case POOR -> 0.90; case SOUND -> 1.00; case FINE -> 1.20; };
    }
    @Transactional
    public void drink(UUID chronicleId) {
        jdbc.update("UPDATE chronicle_physiology SET hours_without_water=GREATEST(0,hours_without_water-10) WHERE chronicle_id=?", chronicleId);
        refreshBody(chronicleId);
    }
    /** Gut-illness load from drinking untreated water (#71/#59): a small rise in illness that accumulates —
     *  drinking raw or from a standing source repeatedly is how a Chronicle sickens. Boiled water carries none. */
    @Transactional
    public void applyWaterborneRisk(UUID chronicleId, int severity) {
        jdbc.update("UPDATE chronicle_physiology SET illness_severity=LEAST(100,illness_severity+?) WHERE chronicle_id=?", Math.max(0, severity), chronicleId);
        refreshBody(chronicleId);
    }
    @Transactional
    public void wash(UUID chronicleId) { wash(chronicleId, false); }
    /** Wash in reachable water (#66). Soap (V89) lifts far more dirt than water alone and settles the mind more. */
    @Transactional
    public void wash(UUID chronicleId, boolean withSoap) {
        int hygiene = withSoap ? 45 : 28, stress = withSoap ? 6 : 4;
        jdbc.update("UPDATE chronicle_physiology SET hygiene_level=LEAST(100,hygiene_level+?),wetness_level=LEAST(100,wetness_level+18),stress_level=GREATEST(0,stress_level-?) WHERE chronicle_id=?", hygiene, stress, chronicleId);
        refreshBody(chronicleId);
    }
    /** A bare-hand cover placed here (#195): a windbreak or brush screen keeps the wind off, so a fire warms
     *  better and rest out of the wind eases the body more. Not shelter — it turns no rain and has no roof. */
    private boolean windbreakAt(UUID chronicleId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object w ON w.id=cp.object_id JOIN world_object body ON body.current_location_id=w.current_location_id WHERE body.id=? AND cp.project_kind='WINDBREAK' AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND w.lifecycle_state='ACTIVE')", Boolean.class, chronicleId));
    }
    /** Warm by a fire in reach (#66): core temperature climbs toward normal, and a little wet steams off. A
     *  windbreak (#195) keeps the wind from stealing the heat, so more of it reaches the body. */
    /** A ring of stones set round the fire (#195): it holds the embers and throws their heat back, so a little
     *  more of the fire's warmth reaches the body. Not a hearth — just gathered cobbles, but they help. */
    private boolean stoneRingAt(UUID chronicleId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object r ON r.id=cp.object_id JOIN world_object body ON body.current_location_id=r.current_location_id WHERE body.id=? AND cp.project_kind='STONE_RING' AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND r.lifecycle_state='ACTIVE')", Boolean.class, chronicleId));
    }
    @Transactional
    public void warmByFire(UUID chronicleId) {
        boolean wind = windbreakAt(chronicleId);
        boolean ring = stoneRingAt(chronicleId);
        double tempGain = (wind ? 1.0 : 0.7) + (ring ? 0.2 : 0.0); int wetLoss = wind ? 16 : 12;
        jdbc.update("UPDATE chronicle_physiology SET core_temperature_c=LEAST(37.5, core_temperature_c+?), wetness_level=GREATEST(0,wetness_level-?), stress_level=GREATEST(0,stress_level-3) WHERE chronicle_id=?", tempGain, wetLoss, chronicleId);
        refreshBody(chronicleId);
    }
    /** Dry off by a fire or under cover (#66): wetness falls markedly. */
    @Transactional
    public void dryOff(UUID chronicleId) {
        jdbc.update("UPDATE chronicle_physiology SET wetness_level=GREATEST(0,wetness_level-30), stress_level=GREATEST(0,stress_level-2) WHERE chronicle_id=?", chronicleId);
        refreshBody(chronicleId);
    }
    /** Cool off in shade or water (#66): core temperature eases down toward normal when overheated. */
    @Transactional
    public void coolOff(UUID chronicleId) {
        jdbc.update("UPDATE chronicle_physiology SET core_temperature_c=GREATEST(36.5, core_temperature_c-0.7), stress_level=GREATEST(0,stress_level-2) WHERE chronicle_id=?", chronicleId);
        refreshBody(chronicleId);
    }
    /** Get under cover out of the weather (#66): a little drying and an easing of stress. */
    @Transactional
    public void shelterFromWeather(UUID chronicleId) {
        jdbc.update("UPDATE chronicle_physiology SET wetness_level=GREATEST(0,wetness_level-15), stress_level=GREATEST(0,stress_level-4) WHERE chronicle_id=?", chronicleId);
        refreshBody(chronicleId);
    }
    /** Stretch and loosen the limbs (#66): a small easing of stiffness and stress. */
    @Transactional
    public void stretch(UUID chronicleId) {
        jdbc.update("UPDATE chronicle_physiology SET stress_level=GREATEST(0,stress_level-5), pain_level=GREATEST(0,pain_level-2) WHERE chronicle_id=?", chronicleId);
        refreshBody(chronicleId);
    }
    /** The small easing of mind that comes of setting a camp in order (#71 maintain_camp). */
    @Transactional
    public void settleCamp(UUID chronicleId) {
        jdbc.update("UPDATE chronicle_physiology SET stress_level=GREATEST(0,stress_level-6) WHERE chronicle_id=?", chronicleId);
        refreshBody(chronicleId);
    }
    /**
     * A bed the chronicle can lie on here (#71): a laid pallet or a raised platform, off the cold, wet ground.
     * Not shelter — it turns no weather — but it makes rest and even exposed sleep markedly less broken.
     */
    private boolean beddedAt(UUID chronicleId) {
        // A bark/grass groundsheet (#195) is the humblest of these — it turns no weather, but like any bed it gets
        // the sleeper off the cold, wet ground, so it counts here for rest and drying.
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object bed ON bed.id=cp.object_id JOIN world_object body ON body.current_location_id=bed.current_location_id WHERE body.id=? AND cp.project_kind IN ('GROUND_BED','RAISED_SLEEPING_PLATFORM','GROUNDSHEET') AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND bed.lifecycle_state='ACTIVE')", Boolean.class, chronicleId));
    }
    /**
     * Whether a built seat — the wooden chair a Chronicle can craft (CRAFT_CHAIR) — stands on this ground. A seat
     * eases a sit-down rest more than the bare earth does, the humblest of camp comforts; it gives the built chair
     * something to do, where it read against nothing before. Sited at a location like other furniture, reachable
     * there.
     */
    private boolean seatedAt(UUID chronicleId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM world_object chair JOIN item_instance i ON i.object_id=chair.id JOIN world_object body ON body.current_location_id=chair.current_location_id WHERE body.id=? AND i.item_key='wooden_chair' AND chair.lifecycle_state='ACTIVE')", Boolean.class, chronicleId));
    }
    @Transactional
    public void rest(UUID chronicleId, int minutes) {
        double hours = minutes / 60.0;
        Boolean sheltered = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object shelter ON shelter.id=cp.object_id JOIN world_object body ON body.current_location_id=shelter.current_location_id WHERE body.id=? AND cp.project_kind IN ('LEAN_TO','WATTLE_AND_DAUB_HUT','EARTH_SHELTERED_HUT','LOG_CABIN') AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND shelter.lifecycle_state='ACTIVE')", Boolean.class, chronicleId);
        boolean bed = beddedAt(chronicleId);
        // A rest recovers best under a shelter, next in a bed off the cold ground, next on a proper seat, and least
        // on the bare earth. A chair is a modest comfort — better than the ground, less than lying down — and only
        // matters when nothing better is to hand (you would not sit up in a chair when a bed or shelter is there).
        double recovery = Boolean.TRUE.equals(sheltered) ? 1.25 : (bed ? 1.12 : (seatedAt(chronicleId) ? 1.06 : 1.0));
        int drying = Boolean.TRUE.equals(sheltered) ? Math.max(1, (int)Math.round(hours * 8)) : (bed ? Math.max(1, (int)Math.round(hours * 3)) : 0);
        jdbc.update("UPDATE chronicle_physiology SET sleep_debt_hours=GREATEST(0,sleep_debt_hours-?),energy_level=LEAST(100,energy_level+?),pain_level=GREATEST(0,pain_level-?),stress_level=GREATEST(0,stress_level-?),wetness_level=GREATEST(0,wetness_level-?) WHERE chronicle_id=?", hours * .85 * recovery, Math.max(1, (int)Math.round(hours * 9 * recovery)), Math.max(0, (int)Math.round(hours * recovery)), Math.max(0, (int)Math.round(hours * 2 * recovery)), drying, chronicleId);
        refreshBody(chronicleId);
    }
    /** Deep sleep — a fuller recovery than a brief rest. Shelter makes it restful; sleeping exposed is possible but shallow. Returns whether the chronicle was sheltered. */
    @Transactional
    public boolean sleep(UUID chronicleId, int minutes) {
        double hours = minutes / 60.0;
        Boolean sheltered = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object shelter ON shelter.id=cp.object_id JOIN world_object body ON body.current_location_id=shelter.current_location_id WHERE body.id=? AND cp.project_kind IN ('LEAN_TO','WATTLE_AND_DAUB_HUT','EARTH_SHELTERED_HUT','LOG_CABIN') AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND shelter.lifecycle_state='ACTIVE')", Boolean.class, chronicleId);
        boolean safe = Boolean.TRUE.equals(sheltered);
        boolean bed = beddedAt(chronicleId);
        // Sheltered sleep is deep and restorative; a bed off the cold, wet ground makes
        // exposed sleep far less broken than lying on bare earth, though it turns no weather.
        double recovery = safe ? 1.5 : (bed ? 1.0 : 0.7);
        int drying = safe ? Math.max(1, (int)Math.round(hours * 9)) : (bed ? Math.max(1, (int)Math.round(hours * 4)) : 0);
        jdbc.update("UPDATE chronicle_physiology SET sleep_debt_hours=GREATEST(0,sleep_debt_hours-?),energy_level=LEAST(100,energy_level+?),pain_level=GREATEST(0,pain_level-?),stress_level=GREATEST(0,stress_level-?),wetness_level=GREATEST(0,wetness_level-?) WHERE chronicle_id=?", hours * 1.1 * recovery, Math.max(1, (int)Math.round(hours * 12 * recovery)), Math.max(0, (int)Math.round(hours * 1.5 * recovery)), Math.max(0, (int)Math.round(hours * 3 * recovery)), drying, chronicleId);
        refreshBody(chronicleId);
        return safe;
    }
    @Transactional
    public void applyInjury(UUID chronicleId, int severity, UUID actionId, Instant occurredAt, String source) {
        int bounded=clamp(severity);
        jdbc.update("UPDATE chronicle_physiology SET injury_severity=LEAST(100,injury_severity+?),pain_level=LEAST(100,pain_level+?),stress_level=LEAST(100,stress_level+?),blood_loss_ml=LEAST(7000,blood_loss_ml+?) WHERE chronicle_id=?",bounded,Math.max(1,bounded/2),Math.max(1,bounded/3),bounded*8,chronicleId);
        jdbc.update("INSERT INTO chronicle_condition_event (chronicle_id,occurred_at,condition_kind,severity,source_action_id,payload) VALUES (?,?,?,?,?,jsonb_build_object('source',?))",chronicleId,Timestamp.from(occurredAt),"INJURY",bounded,actionId,source);
        refreshBody(chronicleId);
    }
    /**
     * A strain of overexertion (#217) — a pulled muscle, a wrenched back from driving the body past what it had
     * left. It hurts (pain is the main signal) and leaves a minor tissue injury that eases with rest and care, but
     * unlike a cut or a bite it does not bleed: no blood loss. A real recovery need, kept in history like any
     * condition, so there is no silent fatigue reset.
     */
    @Transactional
    public void applyStrain(UUID chronicleId, int severity, UUID actionId, Instant occurredAt) {
        int bounded = clamp(severity);
        jdbc.update("UPDATE chronicle_physiology SET injury_severity=LEAST(100,injury_severity+?),pain_level=LEAST(100,pain_level+?),stress_level=LEAST(100,stress_level+?) WHERE chronicle_id=?", Math.max(1, bounded / 2), bounded, Math.max(1, bounded / 3), chronicleId);
        jdbc.update("INSERT INTO chronicle_condition_event (chronicle_id,occurred_at,condition_kind,severity,source_action_id,payload) VALUES (?,?,?,?,?,jsonb_build_object('source','overexertion'))", chronicleId, Timestamp.from(occurredAt), "STRAIN", bounded, actionId);
        refreshBody(chronicleId);
    }
    @Transactional
    public void applyFoodborneIllness(UUID chronicleId, UUID actionId, Instant occurredAt) {
        jdbc.update("UPDATE chronicle_physiology SET illness_severity=LEAST(100,illness_severity+12),stress_level=LEAST(100,stress_level+5) WHERE chronicle_id=?",chronicleId);
        jdbc.update("INSERT INTO chronicle_condition_event (chronicle_id,occurred_at,condition_kind,severity,source_action_id,payload) VALUES (?,?,?,?,?,jsonb_build_object('source','spoiled_food'))",chronicleId,Timestamp.from(occurredAt),"FOODBORNE_ILLNESS",12,actionId);
        refreshBody(chronicleId);
    }
    /** A general illness from any cause — venom, tainted water, a poisonous plant. Raises illness_severity and a little stress. */
    @Transactional
    public void applyIllness(UUID chronicleId, int severity, UUID actionId, Instant occurredAt, String source) {
        int bounded = clamp(severity);
        jdbc.update("UPDATE chronicle_physiology SET illness_severity=LEAST(100,illness_severity+?),stress_level=LEAST(100,stress_level+?) WHERE chronicle_id=?", bounded, Math.max(1, bounded / 3), chronicleId);
        jdbc.update("INSERT INTO chronicle_condition_event (chronicle_id,occurred_at,condition_kind,severity,source_action_id,payload) VALUES (?,?,?,?,?,jsonb_build_object('source',?))", chronicleId, Timestamp.from(occurredAt), "ILLNESS", bounded, actionId, source);
        refreshBody(chronicleId);
    }
    @Transactional
    public boolean bindWound(UUID chronicleId, PhysicalItemService items, UUID actionId, Instant occurredAt) {
        Integer wounded = jdbc.queryForObject("SELECT COUNT(*) FROM chronicle_physiology WHERE chronicle_id=? AND (injury_severity>0 OR blood_loss_ml>0)", Integer.class, chronicleId);
        if (wounded == null || wounded == 0) return false;
        // First aid grades by what the Chronicle can bring to bear (#125 Recovery). The binding: a medicinal
        // poultice (V87) staunches best; a clean rolled bandage (V119) next; a raw fibre binding last — at least
        // one is required. A bark splint and an arm sling (V119) are optional additions, each with its own effect:
        // a splint immobilises a break (the biggest cut to injury), a sling rests the limb (pain, and above all
        // stress). Poultice and bandage may both be applied — medicine dressed with a clean binding.
        boolean poultice = items.consumeOne(chronicleId, "herbal_poultice", occurredAt);
        boolean bandage  = items.consumeOne(chronicleId, "fibre_bandage_roll", occurredAt);
        boolean fibre    = (!poultice && !bandage) && items.consumeOne(chronicleId, "plant_fiber", occurredAt);
        if (!poultice && !bandage && !fibre) return false;
        boolean splint = items.consumeOne(chronicleId, "bark_splint_set", occurredAt);
        boolean sling  = items.consumeOne(chronicleId, "cordage_arm_sling", occurredAt);
        int blood  = (poultice ? 300 : 0) + (bandage ? 220 : 0) + (fibre ? 180 : 0);
        int pain   = (poultice ? 9 : 0) + (bandage ? 6 : 0) + (fibre ? 5 : 0) + (splint ? 7 : 0) + (sling ? 4 : 0);
        int injury = (poultice ? 4 : 0) + (bandage ? 2 : 0) + (splint ? 6 : 0) + (sling ? 1 : 0);
        int stress = 3 + (sling ? 3 : 0);
        jdbc.update("UPDATE chronicle_physiology SET blood_loss_ml=GREATEST(0,blood_loss_ml-?),pain_level=GREATEST(0,pain_level-?),stress_level=GREATEST(0,stress_level-?),injury_severity=GREATEST(0,injury_severity-?) WHERE chronicle_id=?", blood, pain, stress, injury, chronicleId);
        String method = (poultice ? "herbal_poultice" : bandage ? "fibre_bandage" : "plant_fiber_binding") + (splint ? "+splint" : "") + (sling ? "+sling" : "");
        jdbc.update("INSERT INTO chronicle_condition_event (chronicle_id,occurred_at,condition_kind,severity,source_action_id,payload) VALUES (?,?,?,?,?,jsonb_build_object('method',?))", chronicleId, Timestamp.from(occurredAt), "WOUND_BOUND", 1, actionId, method);
        refreshBody(chronicleId);
        return true;
    }
    private void refreshBody(UUID chronicleId) {
        jdbc.query("SELECT b.condition_summary,p.hours_without_food,p.hours_without_water,p.energy_level,p.core_temperature_c,p.wetness_level,p.bladder_level,p.bowel_level,p.hygiene_level,p.sleep_debt_hours,p.pain_level,p.stress_level,p.injury_severity,p.illness_severity,p.blood_loss_ml FROM chronicle_body b JOIN chronicle_physiology p ON p.chronicle_id=b.chronicle_id WHERE b.chronicle_id=?",rs->{if(rs.next()){String health=health(rs.getInt(13),rs.getInt(14),rs.getInt(15));String condition=condition(rs.getString(1),rs.getInt(11),rs.getInt(12),rs.getBigDecimal(10).doubleValue());BodyHudSnapshot body=snapshot(health,condition,rs.getBigDecimal(2).doubleValue(),rs.getBigDecimal(3).doubleValue(),rs.getInt(4),rs.getBigDecimal(5).doubleValue(),rs.getInt(6),rs.getInt(7),rs.getInt(8),rs.getInt(9));jdbc.update("UPDATE chronicle_body SET health=?,condition_summary=?,hunger=?,thirst=?,energy=?,temperature=?,wetness=?,bladder=?,bowel=?,hygiene=? WHERE chronicle_id=?",health,condition,body.hunger(),body.thirst(),body.energy(),body.temperature(),body.wetness(),body.bladder(),body.bowel(),body.hygiene(),chronicleId);}return null;},chronicleId);
    }
    private void relocatePossessions(UUID chronicleId, UUID locationId, Instant occurredAt) {
        if (locationId == null) return;
        Timestamp occurred = Timestamp.from(occurredAt);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) SELECT id,?,'CHRONICLE_DIED_DROPPED',jsonb_build_object('locationId',?::text) FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE'", occurred, locationId.toString(), chronicleId);
        jdbc.update("DELETE FROM equipment_attachment WHERE chronicle_id=?", chronicleId);
        jdbc.update("UPDATE world_object SET current_owner_id=NULL,current_location_id=?,updated_at=? WHERE current_owner_id=? AND lifecycle_state='ACTIVE'", locationId, occurred, chronicleId);
    }

    public static BodyHudSnapshot snapshot(String health, String condition, double foodHours, double waterHours, int energy, double temperature, int wetness, int bladder, int bowel, int hygiene) {
        return new BodyHudSnapshot(health, condition, hunger(foodHours), thirst(waterHours), energy(energy), temperature(temperature), wetness(wetness), bladder(bladder), bowel(bowel), hygiene(hygiene));
    }
    private static String hunger(double hours) { return hours >= 720 ? "Critical Starvation" : hours >= 336 ? "Starving" : hours >= 168 ? "Very Hungry" : hours >= 72 ? "Hungry" : "Satisfied"; }
    private static String thirst(double hours) { return hours >= 72 ? "Critical Dehydration" : hours >= 48 ? "Dehydrated" : hours >= 24 ? "Thirsty" : "Hydrated"; }
    private static String energy(int value) { return value >= 90 ? "Energetic" : value >= 70 ? "Rested" : value >= 50 ? "Tired" : value >= 30 ? "Fatigued" : value >= 10 ? "Exhausted" : "Collapsing"; }
    private static String temperature(double value) { return value < 35 ? "Hypothermic" : value < 36.5 ? "Cold" : value <= 37.5 ? "Comfortable" : value <= 38.5 ? "Warm" : value <= 40 ? "Hot" : "Hyperthermic"; }
    private static String wetness(int value) { return value >= 75 ? "Soaked" : value >= 50 ? "Wet" : value >= 20 ? "Damp" : "Dry"; }
    private static String bladder(int value) { return value >= 90 ? "Critical" : value >= 70 ? "Urgent" : value >= 45 ? "Need to Urinate" : value <= 5 ? "Empty" : "Comfortable"; }
    private static String bowel(int value) { return value >= 90 ? "Critical" : value >= 70 ? "Urgent" : value >= 45 ? "Need Relief" : value <= 5 ? "Empty" : "Normal"; }
    private static String hygiene(int value) { return value >= 80 ? "Clean" : value >= 55 ? "Normal" : value >= 30 ? "Dirty" : value >= 10 ? "Filthy" : "Hazardous"; }
    private static int clamp(int value) { return Math.max(0, Math.min(100, value)); }
    private static String health(int injury,int illness,int bloodLoss) { return bloodLoss > 2800 || illness > 80 || injury > 80 ? "Critical" : injury > 35 || illness > 35 ? "Injured" : "Healthy"; }
    private static String condition(String existing,int pain,int stress,double sleepDebt) { if (pain > 70) return "In pain"; if (stress > 75) return "Distressed"; if (sleepDebt > 28) return "Sleep deprived"; return existing; }
    private record Environment(String kind,int intensity,double ambient,int wind,int fuelMinutes,boolean shelter,boolean windbreak,boolean rainCover,boolean sunShade,boolean enclosed,boolean vented,boolean smokeWrap) { }
    public record BodyHudSnapshot(String health, String condition, String hunger, String thirst, String energy, String temperature, String wetness, String bladder, String bowel, String hygiene) { }
}
