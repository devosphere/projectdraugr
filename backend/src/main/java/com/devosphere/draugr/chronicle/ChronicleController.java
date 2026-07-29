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
@CrossOrigin(origins = "${draugr.frontend-origin:http://localhost:5173}")
public class ChronicleController {
    private final ChronicleService chronicles;
    public ChronicleController(ChronicleService chronicles) { this.chronicles = chronicles; }
    @GetMapping("/active") public ChronicleService.ChronicleSummary active() { return chronicles.active(); }
    @GetMapping("/archive") public List<ChronicleService.ChronicleSummary> archive() { return chronicles.archive(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ChronicleService.ChronicleSummary awaken() { return chronicles.awaken(); }
}
