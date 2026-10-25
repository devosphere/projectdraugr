package com.devosphere.draugr.persistence;

import com.devosphere.draugr.assembly.AssemblyService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.quality.QualityGrade;
import com.devosphere.draugr.simulation.LocalWeatherService;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nothing is spent on work that cannot go on (#77, the constraint path).
 *
 * <p>#77's test formulation asks for a <b>constraint path</b>: "omit one component … or move inputs out of reach;
 * assert no completed structure and <b>no silent material loss</b>". Nothing asserted it. The code reads right —
 * {@code AssemblyService.advance} checks every input of a stage before it consumes any, and the primary key on
 * {@code assembly_stage_requirement} rules out the one way that could break (the same item listed twice, each row
 * passing its own check and the two together spending more than is there). But a property that only holds by
 * reading is the kind this project keeps finding broken, so this holds it for the whole catalogue.
 *
 * <p>For every verified assembly whose first stage asks for materials, each input in turn is supplied one short:
 * the work must be refused, the refusal must name what is short and by how much, nothing carried may be spent, and
 * no half-begun record may be left behind. Then the last one is supplied and the work must go ahead, spending
 * exactly the declared bill and no more — the positive case, without which a refusal is indistinguishable from a
 * broken assembly.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class NothingIsSpentOnWorkThatCannotGoOnIntegrationTest {

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
    @Autowired AssemblyService assembly;
    @Autowired PhysicalItemService items;
    @Autowired LocalWeatherService weather;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    /** One of each tool class a stage can ask for, none of which is any stage's material. */
    private static final List<String[]> TOOLS = List.of(
        new String[]{"iron_axe", "Iron axe"}, new String[]{"flint_knife", "Flint knife"},
        new String[]{"stone_hammer", "Stone hammer"});

    private UUID aChronicleWithItsTools(Instant now) {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        for (String[] t : TOOLS) items.createCarriedItem(chronicle, t[0], t[1], now, "TEST_SEED");
        return chronicle;
    }

    /**
     * Ground of this biome with nothing on it at all, so that nothing lying here counts toward reach. A well's
     * ground must also be damp and low, or the well refuses on the ground before it reads a single material.
     */
    private UUID bareGround(String biome, boolean forAWell) {
        UUID ground = jdbc.query(
            "SELECT c.id FROM world_chunk c WHERE c.biome=?"
          + (forAWell ? " AND COALESCE(c.moisture,500) >= 450 AND COALESCE(c.elevation,0) <= 700" : "")
          + " AND NOT EXISTS (SELECT 1 FROM world_object w WHERE w.current_location_id=c.id)"
          + " ORDER BY c.grid_y DESC, c.grid_x DESC LIMIT 1",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null, biome);
        assertNotNull(ground, () -> "the world must have bare " + biome + " ground" + (forAWell ? " that takes a well" : ""));
        return ground;
    }

    private int carried(UUID chronicle, String itemKey) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM world_object w JOIN item_instance i ON i.object_id=w.id"
          + " WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND i.item_key=?", Integer.class, chronicle, itemKey);
        return n == null ? 0 : n;
    }

    private int begun(UUID chronicle, String assemblyKey) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM assembly_instance WHERE chronicle_id=? AND assembly_key=?", Integer.class, chronicle, assemblyKey);
        return n == null ? 0 : n;
    }

    private void give(UUID chronicle, String itemKey, int n, Instant now) {
        String name = jdbc.queryForObject("SELECT display_name FROM item_definition WHERE item_key=?", String.class, itemKey);
        for (int i = 0; i < n; i++) items.createCarriedItem(chronicle, itemKey, name, now, "TEST_SEED");
    }

    /** Use up whatever of these the Chronicle still carries — what an earlier assembly made — so each starts empty. */
    private void spendAll(UUID chronicle, List<String> itemKeys, Instant now) {
        for (String k : itemKeys) while (carried(chronicle, k) > 0) assertTrue(items.consumeOne(chronicle, k, now));
    }

    /** Each assembly's longest keyword that names it and nothing else, so no family question gets in the way. */
    private Map<String, String> aPhraseForEach() {
        Map<String, Integer> uses = new HashMap<>();
        Map<String, List<String>> byAssembly = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT assembly_key, keywords FROM assembly_definition WHERE review_state='VERIFIED' ORDER BY assembly_key")) {
            List<String> kws = new ArrayList<>();
            for (String k : ((String) row.get("keywords")).split(",")) {
                String t = k.trim();
                if (t.isEmpty() || kws.contains(t)) continue;
                kws.add(t);
                uses.merge(t, 1, Integer::sum);
            }
            byAssembly.put((String) row.get("assembly_key"), kws);
        }
        Map<String, String> phrase = new LinkedHashMap<>();
        byAssembly.forEach((key, kws) -> kws.stream().filter(k -> uses.get(k) == 1)
            .max((a, b) -> Integer.compare(a.length(), b.length())).ifPresent(k -> phrase.put(key, k)));
        return phrase;
    }

    @Test
    void oneShortOfAnyInputRefusesAndSpendsNothingAndTheFullBillSpendsExactlyIt() {
        Instant now = ticks.current().simulatedAt();
        UUID chronicle = aChronicleWithItsTools(now);
        Map<String, String> phrases = aPhraseForEach();

        List<String> everyFirstStageMaterial = jdbc.queryForList(
            "SELECT DISTINCT r.item_key FROM assembly_stage_requirement r JOIN assembly_stage s ON s.stage_key=r.stage_key"
          + " WHERE s.stage_order=(SELECT MIN(s2.stage_order) FROM assembly_stage s2 WHERE s2.assembly_key=s.assembly_key)"
          + " ORDER BY 1", String.class);
        for (String[] t : TOOLS)
            assertTrue(!everyFirstStageMaterial.contains(t[0]), () -> "the " + t[0] + " is a material now, so it cannot stand in as the tool");

        List<Map<String, Object>> firstStages = jdbc.queryForList(
            "SELECT DISTINCT ON (d.assembly_key) d.assembly_key, d.construction_kind, d.site_biomes, s.stage_key, s.weather_limit,"
          + " COALESCE(ck.takes_the_whole_ground, FALSE) AS takes_the_whole_ground"
          + " FROM assembly_definition d JOIN assembly_stage s ON s.assembly_key=d.assembly_key"
          + " LEFT JOIN construction_kind ck ON ck.project_kind=d.construction_kind"
          + " WHERE d.review_state='VERIFIED' ORDER BY d.assembly_key, s.stage_order");

        Map<String, UUID> grounds = new HashMap<>();
        List<String> exercised = new ArrayList<>();
        List<String> heldByTheSky = new ArrayList<>();
        for (Map<String, Object> stage : firstStages) {
            String key = (String) stage.get("assembly_key");
            Map<String, Integer> bill = new LinkedHashMap<>();
            for (Map<String, Object> r : jdbc.queryForList(
                    "SELECT item_key, quantity FROM assembly_stage_requirement WHERE stage_key=? ORDER BY item_key",
                    stage.get("stage_key")))
                bill.put((String) r.get("item_key"), ((Number) r.get("quantity")).intValue());
            if (bill.isEmpty()) continue;   // a first stage that asks for nothing has nothing to be short of

            String phrase = phrases.get(key);
            assertNotNull(phrase, () -> key + " has no keyword of its own to be asked for by");
            assertEquals(key, assembly.match(phrase), () -> "'" + phrase + "' must name " + key);

            // Stand where it may go up: its own ground if it names one, open grass otherwise.
            String sites = (String) stage.get("site_biomes");
            String biome = sites == null || sites.isBlank() ? "GRASSLAND" : sites.split(",")[0].trim();
            boolean well = "WELL".equals(stage.get("construction_kind"));
            // A kind that takes the whole ground (V432) stands on ground of its own: a goat fold finished earlier
            // in this sweep would otherwise refuse the mud wallow for room before it read a single material.
            UUID here = Boolean.TRUE.equals(stage.get("takes_the_whole_ground"))
                ? bareGround(biome, well)
                : grounds.computeIfAbsent(biome + well, b -> bareGround(biome, well));
            jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", here, chronicle);
            spendAll(chronicle, new ArrayList<>(bill.keySet()), now);
            for (String k : bill.keySet())
                assertEquals(0, items.reachCountAt(chronicle, here, k),
                    () -> key + ": the test must start from none of " + k + " in reach, or 'one short' is not one short");

            String limit = (String) stage.get("weather_limit");
            boolean skyForbids = ("DRY".equals(limit) && weather.wetHere(here)) || ("CALM".equals(limit) && weather.galeHere(here));

            for (String shortKey : bill.keySet()) {
                // From empty each time: the input left short last round must not stay short, nor this one stay full.
                spendAll(chronicle, new ArrayList<>(bill.keySet()), now);
                for (var e : bill.entrySet())
                    give(chronicle, e.getKey(), e.getValue() - (e.getKey().equals(shortKey) ? 1 : 0), now);
                Map<String, Integer> before = new HashMap<>();
                for (String k : bill.keySet()) before.put(k, carried(chronicle, k));

                String[] r = assembly.advance(chronicle, here, phrase, now);
                assertNotNull(r, () -> "'" + phrase + "' must reach the assembly matcher");
                assertEquals("FAILED", r[0], () -> key + " one " + shortKey + " short must be refused: " + r[1]);
                if (!skyForbids) {
                    String wants = "wants " + bill.get(shortKey) + " " + shortKey.replace('_', ' ');
                    assertTrue(r[1].contains(wants), () -> key + ": the refusal must say what is short (" + wants + "): " + r[1]);
                }
                for (String k : bill.keySet())
                    assertEquals(before.get(k), carried(chronicle, k),
                        () -> key + ": a refused stage spent " + k + " — matter lost to work that never went on");
                assertEquals(0, begun(chronicle, key), () -> key + ": a refused first stage must leave no half-begun record");
            }

            // The positive case: the last one supplied, the work goes ahead and spends the bill exactly.
            for (var e : bill.entrySet()) give(chronicle, e.getKey(), e.getValue() - carried(chronicle, e.getKey()), now);
            if (skyForbids) { heldByTheSky.add(key); spendAll(chronicle, new ArrayList<>(bill.keySet()), now); continue; }
            String[] r = assembly.advance(chronicle, here, phrase, now);
            assertEquals("SUCCEEDED", r[0], () -> key + " with its whole bill to hand must go ahead: " + r[1]);
            for (String k : bill.keySet())
                assertEquals(0, carried(chronicle, k) - (producedBy(key).equals(k) ? 1 : 0),
                    () -> key + ": the stage must spend exactly its " + bill.get(k) + " " + k);
            assertEquals(1, begun(chronicle, key), () -> key + ": the work that went ahead must be on record");
            exercised.add(key);
        }

        // Leave nothing half-built behind for the next test that borrows this Chronicle.
        jdbc.update("UPDATE assembly_instance SET state='ABANDONED' WHERE chronicle_id=? AND state='IN_PROGRESS'", chronicle);

        assertTrue(exercised.size() + heldByTheSky.size() >= 100,
            () -> "the sweep must cover the catalogue, not a corner of it: " + exercised.size() + " + " + heldByTheSky.size());
        assertTrue(heldByTheSky.size() <= 6, () -> "only the six weather-limited first stages may be held by the sky: " + heldByTheSky);
        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    private String producedBy(String assemblyKey) {
        String p = jdbc.queryForObject(
            "SELECT CASE WHEN (SELECT COUNT(*) FROM assembly_stage WHERE assembly_key=?) = 1 THEN produces_item_key END"
          + " FROM assembly_definition WHERE assembly_key=?", String.class, assemblyKey, assemblyKey);
        return p == null ? "" : p;
    }

    @Test
    void noKeywordInTheCatalogueGradesTheWorkItNames() {
        // What the sweep above found. It reached the end clean except for the Auditor, which reported a finished
        // brush dome with a defective stage: the attempt was graded from its words with a raw substring match,
        // and "rush" is inside "brush". A keyword is the catalogue telling the player how to ask for a thing, so
        // asking for it in exactly those words must earn ordinary work — never spoiled, and never fine for free.
        List<String> grading = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT 'assembly ' || assembly_key AS owner, keywords FROM assembly_definition"
              + " UNION ALL SELECT 'process ' || process_key, keywords FROM material_process ORDER BY 1")) {
            if (row.get("keywords") == null) continue;
            for (String k : ((String) row.get("keywords")).split(",")) {
                String t = k.trim();
                if (!t.isEmpty() && QualityGrade.attempt(t) != QualityGrade.SOUND)
                    grading.add(row.get("owner") + ": '" + t + "' → " + QualityGrade.attempt(t));
            }
        }
        assertTrue(grading.isEmpty(), () -> "these keywords grade the work they name: " + grading);
    }

    @Test
    void makingsLyingOnOtherGroundAreNotToHandAndAreNotSpent() {
        // Inputs out of reach. A full bill set down one chunk away is not the makings of anything here.
        Instant now = ticks.current().simulatedAt();
        UUID chronicle = aChronicleWithItsTools(now);
        UUID there = bareGround("GRASSLAND", false);
        give(chronicle, "dry_branch", 4, now);
        List<UUID> branches = jdbc.queryForList(
            "SELECT w.id FROM world_object w JOIN item_instance i ON i.object_id=w.id"
          + " WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND i.item_key='dry_branch'", UUID.class, chronicle);
        for (UUID b : branches) assertTrue(items.drop(b, there, now), "the branches must be set down over there");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?",
            jdbc.queryForObject("SELECT id FROM world_chunk WHERE id<>? AND biome='GRASSLAND'"
              + " AND NOT EXISTS (SELECT 1 FROM world_object w WHERE w.current_location_id=world_chunk.id)"
              + " ORDER BY grid_y, grid_x LIMIT 1", UUID.class, there), chronicle);
        UUID here = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);

        String[] r = assembly.advance(chronicle, here, "build a woodworking stump", now);
        assertNotNull(r);
        assertEquals("FAILED", r[0], () -> "the branches are elsewhere, so nothing is to hand: " + r[1]);
        assertTrue(r[1].contains("none to hand"), () -> "and it must say none is to hand: " + r[1]);
        Integer stillThere = jdbc.queryForObject(
            "SELECT COUNT(*) FROM world_object WHERE id = ANY (?) AND lifecycle_state='ACTIVE' AND current_location_id=?",
            Integer.class, branches.toArray(new UUID[0]), there);
        assertEquals(branches.size(), stillThere, "the branches over there must be untouched");
        assertEquals(0, begun(chronicle, "woodworking_stump"), "and nothing may be begun");
        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
