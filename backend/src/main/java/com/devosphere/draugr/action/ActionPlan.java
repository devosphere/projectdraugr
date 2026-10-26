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
     * Separators that genuinely mean "next step", in their two kinds, because only one kind can be mistaken.
     *
     * <p>A line break, a semicolon and a full stop are unambiguous: a person who writes one has ended a step
     * whatever the words were. A sequencing WORD is a word, and a word can belong to what is being said — which
     * is why these are applied in two passes, punctuation first.
     */
    private static final Pattern PUNCTUATION_SEPARATOR = Pattern.compile(
        "\\R+|\\s*;\\s*|(?<=[\\p{Alnum}\\)\\]\"'])\\.(?:\\s+|$)");
    private static final Pattern WORD_SEPARATOR = Pattern.compile(
        "\\s*,?\\s+(?i:and\\s+then|then|after\\s+that|afterwards?|next|finally|lastly"
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
        for (String raw : pieces(trimmed)) {
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

    /**
     * The text cut at its separators, except where a sequencing word is plainly part of what is being said.
     *
     * <p><b>A sequencing word needs a step in front of it.</b> "next" means "and after that" when it follows an
     * instruction — "gather reeds, next lash the frame" — and means nothing of the kind when it follows a
     * question word. {@code what next for the hide} was cut into "what" and "for the hide", two fragments that
     * name nothing, so a reading that answers that question perfectly was never reached: the sentence came back
     * "you spend the effort and come back with only the effort" (#37, found while measuring V442 live, after the
     * routing test for the same sentence had passed — the unit test calls the classifier, and the splitter runs
     * before it).
     *
     * <p>So a word separator is only honoured when at least three words stand before it, which is the shortest
     * thing that can be an instruction with an object. Line breaks, semicolons and sentence ends are untouched:
     * those are unambiguous, and a person who writes a full stop has ended a step whatever the words were.
     */
    private static List<String> pieces(String trimmed) {
        List<String> out = new ArrayList<>();
        for (String part : PUNCTUATION_SEPARATOR.split(trimmed)) {
            java.util.regex.Matcher m = WORD_SEPARATOR.matcher(part);
            int from = 0;
            while (m.find()) {
                String piece = part.substring(from, m.start());
                if (wordsIn(piece) < 3) continue;   // the word belongs to what is being said
                out.add(piece);
                from = m.end();
            }
            out.add(part.substring(from));
        }
        return out;
    }

    /** How many words stand in a piece — three is the shortest thing that can be an instruction with an object. */
    private static int wordsIn(String piece) {
        String v = piece.trim();
        if (v.isEmpty()) return 0;
        return v.split("\\s+").length;
    }

    /**
     * The whole of what a player says to take up a procedure that stopped (#38). Matched against the ENTIRE
     * submission, never as a substring: "carry on my back" is equipping, "continue building the wall" is an
     * assembly, and "resume the lean-to" belongs to the lean-to. Only a bare request to go on with what was set
     * aside counts, and even then only when something was — otherwise the words reach the world exactly as before.
     */
    private static final java.util.Set<String> RESUME_PHRASES = java.util.Set.of(
        "carry on", "carry on with it", "carry on with the rest", "carry on where i left off",
        "carry on with what i was doing", "continue", "continue with it", "continue with the rest",
        "continue where i left off", "continue the plan", "continue with the plan", "continue what i was doing",
        "resume", "resume the plan", "resume what i was doing", "resume where i left off",
        "pick up where i left off", "take up where i left off", "go on", "go on with it", "go on with the rest",
        "finish what i started", "finish the rest");

    /** Filler a person puts either side of "carry on" that changes nothing about what they mean. */
    private static final Pattern RESUME_LEAD = Pattern.compile("^(?:(?:i|i'll|i will|now|then|so|ok|okay|right|and|let me)\\s+)+");
    private static final Pattern RESUME_TAIL = Pattern.compile("(?:\\s+(?:now|then|please))+$");

    /** Whether the whole submission is a request to take up what was set aside. */
    public static boolean isResumeRequest(String text) {
        if (text == null) return false;
        String v = text.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z' ]+", " ").replaceAll("\\s+", " ").trim();
        v = RESUME_TAIL.matcher(RESUME_LEAD.matcher(v).replaceFirst("")).replaceFirst("").trim();
        return RESUME_PHRASES.contains(v);
    }

    /** Whether this text declares more steps than the world will work through in one submission. */
    public static boolean exceedsLimit(String text) {
        return steps(text).size() > MAX_STEPS;
    }
}
