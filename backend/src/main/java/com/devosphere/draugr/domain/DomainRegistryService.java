package com.devosphere.draugr.domain;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Read access to the Persistent State Architect's domain ledger. The Architect consults
 * {@link #exists(String)} before adding a domain's schema, so a domain invented once is
 * never added twice; {@link #list()} surfaces the whole known catalogue. This service
 * only reads — a domain is recorded by the same Flyway migration that adds its tables,
 * never mutated at runtime here, in keeping with the Architect owning all schema change.
 */
@Service
public class DomainRegistryService {
    private final JdbcTemplate jdbc;
    public DomainRegistryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Whether a domain has already been given schema anywhere in the world's history. */
    @Transactional(readOnly = true)
    public boolean exists(String domainKey) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM domain_registry WHERE domain_key=?", Integer.class, domainKey);
        return n != null && n > 0;
    }

    /** Every domain the world knows how to be, oldest first. */
    @Transactional(readOnly = true)
    public List<Domain> list() {
        return jdbc.query("SELECT domain_key, display_name, description, introduced_in_migration, origin, introduced_at FROM domain_registry ORDER BY introduced_at, domain_key",
            (rs, row) -> new Domain(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getTimestamp(6).toInstant()));
    }

    public record Domain(String key, String displayName, String description, String introducedInMigration, String origin, Instant introducedAt) { }
}
