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
 * evidence → adjacent → ecotone → regional → biome → global. The tiers below stop at the ones the context can
 * honestly answer: <em>adjacent</em>, <em>ecotone</em> and <em>regional</em> all need facts about neighbouring
 * ground, and the visual context deliberately carries none, because reporting a neighbour's sites is how a
 * payload becomes a way to read the Overseer's map. Those tiers want the context to first expose what a standing
 * person can actually see of the next chunk, which is its own piece of work and its own perception question.
 *
 * <p>Every tier returns a key, and the last one always matches, so the resolver can never return nothing.
 */
public final class BackdropResolver {

    private BackdropResolver() { }

    /** The chosen backdrop and why it was chosen — the reason code is safe to show, naming only what is underfoot. */
    public record Choice(String key, String reason) { }

    /** The key that always exists. Reaching it is not a failure; it is a place with nothing remarkable on it. */
    public static final String FALLBACK_KEY = "world.default";

    public static Choice resolve(VisualContextService.VisualContext context) {
        if (context == null) return new Choice(FALLBACK_KEY, "NO_CONTEXT");

        // 1. Interior. Being inside something outranks everything about the country outside it — you cannot see
        //    the country. This is the only tier that can ignore the weather and the hour.
        if ("CAVE_INTERIOR".equals(context.biome()))
            return new Choice("interior.cave" + (context.lit() ? ".lit" : ".dark"), "INTERIOR");

        // 2. A site on this ground. The most specific thing a standing person can actually see.
        Optional<String> site = context.features().stream()
            .filter(f -> f.kind() != null && f.kind().startsWith("SITE:"))
            .map(f -> slug(f.name()))
            .sorted()                                  // ties break by name, so two sites never toss a coin
            .findFirst();
        if (site.isPresent()) return new Choice("site." + site.get(), "SITE_HERE");

        // 3. Evidence of habitation: something a Chronicle has built and finished. Ranked under a natural site
        //    because a lean-to on a riverbank is still, mostly, a riverbank.
        Optional<String> built = context.features().stream()
            .filter(f -> f.kind() != null && f.kind().startsWith("BUILT:"))
            .map(f -> slug(f.kind().substring("BUILT:".length())))
            .sorted()
            .findFirst();
        if (built.isPresent()) return new Choice("built." + built.get(), "BUILT_HERE");

        // 4. The ground itself.
        if (context.biome() != null && !context.biome().isBlank())
            return new Choice("biome." + slug(context.biome()), "BIOME");

        return new Choice(FALLBACK_KEY, "NO_BIOME");
    }

    /** Lower-case, underscore-free, punctuation-free — a key that reads the same however the name was written. */
    private static String slug(String raw) {
        return raw == null ? "" : raw.toLowerCase(Locale.ROOT).replace('_', '-').replaceAll("[^a-z0-9-]+", "-")
            .replaceAll("-+", "-").replaceAll("^-|-$", "");
    }
}
