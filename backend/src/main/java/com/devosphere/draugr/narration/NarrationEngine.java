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

        // --- #30: the ordinary work of a day -------------------------------------------------------------
        // Twenty-one intents had lines and the classifier produces a hundred and twenty-seven, so nearly
        // everything a Chronicle actually did fell to "It is done. The world carries the difference." These
        // witness the specific act instead. None of them names a prerequisite, suggests the action that would
        // have worked, or reports a state the resolver did not establish — a failure line says what the hands
        // met, not what to do about it.
        Map.entry("GATHER_FIBER|SUCCEEDED", new String[]{
            "You strip the long fibres down the stem and coil them against your palm, green and faintly wet.",
            "The stalks give up their length in ribbons, and you gather them into a hank."}),
        Map.entry("GATHER_FIBER|FAILED", new String[]{
            "What grows here tears short and ragged in the hand. None of it runs long enough to hold.",
            "You work at the stems. They break where they should have peeled."}),
        Map.entry("GATHER_STONE|SUCCEEDED", new String[]{
            "You turn the loose stone over, choose what will serve, and take it up cold and gritty.",
            "The stone comes away from the ground with a dull knock and settles heavy in your grip."}),
        Map.entry("GATHER_STONE|FAILED", new String[]{
            "You turn over what is here. It is all too small or too rotten to be worth carrying.",
            "The ground gives up dirt and root and nothing with an edge in it."}),
        Map.entry("GATHER_BRANCHES|SUCCEEDED", new String[]{
            "You gather fallen wood from under the trees, snapping each piece to hear how dry it rings.",
            "Deadfall comes up out of the leaf litter, light and grey and ready to burn."}),
        Map.entry("GATHER_BRANCHES|FAILED", new String[]{
            "Everything on the ground here is damp through and bends instead of breaking.",
            "You go over the ground twice. What wood there is has gone soft with rot."}),
        Map.entry("GATHER_BERRIES|SUCCEEDED", new String[]{
            "The fruit comes off between finger and thumb, and your hands go dark with the juice of it.",
            "You work along the canes and take what is ripe, leaving the hard green ones where they are."}),
        Map.entry("GATHER_BERRIES|FAILED", new String[]{
            "The canes are bare. Whatever was on them has gone to the birds or the season.",
            "You search the growth and find only leaf and thorn."}),
        Map.entry("GATHER_MINERAL|SUCCEEDED", new String[]{
            "You work it loose from the seam and turn it in the light, heavier than it looks.",
            "The rock gives up what was in it, and the broken face shines where it parted."}),
        Map.entry("GATHER_MINERAL|FAILED", new String[]{
            "You break at the rock until your arms burn. It holds nothing but more rock.",
            "The stone here is dead through — no seam, no shine, nothing worth the swing."}),
        Map.entry("COLLECT_WATER|SUCCEEDED", new String[]{
            "You dip and fill, and the cold of it comes through to your fingers.",
            "Water goes in with a hollow note that flattens as the vessel fills."}),
        Map.entry("COLLECT_WATER|FAILED", new String[]{
            "There is nothing here to draw from — the ground is dry to the touch.",
            "You look for water and find only damp earth that gives none up."}),
        Map.entry("BOIL_WATER|SUCCEEDED", new String[]{
            "It climbs to a rolling boil and holds there, throwing steam that beads on everything above it.",
            "The surface breaks and keeps breaking. Whatever was living in it is not living now."}),
        Map.entry("EXAMINE|SUCCEEDED", new String[]{
            "You take your time with it, and the details come up out of the general shape.",
            "Looking closely, you find the small particulars the glance had smoothed over."}),
        Map.entry("EQUIP|SUCCEEDED", new String[]{
            "You settle it into place and shift once until it sits where it will not shift again.",
            "It comes to hand, and your grip closes around it as though it had been waiting."}),
        Map.entry("DROP|SUCCEEDED", new String[]{
            "You set it down on the ground and straighten. Your back reads the difference immediately.",
            "It leaves your hands and stays where you put it."}),
        Map.entry("HARVEST_CROP|SUCCEEDED", new String[]{
            "You take the stand down a handful at a time, and the heads come away dry and rattling.",
            "The crop comes in. What stood in rows this morning is a weight against your chest now."}),
        Map.entry("HARVEST_CROP|FAILED", new String[]{
            "The stand is not ready. What is on it comes away green and useless in your hand.",
            "You go along the row and find nothing on it worth taking yet."}),
        Map.entry("CLEAR_LAND|SUCCEEDED", new String[]{
            "Trees down, brush hauled off, roots grubbed out — and an open patch of earth where the wood stood.",
            "The last of the growth comes out and the ground lies bare and workable for the first time."}),
        Map.entry("COLLECT_INSECTS|SUCCEEDED", new String[]{
            "You turn the ground and take what moves in it, quick, before it can go back under.",
            "They come up out of the damp and dark, and you have them before they scatter."}),
        Map.entry("COLLECT_INSECTS|FAILED", new String[]{
            "You turn the ground over and nothing moves in it.",
            "Whatever lived here has gone deeper than your hands reach."}),
        Map.entry("CHECK_TRAP|SUCCEEDED", new String[]{
            "The trap has held. You take what is in it and reset the whole business behind you.",
            "It sprung, and it kept what it caught."}),
        Map.entry("CHECK_TRAP|FAILED", new String[]{
            "The trap sits exactly as you left it, unsprung and empty.",
            "Nothing has come this way, or nothing careless enough."}),
        Map.entry("ADVANCE_ASSEMBLY|SUCCEEDED", new String[]{
            "The work moves on a stage. What was a heap of parts this morning is beginning to hold its own shape.",
            "You finish this piece of it and stand back. It is further along than it was."}),
        Map.entry("ADVANCE_ASSEMBLY|FAILED", new String[]{
            // The first draft of this line read "will not join to what you need" — and the no-advice policy
            // caught it, correctly: naming what is wanted is the recipe hint #30 forbids. It says what the
            // hands met instead.
            "The work will not go further today. The pieces you have will not meet.",
            "You turn the parts over and set them down again as they were."}),
        Map.entry("LISTEN|SUCCEEDED", new String[]{
            "You go still, and the place fills in around you — small sounds, and the shape of the distance between them.",
            "Held still, you hear what movement covers: water somewhere, wind working, something small in the litter."}),
        Map.entry("INSPECT|SUCCEEDED", new String[]{
            "You go over it closely, and its condition tells you plainly how it has been used.",
            "Turning it in your hands, you read the wear on it."}),
        Map.entry("DISENGAGE|SUCCEEDED", new String[]{
            "You break contact and put ground between you, watching behind until the watching stops mattering.",
            "You back off the way you came. Nothing follows."}),
        Map.entry("DISENGAGE|FAILED", new String[]{
            "You break for open ground and it comes with you, closing the distance as fast as you make it.",
            "There is no getting clear of this. It is still there when you turn."}),
        Map.entry("DRY_BODY|SUCCEEDED", new String[]{
            "The wet goes out of your clothes by degrees, and the shivering loosens its hold.",
            "Warmth works in and the cold that had settled in your bones gives ground."}),
        Map.entry("COOL_BODY|SUCCEEDED", new String[]{
            "The heat comes off you slowly, and your breathing lengthens out of its shallow rhythm.",
            "In the shade the worst of it passes and the pounding behind your eyes eases."}),
        Map.entry("FEED_ANIMAL|SUCCEEDED", new String[]{
            "It takes the feed from the ground without hurry, and does not move away from you while it eats.",
            "The food goes down fast. Whatever wariness it had is thinner afterwards."}),
        Map.entry("EXTINGUISH_FIRE|SUCCEEDED", new String[]{
            "The flame goes out in a rush of grey, and the heat leaves the air faster than it came into it.",
            "You smother it down to black and the place is suddenly much darker than it was."}),
        Map.entry("BANK_FIRE|SUCCEEDED", new String[]{
            "You rake the coals together and cover them, and the fire settles to a low red that will keep.",
            "Banked under ash, it stops throwing light and starts keeping itself."}),
        Map.entry("FILTER_WATER|SUCCEEDED", new String[]{
            "What comes through runs clear where it went in cloudy, and the sediment stays behind.",
            "It passes slowly, and what collects below has lost its colour and its grit."}),
        Map.entry("FORAGE_GROUND|SUCCEEDED", new String[]{
            "You work the ground over close and take what is worth taking from it.",
            "Going slowly with your eyes down, you find what a walking pace would have passed."}),
        Map.entry("FORAGE_GROUND|FAILED", new String[]{
            "You go over the ground and it offers nothing you could use.",
            "There is growth here and none of it is anything."}),
        Map.entry("DEFECATE|SUCCEEDED", new String[]{
            "You go apart from the camp, and afterwards cover it over.",
            "The body's business, done away from where you sleep and eat."}),
        Map.entry("AGGRESSION_INANIMATE|SUCCEEDED", new String[]{
            "You strike it and it gives — wood splitting, or stone shifting, or whatever it is coming apart.",
            "The blow lands with a flat crack and something in it breaks."}),
        Map.entry("AGGRESSION_INANIMATE|FAILED", new String[]{
            "You hit it hard enough to jar your arms and it takes the blow without changing at all.",
            "It absorbs everything you put into it and sits there exactly as it was."}),

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

    private static final Map<String, String> BIOME_COLOR = Map.ofEntries(
        Map.entry("TEMPERATE_FOREST", "The trees stand close and dark around you, the floor deep in leaf-mould"),
        Map.entry("HIGHLAND", "The ground falls away in long open slopes, and the wind is never quite still"),
        Map.entry("MOUNTAIN", "Bare rock shows through everywhere, the air thin and hard to draw"),
        Map.entry("GRASSLAND", "The grass runs out flat to every horizon, bending in slow waves"),
        Map.entry("WETLAND", "The ground gives underfoot, and water stands dark and still in the low places"),
        Map.entry("RIVER_BANK", "The river runs past below you, working steadily at a bank of smoothed stone and packed earth"),
        Map.entry("COAST", "Open water runs out to the edge of sight, and the ground gives way to shingle and wrack at the tide line"),
        Map.entry("CAVE_MOUTH", "The rock opens at your shoulder into a dark that the daylight gets a few paces into and no further"),
        Map.entry("OCEAN", "Water reaches grey to the edge of sight, restless and without end"),
        Map.entry("CAVE_INTERIOR", "The passage closes over behind you and the dark is complete, the rock cold and near on every side"));

    private static final Map<String, String> TIME_COLOR = Map.of(
        "DAWN", "the light still grey and unformed",
        "MORNING", "the light coming in low and long across the ground",
        "MIDDAY", "the shadows short and hard underfoot",
        "AFTERNOON", "the light going gold at the edges of things",
        "DUSK", "the colour draining slowly out of the land",
        "NIGHT", "the dark near complete, so that sound carries further than sight");

    private static final Map<String, String> WEATHER_COLOR = Map.of(
        "RAIN", "Rain moves through steadily, cold on the back of the neck",
        "STORM", "The wind comes in hard enough to lean against, and drives the rain sidelong",
        "SNOW", "Snow comes down without any hurry, muffling every sound to nothing",
        "FOG", "What lies more than a few paces off is only a suggestion of itself",
        "CLEAR", "");

    /** A sound or smell of the place, surfaced only on deliberate attention — the world reaching a sense other than sight. */
    private static final Map<String, String> AMBIENT = Map.ofEntries(
        Map.entry("TEMPERATE_FOREST", "Somewhere off among the trunks a bird falls quiet, then takes it up again."),
        Map.entry("HIGHLAND", "The wind pulls steadily at you and carries the dry smell of turf and stone."),
        Map.entry("MOUNTAIN", "The cold has a mineral edge to it, and nothing moves that you can hear."),
        Map.entry("GRASSLAND", "Insects work unseen in the grass, and the whole plain smells of dry seed."),
        Map.entry("WETLAND", "The air hangs thick with the green smell of standing water and slow rot."),
        Map.entry("RIVER_BANK", "The water keeps up its noise over the stones, and the air off it is cold and clean."),
        Map.entry("COAST", "Salt is on everything, and the water works at the shore without ever stopping."),
        Map.entry("CAVE_MOUTH", "Cold comes steadily out of the opening, smelling of wet stone, and the drip of water carries a long way back."),
        Map.entry("OCEAN", "Salt hangs in the air, and the water works without pause at the shore."),
        Map.entry("CAVE_INTERIOR", "Water finds stone somewhere out of sight and counts the seconds with it. Nothing else moves at all."));

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
        StringBuilder out = new StringBuilder(core);
        // Weather is felt while moving or looking, and always the moment it changes — a chronicle heads-down
        // on a task in steady rain has tuned it out, but feels it begin, or notices it when they look up.
        String w = weather == null ? null : WEATHER_COLOR.get(weather);
        if (w != null && !w.isEmpty() && (weatherChanged || !low)) out.append(" ").append(w).append(".");
        // Deliberate looking (HIGH) takes the place in fully: the shape of the land, the quality of the light,
        // and a sound or smell reaching a sense other than sight — three grounded sentences where it means
        // something. Heads-down work gets none of it; the chronicle is not looking, so the world stays at arm's
        // length. This scales immersion with attention rather than tagging every trivial act with scenery.
        if (high) {
            String b = biome == null ? null : BIOME_COLOR.get(biome);
            if (b != null) {
                String t = timeOfDay == null ? null : TIME_COLOR.get(timeOfDay);
                out.append(" ").append(t == null || t.isEmpty() ? b + "." : b + ", " + t + ".");
                String amb = AMBIENT.get(biome);
                if (amb != null) out.append(" ").append(amb);
            }
        }
        return out.toString();
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
