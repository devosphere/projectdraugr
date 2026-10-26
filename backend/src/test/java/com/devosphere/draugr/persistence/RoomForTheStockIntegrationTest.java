package com.devosphere.draugr.persistence;

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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Overcrowding (#108, V434): how many beasts a pen holds, and what it costs to keep more in it than that.
 *
 * <p>#108 asks that welfare, disease and breeding "use the actual structure condition <b>and animal
 * population</b>". The population half did not exist: every structure was a boolean, standing or not, so a
 * keeper could hold forty goats in one nesting box and the herd would go on breeding inside it for ever.
 * {@code construction_kind.holds_how_many} now says what each kind holds, in the numbers the catalogue's own
 * descriptions give — "wide enough to lead a yoked pair through" is two, "two rows of partitioned stalls" is
 * six — and three things read it: the stock sicken and go matted when packed in, they do not settle to breed,
 * and the keeper's own stock-check says so with both numbers in it.
 *
 * <p>The fixture owns what it measures: the suite shares one world and one Chronicle, so the head count is READ
 * rather than assumed, and the room is then built to fall under it and to clear it. Skips without Docker.
 */
@SpringBootTest
class RoomForTheStockIntegrationTest {

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
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    /** What one goat fold holds, as V434's data says — read, not written in here twice. */
    private int foldHolds() {
        return jdbc.queryForObject("SELECT holds_how_many FROM construction_kind WHERE project_kind='GOAT_FOLD'", Integer.class);
    }

    private UUID fold(UUID chunk, Instant at) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,lifecycle_state,current_location_id) VALUES (?,'CONSTRUCTION','Goat fold','ACTIVE',?)", id, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent,last_structural_update) " +
                    "VALUES (?,'GOAT_FOLD','COMPLETED',100,?,100,?)", id, Timestamp.from(at), Timestamp.from(at));
        return id;
    }

    private UUID herd(UUID chronicle, UUID chunk, int count, Instant at) {
        Timestamp ts = Timestamp.from(at);
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        UUID site = UUID.randomUUID(), pop = UUID.randomUUID(), bond = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Fold stock',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Fold stock',30)",
            site, worldId, chunk);
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
            "VALUES (?,?,'mountain_goat','HERBIVORE','DIURNAL',?,?,'FORAGING',?)", pop, site, count, count + 20, ts);
        jdbc.update("INSERT INTO wildlife_bond (id,chronicle_id,population_id,bond_stage,trust_level,interaction_count,last_interaction_at," +
            "draft_hunger,draft_thirst,draft_fatigue,sickness,coat_condition) VALUES (?,?,?,'TAMED',95,12,?,0,0,0,0,100)", bond, chronicle, pop, ts);
        return bond;
    }

    private int sickness(UUID bond) { return jdbc.queryForObject("SELECT sickness FROM wildlife_bond WHERE id=?", Integer.class, bond); }
    private int coat(UUID bond) { return jdbc.queryForObject("SELECT coat_condition FROM wildlife_bond WHERE id=?", Integer.class, bond); }
    @Test
    void stockPackedInTighterThanTheirPensHoldSickenAndDoNotBreed() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        Instant now = Instant.parse("2026-05-02T10:00:00Z");

        // Its own empty ground, so nothing another test raised is answering for the pens under test.
        UUID chunk = jdbc.queryForObject(
            "SELECT c.id FROM world_chunk c WHERE c.biome NOT IN ('OCEAN') " +
            "  AND NOT EXISTS (SELECT 1 FROM world_object w WHERE w.current_location_id=c.id AND w.lifecycle_state='ACTIVE') " +
            "ORDER BY c.grid_x DESC, c.grid_y DESC LIMIT 1", UUID.class);
        assertNotNull(chunk, "the world must have empty ground for this fixture");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);

        // Two of a kind, which is what breeding wants, and enough head between them to overfill one fold
        // whatever else this Chronicle happens to be keeping.
        int room = foldHolds();
        UUID one = herd(chronicle, chunk, room, now);
        UUID two = herd(chronicle, chunk, room, now);
        UUID theFold = fold(chunk, now);

        int[] firstLook = items.theRoomTheStockHave(chronicle);
        assertEquals(room, firstLook[1], "one fold is the only room on this ground");
        assertTrue(firstLook[0] > firstLook[1],
            () -> "the fixture must be overcrowded to begin with: " + firstLook[0] + " head, room for " + firstLook[1]);

        // 1. PACKED IN: it tells on their health and their coats.
        items.crowdingTellsOnStock(now);
        assertTrue(sickness(one) > 0, () -> "stock packed in past what the pens hold must sicken, got " + sickness(one));
        assertTrue(coat(one) < 100, () -> "and their coats must suffer for it, got " + coat(one));

        // 2. AND IT IS NOT PAID OFF IN THE SAME TURN, which is the whole reason CROWDING_SICKENS is above
        //    SICKNESS_RECOVERY. Clean ground mends an animal by seven a turn; a crowding cost under that would
        //    fire every turn and change nothing a keeper could ever see. Left packed in, a herd goes downhill
        //    until it is too ill to breed or to give — the gates that already exist on both.
        jdbc.update("UPDATE wildlife_bond SET sickness=0 WHERE id IN (?,?)", one, two);
        int turns = 0;
        while (sickness(one) < PhysicalItemService.TOO_SICK_TO_GIVE && turns < 200) {
            items.advanceHerdSickness(now);   // the mending half of the same turn, on clean ground
            items.crowdingTellsOnStock(now);
            turns++;
        }
        final int tookTurns = turns;
        assertTrue(sickness(one) >= PhysicalItemService.TOO_SICK_TO_GIVE,
            () -> "a herd left packed in must go downhill past giving and breeding, not hold steady: "
                + sickness(one) + " after " + tookTurns + " turns");

        // 3. AND THE KEEPER IS TOLD, with both numbers, because a cost a player cannot see is a cost they
        //    cannot answer.
        String packed = items.stockWelfare(chronicle, chunk, "check on the goats")[1];
        assertTrue(packed.contains("packed in tighter"),
            () -> "the stock-check must name the crowding: " + packed);
        assertTrue(packed.contains("room for " + firstLook[1]),
            () -> "and say how much room there actually is: " + packed);

        // 4. RAISE ENOUGH FOLDS AND IT IS ALL UNDONE — the honest way out, and the control: without this, every
        //    assertion above could be passing for some reason that has nothing to do with room.
        int head = items.theRoomTheStockHave(chronicle)[0];
        for (int built = room; built < head; built += room) fold(chunk, now);
        int[] roomy = items.theRoomTheStockHave(chronicle);
        assertTrue(roomy[1] >= roomy[0],
            () -> "the fixture must now have room for them all: " + roomy[0] + " head, room for " + roomy[1]);

        jdbc.update("UPDATE wildlife_bond SET sickness=0, coat_condition=100 WHERE id IN (?,?)", one, two);
        items.crowdingTellsOnStock(now);
        assertEquals(0, sickness(one), "stock with room enough do not sicken for want of it");
        assertEquals(100, coat(one), "nor rub their coats off each other");

        String settled = items.stockWelfare(chronicle, chunk, "check on the goats")[1];
        assertTrue(!settled.contains("packed in tighter"),
            () -> "the report must stop saying they are crowded once they are not: " + settled);

        // And with room, the same clean turn that mended nothing while they were packed in now holds them at
        // nought: the cost is the crowding and not the keeping.
        jdbc.update("UPDATE wildlife_bond SET sickness=0 WHERE id IN (?,?)", one, two);
        items.advanceHerdSickness(now);
        items.crowdingTellsOnStock(now);
        assertEquals(0, sickness(one), "a herd with room enough does not go downhill at all");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
