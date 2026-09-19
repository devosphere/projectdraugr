package com.devosphere.draugr.world;

import java.util.Locale;
import java.util.Optional;

/**
 * Which backdrop a place calls for, decided once and decided the same way every time (#225/#234).
 *
 * <p>A pure function of {@link VisualContextService.VisualContext} — no database, no clock, no randomness. That
 * matters for two reasons beyond testability:
 *
 * <ul>
 *   <li><b>Encounter state cannot influence the choice, structurally.</b> The ticket asks for that as a rule; it
 *       is stronger than a rule here, because the context the resolver is handed carries no encounter at all.
 *       There is nothing to leak. What is hunting you is not scenery.</li>
 *   <li><b>Destroyed context stops qualifying immediately</b>, for the same reason: features come from the
 *       context, and the context is built from ACTIVE objects on the ground underfoot. A shelter pulled down is
 *       simply not in the list any more.</li>
 * </ul>
 *
 * <p><b>Precedence</b>, most specific first. The ticket names a longer chain — exact/interior → site → habitat
 * evidence → adjacent → ecotone → regional → biome → global. <em>Adjacent</em>, <em>ecotone</em> and
 * <em>regional</em> are answered from what a standing person can see of the next ground (#232): its kind of country,
 * never what stands on it, and nothing at all in the dark. They refine the ground's own key rather than replacing
 * it — see {@code setting} — because a tier's key is the one link a caller is promised an image for, and a place at a
 * forest's edge with no edge image must fall back to its ground, not to nothing.
 *
 * <p>Every tier returns a key, and the last one always matches, so the resolver can never return nothing.
 *
 * <p><b>Eligibility</b> (#226/#236) rides on top of precedence rather than inside it. Precedence answers "which
 * place is this"; eligibility answers "which of this place". They are kept apart because a dark field is still a
 * field — the hour must never be able to promote a place over another one — so the variants are offered as a
 * degrading chain on the chosen key rather than as tiers of their own. See {@link Choice#candidates()}.
 */
public final class BackdropResolver {

    private BackdropResolver() { }

    /**
     * The chosen backdrop and why it was chosen — the reason code is safe to show, naming only what is underfoot.
     *
     * <p>{@code key} is the answer that is always safe to use: every tier that produces one produces a key for a
     * place, unqualified by the hour or the sky. {@code candidates} is that same answer offered at increasing
     * degrees of honesty (#226/#236) — most specific first, always ending in {@code key}. A caller takes the
     * first candidate it actually has an image for and stops. That is what lets the world get a snow-lying,
     * after-dark version of a place without any tier ever being able to name something absent: the chain
     * degrades, and its last link is the one guaranteed to exist.
     */
    public record Choice(String key, String reason, java.util.List<String> candidates) { }

    /** The key that always exists. Reaching it is not a failure; it is a place with nothing remarkable on it. */
    public static final String FALLBACK_KEY = "world.default";

    public static Choice resolve(VisualContextService.VisualContext context) {
        if (context == null) return plain(FALLBACK_KEY, "NO_CONTEXT");

        // 1. Interior. Being inside something outranks everything about the country outside it — you cannot see
        //    the country. This is the only tier that can ignore the weather and the hour, and the only one that
        //    takes no eligibility variants: the sky is not a fact about the inside of a rock, and the light in
        //    the chamber is already in the key.
        if ("CAVE_INTERIOR".equals(context.biome()))
            return plain("interior.cave" + (context.lit() ? ".lit" : ".dark"), "INTERIOR");

        // 2. A people's village on this ground (#115). Houses, walkways and hung nets fill the view: the marsh they
        //    stand in is their setting, not the other way round, so the village outranks the site beneath it.
        Optional<String> settlement = context.features().stream()
            .filter(f -> f.kind() != null && f.kind().startsWith("SETTLEMENT:"))
            .map(f -> slug(f.kind().substring("SETTLEMENT:".length())))
            .sorted()
            .findFirst();
        if (settlement.isPresent()) return outdoors("settlement." + settlement.get(), "SETTLEMENT_HERE", context);

        // 3. A site on this ground. The most specific thing a standing person can actually see.
        Optional<String> site = context.features().stream()
            .filter(f -> f.kind() != null && f.kind().startsWith("SITE:"))
            .map(f -> slug(f.name()))
            .sorted()                                  // ties break by name, so two sites never toss a coin
            .findFirst();
        if (site.isPresent()) return outdoors("site." + site.get(), "SITE_HERE", context);

        // 4. Evidence of habitation: something a Chronicle has built and finished. Ranked under a natural site
        //    because a lean-to on a riverbank is still, mostly, a riverbank.
        Optional<String> built = context.features().stream()
            .filter(f -> f.kind() != null && f.kind().startsWith("BUILT:"))
            .map(f -> slug(f.kind().substring("BUILT:".length())))
            .sorted()
            .findFirst();
        if (built.isPresent()) return outdoors("built." + built.get(), "BUILT_HERE", context);

        // 5. The ground itself — seen in its setting where the setting can be seen (#232/#234).
        if (context.biome() != null && !context.biome().isBlank())
            return outdoors("biome." + slug(context.biome()), "BIOME", context, setting(context));

        return plain(FALLBACK_KEY, "NO_BIOME");
    }

