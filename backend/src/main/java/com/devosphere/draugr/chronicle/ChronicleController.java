package com.devosphere.draugr.chronicle;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chronicles")
@CrossOrigin(origins = {"${draugr.frontend-origin:http://localhost:5173}", "http://127.0.0.1:5173"})
public class ChronicleController {
    private final ChronicleService chronicles;
    private final ChroniclePhysiologyService physiology;
    private final ChronicleDiscoveryService discoveries;
    private final ChronicleNarrationExporter exporter;
    public ChronicleController(ChronicleService chronicles, ChroniclePhysiologyService physiology, ChronicleDiscoveryService discoveries, ChronicleNarrationExporter exporter) { this.chronicles = chronicles; this.physiology = physiology; this.discoveries = discoveries; this.exporter = exporter; }
    @GetMapping("/active") public ChronicleService.ChronicleSummary active() { return chronicles.active(); }
    @GetMapping("/active/location") public ChronicleService.ChronicleLocation activeLocation() { return chronicles.activeLocation(); }
    @GetMapping("/active/environment") public ChronicleService.ChronicleEnvironment activeEnvironment() { return chronicles.activeEnvironment(); }
    @GetMapping("/active/body") public ChroniclePhysiologyService.BodyHudSnapshot activeBody() { return physiology.activeBody(); }
    @GetMapping("/active/discoveries") public ChronicleDiscoveryService.DiscoveryContext discoveries() { return discoveries.activeContext(); }
    @GetMapping("/archive") public List<ChronicleService.ChronicleSummary> archive() { return chronicles.archive(); }
    @GetMapping("/{id}/journey") public ChronicleService.ChronicleJourney journey(@PathVariable java.util.UUID id) { return chronicles.journey(id); }

    /** The living chronicle's entire narration as a PDF — what the play-screen Export button downloads. */
    @GetMapping(value = "/active/narration.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportActiveNarration() {
        ChronicleService.ChronicleSummary active = chronicles.active();
        if (active == null) return ResponseEntity.notFound().build();
        return pdf(chronicles.journey(active.id()));
    }

    /** Any chronicle's entire narration as a PDF — living or archived; the Archive uses this per life. */
    @GetMapping(value = "/{id}/narration.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportNarration(@PathVariable java.util.UUID id) {
        return pdf(chronicles.journey(id));
    }

    private ResponseEntity<byte[]> pdf(ChronicleService.ChronicleJourney journey) {
        byte[] bytes = exporter.toPdf(journey);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"chronicle-" + journey.summary().sequenceNumber() + ".pdf\"")
                .body(bytes);
    }

    /** An unknown chronicle id is a missing resource, not a server fault. */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void chronicleNotFound() { }

    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ChronicleService.ChronicleSummary awaken() { return chronicles.awaken(); }
}
