package com.devosphere.draugr.world.genesis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Ensures the approved canonical world exists on startup.
 *
 * World generation is deterministic from the pinned MVP seed, so this reproduces
 * the exact geography and ecology that were approved — identical terrain, biomes,
 * and the same ecological sites in the same chunks. It runs only when no world is
 * present, so it never overwrites an existing world and never touches a Chronicle.
 *
 * Because the immutable-history design forbids deleting a Chronicle's influence in
 * place, a pristine "reset to genesis" is achieved by rebuilding the database and
 * letting this bootstrap re-establish the identical canonical world. After a reset,
 * relaunching lands the player in the original world, untouched by any prior life.
 */
@Component
public class CanonicalWorldBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(CanonicalWorldBootstrap.class);
    private final WorldGenesisService world;
    private final WorldEcologyGenesisService ecology;
    private final boolean enabled;

    public CanonicalWorldBootstrap(WorldGenesisService world, WorldEcologyGenesisService ecology,
                                   @Value("${draugr.world.auto-bootstrap:true}") boolean enabled) {
        this.world = world;
        this.ecology = ecology;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        if (world.current() != null) {
            log.info("Canonical world already present; leaving it untouched.");
            return;
        }
        WorldGenesisService.GenesisSummary summary = world.generate(WorldGenesisService.GenesisRequest.mvpDefault());
        WorldEcologyGenesisService.EcologySummary seeded = ecology.seed();
        log.info("Canonical world bootstrapped from pinned seed {}: {}x{} chunks, {} ecology sites. No Chronicle exists yet.",
                summary.seed(), summary.widthChunks(), summary.heightChunks(), seeded.siteCount());
    }
}
