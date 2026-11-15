package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A crafted crayfish trap is a thing you SET, and the crayfish you name is the one you fish for (#923, #908).
 *
 * <p>Measured before: {@code set a crayfish trap} with a crafted Crayfish trap in the pack answered "you do not
 * have what a trap of that kind needs" — setting only knew how to weave one from rods on the spot — and
 * {@code fish for crayfish with my crayfish trap} at water holding crayfish landed a carp.
 *
 * <p>Owns what it measures: every trap standing on the test water is cleared first (the check reads the oldest
 * set on the ground, whoever set it), and the fish stock is reset so a stretch drawn down by another test does
 * not read as the named species missing. Skips without Docker.
 */
@SpringBootTest
class ACrayfishTrapIsSetNotRebuiltIntegrationTest {

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
    @Autowired WildlifeEncounterService wildlife;
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private UUID me;
    private Instant now;

    /** A living Chronicle standing at water that holds crayfish, with every set on that water cleared. */
    private UUID atCrayfishWater() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.active() != null ? chronicles.active() : chronicles.awaken();
        assertNotNull(summary, "a living Chronicle is needed");
        me = summary.id();
        UUID water = jdbc.query(
            "SELECT c.id FROM world_chunk c WHERE EXISTS (SELECT 1 FROM wildlife_species s WHERE s.species_key='crayfish' "
            + "AND s.movement_class='AQUATIC' AND s.biome_affinity ILIKE '%'||c.biome||'%') ORDER BY c.grid_y, c.grid_x LIMIT 1",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null);
        Assumptions.assumeTrue(water != null, "this world has no water that holds crayfish");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", water, me);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", me);
        jdbc.update("UPDATE placed_trap SET sprung=true WHERE NOT sprung AND (chunk_id=? OR set_by=?)", water, me);
        jdbc.update("DELETE FROM fish_stock WHERE chunk_id=?", water);
        now = ticks.current().simulatedAt();
        return water;
    }

    private int carried(String itemKey) {
        Integer n = jdbc.queryForObject(
            "SELECT count(*) FROM world_object w JOIN item_instance i ON i.object_id=w.id "
            + "WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND i.item_key=?", Integer.class, me, itemKey);
        return n == null ? 0 : n;
    }

    private int standing() {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM placed_trap WHERE set_by=? AND NOT sprung", Integer.class, me);
        return n == null ? 0 : n;
    }

    @Test
    void aCarriedCrayfishTrapIsSetAsItIsAndComesBackWhole() {
        UUID water = atCrayfishWater();
        // Cordage in hand too, so a fall-through to the snare recipe would have something to spend and be seen.
        items.createCarriedItem(me, "plant_fiber", "Plant fiber", now, "TEST_SEED");
        items.createCarriedItem(me, "plant_fiber", "Plant fiber", now, "TEST_SEED");
        int fibreBefore = carried("plant_fiber");
        items.createCarriedItem(me, "crayfish_trap", "Crayfish trap", now, "TEST_SEED");
        int trapsBefore = carried("crayfish_trap");

        var set = wildlife.setTrap(me, water, now, "Set a crayfish trap");
        assertEquals("SUCCEEDED", set.outcome(), () -> "a carried crayfish trap must set: " + set.narration());
        assertEquals(trapsBefore - 1, carried("crayfish_trap"), "the trap leaves the pack for the water");
        assertEquals(fibreBefore, carried("plant_fiber"), "setting a crayfish trap spends no snare cordage");
        String kind = jdbc.queryForObject("SELECT trap_kind FROM placed_trap WHERE set_by=? AND NOT sprung", String.class, me);
        assertEquals("FISH_TRAP", kind, "it stands in the water as a water trap, never a snare");
        List<String> builtFrom = jdbc.queryForList("SELECT unnest(built_from) FROM placed_trap WHERE set_by=? AND NOT sprung", String.class, me);
        assertEquals(List.of("crayfish_trap"), builtFrom, "the set records the trap itself, so the lift can give it back");

        var lifted = wildlife.liftTrap(me, water, now);
        assertEquals("SUCCEEDED", lifted.outcome(), () -> "a standing crayfish trap must lift: " + lifted.narration());
        assertEquals(trapsBefore, carried("crayfish_trap"), "a woven funnel trap comes back whole, not frayed to nothing");
        assertEquals(0, standing(), "and nothing of it is left standing");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void aWaterTrapIsNotSetOnDryGroundAndANamedTrapYouLackIsNotWovenInstead() {
        atCrayfishWater();
        UUID dry = jdbc.query(
            "SELECT c.id FROM world_chunk c WHERE NOT EXISTS (SELECT 1 FROM wildlife_species s WHERE s.movement_class='AQUATIC' "
            + "AND s.biome_affinity ILIKE '%'||c.biome||'%') ORDER BY c.grid_y, c.grid_x LIMIT 1",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null);
        Assumptions.assumeTrue(dry != null, "this world has no ground without water life");
        items.createCarriedItem(me, "crayfish_trap", "Crayfish trap", now, "TEST_SEED");
        int traps = carried("crayfish_trap");

        var onDryGround = wildlife.setTrap(me, dry, now, "set a crayfish trap");
        assertEquals("FAILED", onDryGround.outcome(), () -> "a water trap on dry ground must be refused: " + onDryGround.narration());
        assertTrue(onDryGround.narration().toLowerCase().contains("water"), () -> "and the refusal says why: " + onDryGround.narration());
        assertEquals(traps, carried("crayfish_trap"), "nothing is spent on a refused set");

        // A basket fish trap named and not carried is refused by name; the hazel rods in the pack stay there.
        UUID water = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, me);
        Assumptions.assumeTrue(carried("basket_fish_trap") == 0, "the Chronicle already carries a basket fish trap");
        items.createCarriedItem(me, "hazel_rod", "Hazel Rod", now, "TEST_SEED");
        items.createCarriedItem(me, "hazel_rod", "Hazel Rod", now, "TEST_SEED");
        int rods = carried("hazel_rod");
        var named = wildlife.setTrap(me, water, now, "set the basket fish trap");
        assertEquals("FAILED", named.outcome(), () -> "a named trap you do not carry must not set: " + named.narration());
        assertEquals(rods, carried("hazel_rod"), "and no other trap is woven from your rods in its place");
        assertEquals(0, standing(), "nothing stands");
    }

    /**
     * A SET crayfish trap fishes for crayfish (#908's remainder). Before: the check drew uniformly from every water
     * species of the biome, so a crayfish trap at a wetland came up with carp, pike and sturgeon. Now its throat
     * admits only crayfish-sized and small fish, and crayfish are at least half of what it takes.
     */
    @Test
    void aSetCrayfishTrapTakesCrayfishAndNothingTooBigForItsThroat() {
        UUID water = atCrayfishWater();
        items.createCarriedItem(me, "crayfish_trap", "Crayfish trap", now, "TEST_SEED");
        java.util.Random rnd = new java.util.Random(9080);
        int catches = 0, crayfish = 0;
        for (int i = 0; i < 60; i++) {
            jdbc.update("UPDATE placed_trap SET sprung=true WHERE NOT sprung AND chunk_id=?", water);
            var set = wildlife.setTrap(me, water, now, "set a crayfish trap");
            assertEquals("SUCCEEDED", set.outcome(), () -> "the crayfish trap must set again after each check: " + set.narration());
            UUID trap = jdbc.queryForObject("SELECT object_id FROM placed_trap WHERE set_by=? AND NOT sprung", UUID.class, me);
            var checked = wildlife.checkTrap(me, water, new UUID(rnd.nextLong(), rnd.nextLong()), now.plusSeconds(12 * 3600));
            String caught = jdbc.queryForObject("SELECT caught_species FROM placed_trap WHERE object_id=?", String.class, trap);
            if (caught == null) {
                // An empty check leaves the trap standing; take it back so the next round sets the same one.
                wildlife.liftTrap(me, water, now);
                continue;
            }
            catches++;
            if (caught.equals("crayfish")) crayfish++;
            String tier = jdbc.queryForObject("SELECT size_tier FROM wildlife_species WHERE species_key=?", String.class, caught);
            assertTrue(tier.equals("TINY") || tier.equals("SMALL"),
                () -> "a crayfish trap's throat takes nothing bigger than a small fish, but held a " + caught + " (" + tier + "): " + checked.narration());
            assertTrue(checked.narration().contains(caught.replace('_', ' ')),
                () -> "the check names what was in it: " + checked.narration());
        }
        final int took = catches, cray = crayfish;
        assertTrue(took >= 20, () -> "a crayfish trap left twelve hours should take something most times, took " + took + " of 60");
        assertTrue(cray * 10 >= took * 4, () -> "crayfish should be most of what a crayfish trap takes, were " + cray + " of " + took);
        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void fishingForCrayfishCatchesCrayfishOrSaysItCannot() {
        UUID water = atCrayfishWater();
        items.createCarriedItem(me, "crayfish_trap", "Crayfish trap", now, "TEST_SEED");
        List<String> otherSpecies = jdbc.queryForList(
            "SELECT species_key FROM wildlife_species WHERE movement_class='AQUATIC' AND species_key<>'crayfish'", String.class);

        java.util.Random rnd = new java.util.Random(908);
        int landed = 0;
        for (int i = 0; i < 40; i++) {
            jdbc.update("DELETE FROM fish_stock WHERE chunk_id=?", water);
            var r = wildlife.fish(me, water, new UUID(rnd.nextLong(), rnd.nextLong()), now, "fish for crayfish with my crayfish trap");
            if (!"SUCCEEDED".equals(r.outcome())) continue;
            landed++;
            String said = r.narration().toLowerCase();
            assertTrue(said.contains("crayfish"), () -> "a catch made fishing for crayfish is a crayfish: " + r.narration());
            for (String other : otherSpecies)
                assertFalse(said.contains(other.replace('_', ' ')), () -> "fishing for crayfish must not land a " + other + ": " + r.narration());
        }
        assertTrue(landed > 0, "a trap at crayfish water must land some crayfish in forty tries");

        // A species this water does not hold is refused by name, not answered with whatever is there.
        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, water);
        String absent = jdbc.query(
            "SELECT species_key FROM wildlife_species WHERE movement_class='AQUATIC' AND biome_affinity NOT ILIKE ? "
            + "AND species_key NOT LIKE '%\\_%' ORDER BY species_key LIMIT 1",
            rs -> rs.next() ? rs.getString(1) : null, "%" + biome + "%");
        Assumptions.assumeTrue(absent != null, "every single-word water species lives here");
        var none = wildlife.fish(me, water, UUID.randomUUID(), now, "fish for " + absent);
        assertEquals("FAILED", none.outcome(), () -> "fishing for " + absent + " where there is none must fail: " + none.narration());
        assertTrue(none.narration().contains(absent), () -> "and must name what was asked for: " + none.narration());
    }
}
