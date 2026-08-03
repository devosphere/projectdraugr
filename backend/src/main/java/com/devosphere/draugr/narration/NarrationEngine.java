package com.devosphere.draugr.narration;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic witness-stance prose for every action the world resolves.
 *
 * <p>This is the layer that makes the AI narrator affordable. The engine produces
 * complete, factually correct narration at zero token cost; the router (DR-0017)
 * then decides whether a given moment is worth spending an API call to add
 * atmosphere on top. If the API is unavailable the game still narrates correctly
 * — the AI is an upgrade layer, never a dependency.
 *
 * <p>Prose is assembled from fragments across three axes: what was attempted and
 * how it went, where it happened, and when. The engine is a pure function on those
 * inputs — no database, no chronicle state — so it is trivially testable and can
 * never itself change the world.
 *
 * <p>It obeys the same rule as every other narrator in this game: it describes the
 * attempt, the perception, and the outcome, and it never advises. See
 * docs/architecture/narration-engine.md.
 */
@Component
public class NarrationEngine {

    /** Everything the engine needs to write a line. All fields may be null except intent and outcome. */
    public record Scene(String intent, String outcome, String biome, String timeOfDay,
                        String weather, String species, Integer woundSeverity, String subject) { }

    private static final Map<String, String[]> BY_INTENT_OUTCOME = Map.ofEntries(
        // Gathering and flora
        Map.entry("GATHER_PLANT|SUCCEEDED", new String[]{
            "The stems give way at the base, and what you wanted comes free in your hand.",
            "You work through the growth and take what is worth taking."}),
        Map.entry("GATHER_PLANT|FAILED", new String[]{
            "You go through the growth carefully. Your hands come away with nothing.",
            "There is green here, but nothing in it that answers what you were after."}),
        Map.entry("FELL_TREE|SUCCEEDED", new String[]{
            "The trunk goes over with a crack that carries, and then the long tearing sound of it coming down through the canopy.",
            "It leans, hangs a moment, and falls. Where it stood there is a gap and a great deal of light."}),
        Map.entry("FELL_TREE|FAILED", new String[]{
            "You set your hands against the trunk. It does not move, and nothing you have will make it.",
            "The bark takes the blows without any sign of caring."}),
        Map.entry("GATHER_CLAY|SUCCEEDED", new String[]{
            "The earth here is heavy and holds together. A dense wet lump of it comes away in your fingers.",
            "You work down past the loose topsoil and pull free something that keeps the shape you press into it."}),
        Map.entry("GATHER_CLAY|FAILED", new String[]{
            "You turn the ground over. It crumbles apart and holds nothing.",
            "The soil runs dry through your fingers."}),
        Map.entry("STRIP_BARK|SUCCEEDED", new String[]{
            "The bark peels away in a broad sheet, showing pale wet wood underneath.",
            "You work the edge under and lift a long strip free."}),
        Map.entry("STRIP_BARK|FAILED", new String[]{
            "Nothing here has bark that will come away whole.",
            "The bark cracks and splinters rather than lifting."}),

        // Fire and survival
        Map.entry("LIGHT_FIRE|SUCCEEDED", new String[]{
            "An ember catches. You feed it carefully until flame stands up inside the ring of stone.",
            "The smoke thickens, glows, and becomes fire."}),
        Map.entry("LIGHT_FIRE|FAILED", new String[]{
            "You work until your arms burn. No ember comes.",
            "A thread of smoke rises, thins, and is gone."}),
        Map.entry("FEED_FIRE|SUCCEEDED", new String[]{
            "You settle more wood into the coals. The fire takes it and deepens.",
            "The flame climbs the new fuel and steadies."}),
        Map.entry("MAKE_CHARCOAL|SUCCEEDED", new String[]{
            "You lift a cooled black piece out of the spent fire. It marks your fingers immediately.",
            "The burnt wood comes away light and dry and very black."}),
        Map.entry("SLEEP|SUCCEEDED", new String[]{
            "You lie down and the hours go past without you.",
            "Sleep takes you, and when it lets go the light has moved."}),
        Map.entry("COOK_MEAT|SUCCEEDED", new String[]{
            "You hold the meat over the heat until its surface tightens and darkens.",
            "Fat runs and catches, and the smell of it changes entirely."}),
        Map.entry("EAT|SUCCEEDED", new String[]{
            "You eat. The immediate emptiness eases.",
            "It goes down, and the body takes what it can from it."}),
        Map.entry("DRINK|SUCCEEDED", new String[]{
            "You drink until the cold of it sits behind your breastbone.",
            "The water is cold and tastes of stone."}),

        // Wildlife
        Map.entry("CONFRONT_WILDLIFE|SUCCEEDED", new String[]{
            "The struggle ends. What is left lies still on the ground where it fell.",
            "It goes down and does not get up again. The wood is very quiet afterwards."}),
        Map.entry("CONFRONT_WILDLIFE|FAILED", new String[]{
            "Nothing comes of it. Whatever was here is not here now.",
            "The ground gives you nothing to close with."}),
        Map.entry("HARVEST_CARCASS|SUCCEEDED", new String[]{
            "You work over the remains and take what will carry.",
            "It is slow work with cold hands, but what comes away is worth having."}),
        Map.entry("FISH|SUCCEEDED", new String[]{
            "It comes up out of the water still fighting, cold and heavier than it looked.",
            "The water breaks and there it is, thrashing, and then yours."}),
        Map.entry("FISH|FAILED", new String[]{
            "The water moves past. Whatever is in it stays in it.",
            "You work the water a long while for nothing at all."}),
        Map.entry("TRACK|SUCCEEDED", new String[]{
            "The ground holds a record of what has passed over it.",
            "There is sign here, if you take the time to read it."}),
        Map.entry("TRACK|FAILED", new String[]{
            "The ground holds nothing. Nothing has come this way, or nothing that left a mark.",
            "You go over it twice and find only your own prints."}),

        // Craft and construction
        Map.entry("BUILD_FIRE_PIT|SUCCEEDED", new String[]{
            "You set the stones into a low ring. It stays where you put it.",
            "The ring closes. Whatever burns here will stay contained."}),
        Map.entry("CRAFT_BASKET|SUCCEEDED", new String[]{
            "The fibers tighten against each other until the shape holds on its own.",
            "It takes shape slowly under your hands, and then it is a basket."}),
        Map.entry("CRAFT_SPEAR|SUCCEEDED", new String[]{
            "Stone, shaft, and binding become one thing with a point on the end of it.",
            "You test the weight of it. It sits right in the hand."}),

        // Literature
        Map.entry("WRITE|SUCCEEDED", new String[]{
            "The charcoal leaves its marks. What was only in your head is now outside of it.",
            "You set the words down. They will still be there when you are not."}),
        Map.entry("SKETCH_MAP|SUCCEEDED", new String[]{
            "You draw the country as you remember it, which is not the same as how it is.",
            "Lines and marks accumulate into something that could be followed."}),
        Map.entry("EDIT_DOCUMENT|SUCCEEDED", new String[]{
            "You add to what was already there.",
            "The record grows by a few lines."})
    );

