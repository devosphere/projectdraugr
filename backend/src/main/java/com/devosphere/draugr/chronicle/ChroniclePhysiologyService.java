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
        jdbc.query("SELECT c.id, p.last_metabolic_update, p.hours_without_food, p.hours_without_water, p.energy_level, p.core_temperature_c, p.wetness_level, p.bladder_level, p.bowel_level, p.hygiene_level, b.health, b.condition_summary FROM chronicle c JOIN chronicle_physiology p ON p.chronicle_id = c.id JOIN chronicle_body b ON b.chronicle_id = c.id WHERE c.life_state = 'LIVING' FOR UPDATE", rs -> {
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
            if (food > STARVATION_DEATH_HOURS || water > DEHYDRATION_DEATH_HOURS) {
                String cause = water > DEHYDRATION_DEATH_HOURS ? "Critical Dehydration" : "Critical Starvation";
                UUID deathLocation = jdbc.query("SELECT current_location_id FROM world_object WHERE id=?", result -> result.next() ? result.getObject(1, UUID.class) : null, id);
                relocatePossessions(id, deathLocation, now);
                jdbc.update("UPDATE chronicle SET life_state = 'DEAD', died_at = ?, death_cause = ? WHERE id = ?", now, cause, id);
                jdbc.update("UPDATE chronicle_body SET health = 'Dying', condition_summary = ? WHERE chronicle_id = ?", cause, id);
                jdbc.update("INSERT INTO chronicle_event (chronicle_id, occurred_at, event_type, payload) VALUES (?, ?, 'CHRONICLE_DIED', jsonb_build_object('cause', ?))", id, now, cause);
                jdbc.update("INSERT INTO world_event (occurred_at, event_type, aggregate_id, payload) VALUES (?, 'CHRONICLE_DIED', ?, jsonb_build_object('cause', ?))", now, id, cause);
            } else {
                jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update = ?, hours_without_food = ?, hours_without_water = ?, energy_level = ?, wetness_level = ?, bladder_level = ?, bowel_level = ?, hygiene_level = ? WHERE chronicle_id = ?", now, BigDecimal.valueOf(food), BigDecimal.valueOf(water), energy, wetness, bladder, bowel, hygiene, id);
                BodyHudSnapshot body = snapshot(rs.getString(11), rs.getString(12), food, water, energy, rs.getBigDecimal(6).doubleValue(), wetness, bladder, bowel, hygiene);
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
    public record BodyHudSnapshot(String health, String condition, String hunger, String thirst, String energy, String temperature, String wetness, String bladder, String bowel, String hygiene) { }
}
