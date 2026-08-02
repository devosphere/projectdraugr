package com.devosphere.draugr.chronicle;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The narration exporter is pure over a {@link ChronicleService.ChronicleJourney},
 * so it is exercised here with a synthetic long life — no database, no Spring
 * context. The output is read back with OpenPDF's own {@link PdfReader}, so the
 * assertions are on a genuinely parsed PDF, not on the byte stream we wrote.
 */
class ChronicleNarrationExporterTest {

    private final ChronicleNarrationExporter exporter = new ChronicleNarrationExporter();

    /** A four-sentence perception, roughly what a resolved action returns in play. */
    private String paragraph(int i) {
        return "Entry " + i + ": Tall trees close overhead, their trunks dark with damp and the floor "
                + "deep in leaf litter. The light shifts as the hour turns and the air carries the smell of "
                + "wet earth and pine. You work steadily, mindful of the cold and of how far the day has "
                + "gone. Nothing disturbs the clearing but the sound of water somewhere beyond the ferns.";
    }

    @Test
    void exportsAtLeastFifteenPagesOfNarrativeAccurately() {
        // A long-lived chronicle: enough entries that the narration alone spans well
        // past fifteen A4 pages. ~9 entries render per page, so 240 comfortably clears
        // the bar even if font metrics shift slightly across platforms.
        int count = 240;
        Instant arrival = Instant.parse("2026-08-02T00:00:00Z");
        List<ChronicleService.JourneyEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String narration = paragraph(i);
            // Unique sentinels at the start, middle, and end prove content from every
            // part of the life reaches the PDF — not just the first page.
            if (i == 0) narration = "OPENING_SENTINEL_A1. " + narration;
            if (i == count / 2) narration = "MIDDLE_SENTINEL_M2. " + narration;
            if (i == count - 1) narration = narration + " CLOSING_SENTINEL_Z3.";
            entries.add(new ChronicleService.JourneyEntry(
                    arrival.plusSeconds(i * 1800L), "OBSERVE", "SUCCEEDED", narration));
        }
        ChronicleService.ChronicleSummary summary = new ChronicleService.ChronicleSummary(
                UUID.randomUUID(), 7, "LIVING", arrival, null, null, UUID.randomUUID());
        ChronicleService.ChronicleJourney journey =
                new ChronicleService.ChronicleJourney(summary, entries, null, 3, 2);

        byte[] pdf = exporter.toPdf(journey);

        // It is a real, non-trivial PDF.
        assertTrue(pdf.length > 20_000, "a 240-entry narration must produce a substantial PDF");

        PdfReader reader = readable(pdf);
        try {
            int pages = reader.getNumberOfPages();
            assertTrue(pages >= 15,
                    () -> "the export must span at least 15 pages of narrative; rendered " + pages);

            // Accuracy: pull the text from every page and confirm the whole arc survived
            // the page breaks — first entry, a middle entry, and the last entry.
            StringBuilder all = new StringBuilder();
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            for (int page = 1; page <= pages; page++) {
                all.append(extractor.getTextFromPage(page)).append('\n');
            }
            String text = all.toString();
            assertTrue(text.contains("OPENING_SENTINEL_A1"), "the first entry must be present in the PDF");
            assertTrue(text.contains("MIDDLE_SENTINEL_M2"), "a mid-life entry must be present in the PDF");
            assertTrue(text.contains("CLOSING_SENTINEL_Z3"), "the final entry must be present in the PDF");
            assertTrue(text.contains("Chronicle 7"), "the chronicle heading must be present");
        } catch (Exception e) {
            throw new AssertionError("the exported PDF must be parseable and text-extractable", e);
        } finally {
            reader.close();
        }
    }

    @Test
    void anEmptyLifeStillExportsOnePage() {
        ChronicleService.ChronicleSummary summary = new ChronicleService.ChronicleSummary(
                UUID.randomUUID(), 1, "DEAD", Instant.parse("2026-08-02T00:00:00Z"),
                Instant.parse("2026-08-02T00:05:00Z"), "the cold", UUID.randomUUID());
        ChronicleService.ChronicleJourney journey =
                new ChronicleService.ChronicleJourney(summary, List.of(), null, 0, 0);

        byte[] pdf = exporter.toPdf(journey);
        PdfReader reader = readable(pdf);
        try {
            assertEquals(1, reader.getNumberOfPages(), "even a life with no actions exports a single page");
        } finally {
            reader.close();
        }
    }

    private PdfReader readable(byte[] pdf) {
        try {
            return new PdfReader(pdf);
        } catch (Exception e) {
            throw new AssertionError("the exporter must produce a valid, readable PDF", e);
        }
    }
}
