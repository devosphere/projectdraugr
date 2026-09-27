package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Four draft vehicles were one draft vehicle (#106).
 *
 * <p>The haul was gated on {@code EXISTS(... draft_vehicle ...)} — a yes-or-no — and then took the whole of the
 * team's pull, so a travois, a sledge, a cart and a pack-saddle were interchangeable. Two tests in this suite said
 * so outright, each asserting {@code before + 250000}: the same number for a 2 kg pack-saddle and for a sledge,
 * because the vehicle never entered the sum. A cart cost two turned wheels and two hours of work and hauled not
 * one gram more than the fifty-minute travois. That is the generic equipment class this ticket's acceptance
 * criterion forbids — the same defect V371 fixed for the gear worn by the animal, one table along.
 *
 * <p>Nothing is invented here. The world has declared all four beds since V187–V194 — pack-saddle 120 kg,
 * travois 250 kg, sledge 400 kg, cart 600 kg — and the haul read none of them. A team brings home what it can
 * pull or what the bed can hold, whichever runs out first.
 *
 * <p>Asserted as an ORDERING over one unchanged team, because that is the claim: the vehicle decides. Two oxen
 * pull 500 kg between them, so each bed is the answer until the team is the answer — which is exactly when a cart
 * starts being worth its wheels. Skips without Docker.
 */
@SpringBootTest
class DraftVehicleBedIntegrationTest {

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
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private void tameAnAurochs(UUID chronicle, Instant now) {
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        UUID world = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Range',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Range',20)", site, world, chunk);
        UUID pop = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'aurochs','HERBIVORE','DIURNAL',2,4,'FORAGING',?)", pop, site, Timestamp.from(now));
        jdbc.update("INSERT INTO wildlife_bond (chronicle_id,population_id,bond_stage,trust_level,interaction_count,last_interaction_at) " +
                "VALUES (?,?,'TAMED',100,10,?)", chronicle, pop, Timestamp.from(now));
    }

    /** The keeper's capacity over their own, hauling with exactly one sound vehicle of this kind and no other. */
    private int haulWith(UUID chronicle, String vehicle, Instant now, int base) {
        jdbc.update("UPDATE item_instance SET condition_state='BROKEN' WHERE item_key IN (SELECT item_key FROM draft_vehicle) " +
            "AND object_id IN (SELECT id FROM world_object WHERE current_owner_id=?)", chronicle);
        items.createCarriedItem(chronicle, vehicle, vehicle, now, "TEST");
        jdbc.update("UPDATE wildlife_bond SET draft_fatigue=0, draft_hunger=0, draft_thirst=0, draft_conditioning=0 WHERE chronicle_id=?", chronicle);
        return items.sustainedMassCapacity(chronicle) - base;
    }

    @Test
    void theBedDecidesWhatATeamCanBringHome() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        Instant now = ticks.current().simulatedAt();

        // Clean ground: this measures the vehicle, so the keeper must own no other one and no beast but these.
        jdbc.update("DELETE FROM tamed_young WHERE bond_id IN (SELECT id FROM wildlife_bond WHERE chronicle_id=?)", chronicle);
        jdbc.update("DELETE FROM tamed_gestation WHERE bond_id IN (SELECT id FROM wildlife_bond WHERE chronicle_id=?)", chronicle);
        jdbc.update("DELETE FROM wildlife_bond WHERE chronicle_id=?", chronicle);
        jdbc.update("UPDATE item_instance SET condition_state='BROKEN' WHERE item_key IN (SELECT item_key FROM draft_vehicle) " +
            "AND object_id IN (SELECT id FROM world_object WHERE current_owner_id=?)", chronicle);
        int base = items.sustainedMassCapacity(chronicle);

        // The beds the world declares. Asserted rather than assumed: every number below is one of these, and if
        // the catalogue changes them this test must be the thing that notices.
        assertEquals(java.util.List.of(120000, 250000, 400000, 600000), jdbc.queryForList(
            "SELECT max_mass_grams FROM container_capacity_default WHERE item_key IN (SELECT item_key FROM draft_vehicle) ORDER BY max_mass_grams",
            Integer.class), "the four draft vehicles declare four different beds");

        // Two oxen: 500 kg of pull between them, which is more than three of the four beds can hold.
        tameAnAurochs(chronicle, now);
        tameAnAurochs(chronicle, now);

        int pack = haulWith(chronicle, "pack_saddle", now, base);
        int travois = haulWith(chronicle, "travois", now, base);
        int sledge = haulWith(chronicle, "sledge", now, base);
        int cart = haulWith(chronicle, "cart", now, base);

        assertEquals(120000, pack, "a pack-saddle carries a pack-saddle's worth, whatever is pulling it");
        assertEquals(250000, travois, "a travois brings home a travois-bed");
        assertEquals(400000, sledge, "a sledge bed holds more, and now that is worth something");
        assertEquals(500000, cart, "a 600 kg cart is the first bed the team fills rather than the bed filling first");

        // The claim, stated as the ordering it is. Before this, every one of these four was the same number.
        assertTrue(pack < travois && travois < sledge && sledge < cart,
            () -> "the vehicle must decide the haul: " + pack + " < " + travois + " < " + sledge + " < " + cart);

        // Owning several, the best bed answers — a keeper does not lose their cart by also owning a travois.
        items.createCarriedItem(chronicle, "travois", "Travois", now, "TEST");
        jdbc.update("UPDATE wildlife_bond SET draft_fatigue=0, draft_hunger=0, draft_thirst=0, draft_conditioning=0 WHERE chronicle_id=?", chronicle);
        assertEquals(base + 500000, items.sustainedMassCapacity(chronicle),
            "with a cart and a travois both to hand, the cart is what they load");

        // And a bed is not a way around a tired team: the cap is the smaller of the two, in both directions.
        jdbc.update("UPDATE wildlife_bond SET draft_fatigue=100 WHERE chronicle_id=?", chronicle);
        assertEquals(base, items.sustainedMassCapacity(chronicle),
            "a spent team pulls nothing, and the biggest bed in the world does not pull it for them");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
