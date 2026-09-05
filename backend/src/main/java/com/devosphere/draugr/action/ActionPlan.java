package com.devosphere.draugr.action;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits a submitted procedure into the ordered steps it actually declares (#38).
 *
 * <p>The action composer takes 2,500 characters, and the resolver matched exactly one process to the whole of it:
 * a Chronicle who wrote out four steps of fire-making got one of them and lost the other three without being told.
 * As #38 puts it, the player typed a lot of stuff for nothing. This is the parser that makes a written procedure
 * mean what it says.
 *
 * <p>The splitting is deliberately conservative, because a wrong split is worse than no split — it would route
 * half a phrase and act on it. Only separators a person uses to mean "and after that" count: a line break, a
 * semicolon, the end of a sentence, an explicit sequencing word, or a numbered list. In particular <b>a bare
 * "and" is never a separator</b>: "flint and pyrite", "a wattle and daub hut", "grass and willow fibre" are one
 * thing each, and splitting them would break routing that works today.
 *
 * <p>A text with no separators comes back as one step, so ordinary single actions behave exactly as before.
 */
public final class ActionPlan {

    private ActionPlan() { }

    /** How many steps of one submitted plan the world will actually work through. */
    public static final int MAX_STEPS = 8;

    /**
     * Separators that genuinely mean "next step". Sentence ends require the following space so a decimal or an
     * abbreviation does not split; sequencing words are matched whole and may carry their own comma.
     */
    private static final Pattern SEPARATOR = Pattern.compile(
        "\\R+"                                                        // any line break
      + "|\\s*;\\s*"                                                  // semicolon
      + "|(?<=[\\p{Alnum}\\)\\]\"'])\\.(?:\\s+|$)"                    // end of a sentence
      + "|\\s*,?\\s+(?i:and\\s+then|then|after\\s+that|afterwards?|next|finally|lastly"
      + "|once\\s+that\\s+is\\s+done|once\\s+that's\\s+done|when\\s+that\\s+is\\s+done)\\s*,?\\s+");

    /** A list marker opening a step: "1.", "2)", "-", "*", "•". */
    private static final Pattern ENUMERATOR = Pattern.compile("^\\s*(?:\\d{1,2}\\s*[.):]|[-*•])\\s*");

    /**
     * A sequencing word left at the head of a step by a split on the sentence before it — "Then listen for a
     * while". It is scaffolding for the reader, not part of the instruction, and leaving it on would put a word
     * in front of the verb for every matcher downstream to step over.
     */
    private static final Pattern LEADER = Pattern.compile(
        "^(?i:and\\s+then|then|after\\s+that|afterwards?|next|finally|lastly)\\s*,?\\s+");

    /**
     * The ordered steps this text declares. A text that declares only one returns a single-element list holding
     * the text as written, so nothing about an ordinary action changes.
     */
    public static List<String> steps(String text) {
        if (text == null) return List.of();
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return List.of();

        List<String> steps = new ArrayList<>();
        for (String raw : SEPARATOR.split(trimmed)) {
            String step = LEADER.matcher(ENUMERATOR.matcher(raw).replaceFirst("").trim()).replaceFirst("").trim();
            // Strip a trailing comma or sentence punctuation left by the split.
            while (!step.isEmpty() && ",;.".indexOf(step.charAt(step.length() - 1)) >= 0)
                step = step.substring(0, step.length() - 1).trim();
            // A fragment too short to name anything is punctuation noise, not a step.
            if (step.length() >= 3) steps.add(step);
        }
        // Nothing survived the tidy — hand back what was written, so gibberish still reaches the pre-pass filter
        // that is meant to answer it.
        if (steps.isEmpty()) return List.of(trimmed);
        return List.copyOf(steps);
    }

    /** Whether this text declares more steps than the world will work through in one submission. */
    public static boolean exceedsLimit(String text) {
        return steps(text).size() > MAX_STEPS;
    }
}
