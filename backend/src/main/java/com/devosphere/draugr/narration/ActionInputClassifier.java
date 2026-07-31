package com.devosphere.draugr.narration;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * The pre-pass filter that runs before intent classification. It catches the
 * inputs a survival simulation must handle but should never spend AI tokens on:
 * sexual self-acts, aggression toward creatures or scenery, gibberish, and the
 * physically impossible. See DR-0019 and docs/architecture/narration-engine.md.
 *
 * <p>Crucially, this is not a moral filter. The classifier only sorts input by
 * what the world does with it. A personal physical act has real physiological
 * cost — energy, hunger, thirst, hygiene — so a player who spams it drains and
 * dies; the world applies physics, not judgment. Aggression toward a live animal
 * routes into the ordinary encounter system, badly prepared. Only genuine
 * nonsense and the impossible cost nothing, because nothing physical happened.
 *
 * <p>The classifier is a pure function on the action text — no database, no
 * chronicle state — so it is trivially unit-testable and never itself mutates
 * the world. The dispatch that acts on its verdict lives in
 * {@code ChronicleActionService}.
 */
@Component
public class ActionInputClassifier {

    public enum InputClass {
        /** A well-formed action the normal dispatch should resolve. */
        VALID,
        /** Masturbation or a sexual self-act: time passes, the body pays a real cost, no AI. */
        PERSONAL_PHYSICAL_ACT,
        /** A sexual or violent contact attempt with a live animal: routes into the encounter system, badly prepared. */
        AGGRESSION_TOWARD_WILDLIFE,
        /** Venting profanity at scenery: a trivial energy cost, a flat line, and a minute gone. */
        AGGRESSION_TOWARD_INANIMATE,
        /** Gibberish, keyboard mash, or a fragment too short to be an action: nothing happens. */
        NONSENSICAL,
        /** An attempt the physics of this world forbid outright: nothing happens. */
        PHYSICALLY_IMPOSSIBLE
    }

    private static final List<String> SELF_ACT = List.of(
        "masturbat", "jerk off", "jerking off", "jack off", "jacking off",
        "touch myself", "touching myself", "pleasure myself", "pleasuring myself", "fap");

    // Sexual or predatory-sexual contact terms. On their own these are ambiguous;
    // paired with an animal noun they read as aggression toward wildlife.
    private static final List<String> SEXUAL_CONTACT = List.of(
        "sex with", "have sex", "fuck the", "fuck that", "fuck a ", "fuck an ",
        "rape", "mount the", "mount a", "hump the", "hump a", "breed the", "breed with");

    private static final List<String> ANIMAL_NOUNS = List.of(
        "wolf", "boar", "bear", "deer", "elk", "hare", "rabbit", "goat", "beaver",
        "otter", "fox", "lynx", "wildcat", "badger", "stoat", "marten", "wolverine",
        "aurochs", "reindeer", "squirrel", "mole", "mouse", "hedgehog", "bat",
        "snake", "adder", "lizard", "turtle", "frog", "toad", "newt", "salamander",
        "trout", "perch", "catfish", "eel", "pike", "carp", "crayfish",
        "eagle", "raven", "owl", "falcon", "pigeon", "duck", "heron", "vulture",
        "turkey", "stork", "fowl", "animal", "creature", "beast", "bird", "fish");

    private static final List<String> INANIMATE_NOUNS = List.of(
        "tree", "rock", "stone", "ground", "dirt", "earth", "log", "branch", "wall",
        "water", "river", "mud", "sky", "fire", "hill", "mountain", "slab", "boulder");

    // Kept deliberately narrow: only unambiguously vulgar venting. "screw" and
    // "damn" are excluded because they collide with construction verbs and idiom
    // ("screw the shelf to the wall", "damn good spot").
    private static final List<String> PROFANITY = List.of(
        "fuck", "shit on", "piss on");

    private static final List<String> IMPOSSIBLE = List.of(
        "fly ", "fly up", "fly away", "fly to", "take flight", "levitate",
        "teleport", "disappear", "turn invisible", "become invisible", "vanish",
        "cast a spell", "cast spell", "summon", "conjure", "breathe underwater",
        "time travel", "travel through time", "read minds", "shapeshift", "phase through");

    private static final List<String> VOWELS = List.of("a", "e", "i", "o", "u", "y");

