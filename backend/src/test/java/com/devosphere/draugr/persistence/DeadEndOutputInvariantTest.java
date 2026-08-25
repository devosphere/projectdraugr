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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The core mandate as an enforced invariant: <b>every produced item is terminally useful — never a catalogue token.</b>
 * An item that a process or assembly can make must then be consumed by some process, input group, or assembly stage —
 * OR be a terminal item read directly in code (a light burned, a tool wielded, a bandage applied). Anything else is a
 * dead-end: craftable but pointless, the exact defect the whole design exists to forbid.
 *
 * <p>This guards against a whole class of regression that manual audits kept missing (they checked material-process
 * inputs but forgot {@code assembly_stage_requirement}, which is how #363's ashlar-course dead-end slipped in). The
 * allowlist below is the closed set of items whose only consumer is a code reader; every entry names that reader, so
 * a genuinely new dead-end fails here loudly rather than shipping as a token. Query the schema only — reference data
 * comes from migrations, so no world genesis is needed. Skips gracefully without Docker.
 */
@SpringBootTest
class DeadEndOutputInvariantTest {

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

    /**
     * Items whose sole consumer is a code reader, so they are terminal despite never appearing as a recipe or assembly
     * input. Each is read in the service layer for a real effect — verified 2026-08-25. Adding to this list is a
     * deliberate act: it asserts "this item IS used, by code" and should be done only with the reader in hand.
     */
    private static final Set<String> CODE_TERMINAL = Set.of(
            // Lights — burned to work by in the dark (PhysicalItemService.consumePortableLight).
            "resin_torch", "rush_light", "tallow_candle",
            // Fuels / tinder read by fire code (FireService fuels; PhysicalItemService tinder list; oil_lamp burns fish_oil).
            "fish_oil", "wood_shaving",
            // First aid — consumed treating a wound (ChroniclePhysiologyService.treatWound).
            "fibre_bandage_roll", "herbal_poultice", "bark_splint_set", "cordage_arm_sling",
            // Garment materials — worked into worn garments (PhysicalItemService.craftGarment hide/cloth list).
            "dyed_cloth", "fish_skin_leather", "leather_boot_sole",
            // Comfort / hygiene / bedding read in code (WASH consumes soap; BEDDING reads reed_mat).
            "soap", "reed_mat",
            // Tools wielded through code readers (fishing tackle, sewing/leatherwork, archery, boiling, filtering, grinding, wool-combing).
            "bark_water_filter", "boiling_stone_set", "bone_awl", "bone_comb", "bone_fish_hook", "bone_needle",
            "bone_scraper", "bronze_fish_hook", "fish_trap", "hunting_arrow", "lead_sinker", "quern_stone",
            "steel_striker", "wooden_spoon");

    @Test
    void everyProducedItemIsTerminallyUsefulOrAKnownCodeReadItem() {
        // Items a process can make (a real output), consumed by NO process, input group, or assembly stage, and not
        // terminal by their very category (food eaten, a container filled, furniture sited, a document read) nor worn.
        List<String> deadEnds = jdbc.queryForList(
                "SELECT d.item_key FROM item_definition d " +
                "WHERE EXISTS (SELECT 1 FROM material_process mp WHERE mp.output_item_key=d.item_key) " +
                "  AND NOT EXISTS (SELECT 1 FROM material_process_input i WHERE i.item_key=d.item_key) " +
                "  AND NOT EXISTS (SELECT 1 FROM material_process_input_group g WHERE g.item_key=d.item_key) " +
                "  AND NOT EXISTS (SELECT 1 FROM assembly_stage_requirement r WHERE r.item_key=d.item_key) " +
                "  AND d.equippable = false " +
                "  AND d.category NOT IN ('FOOD','DRINK','MEDICINE','FURNITURE','CONTAINER','LITERATURE','MAP') " +
                "ORDER BY d.item_key", String.class);

        List<String> unexplained = deadEnds.stream().filter(k -> !CODE_TERMINAL.contains(k)).collect(Collectors.toList());

        assertTrue(unexplained.isEmpty(), () ->
                "Catalogue-token regression: these items can be produced but are consumed by nothing — no process, " +
                "input group, or assembly stage takes them, and they are not in the known code-read terminal set. " +
                "Either give each a real use (a recipe/assembly that consumes it, or a code reader) or, if it truly is " +
                "read in code, add it to CODE_TERMINAL with the reader named. Dead-ends: " + unexplained);
    }
}
