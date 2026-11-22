package com.devosphere.draugr.capability;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant; import java.sql.Timestamp; import java.util.UUID;
/** Hidden, evidence-led adaptation. Values are simulation coefficients, never RPG stats or player-facing levels. */
@Service public class CapabilityAdaptationService {
 private final JdbcTemplate jdbc; private final CausalMetricService metrics;
 public CapabilityAdaptationService(JdbcTemplate jdbc, CausalMetricService metrics){this.jdbc=jdbc;this.metrics=metrics;}

 /**
  * WHICH TRACKED METRIC A DOMAIN MOVES (#953). The domains were a switch over column NAMES, which meant the
  * caller had to know the schema; they are metric keys now, and metric_subject holds the column. One list,
  * because two lists disagree eventually.
  */
 private static String metricOf(String domain){return switch(domain){
   case "LOAD"->"LOAD_CONDITIONING"; case "LOCOMOTION"->"LOCOMOTION_FAMILIARITY";
   case "FINE_MOTOR"->"FINE_MOTOR_FAMILIARITY"; case "AIM"->"VISUAL_AIM_FAMILIARITY";
   case "INSIGHT"->"INSIGHT_FAMILIARITY"; case "KNOWLEDGE"->"KNOWLEDGE_FAMILIARITY";
   default->"ATTENTION_RESILIENCE";};}
 @Transactional public void record(UUID chronicle,UUID action, String domain,int minutes,double load,double recovery,Instant at){
  Timestamp ts=Timestamp.from(at);
  jdbc.update("INSERT INTO chronicle_capability_adaptation (chronicle_id) VALUES (?) ON CONFLICT DO NOTHING",chronicle);
  jdbc.update("INSERT INTO chronicle_capability_evidence (chronicle_id,action_id,occurred_at,domain,exposure_minutes,load_ratio,recovery_context,payload) VALUES (?,?,?,?,?,?,?,jsonb_build_object('source','resolved_action'))",chronicle,action,ts,domain,minutes,load,recovery);
  // WHAT CHANGED, AND WHY (#953). This was one blind UPDATE: it added a delta to a column without ever
  // reading what was there, so the world could say an exposure had happened and never what it did. Both
  // moves go through CausalMetricService now, which reads the before, writes the after, and records the
  // pair with its cause — so a Chronicle's conditioning can be reconstructed from the ledger alone.
  metrics.move(chronicle, metricOf(domain), Math.min(.003, minutes*.00002*(.35+load)), "PRACTICE",
               action, null, minutes, recovery, at);
  metrics.move(chronicle, "RECOVERY_READINESS", (recovery-.5)*.002, "RECOVERY",
               action, null, minutes, recovery, at);
  jdbc.update("UPDATE chronicle_capability_adaptation SET updated_at=? WHERE chronicle_id=?", ts, chronicle);
  // AND WHAT HAS NOT BEEN ASKED OF THE BODY IN A WHILE (#953). Seven of these eight numbers could only ever
  // rise, so a Chronicle who hauled timber for a week and then sat for a month was as conditioned as the day
  // they stopped. Checked here, on an act that is already writing, rather than on a tick of its own: the
  // ledger says when each metric last saw real practice, so the cost is one query and nothing drifts.
  metrics.letTheUnpractisedSlip(chronicle, at);
 }
 /**
  * WHAT THE BODY HAS NOTICED SINCE IT WAS LAST TOLD (#953), as one run of prose, or null — the usual answer.
  * Drawn from the ledger, so it speaks only of a change that really happened and only once that change has
  * grown large enough to be felt. Telling marks the rows told, so the same change is never narrated twice.
  */
 @Transactional public String whatIsFelt(UUID chronicle){
  java.util.List<String> felt=metrics.whatIsFelt(chronicle);
  return felt.isEmpty()?null:String.join("",felt);
 }
 /** The chronicle's accumulated familiarity (0..1) in a domain — the passive-knowledge layer that lets a practiced hand succeed on a terse command. 0 when never practiced. */
 @Transactional(readOnly=true) public double familiarity(UUID chronicle, String domain){
  // The column is metric_subject's to name, in one place, so this reader cannot drift from the writer.
  String field=jdbc.query("SELECT source_column FROM metric_subject WHERE metric_key=?",
      rs->rs.next()?rs.getString(1):null, metricOf(domain));
  if(field==null) return 0.0;
  Double v=jdbc.query("SELECT "+field+" FROM chronicle_capability_adaptation WHERE chronicle_id=?", rs->rs.next()?(Double)rs.getDouble(1):null, chronicle);
  return v==null?0.0:v;
 }
}
