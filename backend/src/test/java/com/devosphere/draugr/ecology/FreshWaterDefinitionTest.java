package com.devosphere.draugr.ecology;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fresh water must have exactly one definition (#156).
 *
 * <p>Eight places used to spell out "a spring, a stream, a river or freshwater" for themselves — seven in the main
 * code and one in a test fixture that clears water so it can assert dry ground. Adding standing water to the
 * catalogue would then have made a pond water to whichever call sites somebody remembered and dry to the rest,
 * and the fixture would have failed to clear it.
 *
 * <p>This is the guard, and it is a source check rather than a behaviour check on purpose: the failure mode is a
 * NEW copy appearing, which no runtime assertion can see. Needs no database.
 */
class FreshWaterDefinitionTest {

    private static final Path BACKEND = Path.of("src");

    private List<Path> javaSources() throws IOException {
        try (Stream<Path> walk = Files.walk(BACKEND)) {
            List<Path> out = new ArrayList<>();
            walk.filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !p.getFileName().toString().equals("FreshWater.java"))
                .filter(p -> !p.getFileName().toString().equals("FreshWaterDefinitionTest.java"))
                .forEach(out::add);
            return out;
        }
    }

    @Test
    void nobodySpellsOutTheFreshwaterSiteKindsForThemselves() throws IOException {
        List<Path> sources = javaSources();
        // A scan that walked nothing would pass without looking at anything, which is the failure this guard is
        // most likely to have: a wrong working directory turns the whole test into a no-op that reads as green.
        assertTrue(sources.size() > 200,
            "the scan must actually reach the sources — found only " + sources.size()
          + " java files under " + BACKEND.toAbsolutePath());

        List<String> offenders = new ArrayList<>();
        for (Path source : sources) {
            String text = Files.readString(source, StandardCharsets.UTF_8);
            // The tell is a site_kind match on one of the water words. Any occurrence outside FreshWater is a copy.
            if (text.contains("site_kind ILIKE '%spring%'")
                || text.contains("site_kind ILIKE '%stream%'")
                || text.contains("site_kind ILIKE '%freshwater%'")) {
                offenders.add(BACKEND.relativize(source).toString());
            }
        }
        assertTrue(offenders.isEmpty(),
            "fresh water has one definition — com.devosphere.draugr.ecology.FreshWater — and these spell it out "
          + "again, so a water kind added to the catalogue would be water in some places and not others: " + offenders);
    }

    /** The definition must actually cover the standing water #156 still owes, or widening it later is forgotten. */
    @Test
    void theDefinitionAlreadyKnowsTheStandingWaterStillToCome() {
        String sql = FreshWater.sites();
        for (String kind : new String[]{"spring", "stream", "river", "freshwater", "pond", "lake"}) {
            assertTrue(sql.contains("'%" + kind + "%'"), kind + " must count as fresh water: " + sql);
        }
        // "pool" is deliberately absent: "Fen siren pool" is a monster lair, not a drinking source.
        assertTrue(!sql.contains("'%pool%'"),
            "a word that broad would turn a fen siren's pool into a water source: " + sql);
        // The alias form must qualify every column, or a join with two site tables becomes ambiguous.
        assertTrue(!FreshWater.sites("es").contains(" site_kind"),
            "the aliased form must qualify every column: " + FreshWater.sites("es"));
    }
}
