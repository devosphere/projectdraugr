package com.devosphere.draugr.routing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Counts the actions the foundation could not resolve.
 *
 * <p>A separate bean rather than a method on {@link ProcessMatcher} for one concrete
 * reason: {@code REQUIRES_NEW} is applied by a Spring proxy, and a proxy never sees a
 * call a bean makes to itself. Recording from inside the matcher would have silently
 * joined the caller's transaction and rolled back with it — losing exactly the misses
 * that matter most, the ones where the action then failed.
 */
@Component
public class RoutingMissRecorder {

    private static final Logger log = LoggerFactory.getLogger(RoutingMissRecorder.class);

    private final JdbcTemplate jdbc;

    public RoutingMissRecorder(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /**
     * Record one miss, or increment the count if this text has been seen before.
     *
     * <p>Runs in its own transaction so a miss survives an action that later rolls
     * back, and swallows its own failures: telemetry must never cost a player their
     * action, and a backlog problem must never become a play problem.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String actionText, String category, ProcessMatcher.Result result) {
        try {
            String normalised = ActivityClassifier.normalise(actionText).trim();
            if (normalised.isEmpty()) return;
            jdbc.update(
                "INSERT INTO routing_miss (text_hash, normalised_text, sample_text, " +
                "                          classified_category, furthest_gate, near_process_key) " +
                "VALUES (md5(?), ?, ?, ?, ?, ?) " +
                "ON CONFLICT (text_hash) DO UPDATE SET " +
                "  hit_count = routing_miss.hit_count + 1, " +
                "  last_seen = now(), " +
                "  classified_category = EXCLUDED.classified_category, " +
                "  furthest_gate = EXCLUDED.furthest_gate, " +
                "  near_process_key = EXCLUDED.near_process_key",
                normalised, normalised, actionText, category, result.furthestGate(), result.nearProcessKey());
        } catch (RuntimeException e) {
            log.debug("Could not record routing miss for '{}'", actionText, e);
        }
    }
}
