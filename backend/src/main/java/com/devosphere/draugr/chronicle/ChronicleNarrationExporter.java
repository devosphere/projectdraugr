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
                for (ChronicleService.JourneyEntry e : journey.entries()) {
                    Paragraph mk = new Paragraph(markerFor(arrival, e.at()), marker);
                    mk.setSpacingBefore(11f);
                    mk.setSpacingAfter(1.5f);
                    doc.add(mk);
                    Paragraph n = new Paragraph(e.narration() == null ? "" : e.narration(), body);
                    n.setLeading(15.5f);
                    doc.add(n);
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
}
