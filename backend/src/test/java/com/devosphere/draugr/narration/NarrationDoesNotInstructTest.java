package com.devosphere.draugr.narration;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Narration witnesses; it does not instruct (#30).
 *
 * <p>The ticket's standard is explicit: a line must witness the resolved action, the visible setting and the
 * physical outcome, and must NOT give hidden recipe prerequisites or suggest the correct action. The scene library
 * has had a policy test to that effect since #565 — but the scenes are unreachable, and the prose a player actually
 * reads is the inline strings in the resolving services, which nothing has ever measured.
 *
 * <p>So this measures them. It reads the main sources, pulls out every string literal long enough to be a sentence,
 * and fails on the handful of shapes that are unambiguously instruction rather than observation: telling the
 * Chronicle what they NEED, or what to make or do FIRST. Three lines were caught when it was written:
 *
 * <ul>
 *   <li>a net's cordage shortfall naming the exact number of lengths, and ending "Twist more first";</li>
 *   <li>a landing net's missing hoop naming "a couple of green branches for the frame";</li>
 *   <li>a felled log adding "You will need to buck and split the wood into pieces you can carry".</li>
 * </ul>
 *
 * <p>Each was rewritten to say what is physically so — the cordage would not reach across a corner of the mesh,
 * nothing in reach will bend into a hoop, the logs are too heavy to shift — which is more use to a player than the
 * recipe was, and does not do their thinking for them.
 *
 * <p>Deliberately narrow. It is a floor, not a style guide: a line can pass this and still be flat, and the rest of
 * #30 is about those. What it makes impossible is the one thing the ticket forbids outright.
 */
class NarrationDoesNotInstructTest {

    /** Sentence-shaped literals only: keys, SQL fragments and single words are not narration. */
    private static final Pattern LITERAL = Pattern.compile("\"([^\"\\\\]|\\\\.){25,400}\"");

    /**
     * Instruction, not observation. Each of these tells the player what to obtain or what to do next, which is the
     * recipe the world is supposed to make them work out.
     */
    private static final Pattern INSTRUCTS = Pattern.compile(
        "\\byou (will |would )?need\\b"
        + "|\\bneeds \\d"
        + "|\\bmust first\\b"
        // "— cordage or fibre must come first": the recipe as a list, then the order to fetch it. Missed by the
        // first cut of this guard, which is how eighteen of them survived it; the scene library's own test already
        // forbade the phrase, so the services now answer to the same rule.
        + "|\\b(must|has to|have to) come first\\b"
        + "|\\b(twist|make|craft|build|gather|cut) more first\\b"
        + "|\\bfirst (make|craft|build|twist)\\b"
        + "|\\byou should (make|craft|build|try)\\b",
        Pattern.CASE_INSENSITIVE);

    @Test
    void noPlayerFacingLineTellsTheChronicleWhatToFetchOrDoNext() throws IOException {
        Path main = Path.of("src", "main", "java");
        Assumptions.assumeTrue(Files.isDirectory(main),
            "run from the backend module, where the sources this guard reads actually live");

        List<String> offences = new ArrayList<>();
        try (Stream<Path> tree = Files.walk(main)) {
            for (Path file : tree.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                Matcher literal = LITERAL.matcher(source);
                while (literal.find()) {
                    String text = literal.group();
                    // Only prose: a literal with no spaces is a key or a column, and one that is plainly SQL is
                    // not something a player ever reads.
                    if (!text.contains(" ") || text.contains("SELECT ") || text.contains("INSERT ")
                        || text.contains("UPDATE ") || text.contains("DELETE ")) continue;
                    if (INSTRUCTS.matcher(text).find())
                        offences.add(file.getFileName() + ": " + text);
                }
            }
        }

        assertTrue(offences.isEmpty(),
            () -> "narration must witness the obstacle, never name the recipe or the next action (#30) — "
                + offences.size() + " line(s):\n  " + String.join("\n  ", offences));
    }
}
