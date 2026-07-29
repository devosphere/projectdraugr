package com.devosphere.draugr.world.genesis;

import org.springframework.http.HttpStatus;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

@RestController
@RequestMapping("/api/overseer/world")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"})
public class OverseerWorldController {
    private final WorldGenesisService worldGenesis;
    public OverseerWorldController(WorldGenesisService worldGenesis) { this.worldGenesis = worldGenesis; }

    @PostMapping("/genesis")
    public WorldGenesisService.GenesisSummary generate(@RequestBody(required = false) WorldGenesisService.GenesisRequest request) {
        return worldGenesis.generate(request == null ? WorldGenesisService.GenesisRequest.mvpDefault() : request);
    }
    @PostMapping("/preview")
    public WorldGenesisService.PreviewSummary preview(@RequestBody(required = false) WorldGenesisService.GenesisRequest request) {
        return worldGenesis.preview(request == null ? WorldGenesisService.GenesisRequest.mvpDefault() : request);
    }
    @GetMapping(value = "/preview.png", produces = MediaType.IMAGE_PNG_VALUE)
    public FileSystemResource previewImage(@RequestParam(defaultValue = "681013497") long seed,
                                           @RequestParam(defaultValue = "28") int widthChunks,
                                           @RequestParam(defaultValue = "20") int heightChunks) {
        return new FileSystemResource(worldGenesis.previewImage(new WorldGenesisService.GenesisRequest(seed, widthChunks, heightChunks)));
    }
    @GetMapping
    public WorldGenesisService.GenesisSummary current() {
        WorldGenesisService.GenesisSummary current = worldGenesis.current();
        if (current == null) throw new WorldNotGeneratedException();
        return current;
    }
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NOT_FOUND)
    private static class WorldNotGeneratedException extends RuntimeException { }
}
