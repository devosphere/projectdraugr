package com.devosphere.draugr.narration;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One definition of "this text names that thing".
 *
 * <p>Written because a name can hide inside another name. The catalogue holds a monster whose species key is
 * {@code roc}, and it sits inside {@code au-roc-hs}: so a Chronicle who wrote "hunt the aurochs" was matched
 * against a roc by {@code actionText.contains("roc")}, and ambient tracking that named an aurochs read as having
 * named a monster. The bug was not in either of those features. It was that both asked whether one string occurs
 * inside another, when the question they meant was whether the text says the word.
 *
 * <p>Species, item, mineral and project keys are all {@code lower_snake_case}, so the spoken form of a key is the
 * key with its underscores opened out. Callers pass keys; this decides whether the text says them.
 */
public final class Words {

    private Words() { }

    /**
     * Whole-word containment: {@code word("smoke the meat", "eat")} is false, {@code word("take a nap", "nap")} is
     * true. The needle is quoted, so a name carrying regex punctuation is matched literally rather than compiled.
     *
     * <p>Bounded with lookarounds rather than {@code \b}, because {@code \b} is defined against the character next
     * to it: a needle that ends in punctuation — a display name like "lean-to" — has no word boundary after it,
     * and {@code \b} would refuse to match a name that is plainly present. The lookarounds ask the question that
     * was actually meant, which is whether the surrounding text runs on into the name.
     */
    public static boolean word(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isBlank()) return false;
        return Pattern.compile("(?<!\\w)" + Pattern.quote(needle) + "(?!\\w)", Pattern.CASE_INSENSITIVE)
            .matcher(haystack).find();
    }

    /**
     * Whether the text names the thing this key stands for — {@code names("hunt the aurochs", "roc")} is false,
     * {@code names("hunt the wave roc", "wave_roc")} is true.
     */
    public static boolean names(String text, String key) {
        return key != null && word(text, key.replace('_', ' '));
    }

    /**
     * The article a name takes when it is read aloud, so narration says "an aurochs" rather than "a aurochs".
     * Judged on the written vowel, which is what the narration has to go on; it is right far more often than the
     * bare "a" it replaces, and wrong in the same handful of places English itself is irregular.
     */
    public static String article(String name) {
        if (name == null || name.isBlank()) return "a";
        return "aeiou".indexOf(Character.toLowerCase(name.charAt(0))) >= 0 ? "an" : "a";
    }

    /** {@link #article(String)} joined to the name: {@code "an aurochs"}, {@code "a red deer"}. */
    public static String withArticle(String name) {
        return article(name) + " " + name;
    }

    /** The same, opening a sentence: {@code "An aurochs grazes the open ground."} */
    public static String startingSentence(String name) {
        String a = article(name);
        return Character.toUpperCase(a.charAt(0)) + a.substring(1) + " " + name;
    }

    /** Escape for callers building their own patterns around a key. */
    public static String quoted(String needle) {
        return Matcher.quoteReplacement(needle);
    }
}
