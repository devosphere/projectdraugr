package com.devosphere.draugr.chronicle;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
        return jdbc.query("SELECT b.health, b.condition_summary, p.hours_without_food, p.hours_without_water, p.energy_level, p.core_temperature_c, p.wetness_level, p.bladder_level, p.bowel_level, p.hygiene_level FROM chronicle c JOIN chronicle_body b ON b.chronicle_id = c.id JOIN chronicle_physiology p ON p.chronicle_id = c.id WHERE c.life_state = 'LIVING'", rs -> rs.next() ? snapshot(rs.getString(1), rs.getString(2), rs.getBigDecimal(3).doubleValue(), rs.getBigDecimal(4).doubleValue(), rs.getInt(5), rs.getBigDecimal(6).doubleValue(), rs.getInt(7), rs.getInt(8), rs.getInt(9), rs.getInt(10)) : null);
    }

    @Transactional
    public void advanceTo(Instant now) {
        jdbc.query("SELECT c.id, p.last_metabolic_update, p.hours_without_food, p.hours_without_water, p.energy_level, p.core_temperature_c, p.wetness_level, p.bladder_level, p.bowel_level, p.hygiene_level, b.health, b.condition_summary, p.sleep_debt_hours, p.pain_level, p.stress_level, p.injury_severity, p.illness_severity, p.blood_loss_ml FROM chronicle c JOIN chronicle_physiology p ON p.chronicle_id = c.id JOIN chronicle_body b ON b.chronicle_id = c.id WHERE c.life_state = 'LIVING' FOR UPDATE", rs -> {
            if (!rs.next()) return null;
            UUID id = rs.getObject(1, UUID.class);
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
            int hygiene = clamp((int) Math.round(rs.getInt(10) - hours * .25));
            double sleepDebt = Math.min(72, rs.getBigDecimal(13).doubleValue() + hours);
            int pain = rs.getInt(14); int stress = rs.getInt(15); int injury = rs.getInt(16); int illness = rs.getInt(17); int bloodLoss = rs.getInt(18);
            Environment environment = jdbc.query("SELECT ww.weather_kind,ww.intensity,ww.ambient_temperature_c,ww.wind_speed_kph,EXISTS(SELECT 1 FROM construction_project cp JOIN fire_state fs ON fs.construction_id=cp.object_id JOIN world_object pit ON pit.id=cp.object_id JOIN world_object body ON body.current_location_id=pit.current_location_id WHERE body.id=c.id AND fs.active=true) FROM chronicle c JOIN world_weather ww ON ww.world_id=c.world_id WHERE c.id=?", result -> result.next() ? new Environment(result.getString(1),result.getInt(2),result.getBigDecimal(3).doubleValue(),result.getInt(4),result.getBoolean(5)) : new Environment("CLEAR",0,18,0,false), id);
            double core = rs.getBigDecimal(6).doubleValue();
            double exposure = (environment.ambient() - core) * Math.min(.22, hours * .04) - environment.wind() * hours * .004;
            if (environment.fire()) exposure += (37.0 - core) * Math.min(.35, hours * .12);
            core = Math.max(20, Math.min(45, core + exposure));
            if ("RAIN".equals(environment.kind()) || "STORM".equals(environment.kind())) wetness = clamp((int)Math.round(wetness + hours * (environment.intensity() / 7.0)));
            if (environment.fire()) wetness = clamp((int)Math.round(wetness - hours * 14));
            if (food > STARVATION_DEATH_HOURS || water > DEHYDRATION_DEATH_HOURS || bloodLoss > 3500 || illness >= 100) {
                String cause = water > DEHYDRATION_DEATH_HOURS ? "Critical Dehydration" : food > STARVATION_DEATH_HOURS ? "Critical Starvation" : bloodLoss > 3500 ? "Critical Blood Loss" : "Systemic Illness";
                UUID deathLocation = jdbc.query("SELECT current_location_id FROM world_object WHERE id=?", result -> result.next() ? result.getObject(1, UUID.class) : null, id);
                relocatePossessions(id, deathLocation, now);
                jdbc.update("UPDATE chronicle SET life_state = 'DEAD', died_at = ?, death_cause = ? WHERE id = ?", now, cause, id);
                jdbc.update("UPDATE chronicle_body SET health = 'Dying', condition_summary = ? WHERE chronicle_id = ?", cause, id);
                jdbc.update("INSERT INTO chronicle_event (chronicle_id, occurred_at, event_type, payload) VALUES (?, ?, 'CHRONICLE_DIED', jsonb_build_object('cause', ?))", id, now, cause);
                jdbc.update("INSERT INTO world_event (occurred_at, event_type, aggregate_id, payload) VALUES (?, 'CHRONICLE_DIED', ?, jsonb_build_object('cause', ?))", now, id, cause);
            } else {
                jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update = ?, hours_without_food = ?, hours_without_water = ?, energy_level = ?, core_temperature_c=?, wetness_level = ?, bladder_level = ?, bowel_level = ?, hygiene_level = ?, sleep_debt_hours=?, pain_level=?, stress_level=?, injury_severity=?, illness_severity=?, blood_loss_ml=? WHERE chronicle_id = ?", now, BigDecimal.valueOf(food), BigDecimal.valueOf(water), energy, BigDecimal.valueOf(core), wetness, bladder, bowel, hygiene, BigDecimal.valueOf(sleepDebt), pain, stress, injury, illness, bloodLoss, id);
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

    @Transactional
    public void eat(UUID chronicleId) {
        jdbc.update("UPDATE chronicle_physiology SET hours_without_food=GREATEST(0,hours_without_food-8), energy_level=LEAST(100,energy_level+6) WHERE chronicle_id=?", chronicleId);
        refreshBody(chronicleId);
    }
    @Transactional
    public void drink(UUID chronicleId) {
        jdbc.update("UPDATE chronicle_physiology SET hours_without_water=GREATEST(0,hours_without_water-10) WHERE chronicle_id=?", chronicleId);
        refreshBody(chronicleId);
    }
    @Transactional
    public void rest(UUID chronicleId, int minutes) {
        double hours = minutes / 60.0;
        jdbc.update("UPDATE chronicle_physiology SET sleep_debt_hours=GREATEST(0,sleep_debt_hours-?),energy_level=LEAST(100,energy_level+?),pain_level=GREATEST(0,pain_level-?),stress_level=GREATEST(0,stress_level-?) WHERE chronicle_id=?", hours * .85, Math.max(1, (int)Math.round(hours * 9)), Math.max(0, (int)Math.round(hours)), Math.max(0, (int)Math.round(hours * 2)), chronicleId);
        refreshBody(chronicleId);
    }
    @Transactional
    public void applyInjury(UUID chronicleId, int severity, UUID actionId, Instant occurredAt, String source) {
        int bounded=clamp(severity);
        jdbc.update("UPDATE chronicle_physiology SET injury_severity=LEAST(100,injury_severity+?),pain_level=LEAST(100,pain_level+?),stress_level=LEAST(100,stress_level+?),blood_loss_ml=LEAST(7000,blood_loss_ml+?) WHERE chronicle_id=?",bounded,Math.max(1,bounded/2),Math.max(1,bounded/3),bounded*8,chronicleId);
        jdbc.update("INSERT INTO chronicle_condition_event (chronicle_id,occurred_at,condition_kind,severity,source_action_id,payload) VALUES (?,?,?,?,?,jsonb_build_object('source',?))",chronicleId,occurredAt,"INJURY",bounded,actionId,source);
        refreshBody(chronicleId);
    }
    private void refreshBody(UUID chronicleId) {
        jdbc.query("SELECT b.health,b.condition_summary,p.hours_without_food,p.hours_without_water,p.energy_level,p.core_temperature_c,p.wetness_level,p.bladder_level,p.bowel_level,p.hygiene_level FROM chronicle_body b JOIN chronicle_physiology p ON p.chronicle_id=b.chronicle_id WHERE b.chronicle_id=?",rs->{if(rs.next()){BodyHudSnapshot body=snapshot(rs.getString(1),rs.getString(2),rs.getBigDecimal(3).doubleValue(),rs.getBigDecimal(4).doubleValue(),rs.getInt(5),rs.getBigDecimal(6).doubleValue(),rs.getInt(7),rs.getInt(8),rs.getInt(9),rs.getInt(10));jdbc.update("UPDATE chronicle_body SET hunger=?,thirst=?,energy=?,temperature=?,wetness=?,bladder=?,bowel=?,hygiene=? WHERE chronicle_id=?",body.hunger(),body.thirst(),body.energy(),body.temperature(),body.wetness(),body.bladder(),body.bowel(),body.hygiene(),chronicleId);}return null;},chronicleId);
    }
    private void relocatePossessions(UUID chronicleId, UUID locationId, Instant occurredAt) {
        if (locationId == null) return;
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) SELECT id,?,'CHRONICLE_DIED_DROPPED',jsonb_build_object('locationId',?::text) FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE'", occurredAt, locationId.toString(), chronicleId);
        jdbc.update("DELETE FROM equipment_attachment WHERE chronicle_id=?", chronicleId);
        jdbc.update("UPDATE world_object SET current_owner_id=NULL,current_location_id=?,updated_at=? WHERE current_owner_id=? AND lifecycle_state='ACTIVE'", locationId, occurredAt, chronicleId);
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
    private record Environment(String kind,int intensity,double ambient,int wind,boolean fire) { }
    public record BodyHudSnapshot(String health, String condition, String hunger, String thirst, String energy, String temperature, String wetness, String bladder, String bowel, String hygiene) { }
}
