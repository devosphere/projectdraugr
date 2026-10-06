package com.devosphere.draugr.people;

import java.util.Locale;
import java.util.Set;

/**
 * The one vocabulary of words that name a person, or a thing that is a person's (#106/#114).
 *
 * <p><b>Why this exists.</b> {@link ConductService}'s phrase table recognises an offence against a people by its
 * opening words — {@code "tie up the"}, {@code "seize the"}, {@code "drag the"}, {@code "steal the"} — and took
 * whatever followed the article without ever looking at it. So <i>tie up the goat</i>, <i>drag the sledge</i>,
 * <i>capture the duck</i> and <i>seize the rope</i> were each resolved as an act against the community in reach,
 * and the Chronicle was set upon and driven off the isle for tying up a bundle. Measured on a booted world,
 * <b>11 of 31 sentences about a thing were answered as an act against people</b>, and the rest were told "you put
 * that to people, and there are none within reach" — which is the same mistake with nobody present to commit it
 * against. ConductService's own class note says <i>"Persons are not livestock"</i>; the defect was the exact
 * inverse of it.
 *
 * <p>So the open-object phrases are held to their object, and this is the list they are held to. It is
 * deliberately ONE list in ONE place, the same discipline V313 settled for the wearable positions: a second copy
 * of a vocabulary is how the first one goes stale. A named individual is not in here — names live in
 * {@code native_individual} and are asked of the database, because the world invents them.
 *
 * <p>Both halves are needed. A person is named directly — a kinsperson, an elder, a child — and so is a person's
 * place or property, because <i>burn their village</i> and <i>steal the storehouse grain</i> are offences against
 * the people who live there as surely as striking one of them is.
 */
public final class PersonWords {

    private PersonWords() { }

    /** What a person is called. The community's own name for itself is among them. */
    private static final Set<String> A_PERSON = Set.of(
        "them", "they", "someone", "somebody", "anyone", "person", "people", "folk", "folks",
        "reedkin", "kin", "kinsman", "kinswoman", "kinfolk", "kindred",
        "elder", "elders", "headsperson", "headman", "headwoman", "speaker", "chief",
        "child", "children", "infant", "baby", "lad", "girl", "boy", "youth",
        "man", "men", "woman", "women", "wife", "husband", "mother", "father",
        "villager", "villagers", "stranger", "strangers", "neighbour", "neighbours", "neighbor", "neighbors",
        "family", "household", "hostage", "captive", "prisoner", "slave");

    /** A place or possession that belongs to a people, and cannot be wronged without wronging them. */
    private static final Set<String> A_PEOPLES_OWN = Set.of(
        "village", "settlement", "isle", "hamlet", "camp", "longhouse", "storehouse", "store",
        "houses", "house", "home", "homes", "hut", "huts", "dwelling", "dwellings",
        "weir", "weirs", "nets", "boats", "belongings", "goods", "possessions", "things", "body", "dead");

    /**
     * Whether the sentence names a person, or something that is a people's.
     *
     * <p>Word by word, never as a substring: "theman" is nobody, "mankind" is not a man, and the whole reason this
     * class exists is a match that was not careful about what it was matching. The project has been bitten by the
     * substring four times — "scutch" contains "cut", "cooked" contains "cook", "pick" sits inside "pickaxe" — and
     * a vocabulary of short words like "men" and "kin" is the worst possible place to repeat it.
     */
    public static boolean namesAPerson(String text) {
        if (text == null || text.isBlank()) return false;
        for (String w : text.toLowerCase(Locale.ROOT).split("[^a-z]+"))
            if (A_PERSON.contains(w) || A_PEOPLES_OWN.contains(w)) return true;
        return false;
    }

    /** Whether the sentence uses a word for a person specifically, setting aside their property. */
    public static boolean namesSomebody(String text) {
        if (text == null || text.isBlank()) return false;
        for (String w : text.toLowerCase(Locale.ROOT).split("[^a-z]+")) if (A_PERSON.contains(w)) return true;
        return false;
    }
}
