package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.chronicle.ChroniclePhysiologyService;
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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "How is my leg" is answered (#37/#99).
 *
 * <p>The ticket's complaint was that a question about a part of the Chronicle's own body reached nothing at
 * all. The cause had two halves. {@code asksAboutABodyPart} already existed — with the whole body-part
 * vocabulary — and was used in exactly ONE place, as a negative guard, so the words were recognised, the
 * question was kept away from the gear rule that used to answer it with a list of clothes, and then nothing
 * answered it. Its own comment said why: "there is no honest answer to route it to and inventing one would be
 * the opposite of this ticket's whole point", because nothing recorded WHERE a wound was.
 *
 * <p>Both halves are closed now. Every injury records its part in the condition history it was already
 * writing, and the body reading says where the hurt sits.
 *
 * <p>THREE ANSWERS, and the third is the one this test exists to protect:
 * <ol>
 *   <li>a wound at the part asked after — name it, and how bad it is;</li>
 *   <li>a wound somewhere else — the part asked after is sound, and say where the hurt actually is;</li>
 *   <li>a hurt with no part recorded — <b>say you cannot tell where it sits</b>, rather than naming a limb so
 *       the answer sounds definite.</li>
 * </ol>
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class WhereItHurtsIntegrationTest {

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
    @Autowired ChroniclePhysiologyService physiology;
    @Autowired ChronicleActionService actions;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private UUID aLivingChronicle() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.active() != null ? chronicles.active() : chronicles.awaken();
        assertNotNull(summary, "a living Chronicle is needed");
        return summary.id();
    }

    private String said(String text) {
        var r = actions.resolve(text, UUID.randomUUID());
        assertNotNull(r, () -> "the composer must answer: " + text);
        return (r.perception() == null ? "" : r.perception()).toLowerCase(Locale.ROOT);
    }

    /** Whole, so the "sound" answers are about a body that really is. */
    private void whole(UUID me) {
        jdbc.update("UPDATE chronicle_physiology SET injury_severity=0, pain_level=0, blood_loss_ml=0 WHERE chronicle_id=?", me);
    }

    @Test
    void aQuestionAboutOnePartIsAnsweredAboutThatPart() {
        UUID me = aLivingChronicle();
        Instant now = ticks.current().simulatedAt();

        // (1) A WHOLE BODY SAYS SO. Asserted first, because "sound" must not be what this reading says to
        // everything — the answer has to move when the body does.
        whole(me);
        String sound = said("how is my leg");
        assertTrue(sound.contains("sound"), () -> "a whole leg must read as sound: " + sound);
        assertFalse(sound.contains("hurt sits"), () -> "and must not report a hurt that is not there: " + sound);

        // AND IN THE NUMBER IT WAS ASKED IN. "how are my feet" answered "Your foot is sound" in the first cut —
        // true, in the wrong number, which in prose written this carefully reads as a seam.
        String feet = said("how are my feet");
        assertTrue(feet.contains("your feet are"), () -> "a question about both feet is answered about both: " + feet);

        // (2) A WOUND AT THE PART ASKED AFTER. Applied through the service, so what is asserted is the path the
        // world actually takes when an animal bites — not a row written straight into the table.
        physiology.applyInjury(me, 34, null, now, "TEST_FIXTURE", "ANKLE");
        String ankle = said("how is my ankle");
        assertTrue(ankle.contains("hurt sits in your ankle"),
            () -> "the hurt was recorded at the ankle and the ankle was asked after: " + ankle);

        // (3) A WOUND SOMEWHERE ELSE. The leg is sound AND the answer says where the hurt really is, because a
        // keeper of their own body wants the second half as much as the first.
        String leg = said("how is my leg");
        assertTrue(leg.contains("sound"), () -> "the leg itself is sound: " + leg);
        assertTrue(leg.contains("ankle"), () -> "and the answer must say where the hurt actually sits: " + leg);

        // AND THE WHOLE-BODY READING CARRIES IT TOO, so "where am I hurt" is answered rather than merely heard.
        String whereAmI = said("where am i hurt");
        assertTrue(whereAmI.contains("ankle"), () -> "\"where am I hurt\" must say where: " + whereAmI);

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /**
     * A HURT WITH NO PART RECORDED SAYS SO — the answer that keeps this feature honest.
     *
     * <p>It happens for an injury older than the record and for a source the catalogue cannot place, and the
     * truthful answer is that you cannot tell where it sits. Naming a limb would be the invention the scenario
     * matrix warned about when it called per-limb state a design decision rather than a defect.
     *
     * <p>OWNED RATHER THAN ASSUMED: this suite shares one world and one Chronicle across hundreds of tests, so
     * another test may already have wounded this body at a known part — in which case the case under test does
     * not exist and the assertion would be measuring someone else's fixture. It is skipped then, with the
     * reason said out loud, which is the lesson this project has already paid for four times.
     */
    @Test
    void aHurtWithNoPlaceSaysItHasNoPlace() {
        UUID me = aLivingChronicle();
        Instant now = ticks.current().simulatedAt();
        whole(me);
        physiology.applyInjury(me, 22, null, now, "TEST_FIXTURE", null);

        List<Map<String, Object>> placed = physiology.whereItHurts(me, now);
        Assumptions.assumeTrue(placed.isEmpty(),
            () -> "this Chronicle already carries a wound with a place on it, so the no-place case is not "
                + "reachable here: " + placed);

        String leg = said("how is my leg");
        assertTrue(leg.contains("could not say where"),
            () -> "a hurt with no recorded place must admit it has none: " + leg);
        assertFalse(leg.contains("hurt sits in"),
            () -> "and must not name a part it does not know: " + leg);
    }
}
