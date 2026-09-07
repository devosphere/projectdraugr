package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two more lists that were snapshots of the catalogue (#123/#127, #219).
 *
 * <p>A fresh kill carried on the body draws a hungry predator in, and which items counted as a fresh kill was two
 * keys in the Java. The world also yields {@code raw_fowl_meat} — from four species — and {@code crayfish_meat},
 * and neither drew anything: a Chronicle could carry a goose through predator ground and be no more interesting
 * than one carrying firewood. That list now lives in {@code carcass_scent}, where facts about items belong.
 *
 * <p>Unfired pottery left out in wet ground slumps, and that check named {@code unfired_bowl} and
 * {@code unfired_cup}. {@code unfired_vessel} — which {@code form_vessel} makes and {@code fire_vessel} fires —
 * was wet clay that the rain could not touch.
 *
 * <p>These assertions are about the data because the defect was code and catalogue disagreeing; what must keep
 * being true is that they agree. Skips without Docker.
 */
@SpringBootTest
class ScentAndGreenWareIntegrationTest {

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

    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    /** Every flesh the world yields must smell like flesh. */
    @Test
    void aCarcassIsACarcassWhateverItCameOff() {
        List<String> scented = jdbc.queryForList("SELECT item_key FROM carcass_scent ORDER BY item_key", String.class);
        assertTrue(scented.containsAll(List.of("raw_game_meat", "raw_fish", "raw_fowl_meat", "crayfish_meat")),
            () -> "fowl and crayfish are carcasses too — a goose over the shoulder drew nothing: " + scented);

        List<String> phantom = jdbc.queryForList(
            "SELECT c.item_key FROM carcass_scent c " +
            "WHERE NOT EXISTS (SELECT 1 FROM item_definition d WHERE d.item_key=c.item_key)", String.class);
        assertTrue(phantom.isEmpty(), () -> "carcass_scent must name real items: " + phantom);
    }

    /**
     * The decision not to sweep honey in. A bear will come to honey, but this rule is written about blood and
     * carcass scent, and quietly widening it under cover of fixing a list would be a mechanic changed by accident.
     */
    @Test
    void honeyIsNotACarcass() {
        Integer honey = jdbc.queryForObject(
            "SELECT COUNT(*) FROM carcass_scent WHERE item_key IN ('raw_honey','bird_egg')", Integer.class);
        assertEquals(0, honey,
            "honey and eggs are a separate question and deserve their own decision, not this one by omission");
    }

    /** All green ware is green ware — the rain does not care what the pot was going to be called. */
    @Test
    void everyUnfiredPotIsWetClay() {
        List<String> green = jdbc.queryForList(
            "SELECT item_key FROM item_definition WHERE item_key LIKE 'unfired%' ORDER BY item_key", String.class);
        assertTrue(green.contains("unfired_vessel"),
            () -> "unfired_vessel is what form_vessel makes and fire_vessel fires; it must read as green ware: " + green);
        assertTrue(green.size() >= 3,
            () -> "the weathering rule must cover more than the two keys it used to name: " + green);

        // And the thing the rule must NOT catch: a jar no process fires would slump with no way to save it.
        Integer firesJar = jdbc.queryForObject(
            "SELECT COUNT(*) FROM material_process_input WHERE item_key='clay_jar'", Integer.class);
        assertEquals(0, firesJar,
            "clay_jar is fired by nothing, which is why it is deliberately outside the 'unfired%' rule");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
