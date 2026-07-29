package com.devosphere.draugr.literature;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class LiteratureService {
    private final JdbcTemplate jdbc;
    public LiteratureService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public UUID revise(UUID documentId, UUID chronicleId, UUID actionId, Edit edit, String text, String anchor) {
        Document current = jdbc.query("SELECT d.title,d.current_revision_id,r.revision_number,r.content FROM literature_document d LEFT JOIN literature_revision r ON r.id=d.current_revision_id WHERE d.object_id=? FOR UPDATE", rs -> rs.next() ? new Document(rs.getString(1),rs.getObject(2,UUID.class),rs.getInt(3),rs.getString(4)) : null, documentId);
        if (current == null) throw new IllegalArgumentException("That physical document does not exist.");
        String prior = current.content() == null ? "" : current.content();
        String next = switch (edit) { case INITIAL -> { if (current.revisionId()!=null) throw new IllegalStateException("The document already has content."); yield text; } case APPEND -> prior + text; case REPLACE -> text; case INSERT -> insertAtAnchor(prior, text, anchor); };
        UUID revision = UUID.randomUUID();
        jdbc.update("INSERT INTO literature_revision (id,document_id,revision_number,parent_revision_id,created_at,created_by_chronicle_id,source_action_id,edit_kind,content,content_hash) VALUES (?,?,?,?,?,?,?,?,?,?)",revision,documentId,current.revisionNumber()+1,current.revisionId(),Instant.now(),chronicleId,actionId,edit.name(),next,hash(next));
        jdbc.update("UPDATE literature_document SET current_revision_id=? WHERE object_id=?",revision,documentId);
        return revision;
    }
    private String insertAtAnchor(String prior,String text,String anchor) { if(anchor==null||anchor.isBlank()) throw new IllegalArgumentException("An insertion needs an exact anchor."); int first=prior.indexOf(anchor); if(first<0||first!=prior.lastIndexOf(anchor)) throw new IllegalArgumentException("The anchor is absent or ambiguous."); return prior.substring(0,first)+text+prior.substring(first); }
    private String hash(String content) { try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);} }
    private record Document(String title,UUID revisionId,int revisionNumber,String content){}
    public enum Edit { INITIAL, APPEND, INSERT, REPLACE }
}
