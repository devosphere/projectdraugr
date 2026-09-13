package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
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

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a belt keeps to hand (#134, V315).
 *
 * <p>V248's leather tool girdle and leather pouch girdle were craftable and wearable and did nothing: the tool-belt
 * saving read a three-key list the leather girdle was never added to, and the pouch girdle had no capacity, so it held
 * nothing. These tests wear the girdle and time real bench work, and put something in the pouch girdle.
 *
 * <p>Skips gracefully without Docker.
 */
@SpringBootTest
class WhatABeltKeepsToHandIntegrationTest {

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
    @Autowired ChronicleActionService actions;
    @Autowired PhysicalItemService items;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private UUID awaken() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        return summary.id();
    }

    /** The leather tool girdle shortens bench work, as every lesser tool belt already did. */
    @Test
    void aLeatherToolGirdleShortensBenchWork() {
        UUID chronicle = awaken();
        Instant now = Instant.now();

        var plain = actions.resolve("craft a tinder bundle");
        int plainMinutes = plain.durationMinutes();
        UUID girdle = items.createCarriedItem(chronicle, "leather_utility_belt", "Leather utility belt", now, "TEST_FIXTURE");
        jdbc.update("INSERT INTO equipment_attachment (chronicle_id,item_id,body_position,layer) VALUES (?,?,'WAIST','ATTACHED')", chronicle, girdle);
        var girdled = actions.resolve("craft a tinder bundle");
        assertTrue(girdled.durationMinutes() < plainMinutes,
            () -> "wearing a leather tool girdle must shorten bench work (" + plainMinutes + " -> " + girdled.durationMinutes() + ")");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** Every declared tool carrier is a worn waist item the saving actually reads. */
    @Test
    void everyToolCarrierIsReadAndWearable() {
        Integer unwearable = jdbc.queryForObject(
            "SELECT COUNT(*) FROM tool_carrier tc WHERE NOT EXISTS (SELECT 1 FROM item_equipment_compatibility c " +
            "WHERE c.item_key=tc.item_key AND c.body_position='WAIST')", Integer.class);
        assertEquals(0, unwearable, "a tool carrier must be wearable at the waist");
        Integer leather = jdbc.queryForObject("SELECT COUNT(*) FROM tool_carrier WHERE item_key='leather_utility_belt'", Integer.class);
        assertEquals(1, leather, "the leather tool girdle is a tool carrier");
    }

    /** A girdle hung with pouches holds things. */
    @Test
    void aLeatherPouchGirdleHoldsSomething() {
        UUID chronicle = awaken();
        Instant now = Instant.now();
        UUID here = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        UUID girdle = UUID.randomUUID();
        // The path every made thing takes, which gives a container its declared capacity.
        items.createCraftedItem(chronicle, here, girdle, "leather_pouch_belt", "Leather pouch belt", now, "CRAFTED",
            com.devosphere.draugr.quality.QualityGrade.SOUND);
        Integer volume = jdbc.queryForObject(
            "SELECT COALESCE((SELECT max_volume_ml FROM container_properties WHERE object_id=?),0)", Integer.class, girdle);
        assertTrue(volume > 0, "a pouch girdle must have room in its pouches (had " + volume + " ml)");
    }
}
