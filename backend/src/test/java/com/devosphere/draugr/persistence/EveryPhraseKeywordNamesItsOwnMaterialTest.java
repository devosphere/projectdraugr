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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The catalogue must keep its word: a phrase a recipe advertises has to be a phrase that recipe can answer.
 *
 * <p>{@code ProcessMatcher} runs a process only when THREE things agree — the classified category, one of the
 * process's keywords, and one of its SUBJECT TERMS, all whole-word in the sentence. So a multi-word keyword
 * whose own words name none of its process's subject terms can never match on its own: the sentence carries the
 * keyword and nothing to satisfy the gate standing behind it. The recipe promises an answer and then refuses it.
 *
 * <p>Measured against a booted world before V462: of 2,773 declared keywords, <b>114</b> multi-word phrases
 * were in that state, and sixteen sampled live reached NOTHING AT ALL — {@code leather boots},
 * {@code cuir bouilli}, {@code pressure flake}, {@code brain tan}, {@code saddle notch}, {@code half lap},
 * {@code box joint}, {@code leach tannin}, {@code bind the fletching}, {@code cut a mortise}. V462 gave each
 * such keyword its own content words as subject terms, and 111 of the 114 then reached the catalogue.
 *
 * <p>THE SAME DEFECT V459 FOUND ONCE: there {@code shape sling stones} was declared while the subject term was
 * the singular {@code sling stone}. The two halves of one recipe disagreeing about a word is the shape; this
 * test is the standing guard for it, so the next recipe that disagrees with itself fails here instead of
 * shipping as a phrase the world quietly ignores.
 *
 * <p>Single-word keywords are deliberately out of scope. A bare {@code cut} or {@code shape} is not a sentence
 * that names a material, and it SHOULD need the rest of the sentence to supply one — that is the gate working.
 *
 * <p>Schema and reference data only, so no world genesis is needed. Skips gracefully without Docker.
 */
@SpringBootTest
class EveryPhraseKeywordNamesItsOwnMaterialTest {

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
     * The closed set of phrase keywords that carry no material word of their own, and need none.
     *
     * <p>Every one of these is built entirely out of prepositions and the making verb — there is no noun in them
     * to make a subject term of. They are not promises the catalogue breaks on its own account: each one works
     * the moment the sentence names the thing, which is how a player writes it anyway.
     *
     * <pre>
     *   hang it to dry        ->  hang the meat to dry        reaches dry_meat
     *   take the flesh off    ->  take the flesh off the hide reaches flesh_hide
     *   pack in salt          ->  pack the fish in salt       reaches salt_fish
     *   lay out / cut out     ->  lay out the hide            reaches lay_out_hide
     * </pre>
     *
     * <p>Adding to this list is a deliberate act: it asserts "this phrase names no material and does not need
     * to", and should be done only with the working sentence in hand.
     */
    private static final Set<String> NAMES_NO_MATERIAL_AND_NEEDS_NONE = Set.of(
            "dry_meat :: hang it to dry",
            "flesh_hide :: take the flesh off",
            "lay_out_hide :: cut out",
            "lay_out_hide :: lay out",
            "salt_fish :: pack in salt");

    @Test
    void everyPhraseARecipeAdvertisesNamesSomethingThatRecipeWorksOn() {
        List<String> offenders = jdbc.queryForList(
            "WITH kw AS (" +
            "  SELECT mp.process_key, trim(k) AS keyword " +
            "    FROM material_process mp, unnest(string_to_array(mp.keywords, ',')) AS k " +
            "   WHERE mp.review_state = 'VERIFIED') " +
            "SELECT kw.process_key || ' :: ' || kw.keyword " +
            "  FROM kw " +
            " WHERE array_length(string_to_array(kw.keyword, ' '), 1) >= 2 " +
            "   AND NOT EXISTS (SELECT 1 FROM process_subject s " +
            "                    WHERE s.process_key = kw.process_key " +
            "                      AND (' ' || kw.keyword || ' ') ~ ('\\m' || s.subject_term || '\\M')) " +
            " ORDER BY 1", String.class);

        List<String> unexplained = offenders.stream().filter(o -> !NAMES_NO_MATERIAL_AND_NEEDS_NONE.contains(o)).toList();

        assertTrue(unexplained.isEmpty(),
            "These recipes advertise a phrase their own subject gate must refuse, so the phrase reaches nothing. "
          + "Give the keyword's own noun to the process as a subject term (see V462), or, if the phrase genuinely "
          + "names no material, add it to NAMES_NO_MATERIAL_AND_NEEDS_NONE with the working sentence: "
          + unexplained);
    }
}
