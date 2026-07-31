package com.devosphere.draugr.domain;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The Architect's read of its own ledger, verifiable without a database: a known domain
 * reports present so its schema is not added twice, an unknown one reports absent so it
 * can be invented.
 */
class DomainRegistryServiceTest {

    @Test
    void aKnownDomainIsPresent() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT COUNT(*) FROM domain_registry WHERE domain_key=?", Integer.class, "literature")).thenReturn(1);
        assertTrue(new DomainRegistryService(jdbc).exists("literature"), "a domain already in the registry must report present");
    }

    @Test
    void anUninventedDomainIsAbsent() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT COUNT(*) FROM domain_registry WHERE domain_key=?", Integer.class, "pottery")).thenReturn(0);
        assertFalse(new DomainRegistryService(jdbc).exists("pottery"), "a domain not yet invented must report absent");
    }
}
