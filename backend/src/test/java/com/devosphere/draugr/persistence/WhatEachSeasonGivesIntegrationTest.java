package com.devosphere.draugr.persistence;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What each season gives (#161, V323): milk, eggs, wool, honey, insects and wild bird eggs keep their seasons.
 *
 * <p>Every reader of tamed_yield, insect_colony_product and wildlife_drop calls {@code in_season()}, so the function
 * and the months it is given are what is asserted here — against the world clock set to midwinter and to high summer,
 * and restored afterwards.
 *
 * <p>Skips gracefully without Docker.
 */
@SpringBootTest
class WhatEachSeasonGivesIntegrationTest {

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

    @Autowired JdbcTemplate jdbc;

    private boolean given(String sql) {
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class));
    }

    @Test
    void eachSupplyIsThereInItsSeasonAndNotOutOfIt() {
        Timestamp original = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class);
        try {
            jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", Timestamp.from(Instant.parse("2031-01-15T12:00:00Z")));
            assertFalse(given("SELECT in_season(available_months) FROM tamed_yield WHERE species_key='mountain_goat' AND item_key='goat_milk'"), "no goat milk in January");
            assertFalse(given("SELECT in_season(available_months) FROM tamed_yield WHERE species_key='quail' AND item_key='fowl_egg'"), "no laying in January");
            assertFalse(given("SELECT in_season(available_months) FROM tamed_yield WHERE species_key='bighorn_sheep' AND item_key='wool_tuft'"), "no moult in January");
            assertFalse(given("SELECT bool_or(in_season(available_months)) FROM insect_colony_product WHERE item_key='raw_honey'"), "no honey in January");
            assertFalse(given("SELECT bool_or(in_season(available_months)) FROM wildlife_drop WHERE item_key='bird_egg'"), "no wild egg in January");
            assertTrue(given("SELECT in_season(available_months) FROM tamed_yield WHERE species_key='water_buffalo' AND item_key='goat_milk'"), "buffalo calve in any month");
            assertTrue(given("SELECT bool_and(in_season(available_months)) FROM wildlife_drop WHERE item_key <> 'bird_egg'"), "meat, hide and bone are there all year");

            jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", Timestamp.from(Instant.parse("2031-06-15T12:00:00Z")));
            assertTrue(given("SELECT in_season(available_months) FROM tamed_yield WHERE species_key='mountain_goat' AND item_key='goat_milk'"), "goat milk in June");
            assertTrue(given("SELECT in_season(available_months) FROM tamed_yield WHERE species_key='quail' AND item_key='fowl_egg'"), "laying in June");
            assertTrue(given("SELECT in_season(available_months) FROM tamed_yield WHERE species_key='bighorn_sheep' AND item_key='wool_tuft'"), "the moult in June");
            assertTrue(given("SELECT bool_or(in_season(available_months)) FROM wildlife_drop WHERE item_key='bird_egg'"), "wild eggs in June");
            assertFalse(given("SELECT bool_or(in_season(available_months)) FROM insect_colony_product WHERE item_key='raw_honey'"), "honey waits for late summer");

            assertEquals(0, (int) jdbc.queryForObject(
                "SELECT count(*) FROM tamed_yield WHERE available_months IS NOT NULL AND cardinality(available_months)=0", Integer.class),
                "an empty month list would mean never, which is a deletion");
        } finally {
            jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", original);
        }
    }
}
