package com.devosphere.draugr.ai;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Every thing the world has a name for (#37).
 *
 * <p>The narrator is allowed one sentence of atmosphere and is told not to invent objects. "Told not to" is not a
 * rule; it is a request. To hold it to that, something has to know what counts as naming an object, and that is this:
 * the display names of the catalogue — every item, and every creature by the name its key spells out.
 *
 * <p>Read once and kept for an hour. The catalogue changes only when a migration adds to it, and a narrator holding
 * an hour-old list of names is never wrong about the ones that were already there.
 */
@Component
public class WorldVocabulary {

    private static final Duration KEPT_FOR = Duration.ofHours(1);
    /** Short words are ordinary English as often as they are things ("mat", "cup"), so they are not held against a sentence. */
    private static final int LONG_ENOUGH = 5;

    private final JdbcTemplate jdbc;
    private volatile Set<String> names = Set.of();
    private volatile Instant read = Instant.EPOCH;

    public WorldVocabulary(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** The lower-cased display names of everything in the catalogue, long enough to be unmistakable. */
    public Set<String> names() {
        if (Duration.between(read, Instant.now()).compareTo(KEPT_FOR) < 0 && !names.isEmpty()) return names;
        try {
            List<String> rows = jdbc.queryForList(
                "SELECT lower(display_name) FROM item_definition UNION SELECT lower(replace(species_key,'_',' ')) FROM wildlife_species", String.class);
            names = rows.stream().filter(n -> n != null && n.length() >= LONG_ENOUGH).map(n -> n.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
            read = Instant.now();
        } catch (RuntimeException couldNotRead) {
            // A narrator that cannot read the catalogue holds the model to nothing rather than to everything.
            names = Set.of();
        }
        return names;
    }
}
