package com.devosphere.draugr.persistence;

import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.web.SystemErrorRecorder;
import com.devosphere.draugr.world.genesis.WorldEcologyGenesisService;
import com.devosphere.draugr.world.genesis.WorldGenesisService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The regression record (#83, V321): a real database fault, filed with what a developer needs to reproduce it.
 *
 * <p>Skips gracefully without Docker.
 */
@SpringBootTest
class SystemErrorRecordIntegrationTest {

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void startDatabase() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required for this integration test");
        System.setProperty("java.awt.headless", "true");
        postgres.start();
    }

    @AfterAll
    static void stopDatabase() { if (postgres.isRunning()) postgres.stop(); }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired WorldGenesisService worldGenesis;
    @Autowired WorldEcologyGenesisService ecology;
    @Autowired ChronicleService chronicles;
    @Autowired SystemErrorRecorder recorder;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void unbindRequest() { RequestContextHolder.resetRequestAttributes(); }

    @Test
    void aRealDatabaseFaultIsFiledWithEverythingNeededToReproduceIt() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        UUID chronicle = chronicles.awaken().id();

        // The request an action arrives on, as ChronicleActionController leaves it.
        UUID key = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/actions");
        request.setAttribute(SystemErrorRecorder.ACTION_TEXT_ATTRIBUTE, "I gather firewood.");
        request.setAttribute(SystemErrorRecorder.ACTION_KEY_ATTRIBUTE, key);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        UUID named = UUID.randomUUID();
        DataAccessException fault = assertThrows(DataAccessException.class,
            () -> jdbc.queryForList("SELECT nope FROM nowhere_" + named.toString().replace('-', '_') + " WHERE id='" + named + "'"));

        recorder.record(500, fault, "/api/actions");

        Map<String, Object> row = jdbc.queryForMap(
            "SELECT sql_state, failing_sql, schema_version, chronicle_id, action_text, action_idempotency_key, " +
            "  array_to_string(object_ids, ',') AS ids, error_class FROM system_error_log WHERE action_idempotency_key=?", key);
        assertEquals("42P01", row.get("sql_state"), "an undefined table is SQLSTATE 42P01");
        assertTrue(((String) row.get("failing_sql")).contains("SELECT nope FROM nowhere_"), () -> "the failing statement must be filed: " + row);
        assertNotNull(row.get("schema_version"), "the schema version the fault happened under must be filed");
        assertEquals(chronicle, row.get("chronicle_id"), "the living Chronicle at the time must be filed");
        assertEquals("I gather firewood.", row.get("action_text"));
        assertTrue(((String) row.get("ids")).contains(named.toString()), () -> "the object the error named must be filed: " + row);
        assertTrue(((String) row.get("error_class")).contains("PSQLException"), "the class filed is still the driver's own, as before");
    }
}