    private static final Map<String, String> BIOME_COLOR = Map.of(
        "TEMPERATE_FOREST", "The trees stand close here",
        "HIGHLAND", "The ground falls away in long slopes",
        "MOUNTAIN", "Bare rock shows through everywhere",
        "GRASSLAND", "The grass runs out flat in every direction",
        "WETLAND", "The ground gives underfoot and water stands in the low places",
        "OCEAN", "There is water to the horizon");

    private static final Map<String, String> TIME_COLOR = Map.of(
        "DAWN", "the light still grey and flat",
        "MORNING", "the light coming in low and long",
        "MIDDAY", "the shadows short and hard",
        "AFTERNOON", "the light going gold at the edges",
        "DUSK", "the colour draining out of everything",
        "NIGHT", "and it is dark enough that sound matters more than sight");

    private static final Map<String, String> WEATHER_COLOR = Map.of(
        "RAIN", "Rain moves through steadily",
        "STORM", "The wind comes in hard enough to lean against",
        "SNOW", "Snow comes down without any hurry",
        "FOG", "What is more than a few paces off is only a suggestion",
        "CLEAR", "");

    private static final String[] GENERIC_SUCCESS = {
        "It is done. The world carries the difference.",
        "The work finishes. What you set out to do is behind you now."};
    private static final String[] GENERIC_FAILURE = {
        "The attempt comes to nothing. The ground is as it was.",
        "Nothing you do changes anything here."};
    private static final String[] GENERIC_PARTIAL = {
        "Something happens, but not the whole of what you meant.",
        "It goes part of the way and stops there."};

