package com.devosphere.draugr.literature;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.List;

@Service
public class LiteratureService {
    private final JdbcTemplate jdbc;
    public LiteratureService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public UUID revise(UUID documentId, UUID chronicleId, UUID actionId, Instant occurredAt, Edit edit, String text, String anchor) {
        assertReachable(documentId, chronicleId);
        Document current = jdbc.query("SELECT d.title,d.current_revision_id,r.revision_number,r.content FROM literature_document d LEFT JOIN literature_revision r ON r.id=d.current_revision_id WHERE d.object_id=? FOR UPDATE OF d", rs -> rs.next() ? new Document(rs.getString(1),rs.getObject(2,UUID.class),rs.getInt(3),rs.getString(4)) : null, documentId);
        if (current == null) throw new IllegalArgumentException("That physical document does not exist.");
        String prior = current.content() == null ? "" : current.content();
        String next = switch (edit) { case INITIAL -> { if (current.revisionId()!=null) throw new IllegalStateException("The document already has content."); yield text; } case APPEND -> prior + text; case REPLACE -> text; case INSERT -> insertAtAnchor(prior, text, anchor); };
        UUID revision = UUID.randomUUID();
        jdbc.update("INSERT INTO literature_revision (id,document_id,revision_number,parent_revision_id,created_at,created_by_chronicle_id,source_action_id,edit_kind,content,content_hash) VALUES (?,?,?,?,?,?,?,?,?,?)",revision,documentId,current.revisionNumber()+1,current.revisionId(),Timestamp.from(occurredAt),chronicleId,actionId,edit.name(),next,hash(next));
        jdbc.update("UPDATE literature_document SET current_revision_id=? WHERE object_id=?",revision,documentId);
        return revision;
    }
    @Transactional(readOnly = true)
    public List<DocumentView> reachable(UUID chronicleId) {
        return jdbc.query("WITH RECURSIVE reachable(id) AS (SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE' UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id JOIN world_object nested ON nested.id=ic.item_id WHERE nested.lifecycle_state='ACTIVE') SELECT d.object_id,d.document_kind,d.title,d.current_revision_id,COALESCE(r.revision_number,0) FROM reachable x JOIN literature_document d ON d.object_id=x.id LEFT JOIN literature_revision r ON r.id=d.current_revision_id ORDER BY d.title",(rs,row)->new DocumentView(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getObject(4,UUID.class),rs.getInt(5)),chronicleId);
    }
    @Transactional(readOnly = true)
    public RevisionView current(UUID documentId, UUID chronicleId) {
        assertReachable(documentId,chronicleId);
        return jdbc.query("SELECT d.object_id,d.document_kind,d.title,r.id,r.revision_number,r.content,r.created_at FROM literature_document d JOIN literature_revision r ON r.id=d.current_revision_id WHERE d.object_id=?",rs->rs.next()?new RevisionView(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getObject(4,UUID.class),rs.getInt(5),rs.getString(6),rs.getTimestamp(7).toInstant()):null,documentId);
    }
    private void assertReachable(UUID documentId,UUID chronicleId) { Integer count=jdbc.queryForObject("WITH RECURSIVE reachable(id) AS (SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE' UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id JOIN world_object nested ON nested.id=ic.item_id WHERE nested.lifecycle_state='ACTIVE') SELECT COUNT(*) FROM reachable WHERE id=?",Integer.class,chronicleId,documentId); if(count==null||count==0)throw new IllegalArgumentException("The document is not physically reachable by the Chronicle."); }
    private String insertAtAnchor(String prior,String text,String anchor) { if(anchor==null||anchor.isBlank()) throw new IllegalArgumentException("An insertion needs an exact anchor."); int first=prior.indexOf(anchor); if(first<0||first!=prior.lastIndexOf(anchor)) throw new IllegalArgumentException("The anchor is absent or ambiguous."); return prior.substring(0,first)+text+prior.substring(first); }
    private String hash(String content) { try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);} }
    private record Document(String title,UUID revisionId,int revisionNumber,String content){}
    public enum Edit { INITIAL, APPEND, INSERT, REPLACE }
    public record DocumentView(UUID id,String kind,String title,UUID revisionId,int revisionNumber){}
    public record RevisionView(UUID id,String kind,String title,UUID revisionId,int revisionNumber,String content,Instant createdAt){}
}