    /** Sort the action text into the category that decides what the world does with it. */
    public InputClass classify(String text) {
        if (text == null) return InputClass.NONSENSICAL;
        String trimmed = text.trim();
        if (isNonsensical(trimmed)) return InputClass.NONSENSICAL;

        String v = trimmed.toLowerCase(Locale.ROOT);

        if (containsAny(v, SELF_ACT)) return InputClass.PERSONAL_PHYSICAL_ACT;

        boolean namesAnimal = containsAny(v, ANIMAL_NOUNS);
        if (namesAnimal && containsAny(v, SEXUAL_CONTACT)) return InputClass.AGGRESSION_TOWARD_WILDLIFE;

        // Profanity aimed at scenery, or bare venting with no constructive verb, is
        // trivial aggression toward the inanimate. Kept below the wildlife check so
        // "fuck the wolf" routes to the animal, not the scenery.
        if (containsAny(v, PROFANITY) && (containsAny(v, INANIMATE_NOUNS) || isBareVenting(v))) {
            return InputClass.AGGRESSION_TOWARD_INANIMATE;
        }

        if (containsAny(v, IMPOSSIBLE)) return InputClass.PHYSICALLY_IMPOSSIBLE;

        return InputClass.VALID;
    }

    /**
     * A witness-stance line for an intercepted input. Flat, indifferent, never a
     * hint and never a judgment. Selection is deterministic on the text so a given
     * input always reads the same and tests can assert on it.
     */
    public String narrate(InputClass cls, String text) {
        List<String> pool = switch (cls) {
            case PERSONAL_PHYSICAL_ACT -> List.of(
                "The act is done. Time has passed. The forest does not acknowledge it.",
                "The body does what it does. The world is unchanged except the body itself.",
                "It is finished. The tiredness is real, the hunger a little deeper. The world waits.");
            case AGGRESSION_TOWARD_WILDLIFE -> List.of(
                "The animal reads the approach as threat. What follows is its answer, not yours.",
                "The creature does not cooperate with that. It responds the only way it knows.");
            case AGGRESSION_TOWARD_INANIMATE -> List.of(
                "It receives that and does not respond. A moment has passed.",
                "The sentiment goes into the air. Nothing moves that was not already moving.");
            case NONSENSICAL -> List.of(
                "The body has no shape for that intent. Nothing moves.",
                "That resolves into nothing. The ground is still beneath you.");
            case PHYSICALLY_IMPOSSIBLE -> List.of(
                "The attempt produces exactly what the physics of this place allow: nothing.",
                "The body reaches toward that and finds no path. It stands where it stood.");
            case VALID -> List.of("");
        };
        if (pool.size() == 1) return pool.get(0);
        int idx = Math.floorMod(text == null ? 0 : text.hashCode(), pool.size());
        return pool.get(idx);
    }

    private boolean isNonsensical(String trimmed) {
        if (trimmed.length() < 3) return true;
        long letters = trimmed.chars().filter(Character::isLetter).count();
        long nonSpace = trimmed.chars().filter(c -> !Character.isWhitespace(c)).count();
        if (nonSpace == 0) return true;
        // Mostly symbols or digits — "!!!", "12345", "@#$%^&" — is not an action.
        if ((double) letters / nonSpace < 0.4) return true;
        // No plausible word at all reads as keyboard mash. A token is plausible if
        // it is an alphabetic run with a vowel and no run of four-plus consecutive
        // consonants — the structural giveaway of "asdfghjkl" or "zxcvbnm".
        for (String token : trimmed.toLowerCase(Locale.ROOT).split("\\s+")) {
            String alpha = token.replaceAll("[^a-z]", "");
            if (alpha.length() >= 2 && containsAny(alpha, VOWELS) && !hasLongConsonantRun(alpha)) return false;
        }
        return true;
    }

    /** True when the word contains four or more consecutive consonants — rare in real English, common in mash. */
    private boolean hasLongConsonantRun(String alpha) {
        int run = 0;
        for (int i = 0; i < alpha.length(); i++) {
            char c = alpha.charAt(i);
            boolean vowel = c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u' || c == 'y';
            run = vowel ? 0 : run + 1;
            if (run >= 4) return true;
        }
        return false;
    }

    /** The whole input is essentially just an oath, with no constructive verb behind it. */
    private boolean isBareVenting(String v) {
        String stripped = v.replaceAll("[^a-z ]", "").trim();
        return stripped.split("\\s+").length <= 3;
    }

    private static boolean containsAny(String haystack, List<String> needles) {
        for (String n : needles) if (haystack.contains(n)) return true;
        return false;
    }
}
