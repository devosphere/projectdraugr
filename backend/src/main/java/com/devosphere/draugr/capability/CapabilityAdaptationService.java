package com.devosphere.draugr.capability;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant; import java.util.UUID;
/** Hidden, evidence-led adaptation. Values are simulation coefficients, never RPG stats or player-facing levels. */
@Service public class CapabilityAdaptationService {
 private final JdbcTemplate jdbc; public CapabilityAdaptationService(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @Transactional public void record(UUID chronicle,UUID action, String domain,int minutes,double load,double recovery,Instant at){
  jdbc.update("INSERT INTO chronicle_capability_adaptation (chronicle_id) VALUES (?) ON CONFLICT DO NOTHING",chronicle);
  jdbc.update("INSERT INTO chronicle_capability_evidence (chronicle_id,action_id,occurred_at,domain,exposure_minutes,load_ratio,recovery_context,payload) VALUES (?,?,?,?,?,?,?,jsonb_build_object('source','resolved_action'))",chronicle,action,at,domain,minutes,load,recovery);
  String field=switch(domain){case "LOAD"->"load_conditioning";case "LOCOMOTION"->"locomotion_familiarity";case "FINE_MOTOR"->"fine_motor_familiarity";case "AIM"->"visual_aim_familiarity";default->"attention_resilience";};
  jdbc.update("UPDATE chronicle_capability_adaptation SET "+field+"=LEAST(1, "+field+" + ?), recovery_readiness=LEAST(1, GREATEST(0, recovery_readiness + ?)), updated_at=? WHERE chronicle_id=?", Math.min(.003,minutes*.00002*(.35+load)), (recovery-.5)*.002,at,chronicle);
 }
}
