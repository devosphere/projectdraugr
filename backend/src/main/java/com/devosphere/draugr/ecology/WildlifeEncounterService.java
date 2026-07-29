package com.devosphere.draugr.ecology;

import com.devosphere.draugr.chronicle.ChroniclePhysiologyService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

@Service
public class WildlifeEncounterService {
    private final JdbcTemplate jdbc; private final ChroniclePhysiologyService physiology;
    public WildlifeEncounterService(JdbcTemplate jdbc, ChroniclePhysiologyService physiology) { this.jdbc=jdbc; this.physiology=physiology; }
    @Transactional
    public EncounterResult confront(UUID chronicle, UUID chunk, UUID action, Instant at) {
        Encounter candidate=jdbc.query("SELECT wp.species_key,wp.ecological_role,wp.behavior_state FROM wildlife_population wp JOIN ecology_site es ON es.id=wp.site_id WHERE es.chunk_id=? AND wp.population_count>0 ORDER BY CASE wp.ecological_role WHEN 'CARNIVORE' THEN 0 WHEN 'OMNIVORE' THEN 1 ELSE 2 END LIMIT 1",rs->rs.next()?new Encounter(rs.getString(1),rs.getString(2),rs.getString(3)):null,chunk);
        if(candidate==null)return new EncounterResult(false,"The ground answers only with rain and the small movements of the forest.");
        int severity="CARNIVORE".equals(candidate.role())?22:"OMNIVORE".equals(candidate.role())?12:4;
        if("RESTING".equals(candidate.behavior())) severity=Math.max(2,severity-5);
        physiology.applyInjury(chronicle,severity,action,at,"WILDLIFE_CONTACT");
        return new EncounterResult(true,"The " + candidate.species().replace('_',' ') + " moves with sudden force. The encounter leaves its mark before the forest takes it back.");
    }
    private record Encounter(String species,String role,String behavior){}
    public record EncounterResult(boolean encountered,String narration){}
}
