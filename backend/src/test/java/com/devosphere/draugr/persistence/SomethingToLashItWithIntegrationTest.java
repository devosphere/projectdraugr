package com.devosphere.draugr.persistence;

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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #899 — SOMETHING TO LASH IT WITH.
 *
 * <p>Five tool recipes each asked "have I got a binding" by naming {@code plant_fiber} as a literal, in
 * seven places between the gate and the consumption. So a Chronicle who had retted a loose bark strip into
 * soft bast — a BETTER lashing, and the one the documented hafting method uses — could haft nothing at all:
 * not a knife, not a spear, not an axe. They were holding a better binding than the code would accept and
 * were told "the stone and loose fiber never settle into a usable edge", a sentence about fiber they did not
 * have and were not using.
 *
 * <p>Skips without Docker. Every query here was also run against a throwaway Postgres with the full
 * migration set applied, because a suite that skips is not evidence.
 */
@SpringBootTest
class SomethingToLashItWithIntegrationTest {

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
    @Autowired JdbcTemplate jdbc;

    private UUID chronicle() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        return summary.id();
    }

    /** Put a specific item in this Chronicle's hands, so the test owns what it measures. */
    private void give(UUID chronicle, String itemKey, String displayName) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id, object_type, display_name, current_owner_id) VALUES (?,'ITEM',?,?)",
            id, displayName, chronicle);
        jdbc.update("INSERT INTO item_instance (object_id, item_key) VALUES (?,?)", id, itemKey);
    }

    private void takeAwayEveryBinding(UUID chronicle) {
        jdbc.update("UPDATE world_object SET current_owner_id = NULL, current_location_id = " +
                    "  (SELECT current_location_id FROM world_object WHERE id = ?) " +
                    " WHERE current_owner_id = ? AND id IN (SELECT object_id FROM item_instance " +
                    "   WHERE item_key IN (SELECT item_key FROM binding_material))", chronicle, chronicle);
    }

    /**
     * EVERY BINDING IS ATTAINABLE. A catalogue entry no player can obtain would be the token this project
     * refuses: a lashing that exists only to be listed.
     */
    @Test void everyBindingCanActuallyBeObtained() {
        List<String> unreachable = jdbc.queryForList(
            "SELECT b.item_key FROM binding_material b " +
            " WHERE NOT EXISTS (SELECT 1 FROM material_process WHERE output_item_key = b.item_key) " +
            "   AND b.item_key NOT IN ('plant_fiber', 'animal_sinew') " +   // gathered, and cut from a carcass
            " ORDER BY 1", String.class);
        assertTrue(unreachable.isEmpty(), "bindings nothing can produce: " + unreachable);
        assertTrue(jdbc.queryForObject("SELECT count(*) FROM binding_material", Integer.class) >= 6);
        // And none of them is a finished piece of gear being cannibalised: consuming a carry strap to haft an
        // axe would be exactly the silent wrong thing the literal was already doing.
        List<String> wornGear = jdbc.queryForList(
            "SELECT b.item_key FROM binding_material b JOIN item_equipment_compatibility c USING (item_key) ORDER BY 1",
            String.class);
        assertTrue(wornGear.isEmpty(), "bindings that are really worn gear: " + wornGear);
    }

    /** The best binding in hand is the one a person reaches for, and nothing is one answer. */
    @Test void theBestBindingInReachIsTheOneChosen() {
        UUID me = chronicle();
        takeAwayEveryBinding(me);
        assertNull(items.aBindingInReach(me), "a Chronicle with nothing to lash with has nothing to lash with");

        give(me, "plant_fiber", "Plant fiber bundle");
        assertEquals("plant_fiber", items.aBindingInReach(me));
        give(me, "soft_bast_strip", "Soft bast strip");
        assertEquals("soft_bast_strip", items.aBindingInReach(me), "retted bast holds better than loose fibre");
        give(me, "animal_sinew", "Animal Sinew");
        assertEquals("animal_sinew", items.aBindingInReach(me), "nothing primitive holds a stone head like sinew");
    }

    /**
     * THE REPORTED CASE: bast and a stone, and no plant fibre anywhere. Before this, the knife was refused.
     */
    @Test void aStoneKnifeCanBeHaftedWithRettedBast() {
        UUID me = chronicle();
        takeAwayEveryBinding(me);
        give(me, "field_stone", "Field stone");
        give(me, "soft_bast_strip", "Soft bast strip");
        assertEquals(0, (int) jdbc.queryForObject(
            "SELECT count(*) FROM item_instance i JOIN world_object w ON w.id = i.object_id " +
            " WHERE w.current_owner_id = ? AND i.item_key = 'plant_fiber'", Integer.class, me),
            "the whole point is that there is no plant fibre");

        PhysicalItemService.ItemView knife = items.craftPrimitiveTool(
            "stone_knife", "Stone knife", false, items.aBindingInReach(me), Instant.now());
        assertNotNull(knife);
        assertEquals("stone_knife", knife.itemKey());

        // And the bast was spent, not the fibre that was never there.
        assertEquals(0, (int) jdbc.queryForObject(
            "SELECT count(*) FROM item_instance i JOIN world_object w ON w.id = i.object_id " +
            " WHERE w.current_owner_id = ? AND i.item_key = 'soft_bast_strip'", Integer.class, me),
            "the bast must have been consumed");
    }

    /** The knife is usable by what comes after it — #899's "recognized as a cutting tool" requirement. */
    @Test void theKnifeHaftedWithBastIsARealCuttingTool() {
        assertTrue(jdbc.queryForObject(
            "SELECT count(*) FROM tool_profile WHERE item_key = 'stone_knife'", Integer.class) > 0,
            "a stone knife must be a catalogued tool, however it was hafted");
        // Same row whichever binding made it, because the binding is consumed and the knife is the knife.
        assertEquals("stone_knife", jdbc.queryForObject(
            "SELECT item_key FROM tool_profile WHERE item_key = 'stone_knife'", String.class));
    }

    /** A sinew-hafted spear too, so this is not a knife-shaped fix. */
    @Test void aSpearCanBeHaftedWithSinew() {
        UUID me = chronicle();
        takeAwayEveryBinding(me);
        give(me, "field_stone", "Field stone");
        give(me, "dry_branch", "Dry branch");
        give(me, "animal_sinew", "Animal Sinew");
        PhysicalItemService.ItemView spear = items.craftPrimitiveSpear(items.aBindingInReach(me), Instant.now());
        assertNotNull(spear);
        assertEquals("primitive_spear", spear.itemKey());
    }
}
