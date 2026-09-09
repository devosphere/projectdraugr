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
            "The record grows by a few lines."}),

        // --- #30: the rest of the day's work -------------------------------------------------------------
        //
        // The engine covered sixty scenes against a classifier producing a hundred and twenty-seven intents,
        // and the two families a settling Chronicle spends most of their days inside — BUILD_* and CRAFT_* —
        // were almost entirely uncovered. Every one of them fell to "It is done. The world carries the
        // difference." A player who raises a fence, digs a latrine, hafts a hatchet and sets a trap on the same
        // afternoon read that line four times.
        //
        // The rule these are written to is the same one the policy enforces: say what the hands met and what
        // the world did about it. A FAILED line names the obstacle, never the missing ingredient — "the ground
        // turns the point aside" rather than "you have no timber", because the second is a recipe hint wearing
        // a sentence's clothes.

        // Building. A build fails on the ground or the materials to hand, and the failure says which without
        // naming what would have worked.
        Map.entry("BUILD_FENCE|SUCCEEDED", new String[]{
            "Stake by stake the line closes, until there is an inside to this ground and an outside.",
            "The last of it goes in and the run of it stands, leaning slightly, holding."}),
        Map.entry("BUILD_FENCE|FAILED", new String[]{
            "The stakes will not stand in this. What you drive in leans out again as soon as you let go.",
            "You get half a line up and it comes down under its own weight."}),
        Map.entry("BUILD_PEN|SUCCEEDED", new String[]{
            "The rails go up and meet, and what stands inside them is contained without being shut in.",
            "You close the last gap. It would hold something that did not want to leave very badly."}),
        Map.entry("BUILD_PEN|FAILED", new String[]{
            "The ground here will not take the posts, and nothing you set stays where it is set.",
            "It will not close. Whatever you put in it would be out again by dark."}),
        Map.entry("BUILD_LATRINE|SUCCEEDED", new String[]{
            "You dig well away from the water and the sleeping ground, and screen it. The camp smells better for it.",
            "The pit goes down through the topsoil, and afterwards there is one place for it instead of everywhere."}),
        Map.entry("BUILD_LATRINE|FAILED", new String[]{
            "The spade turns on stone a hand's depth down and will go no further.",
            "Water comes up into the hole as fast as you take the earth out."}),
        Map.entry("BUILD_LOOKOUT|SUCCEEDED", new String[]{
            "You get up onto it and the country opens out — ground you have walked across for days, laid flat below you.",
            "The platform takes your weight. From up here the approaches show themselves."}),
        Map.entry("BUILD_LOOKOUT|FAILED", new String[]{
            "It sways under the first of your weight and you come down off it before it decides for you.",
            "Nothing here stands tall enough or firm enough to build up from."}),
        Map.entry("BUILD_FUEL_RACK|SUCCEEDED", new String[]{
            "The wood comes up off the wet ground and under cover, and the air can get at it from underneath.",
            "You stack it on the rails. What was going soft on the earth will be dry in a week."}),
        Map.entry("BUILD_FUEL_RACK|FAILED", new String[]{
            "The frame racks sideways under the first armful and spills the lot.",
            "It will not stand square, and a rack that is not square holds nothing."}),
        Map.entry("BUILD_TOOL_SHED|SUCCEEDED", new String[]{
            "The roof goes over and the wall goes up, and what you work with has somewhere out of the weather.",
            "It is small and it is square and everything you own fits under it."}),
        Map.entry("BUILD_TOOL_SHED|FAILED", new String[]{
            "The posts go in and the cover will not span between them.",
            "Half-roofed, it lets in as much as it keeps out, and you take it down again."}),
        Map.entry("BUILD_STORAGE_AREA|SUCCEEDED", new String[]{
            "You clear the ground, raise the platform, and what was piled anywhere is now in one place off the earth.",
            "The store takes shape, sorted and lifted clear, and the camp has a floor again."}),
        Map.entry("BUILD_STORAGE_AREA|FAILED", new String[]{
            "The platform will not sit level, and everything you set on it walks off the low side.",
            "There is nowhere here that is dry enough to be worth putting anything down on."}),
        Map.entry("BUILD_SMOKE_VENT|SUCCEEDED", new String[]{
            "You open the roof and the smoke that had been sitting at head height finds the hole and goes.",
            "The air inside clears from the top down, and the stinging goes out of your eyes."}),
        Map.entry("BUILD_SMOKE_VENT|FAILED", new String[]{
            "You open it and the wind pushes straight back down through the gap, and the smoke with it.",
            "The thatch closes over the hole again as fast as you make it."}),
        Map.entry("BUILD_ALARM|SUCCEEDED", new String[]{
            "The line goes taut between the trunks with everything that rattles strung along it.",
            "You set it at shin height and step back. Nothing crosses here now without saying so."}),
        Map.entry("BUILD_ALARM|FAILED", new String[]{
            "The line goes slack between the anchors and hangs there, silent, useless.",
            "There is nothing here that will hold a line and nothing to hang on it."}),

        // Making. Each names the thing under the hands and the moment it stops being parts.
        Map.entry("CRAFT_KNIFE|SUCCEEDED", new String[]{
            "You take the flakes off one at a time until an edge runs the length of it, and bind it into a handle.",
            "It comes off the last strike sharp enough to shave the hair off your forearm."}),
        Map.entry("CRAFT_HAMMER|SUCCEEDED", new String[]{
            "The head seats into the split shaft and the binding pulls it down until it stops moving.",
            "You swing it once against the ground. The weight comes through where it should."}),
        Map.entry("CRAFT_PICKAXE|SUCCEEDED", new String[]{
            "The point beds into the haft and the whole of it swings true, heavy at the far end.",
            "You bring it down and it bites, which is the only test that matters."}),
        Map.entry("CRAFT_HATCHET|SUCCEEDED", new String[]{
            "The bit goes into the throat of the haft, wedged and bound, and stops being two things.",
            "You set the edge against a branch and it takes a chip out without complaint."}),
        Map.entry("CRAFT_KNIFE|FAILED", new String[]{
            "The stone shears the wrong way and the edge you were making goes with it.",
            "It breaks across the middle under the last strike."}),
        Map.entry("CRAFT_HAMMER|FAILED", new String[]{
            "The head will not sit, and every blow you test it with works it looser.",
            "The shaft splits along the grain and the whole thing comes apart in your hands."}),
        Map.entry("CRAFT_PICKAXE|FAILED", new String[]{
            "The point works loose in the haft and nothing you bind it with holds it under a swing.",
            "It comes off the haft on the second stroke and goes into the dirt."}),
        Map.entry("CRAFT_HATCHET|FAILED", new String[]{
            "The haft splits where you opened it for the head and keeps on splitting.",
            "The binding slips under load and the bit turns in the throat."}),
        Map.entry("CRAFT_FIRE_KIT|SUCCEEDED", new String[]{
            "Board, spindle, bearing and bow, wrapped together into one bundle that fits under an arm.",
            "You put the set together and it is all there, dry, in one place."}),
        Map.entry("CRAFT_FIRE_TOOL|SUCCEEDED", new String[]{
            "You cut the notch, true the spindle, and work the pieces until they run against each other smoothly.",
            "It is rough and it is finished, and the parts fit each other."}),
        Map.entry("CRAFT_FIRE_KIT|FAILED", new String[]{
            "The spindle will not run true in the socket and skates out of it every time.",
            "What you have gone damp somewhere in the making, and damp is the end of it."}),
        Map.entry("CRAFT_FIRE_TOOL|FAILED", new String[]{
            "The notch splits out to the edge of the board and takes the whole hearth with it.",
            "The spindle snaps at the narrow point, twice, and you leave it."}),
        Map.entry("CRAFT_TINDER|SUCCEEDED", new String[]{
            "You work the fibre between your palms until it comes apart into something that would catch off a spark.",
            "Teased fine and bundled into a nest, it is dry all the way through."}),
        Map.entry("CRAFT_TINDER|FAILED", new String[]{
            "What you work stays green and pulpy and will not tease out at all.",
            "It comes apart into damp shreds that would smother a spark rather than take it."}),
        Map.entry("CRAFT_NET|SUCCEEDED", new String[]{
            "Knot after knot, the mesh grows square and even until there is a net where the cordage was.",
            "You finish the last row and stretch it out. It holds its shape when you shake it."}),
        Map.entry("CRAFT_NET|FAILED", new String[]{
            "The mesh comes out ragged, wide here and tight there, and pulls itself out of square.",
            "The knots slip as fast as you tie them and the whole thing runs back into a heap of line."}),
        Map.entry("CRAFT_BELT|SUCCEEDED", new String[]{
            "You cut it to length, work the ends, and it goes round you and stays where you put it.",
            "It sits at the waist and takes weight off the shoulders the moment you hang anything on it."}),
        Map.entry("CRAFT_BELT|FAILED", new String[]{
            "The strap tears through at the fastening under the first real pull.",
            "It comes out too short to meet round you, and there is no lengthening it."}),
        Map.entry("CRAFT_GARMENT|SUCCEEDED", new String[]{
            "Stitch by stitch the panels close into a shape with a body inside it.",
            "You pull it on. It is not elegant and it turns the wind."}),
        Map.entry("CRAFT_GARMENT|FAILED", new String[]{
            "The seam runs out crooked and pulls the whole shape askew, and it will not sit on a body at all.",
            "The stitching tears out through the edge and takes the seam with it."}),
        Map.entry("CRAFT_DESK|SUCCEEDED", new String[]{
            "The top goes on and sits flat, and there is a surface here that is not the ground.",
            "You lean on it and it does not move. Work can be done at this."}),
        Map.entry("CRAFT_CHAIR|SUCCEEDED", new String[]{
            "Four legs meet a seat and the whole of it takes your weight without a sound.",
            "You sit down on something that is not a stone or the floor, for the first time in a long while."}),
        Map.entry("CRAFT_SHELF|SUCCEEDED", new String[]{
            "The boards go up level and hold, and things come up off the earth onto them.",
            "You load it and it stays where it was fixed."}),
        Map.entry("CRAFT_WORKSTATION|SUCCEEDED", new String[]{
            "The bench comes together heavy and square, made to be worked at rather than looked at.",
            "You set your weight against it and it does not shift. This is a place to work now."}),
        Map.entry("CRAFT_DESK|FAILED", new String[]{
            "The top will not sit flat on the legs and rocks whichever corner you press.",
            "The joints open under the first load and the whole of it goes out of true."}),
        Map.entry("CRAFT_CHAIR|FAILED", new String[]{
            "A leg splits at the joint the moment it takes weight.",
            "It stands until you sit on it, and then it does not."}),
        Map.entry("CRAFT_SHELF|FAILED", new String[]{
            "The boards will not stay fixed and tip whatever you set on them onto the floor.",
            "The brackets pull out of the wall and the lot comes down."}),
        Map.entry("CRAFT_WORKSTATION|FAILED", new String[]{
            "The frame will not stand square, and a bench that walks under the work is no bench.",
            "The legs splay under the top and the whole thing settles slowly sideways."}),

        // The shelter that is worked at over days, kept, mended, left, and taken up again.
        Map.entry("START_LEAN_TO|SUCCEEDED", new String[]{
            "You get the ridge up and lean the first of the poles against it. It is a beginning and it is not shelter yet.",
            "The frame stands. From here it is a matter of covering it."}),
        Map.entry("WORK_LEAN_TO|SUCCEEDED", new String[]{
            "Another course of thatch goes on, laid from the bottom up so the water runs over rather than in.",
            "You work at it until the light goes. There is less sky through it than there was."}),
        Map.entry("WORK_LEAN_TO|FAILED", new String[]{
            "What you lay on slides off as fast as you set it, and by dusk it is as open as it was.",
            "The wind takes the covering off the frame twice, and the second time you leave it."}),
        Map.entry("REPAIR_LEAN_TO|SUCCEEDED", new String[]{
            "You patch where the weather got in, and the drip onto the sleeping place stops.",
            "The bad course comes off and a sound one goes on in its place."}),
        Map.entry("ABANDON_LEAN_TO|SUCCEEDED", new String[]{
            "You leave it half-made. The frame will still be standing when you come back, or it will not.",
            "You set the work down and walk away from it."}),
        Map.entry("RESUME_LEAN_TO|SUCCEEDED", new String[]{
            "You come back to it and pick up where it was left, and it is much as you remember.",
            "The frame has weathered where it stood. You take up the work again."}),
        Map.entry("REPAIR_STRUCTURE|SUCCEEDED", new String[]{
            "You cut out what had gone and let in sound material, and the whole stands square again.",
            "The weak part comes out and the repair goes in tight enough that you cannot find the join by feel."}),
        Map.entry("REPAIR_STRUCTURE|FAILED", new String[]{
            "What has gone has gone too far, and everything you fix to it pulls away with the rot.",
            "You work at it and the damage spreads ahead of the repair."}),
        Map.entry("REPAIR_ITEM|SUCCEEDED", new String[]{
            "You bind and seat and true it, and it comes back into service.",
            "The fault comes out under your hands. It works again."}),
        Map.entry("REPAIR_ITEM|FAILED", new String[]{
            "It comes apart again in your hands, along the same line, worse than it was.",
            "There is nothing left in it to fix to. Whatever it was, it is scrap now."}),
        Map.entry("DISMANTLE|SUCCEEDED", new String[]{
            "You take it down in the order it went up, and most of what is in it comes out whole.",
            "It comes apart into a pile of materials and a bare patch of ground."}),
        Map.entry("DISMANTLE|FAILED", new String[]{
            "It has weathered into one piece and will not come apart along any line you can find.",
            "What you pull free splinters and is worth nothing."}),

        // Ground worked for what will grow on it.
        Map.entry("TILL_GROUND|SUCCEEDED", new String[]{
            "You break the crust and turn it, and what comes up is dark and smells of itself.",
            "The ground opens along the row, root and stone picked out as you go."}),
        Map.entry("TILL_GROUND|FAILED", new String[]{
            "The ground turns the point aside every time. Under a finger of soil it is all stone.",
            "It comes up in slabs that will not break down however you work at them."}),
        Map.entry("SOW|SUCCEEDED", new String[]{
            "You walk the row scattering, and cover after. What happens next is not up to you.",
            "The seed goes into the drill and the earth goes over it."}),
        Map.entry("SOW|FAILED", new String[]{
            "The ground is not open enough to take seed, and what you scatter sits on the surface.",
            "It goes in and the water takes it straight back out of the row."}),
        Map.entry("WEED_CROP|SUCCEEDED", new String[]{
            "You go down the row on your knees pulling what is not wanted, and the crop stands clearer for it.",
            "What was competing comes out by the root, and there is bare earth between the plants again."}),
        Map.entry("WEED_CROP|FAILED", new String[]{
            "It breaks off at the ground and the root stays in, which means it will be back inside a week.",
            "The row is too far gone to tell what is crop and what is not."}),
        Map.entry("PLANT_TREE|SUCCEEDED", new String[]{
            "You set it in, firm the earth round the collar, and water it. It will outlast the planting of it.",
            "The whip stands upright in ground that was empty a moment ago."}),
        Map.entry("PLANT_TREE|FAILED", new String[]{
            "The hole fills with water faster than you can set anything into it.",
            "The roots will not go down into this, and it leans over as soon as you let go."}),
        Map.entry("COPPICE|SUCCEEDED", new String[]{
            "You cut the stems off close above the stool, sloping so the wet runs off the cut.",
            "The stool comes down to a ring of pale cuts. It will throw up again, straighter than before."}),
        Map.entry("COPPICE|FAILED", new String[]{
            "It is too old to cut back. What you take off it takes the stool with it.",
            "The stems are thicker than the tool will go through."}),
        Map.entry("RESTORE_HABITAT|SUCCEEDED", new String[]{
            "You clear what was choking it and give the ground back to whatever grew here before.",
            "It is slow work and mostly undoing. The place looks less handled afterwards."}),
        Map.entry("RESTORE_HABITAT|FAILED", new String[]{
            "The ground is too far worked to come back to what it was.",
            "What you clear is straight back within the season, thicker than before."}),

        // Animals: closing with them, holding them, taking from them.
        Map.entry("TAME|SUCCEEDED", new String[]{
            "It lets you come nearer than last time and holds there, watching, not moving away.",
            "You go slowly and it stays. Something between you has shifted a little."}),
        Map.entry("TAME|FAILED", new String[]{
            "It breaks and goes before you are within twenty paces, and does not stop to look back.",
            "Whatever you did wrong, it read it, and the ground between you is empty again."}),
        Map.entry("LURE|SUCCEEDED", new String[]{
            "You set the bait down where the wind will carry it and put distance between it and yourself.",
            "It sits there smelling of what it is. Now it is a matter of waiting."}),
        Map.entry("LURE|FAILED", new String[]{
            "The wind is wrong and takes the scent away from everything that might have come to it.",
            "What you set out sits there through the afternoon and draws nothing but flies."}),
        Map.entry("SNARE|SUCCEEDED", new String[]{
            "The loop sits open at the height of a running animal, anchored to something that will not give.",
            "You set it on the run and brush out your own sign around it."}),
        Map.entry("SNARE|FAILED", new String[]{
            "The loop will not hold its shape and falls shut every time you set it.",
            "There is no run here worth setting on."}),
        Map.entry("SET_TRAP|SUCCEEDED", new String[]{
            "The trigger takes the weight and holds it, balanced on the edge of going.",
            "You set it, back away along your own tracks, and leave it to do its work."}),
        Map.entry("SET_TRAP|FAILED", new String[]{
            "The trigger goes off under its own weight, twice, and the third time you leave it sprung.",
            "It will not sit balanced on this ground."}),
        Map.entry("TAKE_ANIMAL_YIELD|SUCCEEDED", new String[]{
            "You work patiently, and carry away what the animal gives without taking anything else from it.",
            "It stands for it, and afterwards is no worse than before."}),
        Map.entry("TAKE_ANIMAL_YIELD|FAILED", new String[]{
            "It has given what it has, and shifts away from your hands.",
            "There is nothing to take. Whatever was here has been taken already."}),
        Map.entry("RAID_HIVE|SUCCEEDED", new String[]{
            "You take the comb quickly, wearing the stings, and come away sticky to the elbows.",
            "The comb comes out heavy and dripping, and the air behind you is loud."}),
        Map.entry("RAID_HIVE|FAILED", new String[]{
            "They come out of it in numbers and you leave with nothing but what they gave you.",
            "You get a hand in and take it straight back out again."}),
        Map.entry("AGGRESSION_WILDLIFE|SUCCEEDED", new String[]{
            "The blow lands and it breaks off, going heavily, not straight.",
            "You put everything into it and the animal decides against staying."}),
        Map.entry("AGGRESSION_WILDLIFE|FAILED", new String[]{
            "You swing and it is not there, and then it is much closer than it was.",
            "The strike glances off and the animal does not so much as check."}),

        // The body, and the camp around it.
        Map.entry("WARM_BODY|SUCCEEDED", new String[]{
            "You get in close to the heat and let it work into you until the shaking stops.",
            "Feeling comes back into your fingers, painfully, which is how it comes back."}),
        Map.entry("SHELTER_BODY|SUCCEEDED", new String[]{
            "You get in out of it, and the weather goes on outside without you in it.",
            "Under cover, the noise of the rain changes and stops meaning anything."}),
        Map.entry("WASH|SUCCEEDED", new String[]{
            "The dirt lifts off in the cold water and takes the smell of days with it.",
            "You come up wet and clean and shivering, and it is worth it."}),
        Map.entry("REST|SUCCEEDED", new String[]{
            "You sit and do nothing at all for a while, and the day catches up with you and passes.",
            "Stopped still, the ache in the legs goes from sharp to dull."}),
        Map.entry("STRETCH|SUCCEEDED", new String[]{
            "You work the stiffness out joint by joint until things move the way they are supposed to.",
            "Something in the back lets go with an audible click."}),
        Map.entry("MAINTAIN_CAMP|SUCCEEDED", new String[]{
            "You clear what has piled up, carry the refuse well off, and the place stops smelling of itself.",
            "An hour of tidying and the camp is somewhere you would choose to sit down in again."}),
        Map.entry("MAKE_BED|SUCCEEDED", new String[]{
            "You pile it deep and even, and press a hollow into the middle of it with your knee.",
            "Bracken and dry grass, thick enough that the cold of the ground will not come through."}),
        Map.entry("MAKE_BED|FAILED", new String[]{
            "What you gather packs flat under your own weight and leaves you on the bare earth.",
            "There is nothing here dry enough to lie on."}),
        Map.entry("PLACE_WINDBREAK|SUCCEEDED", new String[]{
            "You set it across the weather side and the wind goes round instead of through.",
            "Behind it the air goes suddenly still, and it is a different place to sit."}),
        Map.entry("PLACE_COVER|SUCCEEDED", new String[]{
            "You get something over the top of it, and what is underneath stops getting wet.",
            "The cover goes on and holds against the first gust."}),
        Map.entry("URINATE|SUCCEEDED", new String[]{
            "You step away from the camp and come back easier.",
            "Done at a distance, and downwind."}),
        Map.entry("PERSONAL_ACT|SUCCEEDED", new String[]{
            "You take a moment for yourself, and nobody in the world is any the wiser.",
            "It is your own business, done and finished."}),

        // Looking, handling, going.
        Map.entry("OBSERVE|SUCCEEDED", new String[]{
            "You stand and take the place in properly, and it resolves into particulars.",
            "Looking without doing anything else, you start to notice what was there all along."}),
        Map.entry("SCOUT|SUCCEEDED", new String[]{
            "You work the edges of it, keeping to cover, and build a picture of the ground.",
            "A slow circuit, and the shape of the country settles into something you could describe."}),
        Map.entry("SEARCH|SUCCEEDED", new String[]{
            "You go through it properly, hands as well as eyes, and turn up what a glance missed.",
            "It takes a while, and something comes out of it."}),
        Map.entry("SEARCH|FAILED", new String[]{
            "You go over every part of it and it is exactly as empty as it looks.",
            "Nothing. Whatever was here, it is not here now."}),
        Map.entry("INVESTIGATE|SUCCEEDED", new String[]{
            "You follow the thing that struck you as wrong until it explains itself.",
            "Working at it, the odd detail resolves into a plain fact."}),
        Map.entry("INVESTIGATE|FAILED", new String[]{
            "You work at it and it stays exactly as opaque as it was.",
            "Whatever is going on here does not give itself up."}),
        Map.entry("ANALYZE|SUCCEEDED", new String[]{
            "You take it apart in your head, piece by piece, and the sense of it comes clear.",
            "Set against what you already know, it stops being strange."}),
        Map.entry("MEASURE|SUCCEEDED", new String[]{
            "You pace it, span it, and set the number in your head against something you know the size of.",
            "It comes out at a figure you can work from."}),
        Map.entry("SMELL|SUCCEEDED", new String[]{
            "You draw the air in slowly, and it carries more than it seemed to.",
            "There is something on the air here that was not obvious until you went looking for it."}),
        Map.entry("FEEL|SUCCEEDED", new String[]{
            "Your fingers find what your eyes were passing over — grain, damp, a fault under the surface.",
            "It tells you more by touch than it did by sight."}),
        Map.entry("READ|SUCCEEDED", new String[]{
            "You go through the marks slowly and the sense of them assembles as you go.",
            "Someone set this down deliberately, and now it is in your head as well as theirs."}),
        Map.entry("MARK|SUCCEEDED", new String[]{
            "You cut the sign deep enough that weather will not take it out inside a year.",
            "The mark goes on. Coming back, you will know this from anywhere else."}),
        Map.entry("DESIGNATE|SUCCEEDED", new String[]{
            "You give the ground a name, and it stops being anywhere and becomes somewhere.",
            "Named, the place separates itself from all the country around it."}),
        Map.entry("PICK_UP|SUCCEEDED", new String[]{
            "You take it up off the ground and it comes with you.",
            "Lifted, weighed in the hand, kept."}),
        Map.entry("STORE|SUCCEEDED", new String[]{
            "It goes in and the lid goes over, and it is off you and out of the weather.",
            "Stowed, it stops being weight on the body and starts being weight in one place."}),
        Map.entry("OPEN_CONTAINER|SUCCEEDED", new String[]{
            "You work the lid off and what is inside shows itself.",
            "It comes open, and the smell of what has been shut in comes out with it."}),
        Map.entry("CLOSE_CONTAINER|SUCCEEDED", new String[]{
            "The lid goes back on and seats, and what is inside is out of the weather again.",
            "You close it and press it down until it holds."}),
        Map.entry("UNEQUIP|SUCCEEDED", new String[]{
            "You take it off and the place it was pressing goes cool.",
            "Off the body, it is only an object again."}),
        Map.entry("MOVE|SUCCEEDED", new String[]{
            "You cross the ground at a working pace, and the country changes around you as you go.",
            "The ground goes by under you, and by the end of it you are somewhere else."}),
        Map.entry("TRAVEL|SUCCEEDED", new String[]{
            "A long stretch of walking, and the land opens and closes and opens again.",
            "You put the distance behind you the only way it goes — a step at a time, for hours."}),
        Map.entry("TRAVEL|FAILED", new String[]{
            "The way is closed. Water, or rock, or ground that will not take weight.",
            "You get part of the way and turn back from what is between you and the rest of it."}),
        Map.entry("PROCESS_MATERIAL|SUCCEEDED", new String[]{
            "The work goes the way it should, and what comes out is not what went in.",
            "You take it through the stages, and it changes into the other thing."}),
        Map.entry("PROCESS_MATERIAL|FAILED", new String[]{
            "It will not go. What you are working on takes the effort and stays exactly as it was.",
            "Somewhere in the middle of it, it spoils, and there is no going back a step."}),
        Map.entry("REFINE|SUCCEEDED", new String[]{
            "You work the last of the coarseness out of it and it comes up clean.",
            "What was rough goes smooth under the repeated pass."}),
        Map.entry("REWORK|SUCCEEDED", new String[]{
            "You undo the bad part and do it again, and this time it sits right.",
            "The second attempt goes better, mostly because the first one did not."}),
        Map.entry("GATHER_STONE_SLAB|SUCCEEDED", new String[]{
            "You lever a flat piece up off the bed and walk it clear, edge over edge, too heavy to lift.",
            "It comes away in one slab, broad and flat, and takes both arms and your back to shift."}),
        Map.entry("GATHER_STONE_SLAB|FAILED", new String[]{
            "Every piece you lever up breaks across the middle before it is clear of the ground.",
            "The bed here comes up in fragments and nothing wide enough to be worth carrying."})
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