    /** A key with nothing the hour or the sky could qualify. */
    private static Choice plain(String key, String reason) {
        return new Choice(key, reason, java.util.List.of(key));
    }

    /**
     * Outdoor eligibility (#226/#236): the same place, offered darkest-and-most-covered first.
     *
     * <p>The complaint the ticket names is <em>false scenery</em> — a sunlit summer field shown at midnight in a
     * blizzard. Two facts change an outdoor scene enough to be worth a different image, and they are the two a
     * standing person could not possibly mistake:
     *
     * <ul>
     *   <li><b>Dark.</b> No light to see by. This is the worse lie of the two, so a plain night image outranks a
     *       daylit snow one when both cannot be had.</li>
     *   <li><b>Snow lying.</b> Falling snow, or cold enough that what fell is still there. Deliberately derived
     *       from the weather and the temperature <em>as felt here</em> rather than from the season, because the
     *       season is the same for the whole world and the ground is not: a sheltered valley in a mild winter has
     *       no snow on it, and saying otherwise is the same lie in the other direction.</li>
     * </ul>
     *
     * <p>Two facts give at most four candidates, and the last is always the bare key, so the chain is bounded and
     * can never end somewhere that might not exist. Season and the finer weather kinds are deliberately absent:
     * rain and wind change how a place feels far more than how it looks, and an image per season per biome per
     * hour is a combinatorial promise the asset catalogue cannot keep. What is here is what a caller can honestly
     * be asked to have.
     */
    private static Choice outdoors(String key, String reason, VisualContextService.VisualContext context) {
        return outdoors(key, reason, context, null);
    }

    /**
     * The same, with the ground seen in its setting (#232/#234). The ticket's adjacent, ecotone and regional tiers
     * live here rather than as tiers of their own, deliberately. A tier replaces the key, and the key is the one link
     * a caller is promised an image for — a place at a forest's edge with no edge image would have been left with
     * nothing, where it should fall back to its ground. So the setting refines the ground's own key and degrades to
     * it, exactly as the dark and the snow do. A setting is only ever offered in daylight: nothing next door can be
     * seen in the dark (the context reports no surroundings then), so the chain never has to hold both, and stays
     * within its bound.
     */
    private static Choice outdoors(String key, String reason, VisualContextService.VisualContext context, String setting) {
        boolean dark = !context.lit();
        boolean snow = "SNOW".equals(context.weather()) || context.temperatureC() <= 0.0;

        java.util.List<String> chain = new java.util.ArrayList<>();
        if (setting != null && !dark) {
            if (snow) chain.add(key + "." + setting + ".snow");
            chain.add(key + "." + setting);
        }
        if (dark && snow) chain.add(key + ".snow.night");
        if (dark)         chain.add(key + ".night");
        if (snow)         chain.add(key + ".snow");
        chain.add(key);
        return new Choice(key, reason, java.util.List.copyOf(chain));
    }

    /** Landforms that dominate any view they are in: a mountain wall, the open sea. */
    private static final java.util.Set<String> DOMINANT = java.util.Set.of("MOUNTAIN", "OCEAN");

    /**
     * Which one setting this ground is seen in, most commanding first (#232/#234), or null when nothing beyond it is
     * seen. Adjacent: a dominant landform next door. Ecotone: a different kind of country at the edge of this one.
     * Regional: the same country in every direction. Ties break by name, so neighbours never toss a coin.
     */
    private static String setting(VisualContextService.VisualContext context) {
        java.util.List<String> around = context.surroundings();
        if (around == null || around.isEmpty() || context.biome() == null) return null;
        java.util.List<String> other = around.stream().filter(b -> b != null && !b.equals(context.biome())).sorted().toList();
        java.util.Optional<String> dominant = other.stream().filter(DOMINANT::contains).findFirst();
        if (dominant.isPresent()) return "beside-" + slug(dominant.get());
        if (!other.isEmpty()) return "edge-" + slug(other.get(0));
        return "deep";
    }

    /** Lower-case, underscore-free, punctuation-free — a key that reads the same however the name was written. */
    private static String slug(String raw) {
        return raw == null ? "" : raw.toLowerCase(Locale.ROOT).replace('_', '-').replaceAll("[^a-z0-9-]+", "-")
            .replaceAll("-+", "-").replaceAll("^-|-$", "");
    }
}
