package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
import com.devosphere.draugr.world.genesis.WorldEcologyGenesisService;
import com.devosphere.draugr.world.genesis.WorldGenesisService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Timestamp;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hunt reads the words (#37).
 *
 * <p>{@code confront} took no action text at all. It picked a quarry from the ground by its own ordering —
 * <b>carnivores first</b> — and fell back to the biggest thing the registry says lives in the biome. So
 * <b>"hunt the deer" in a wood that also held a boar closed with the boar</b>, and "stalk the deer" answered
 * <i>"The wild boar moves with sudden force. The encounter leaves its mark before the forest takes it back."</i>
 *
 * <p>That is "right work, wrong subject" in the place it costs most: this action can injure or kill a Chronicle,
 * so a player who names a herbivore and is given something that fights back has not merely lost a turn.
 *
 * <p>What is held here is the thing a unit test cannot: that the animal actually closed with is <b>the one named</b>,
 * read back from the population the encounter touched — and that naming something absent is refused <b>by name</b>
 * rather than answered with whatever is about.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class TheHuntReadsTheWordsIntegrationTest {

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
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private Timestamp clockWas;
    private UUID keeper;
    private UUID stoodAt;

    @BeforeEach
    void pinTheClock() {
        clockWas = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class);
        // Midday in June: hunting reads wildlife_abroad(species), which is an activity-cycle gate, so the hour
        // decides whether a diurnal animal is out at all. Pinned so the test is not about the time of day.
        jdbc.update("UPDATE simulation_clock SET simulated_at = TIMESTAMPTZ '2031-06-10T12:00:00Z' WHERE id=1");
        jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update = (SELECT simulated_at FROM simulation_clock WHERE id=1), " +
            "hours_without_food=0, hours_without_water=0, sleep_debt_hours=0, injury_severity=0, illness_severity=0, " +
            "blood_loss_ml=0, energy_level=100");
    }

    @AfterEach
    void putItBack() {
        if (keeper != null && stoodAt != null)
            jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", stoodAt, keeper);
        keeper = null; stoodAt = null;
        if (clockWas != null) jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", clockWas);
    }

    private UUID awaken() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        return summary.id();
    }

    /**
     * Ground that holds a named HERBIVORE and a CARNIVORE or OMNIVORE at once — the case the old ordering got
     * wrong, since it took the meat-eater first whatever the hunter said.
     *
     * <p>Both are put on the ground by the test, and the Chronicle stood on it. Nothing is repointed: new sites
     * and populations are added, because the shared world's own herds are other tests' fixtures.
     */
    private Map<String, Object> groundWithBoth(UUID chronicle) {
        // wildlife_abroad() is an activity-cycle gate, and BOTH the standing branch and the materialising one
        // apply it. A nocturnal animal chosen here would be refused at the pinned midday for a reason that has
        // nothing to do with the naming, and the test would pass without having exercised anything. So the
        // animals are chosen to be OUT at the hour the test pinned — the clock is already set by @BeforeEach.
        String herbivore = jdbc.query(
            "SELECT species_key FROM wildlife_species WHERE ecological_role='HERBIVORE' AND kingdom_class <> 'MONSTRUM' " +
            "  AND movement_class <> 'AQUATIC' AND wildlife_abroad(species_key) ORDER BY species_key LIMIT 1",
            rs -> rs.next() ? rs.getString(1) : null);
        String meatEater = jdbc.query(
            "SELECT species_key FROM wildlife_species WHERE ecological_role IN ('CARNIVORE','OMNIVORE') " +
            "  AND kingdom_class <> 'MONSTRUM' AND movement_class <> 'AQUATIC' AND wildlife_abroad(species_key) " +
            "ORDER BY species_key LIMIT 1",
            rs -> rs.next() ? rs.getString(1) : null);
        Assumptions.assumeTrue(herbivore != null && meatEater != null,
            "this catalogue needs a plain herbivore and a plain meat-eater for the case that was wrong");

        keeper = chronicle;
        stoodAt = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        UUID chunk = stoodAt;
        UUID world = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        Timestamp now = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class);

        for (String species : new String[]{herbivore, meatEater}) {
            UUID site = UUID.randomUUID(), pop = UUID.randomUUID();
            jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id,lifecycle_state) " +
                "VALUES (?,'ECOLOGY_SITE',?,?,'ACTIVE')", site, species.replace('_', ' ') + " ground", chunk);
            jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) " +
                "VALUES (?,?,?,'WILDLIFE',?,400)", site, world, chunk, species.replace('_', ' ') + " ground");
            jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle," +
                "population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "SELECT ?,?,?,ws.ecological_role,ws.activity_cycle,6,12,'FORAGING',? FROM wildlife_species ws " +
                "WHERE ws.species_key=?", pop, site, species, now, species);
        }
        return Map.of("chunk", chunk, "herbivore", herbivore, "meatEater", meatEater);
    }

    /** The word a hunter uses: the head noun of a compound key, as every vocabulary in this project now reads. */
    private static String spokenName(String speciesKey) {
        String spoken = speciesKey.replace('_', ' ');
        int cut = spoken.lastIndexOf(' ');
        return cut > 0 ? spoken.substring(cut + 1) : spoken;
    }

    @Test
    void namingTheQuarryCloosesWithThatQuarryAndNotWithWhateverSortsFirst() {
        UUID me = awaken();
        Map<String, Object> ground = groundWithBoth(me);
        String herbivore = (String) ground.get("herbivore");
        String meatEater = (String) ground.get("meatEater");
        // Different words, and neither name inside the other: "boar" sitting inside "wild boar" would make the
        // prose assertion below report a failure that is only a substring. The fix is as liable to the defect
        // as the bug, and that goes for the test too.
        Assumptions.assumeTrue(!spokenName(herbivore).equals(spokenName(meatEater))
                && !herbivore.contains(meatEater) && !meatEater.contains(herbivore),
            "the two must answer to different words for the naming to mean anything");

        // The old behaviour took the meat-eater, because CARNIVORE/OMNIVORE sort before HERBIVORE. Name the
        // herbivore and the herd that loses an animal — or that is merely disturbed — must be the named one.
        int herbivoreBefore = countOf(ground, herbivore);
        int meatEaterBefore = countOf(ground, meatEater);

        WildlifeEncounterService.EncounterResult r = wildlife.confront(
            me, (UUID) ground.get("chunk"), UUID.randomUUID(), clockNow(), 0, "hunt the " + spokenName(herbivore));
        assertNotNull(r, "the hunt must answer");

        // THE TEST MUST NOT PASS VACUOUSLY. If the named animal were not found at all, every assertion below
        // would hold for the wrong reason — nothing touched because nothing happened. The named quarry IS here,
        // so a refusal to find it is itself the failure.
        assertFalse(r.narration().toLowerCase(Locale.ROOT).contains("find none of it here"),
            () -> "the named animal is standing on this ground and must be found: " + r.narration());

        int meatEaterAfter = countOf(ground, meatEater);
        assertEquals(meatEaterBefore, meatEaterAfter,
            () -> "naming the " + spokenName(herbivore) + " must not touch the " + spokenName(meatEater)
                + " herd: " + r.outcome() + " / " + r.narration());

        // And whatever the outcome, the prose must be about what was named rather than about the other animal.
        String said = r.narration().toLowerCase(Locale.ROOT);
        assertFalse(said.contains(meatEater.replace('_', ' ')),
            () -> "the answer names the animal the hunter did not ask for: " + r.narration());

        // A kill takes exactly one from the named herd; a miss takes none. Either is right — what is not right is
        // the other herd moving, which is asserted above.
        int herbivoreAfter = countOf(ground, herbivore);
        assertTrue(herbivoreAfter == herbivoreBefore || herbivoreAfter == herbivoreBefore - 1,
            () -> "a hunt takes one animal or none from the named herd: " + herbivoreBefore + " -> " + herbivoreAfter);
    }

    @Test
    void namingSomethingThatIsNotHereIsRefusedByName() {
        UUID me = awaken();
        Map<String, Object> ground = groundWithBoth(me);
        String chunkBiome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, (UUID) ground.get("chunk"));

        // A species the registry says does NOT belong to this ground and is not standing on it.
        String elsewhere = jdbc.query(
            "SELECT species_key FROM wildlife_species ws WHERE ws.kingdom_class <> 'MONSTRUM' " +
            "  AND ws.biome_affinity NOT ILIKE ? " +
            "  AND NOT EXISTS (SELECT 1 FROM wildlife_population wp JOIN ecology_site es ON es.id=wp.site_id " +
            "                   WHERE es.chunk_id=? AND wp.species_key=ws.species_key) " +
            "  AND ws.species_key <> ? AND ws.species_key <> ? ORDER BY ws.species_key LIMIT 1",
            rs -> rs.next() ? rs.getString(1) : null,
            "%" + chunkBiome + "%", (UUID) ground.get("chunk"), ground.get("herbivore"), ground.get("meatEater"));
        Assumptions.assumeTrue(elsewhere != null, "every species in the catalogue belongs to this biome");

        int meatEaterBefore = countOf(ground, (String) ground.get("meatEater"));
        WildlifeEncounterService.EncounterResult r = wildlife.confront(
            me, (UUID) ground.get("chunk"), UUID.randomUUID(), clockNow(), 0, "hunt the " + elsewhere.replace('_', ' '));

        assertEquals("FAILED", r.outcome(),
            () -> "a species that does not live here cannot be hunted here: " + r.narration());
        assertTrue(r.narration().toLowerCase(Locale.ROOT).contains(elsewhere.replace('_', ' ')),
            () -> "and the refusal names what was looked for: " + r.narration());
        // THE POINT OF THE WHOLE FIX: it must not quietly hand over the thing that IS here.
        assertEquals(meatEaterBefore, countOf(ground, (String) ground.get("meatEater")),
            "naming an absent animal must not start a fight with a present one");

        assertTrue(auditor.inspect().consistent(),
            () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void namingNothingKeepsTheOldBehaviour() {
        UUID me = awaken();
        Map<String, Object> ground = groundWithBoth(me);

        // "kill the animal" names no species, and must still close with what is about — carnivores first, because
        // the dangerous thing is what notices you when you go looking for something to hunt. The half that must
        // not break while the named half is added.
        WildlifeEncounterService.EncounterResult r = wildlife.confront(
            me, (UUID) ground.get("chunk"), UUID.randomUUID(), clockNow(), 0, "kill the animal");
        assertNotNull(r);
        assertFalse(r.narration().toLowerCase(Locale.ROOT).contains("find none of it here"),
            () -> "an unnamed hunt has nothing to fail to find: " + r.narration());

        // And with no text at all — the signature every other caller uses — nothing changes either.
        WildlifeEncounterService.EncounterResult bare = wildlife.confront(
            me, (UUID) ground.get("chunk"), UUID.randomUUID(), clockNow(), 0);
        assertNotNull(bare);
        assertFalse(bare.narration().toLowerCase(Locale.ROOT).contains("find none of it here"),
            () -> "the textless signature must behave exactly as it always did: " + bare.narration());
    }

    private int countOf(Map<String, Object> ground, String species) {
        Integer n = jdbc.queryForObject(
            "SELECT COALESCE(SUM(wp.population_count),0) FROM wildlife_population wp " +
            "JOIN ecology_site es ON es.id=wp.site_id WHERE es.chunk_id=? AND wp.species_key=?",
            Integer.class, ground.get("chunk"), species);
        return n == null ? 0 : n;
    }

    private java.time.Instant clockNow() {
        return jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class).toInstant();
    }
}
