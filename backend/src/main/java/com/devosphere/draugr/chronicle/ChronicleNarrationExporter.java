package com.devosphere.draugr.chronicle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Map;

/**
 * Renders a whole chronicle's narration to a PDF — every resolved action in
 * order, not the paginated slice the play screen holds in memory. The source is
 * {@link ChronicleService#journey}, so a living chronicle and an archived
 * (dead) one export identically; reviewing a finished life is the point of the
 * feature for three-AI playtests.
 *
 * <p>Layout only reads the journey it is handed. It never touches the database
 * or the world clock, so it is safe to call from any read path.
 */
@Service
public class ChronicleNarrationExporter {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC);

    // A restrained parchment/forest palette that matches the in-game reader.
    private static final Color INK = new Color(0x26, 0x2b, 0x22);
    private static final Color MUTED = new Color(0x74, 0x7d, 0x6f);
    private static final Color GOLD = new Color(0x8a, 0x74, 0x2f);

    public byte[] toPdf(ChronicleService.ChronicleJourney journey) {
        ChronicleService.ChronicleSummary s = journey.summary();
        Document doc = new Document(PageSize.A4, 56, 56, 60, 56);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font eyebrow = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, Font.BOLD, MUTED);
            Font title = FontFactory.getFont(FontFactory.TIMES_ROMAN, 26, Font.NORMAL, INK);
            Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 9.5f, Font.NORMAL, MUTED);
            Font marker = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.BOLD, GOLD);
            Font body = FontFactory.getFont(FontFactory.TIMES_ROMAN, 11.5f, Font.NORMAL, INK);
            Font section = FontFactory.getFont(FontFactory.HELVETICA, 10.5f, Font.BOLD, INK);
            Font closing = FontFactory.getFont(FontFactory.TIMES_ITALIC, 11, Font.ITALIC, MUTED);

            Paragraph eb = new Paragraph("PROJECT DRAUGR  —  CHRONICLE", eyebrow);
            eb.setSpacingAfter(2f);
            doc.add(eb);
            doc.add(new Paragraph("Chronicle " + s.sequenceNumber(), title));

            Paragraph fate = new Paragraph(fateLine(s, lastEntryTime(journey)), metaFont);
            fate.setSpacingBefore(5f);
            doc.add(fate);

            Paragraph stats = new Paragraph(statsLine(journey), metaFont);
            stats.setSpacingBefore(1f);
            stats.setSpacingAfter(10f);
            doc.add(stats);

            doc.add(rule());

            if (journey.entries().isEmpty()) {
                Paragraph none = new Paragraph(
                        "This chronicle resolved no actions before the end. The world holds nothing of them but their arrival.", body);
                none.setSpacingBefore(14f);
                doc.add(none);
            } else {
                Instant arrival = s.arrivedAt();
                // THE COMMAND, THEN THE ANSWER (#873). The export used to carry only what the world said, so a
                // reader could not tell what had been asked — and after a death that is exactly the question,
                // because the last command and the cause of death are usually different things.
                //
                // Set to the right, mirroring the screen, so the two voices stay apart at a glance: the player
                // on one side, the world on the other. A row with no command text — written before the ledger
                // carried it to this reader — simply shows the narration, as it always did.
                Font spoken = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9.5f, Font.ITALIC, MUTED);
                Instant lastSubmission = null;
                for (ChronicleService.JourneyEntry e : journey.entries()) {
                    Paragraph mk = new Paragraph(markerFor(arrival, e.at()), marker);
                    mk.setSpacingBefore(11f);
                    mk.setSpacingAfter(1.5f);
                    doc.add(mk);
                    if (e.actionText() != null && !e.actionText().isBlank()) {
                        // A multi-step procedure is several rows sharing one submission. Each step keeps its own
                        // line — that is the order the Chronicle actually did them in — and the continuation
                        // steps are marked so a reader can see they came from one piece of writing.
                        boolean continues = e.submittedAt() != null && e.submittedAt().equals(lastSubmission);
                        Paragraph said = new Paragraph(wordsFor(e.submissionText(), e.actionText(), continues), spoken);
                        said.setAlignment(Element.ALIGN_RIGHT);
                        said.setIndentationLeft(64f);
                        said.setSpacingAfter(3f);
                        said.setLeading(13f);
                        doc.add(said);
                        lastSubmission = e.submittedAt();
                    }
                    Paragraph n = new Paragraph(e.narration() == null ? "" : e.narration(), body);
                    n.setLeading(15.5f);
                    doc.add(n);
                    // AND THE STEPS NEVER ATTEMPTED (#873). Beneath the step the plan stopped on, because
                    // that is where someone reconstructing a death reads. Distinguished from the steps the
                    // Chronicle actually did by sitting apart, in the aside face, and by saying so plainly.
                    String setAside = setAsideFor(e.stepsSetAside(), e.setAsideBecause(), e.takenUpLater());
                    if (setAside != null) {
                        Paragraph untried = new Paragraph(setAside, marker);
                        untried.setIndentationLeft(18f);
                        untried.setSpacingBefore(3f);
                        untried.setSpacingAfter(1f);
                        untried.setLeading(12.5f);
                        doc.add(untried);
                        for (String step : e.stepsSetAside()) {
                            Paragraph one = new Paragraph("not attempted: " + step, marker);
                            one.setIndentationLeft(30f);
                            one.setLeading(12.5f);
                            doc.add(one);
                        }
                    }
                }
            }

            if (journey.finalBody() != null && !journey.finalBody().isBlank()) {
                doc.add(gap(16f));
                doc.add(rule());
                Paragraph h = new Paragraph("The body at the end", section);
                h.setSpacingBefore(12f);
                h.setSpacingAfter(6f);
                doc.add(h);
                renderBody(doc, journey.finalBody());
            }

            doc.add(gap(18f));
            Paragraph mark = new Paragraph("Every life leaves a mark.", closing);
            mark.setAlignment(Element.ALIGN_CENTER);
            doc.add(mark);

            doc.close();
        } catch (DocumentException ex) {
            throw new IllegalStateException("Failed to render the chronicle narration as a PDF.", ex);
        }
        return out.toByteArray();
    }

    private Instant lastEntryTime(ChronicleService.ChronicleJourney journey) {
        return journey.entries().isEmpty() ? null : journey.entries().get(journey.entries().size() - 1).at();
    }

    private String fateLine(ChronicleService.ChronicleSummary s, Instant lastEntry) {
        String arrived = "Arrived " + DATE.format(s.arrivedAt());
        if ("DEAD".equals(s.lifeState()) && s.diedAt() != null) {
            String cause = s.deathCause() == null ? "unknown causes" : s.deathCause();
            return arrived + ".   Fell " + DATE.format(s.diedAt()) + " — " + cause
                    + ".   Lived " + span(s.arrivedAt(), s.diedAt()) + ".";
        }
        Instant end = lastEntry != null ? lastEntry : s.arrivedAt();
        return arrived + ".   Living — " + span(s.arrivedAt(), end) + " so far.";
    }

    private String statsLine(ChronicleService.ChronicleJourney journey) {
        StringBuilder b = new StringBuilder();
        int actions = journey.entries().size();
        b.append(actions).append(actions == 1 ? " action" : " actions");
        if (journey.discoveries() > 0) {
            b.append("    ·    ").append(journey.discoveries())
                    .append(journey.discoveries() == 1 ? " discovery" : " discoveries");
        }
        if (journey.placesNamed() > 0) {
            b.append("    ·    ").append(journey.placesNamed())
                    .append(journey.placesNamed() == 1 ? " place named" : " places named");
        }
        return b.toString();
    }

    /**
     * The word that marks a step as carrying on from the line above it (#892).
     *
     * <p>It was an ellipsis, and an ellipsis in front of a sentence is how every reader on earth writes
     * TRUNCATION. The player read "… prepare a tinder nest" as the archive having cut their words short,
     * which is the one thing it must never appear to do to someone reading back their own life. A word
     * cannot be mistaken for a missing piece of text.
     */
    static final String CARRIES_ON = "then ";

    /**
     * The player's own line for one row of the ledger.
     *
     * <p><b>The ledger keeps one row per resolved STEP; the player wrote one SENTENCE.</b> Both are true and
     * the reader needs both, so the sentence stands at the head of its group — that is what was composed and
     * submitted — and the steps beneath it are shown in the order they actually ran, each marked as carrying
     * on. Before this, the head of the group showed only its first step, so a procedure that read as one
     * sentence on the live screen came back after a relaunch as its pieces and nothing on screen was the
     * thing the player had typed.
     *
     * <p>Pure and static so the rule is written once and can be read without building a PDF. A row from
     * before V464 has no submission to show, and there its step text stands, exactly as it always did.
     *
     * @param submissionText the whole submission this step was cut from, or null for a pre-V464 row
     * @param actionText     this step's own words
     * @param continues      true when the row above came from the same submission
     */
    static String wordsFor(String submissionText, String actionText, boolean continues) {
        String step = actionText == null ? "" : actionText.trim();
        if (continues) return CARRIES_ON + step;
        return submissionText == null || submissionText.isBlank() ? step : submissionText.trim();
    }

    private String markerFor(Instant arrival, Instant at) {
        if (arrival == null || at == null) {
            return at == null ? "" : TIME.format(at) + " UTC";
        }
        long day = Math.max(0, Duration.between(arrival, at).toDays()) + 1;
        return "DAY " + day + "    ·    " + TIME.format(at) + " UTC";
    }

    /** Compact elapsed span: days+hours, then hours+minutes, then minutes. */
    private String span(Instant from, Instant to) {
        long mins = Math.max(0, Duration.between(from, to).toMinutes());
        long days = mins / 1440, hours = (mins % 1440) / 60, m = mins % 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + m + "m";
        return m + "m";
    }

    /** The death snapshot is a JSON body object; render it as tidy label/value lines. */
    private void renderBody(Document doc, String json) throws DocumentException {
        Font label = FontFactory.getFont(FontFactory.HELVETICA, 8f, Font.BOLD, MUTED);
        Font value = FontFactory.getFont(FontFactory.TIMES_ROMAN, 10.5f, Font.NORMAL, INK);
        try {
            JsonNode node = MAPPER.readTree(json);
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> f = fields.next();
                Paragraph p = new Paragraph();
                p.setLeading(14f);
                p.add(new Chunk(titleCase(f.getKey()) + "    ", label));
                p.add(new Chunk(f.getValue().asText(), value));
                doc.add(p);
            }
        } catch (Exception parseFailure) {
            // Never let an unexpected snapshot shape sink the whole export.
            doc.add(new Paragraph(json, value));
        }
    }

    /** camelCase / snake_case key → "Title Case" label. */
    private String titleCase(String key) {
        String spaced = key.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ').trim();
        if (spaced.isEmpty()) return spaced;
        String[] words = spaced.split("\\s+");
        StringBuilder b = new StringBuilder();
        for (String w : words) {
            if (b.length() > 0) b.append(' ');
            b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return b.toString();
    }

    private Paragraph rule() {
        Paragraph p = new Paragraph();
        p.add(new Chunk(new LineSeparator(0.6f, 100, GOLD, Element.ALIGN_CENTER, -2)));
        return p;
    }

    private Paragraph gap(float height) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingBefore(height);
        return p;
    }

    /**
     * The heading above the untried steps, or null when nothing was set aside (#873).
     *
     * <p>The twin of {@code playerWords.ts setAsideLine}, and the two must say the same thing: the archive,
     * the live timeline and this PDF are three readers of one ledger, and a player who sees three different
     * accounts of their own life trusts none of them.
     */
    static String setAsideFor(java.util.List<String> steps, String because, boolean takenUpLater) {
        if (steps == null || steps.isEmpty()) return null;
        String count = steps.size() == 1 ? "One step" : steps.size() + " steps";
        String why = "STEP_LIMIT".equals(because)
            ? "the plan ran longer than one stretch of work"
            : "the work before it did not succeed";
        return count + " left untried — " + why + "." + (takenUpLater ? " You took them up later." : "");
    }
}