    /**
     * Write the line. Never returns null and never returns blank — a scene the engine
     * has no specific fragment for still gets correct, if plainer, witness prose.
     */
    public String narrate(Scene scene) {
        String core = core(scene);
        String setting = setting(scene);
        return setting.isEmpty() ? core : core + " " + setting;
    }

    private String core(Scene s) {
        String key = (s.intent() == null ? "" : s.intent()) + "|" + (s.outcome() == null ? "" : s.outcome());
        String[] pool = BY_INTENT_OUTCOME.get(key);
        if (pool == null) {
            // A wound, however it arrived, is described by its gravity rather than by
            // the intent that led to it.
            if (s.woundSeverity() != null && s.woundSeverity() > 0) return woundLine(s);
            pool = switch (s.outcome() == null ? "" : s.outcome()) {
                case "SUCCEEDED" -> GENERIC_SUCCESS;
                case "PARTIAL" -> GENERIC_PARTIAL;
                default -> GENERIC_FAILURE;
            };
        }
        return pick(pool, s);
    }

    /** Wound register scales with severity, never naming a number or a Body HUD field. */
    private String woundLine(Scene s) {
        int sev = s.woundSeverity();
        String who = s.species() == null ? "It" : "The " + s.species().replace('_', ' ');
        if (sev >= 70) return who + " closes with its whole weight, and for a moment there is only force and tearing.";
        if (sev >= 35) return who + " drives into you hard, and something gives that should not have.";
        if (sev >= 15) return who + " catches you as it goes past. The cut runs and keeps running.";
        return who + " marks you in passing — shallow, stinging, not enough to slow you.";
    }

    /** Where and when, in one clause, when the engine has anything worth saying about it. */
    private String setting(Scene s) {
        String biome = s.biome() == null ? null : BIOME_COLOR.get(s.biome());
        String time = s.timeOfDay() == null ? null : TIME_COLOR.get(s.timeOfDay());
        String weather = s.weather() == null ? null : WEATHER_COLOR.get(s.weather());
        if (weather != null && !weather.isEmpty()) return weather + ".";
        if (biome == null) return "";
        return time == null || time.isEmpty() ? biome + "." : biome + ", " + time + ".";
    }

    /**
     * Deterministic choice within a fragment pool, so the same scene always reads the
     * same way and tests can assert on it, while different scenes vary.
     */
    private String pick(String[] pool, Scene s) {
        if (pool.length == 1) return pool[0];
        int h = (s.intent() + "|" + s.outcome() + "|" + s.biome() + "|" + s.timeOfDay() + "|" + s.species()).hashCode();
        return pool[Math.floorMod(h, pool.length)];
    }

    /**
     * Punctuate a witness-stance core with a clause of setting, so the world is present in the
     * prose and not just the act — the difference between "You take what is worth taking." and
     * "You take what is worth taking. Rain moves through steadily." It lands when it means
     * something rather than tagging every line:
     * <ul>
     *   <li><b>Weather</b> (rain, storm, snow, fog) is felt while moving or looking, and always the
     *       moment it changes — so a chronicle heads-down on a task in steady rain has tuned it out,
     *       but feels it start, or notices it when they look up.</li>
     *   <li><b>The look of the land</b> is added only on deliberate attention (HIGH) — the chronicle
     *       taking the place in, not glancing past it.</li>
     * </ul>
     * Clear weather and heads-down work get nothing added. Pure function; no HUD state named.
     */
    public String ground(String core, String biome, String timeOfDay, String weather, String attention, boolean weatherChanged) {
        boolean low = "LOW".equals(attention), high = "HIGH".equals(attention);
        String w = weather == null ? null : WEATHER_COLOR.get(weather);
        if (w != null && !w.isEmpty() && (weatherChanged || !low)) core = core + " " + w + ".";
        if (high) {
            String b = biome == null ? null : BIOME_COLOR.get(biome);
            if (b != null) {
                String t = timeOfDay == null ? null : TIME_COLOR.get(timeOfDay);
                core = core + " " + (t == null || t.isEmpty() ? b + "." : b + ", " + t + ".");
            }
        }
        return core;
    }

    /** Intents the engine has hand-written prose for — used by tests and by the router. */
    public boolean hasSpecificProse(String intent, String outcome) {
        return BY_INTENT_OUTCOME.containsKey(intent + "|" + outcome);
    }

    /** Every intent|outcome key the engine covers. */
    public List<String> coveredScenes() {
        return BY_INTENT_OUTCOME.keySet().stream().sorted().toList();
    }
}
