package com.devosphere.draugr.chronicle;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
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
    public ChronicleController(ChronicleService chronicles, ChroniclePhysiologyService physiology, ChronicleDiscoveryService discoveries) { this.chronicles = chronicles; this.physiology = physiology; this.discoveries = discoveries; }
    @GetMapping("/active") public ChronicleService.ChronicleSummary active() { return chronicles.active(); }
    @GetMapping("/active/location") public ChronicleService.ChronicleLocation activeLocation() { return chronicles.activeLocation(); }
    @GetMapping("/active/environment") public ChronicleService.ChronicleEnvironment activeEnvironment() { return chronicles.activeEnvironment(); }
    @GetMapping("/active/body") public ChroniclePhysiologyService.BodyHudSnapshot activeBody() { return physiology.activeBody(); }
    @GetMapping("/active/discoveries") public ChronicleDiscoveryService.DiscoveryContext discoveries() { return discoveries.activeContext(); }
    @GetMapping("/archive") public List<ChronicleService.ChronicleSummary> archive() { return chronicles.archive(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ChronicleService.ChronicleSummary awaken() { return chronicles.awaken(); }
}
