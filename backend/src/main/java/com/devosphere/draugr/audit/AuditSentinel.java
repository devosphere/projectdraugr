package com.devosphere.draugr.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The Auditor's runtime surface. It runs the read-only invariant catalog once as the
 * world comes up — the launch gate — and then on a fixed heartbeat thereafter,
 * logging any violation it finds. It never modifies data; it only reads and reports,
 * so a drifting world is noticed the moment it drifts rather than when a player
 * stumbles into the damage. The gate can be made fatal so a corrupted world refuses
 * to serve, but by default it reports loudly and lets an operator decide.
 */
@Component
public class AuditSentinel {
    private static final Logger log = LoggerFactory.getLogger(AuditSentinel.class);
    private final PersistentStateAuditor auditor;
    private final boolean failOnLaunch;

    public AuditSentinel(PersistentStateAuditor auditor, @Value("${draugr.audit.fail-on-launch:false}") boolean failOnLaunch) {
        this.auditor = auditor;
        this.failOnLaunch = failOnLaunch;
    }

    /** The launch gate: audit the persistent world once it is ready to serve. */
    @EventListener(ApplicationReadyEvent.class)
    public void gateLaunch() {
        PersistentStateAuditor.AuditReport report = auditor.inspect();
        if (report.consistent()) {
            log.info("Launch audit clean: the persistent world satisfies every invariant.");
            return;
        }
        log.error("Launch audit found {} invariant violation(s): {}", report.violations().size(), report.violations());
        if (failOnLaunch) throw new IllegalStateException("Refusing to launch on an inconsistent world: " + report.violations());
    }

    /** The heartbeat: re-audit on a fixed interval so drift surfaces promptly in the logs. */
    @Scheduled(fixedDelayString = "${draugr.audit.interval-ms:300000}", initialDelayString = "${draugr.audit.interval-ms:300000}")
    public void heartbeat() {
        PersistentStateAuditor.AuditReport report = auditor.inspect();
        if (report.consistent()) {
            log.debug("Periodic audit clean.");
            return;
        }
        log.warn("Periodic audit found {} invariant violation(s): {}", report.violations().size(), report.violations());
    }
}
