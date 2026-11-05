package com.bbc.km.controller;

import com.bbc.km.model.AppSettings;
import com.bbc.km.service.AppSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(path = {"/settings"}, consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
public class AppSettingsController {

    private final AppSettingsService service;

    public AppSettingsController(AppSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<AppSettings> get() {
        return ResponseEntity.ok(service.get());
    }

    @PutMapping
    public ResponseEntity<AppSettings> update(@RequestBody AppSettings settings) {
        return ResponseEntity.ok(service.update(settings));
    }

    /**
     * Migration of the settings kept in the browser: applied only to the sections not configured yet.
     */
    @PostMapping("/seed")
    public ResponseEntity<AppSettings> seed(@RequestBody AppSettings settings) {
        return ResponseEntity.ok(service.seed(settings));
    }
}
