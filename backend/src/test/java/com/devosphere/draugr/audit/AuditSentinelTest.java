package com.devosphere.draugr.audit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The launch gate's contract, verifiable without a database: a clean world launches,
 * a drifting world is reported, and only the strict flag turns that report fatal.
 * The heartbeat never aborts — it observes and warns.
 */
class AuditSentinelTest {

    private PersistentStateAuditor auditorReturning(boolean consistent, String... violations) {
        PersistentStateAuditor auditor = mock(PersistentStateAuditor.class);
        when(auditor.inspect()).thenReturn(new PersistentStateAuditor.AuditReport(consistent, List.of(violations)));
        return auditor;
    }

    @Test
    void aCleanWorldLaunches() {
        AuditSentinel sentinel = new AuditSentinel(auditorReturning(true), true);
        assertDoesNotThrow(sentinel::gateLaunch, "a consistent world must pass the launch gate");
    }

    @Test
    void aDriftingWorldIsReportedButLaunchesWhenNotStrict() {
        AuditSentinel sentinel = new AuditSentinel(auditorReturning(false, "2 destroyed object(s) do not record how they were destroyed."), false);
        assertDoesNotThrow(sentinel::gateLaunch, "without the strict flag the gate reports but does not abort launch");
    }

    @Test
    void aDriftingWorldAbortsLaunchWhenStrict() {
        AuditSentinel sentinel = new AuditSentinel(auditorReturning(false, "1 fire(s) burn with no fuel remaining."), true);
        assertThrows(IllegalStateException.class, sentinel::gateLaunch, "the strict flag must refuse to launch on an inconsistent world");
    }

    @Test
    void theHeartbeatNeverAborts() {
        AuditSentinel sentinel = new AuditSentinel(auditorReturning(false, "1 exhausted carcass(es) remain active in the world."), true);
        assertDoesNotThrow(sentinel::heartbeat, "the periodic heartbeat observes and warns; it must never throw");
    }
}
