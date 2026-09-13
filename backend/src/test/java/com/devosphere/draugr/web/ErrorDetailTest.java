package com.devosphere.draugr.web;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.BadSqlGrammarException;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a developer needs out of a fault (#83), read from the exception without a database. */
class ErrorDetailTest {

    @Test
    void theSqlStateAndStatementComeOutOfSpringsWrapper() {
        SQLException driver = new SQLException("ERROR: relation \"nowhere\" does not exist", "42P01");
        BadSqlGrammarException wrapped = new BadSqlGrammarException("StatementCallback", "SELECT nope FROM nowhere", driver);
        assertEquals("42P01", ErrorDetail.sqlState(wrapped));
        assertEquals("SELECT nope FROM nowhere", ErrorDetail.failingSql(wrapped));
    }

    @Test
    void aTranslatedMessageThatQuotesItsStatementStillYieldsIt() {
        DataIntegrityViolationException dive = new DataIntegrityViolationException(
            "PreparedStatementCallback; SQL [INSERT INTO item_instance (object_id) VALUES (?)]; ERROR: duplicate key",
            new SQLException("duplicate key value violates unique constraint", "23505"));
        assertEquals("INSERT INTO item_instance (object_id) VALUES (?)", ErrorDetail.failingSql(dive));
        assertEquals("23505", ErrorDetail.sqlState(dive));
    }

    @Test
    void everyNamedObjectIdIsCollectedOnce() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        RuntimeException error = new RuntimeException("Key (object_id)=(" + a + ") is not present",
            new IllegalStateException("also " + b + " and again " + a));
        assertEquals(List.of(a, b), ErrorDetail.objectIds(error));
    }

    @Test
    void aFaultWithNoDatabaseInItAnswersNothingRatherThanThrowing() {
        RuntimeException plain = new RuntimeException((String) null);
        assertNull(ErrorDetail.sqlState(plain));
        assertNull(ErrorDetail.failingSql(plain));
        assertTrue(ErrorDetail.objectIds(plain).isEmpty());
        assertNull(ErrorDetail.sqlState(null));
    }
}
