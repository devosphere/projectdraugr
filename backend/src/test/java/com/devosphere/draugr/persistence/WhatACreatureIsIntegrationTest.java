package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
import com.devosphere.draugr.simulation.SimulationTickService;
import com.devosphere.draugr.world.genesis.WorldEcologyGenesisService;
import com.devosphere.draugr.world.genesis.WorldGenesisService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a creature is decides what may be done to it (#110, V344).
 *
 * <p>No people exist in the world yet; #115 will add the first after its review. The protections have to exist
 * first, so this test makes one for its own duration: it declares the mountain goat a PEOPLE (with every flag the
 * schema insists a person carries) and restores the goat's real profile afterwards. Then it asserts the two things
 * the ticket forbids outright: a person cannot be tamed like livestock, and a person's body is not a drop table.
 * The same acts against the goat as it really is, a herding animal, still work, so the gate is the class and nothing
 * else. Skips without Docker.
 */
@SpringBootTest
class WhatACreatureIsIntegrationTest {

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void startDatabase() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required for this integration test");
        System.setProperty("java.awt.headless", "true");
        postgres.start();
    }

    @AfterAll
    static void stopDatabase() { if (postgres.isRunning()) postgres.stop(); }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired WorldGenesisService worldGenesis;
    @Autowired WorldEcologyGenesisService ecology;
    @Autowired ChronicleService chronicles;
    @Autowired WildlifeEncounterService encounters;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private int owned(UUID chronicle, String key) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE i.item_key=? AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, key, chronicle);
        return n == null ? 0 : n;
    }

    private void declareAPeople(String species) {
        jdbc.update("UPDATE cognition_profile SET cognition_class='PEOPLE', communication_mode='LANGUAGE', symbolic_language=TRUE, " +
            "tool_culture=TRUE, individual_identity=TRUE, kinship_model='KIN_GROUP', community_membership=TRUE, moral_agency=TRUE, " +
            "trade_eligible=TRUE, agreement_eligible=TRUE, companionship_eligible=TRUE, settlement_capable=TRUE, " +
            "working_relationship_eligible=TRUE, restraint_prohibited=TRUE, domestication_prohibited=TRUE, remains_protected=TRUE, " +
            "social_risk_profile='COMMUNITY_RETALIATION' WHERE species_key=?", species);
    }

    private void restore(Map<String, Object> was) {
        jdbc.update("UPDATE cognition_profile SET cognition_class=?, communication_mode=?, symbolic_language=?, tool_culture=?, " +
            "individual_identity=?, kinship_model=?, community_membership=?, moral_agency=?, trade_eligible=?, agreement_eligible=?, " +
            "companionship_eligible=?, settlement_capable=?, working_relationship_eligible=?, restraint_prohibited=?, " +
            "domestication_prohibited=?, remains_protected=?, social_risk_profile=? WHERE species_key=?",
            was.get("cognition_class"), was.get("communication_mode"), was.get("symbolic_language"), was.get("tool_culture"),
            was.get("individual_identity"), was.get("kinship_model"), was.get("community_membership"), was.get("moral_agency"),
            was.get("trade_eligible"), was.get("agreement_eligible"), was.get("companionship_eligible"), was.get("settlement_capable"),
            was.get("working_relationship_eligible"), was.get("restraint_prohibited"), was.get("domestication_prohibited"),
            was.get("remains_protected"), was.get("social_risk_profile"), was.get("species_key"));
    }

    private UUID herdOf(String species, UUID chunk, Timestamp ts) {
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Goat cliff',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Goat cliff',40)", site, worldId, chunk);
        UUID pop = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
            "VALUES (?,?,?,'HERBIVORE','DIURNAL',6,8,'FORAGING',?)", pop, site, species, ts);
        return pop;
    }

    private void bodyAt(String species, UUID herd, UUID chunk, Timestamp ts) {
        UUID carcass = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CARCASS','Body',?)", carcass, chunk);
        jdbc.update("INSERT INTO wildlife_carcass (object_id,source_population_id,species_key,remaining_meat_units,hide_available,killed_by_action_id,died_at) " +
            "VALUES (?,?,?,0,true,?,?)", carcass, herd, species, UUID.randomUUID(), ts);
    }

    private int bondsWith(UUID chronicle, UUID herd) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM wildlife_bond WHERE chronicle_id=? AND population_id=?", Integer.class, chronicle, herd);
        return n == null ? 0 : n;
    }

    @Test
    void aPersonIsNeverTamedAndTheirBodyIsNotAHarvest() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, " +
            "maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();
        Timestamp ts = Timestamp.from(now);
        jdbc.update("UPDATE world_object SET lifecycle_state='DESTROYED', destroyed_at=?, destroyed_location_id=current_location_id, " +
            "destroyed_cause='ROTTED', current_location_id=NULL WHERE object_type='CARCASS' AND current_location_id=?", ts, chunk);

        String species = "mountain_goat";
        Map<String, Object> was = jdbc.queryForMap("SELECT * FROM cognition_profile WHERE species_key=?", species);
        assertEquals("SOCIAL", was.get("cognition_class"), "the goat is a herding animal before this test makes it anything else");
        UUID herd = herdOf(species, chunk, ts);

        declareAPeople(species);
        try {
            // Taming: however often and however kindly the approach, a person is not befriended into a pen.
            for (int i = 0; i < 6; i++) encounters.tame(chronicle, chunk, UUID.randomUUID(), now, "approach the mountain goat slowly and offer food");
            assertEquals(0, bondsWith(chronicle, herd), "a people cannot be tamed like livestock");

            // The body: nothing taken, and it says why in terms of what the Chronicle is looking at.
            bodyAt(species, herd, chunk, ts);
            int hidesBefore = owned(chronicle, "animal_hide");
            var taken = encounters.harvest(chronicle, chunk, UUID.randomUUID(), now);
            assertEquals("FAILED", taken.outcome(), () -> "the remains of a person are not a harvest: " + taken.narration());
            assertEquals(hidesBefore, owned(chronicle, "animal_hide"), "not a hide taken from a person");
        } finally {
            restore(was);
        }

        // The control: the same body, as the herding animal the goat really is, is butchered as before. So the gate
        // was the cognition class and nothing else in the fixture.
        var butchered = encounters.harvest(chronicle, chunk, UUID.randomUUID(), now);
        assertEquals("SUCCEEDED", butchered.outcome(), () -> "an animal's carcass is still worked: " + butchered.narration());

        // And the catalogue contract: every creature has declared what it is, and nobody is a person without review.
        List<String> undeclared = jdbc.queryForList(
            "SELECT species_key FROM wildlife_species s WHERE NOT EXISTS (SELECT 1 FROM cognition_profile c WHERE c.species_key=s.species_key)", String.class);
        assertTrue(undeclared.isEmpty(), () -> "every creature must declare its cognition class: " + undeclared);

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
