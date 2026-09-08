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
 * The registries are the answer; the lists were a snapshot of it (#93/#134).
 *
 * <p>Three checks in the Java named their items literally, and the catalogue moved on without them:
 *
 * <ul>
 *   <li>The taming approach asked whether a Chronicle was visibly armed against six item keys. The catalogue holds
 *       <b>36 weapons</b>. So a Chronicle could walk up to a wild animal holding a bronze spear, a hunting bow, a
 *       war club or a poisoned spear and be read as empty-handed — while a stone hammer frightened it.</li>
 *   <li>{@code clearLand} and the axe-wear check both named seven keys, one of which — {@code hand_axe} — is not
 *       an item the catalogue has ever held. A phantom that could never match anything, sitting in a list for
 *       exactly as long as nobody checked.</li>
 *   <li>{@code stone_hand_axe}, which does exist, had no {@code tool_profile} row at all, so the knapped biface
 *       the Palaeolithic is named for could not perform any of the 156 processes wanting a CUTTING edge.</li>
 * </ul>
 *
 * <p>These assertions are deliberately about the data rather than about narration: the defect is that code and
 * catalogue disagreed, and what has to keep being true is that they agree. Skips without Docker.
 */
@SpringBootTest
class RegistriesNotListsIntegrationTest {

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

    /** The biface must be able to cut something — it is a tool before it is a weapon. */
    @Test
    void theKnappedBifaceIsACuttingTool() {
        Integer cutting = jdbc.queryForObject(
            "SELECT COUNT(*) FROM tool_profile WHERE item_key='stone_hand_axe' AND tool_class='CUTTING'", Integer.class);
        assertEquals(1, cutting,
            "a knapped hand axe cuts, scrapes and butchers; without a tool_profile row it can only be swung");

        Integer reach = jdbc.queryForObject(
            "SELECT COUNT(*) FROM material_process WHERE tool_class='CUTTING'", Integer.class);
        assertTrue(reach != null && reach > 50,
            "and the CUTTING class must actually gate a body of work, or registering it changes nothing (" + reach + ")");
    }

    /** A hand axe is not a felling axe, and that is a decision rather than an oversight. */
    @Test
    void theBifaceIsNotAFellingAxe() {
        Integer asAxe = jdbc.queryForObject(
            "SELECT COUNT(*) FROM tool_profile WHERE item_key='stone_hand_axe' AND tool_class='AXE'", Integer.class);
        assertEquals(0, asAxe,
            "you cannot fell woodland with a stone held in the fist — clearing wants a hafted axe");
    }

    /** Every axe the felling checks rely on must be a real item, and every real axe must be reachable through them. */
    @Test
    void theAxeRegistryNamesOnlyRealItems() {
        List<String> phantom = jdbc.queryForList(
            "SELECT t.item_key FROM tool_profile t WHERE t.tool_class='AXE' " +
            "AND NOT EXISTS (SELECT 1 FROM item_definition d WHERE d.item_key=t.item_key)", String.class);
        assertTrue(phantom.isEmpty(),
            () -> "the axe registry names items that do not exist, which is how 'hand_axe' survived in the Java "
                + "list it replaced: " + phantom);

        Integer axes = jdbc.queryForObject("SELECT COUNT(*) FROM tool_profile WHERE tool_class='AXE'", Integer.class);
        assertTrue(axes != null && axes >= 6,
            "the world must hold a range of axes for the registry to be worth reading (" + axes + ")");
    }

    /**
     * The armed check must see the catalogue's weapons, not a handful of them. Asserted as a ratio rather than a
     * fixed number so it keeps meaning as the catalogue grows.
     */
    @Test
    void anAnimalSeesTheWeaponsTheWorldActuallyHolds() {
        Integer brandished = jdbc.queryForObject(
            "SELECT COUNT(*) FROM weapon_profile WHERE combat_role IN ('HAND','BLUNT','JAVELIN','BOW','SLING')",
            Integer.class);
        assertTrue(brandished != null && brandished >= 20,
            "the six keys the old list named were a small fraction of what a Chronicle can hold (" + brandished + ")");

        // Ammunition must stay out of it: an animal that bolted from a loose arrow would be the same error
        // in the other direction.
        Integer ammunition = jdbc.queryForObject(
            "SELECT COUNT(*) FROM weapon_profile WHERE combat_role IN ('ARROW','THROWN_STONE')", Integer.class);
        assertTrue(ammunition != null && ammunition > 0,
            "the catalogue must distinguish ammunition, or the exclusion above is meaningless");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
