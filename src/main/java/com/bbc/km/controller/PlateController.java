package com.bbc.km.controller;

import com.bbc.km.dto.PlateKitchenMenuItemDTO;
import com.bbc.km.model.Plate;
import com.bbc.km.service.PlateKitchenMenuItemService;
import com.bbc.km.service.PlateService;
import com.bbc.km.service.PlateSettingsSeedService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(path = {"/plate"}, consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
public class PlateController extends RESTController<String, Plate> {

    private final PlateKitchenMenuItemService plateKitchenMenuItemService;
    private final PlateSettingsSeedService plateSettingsSeedService;

    public PlateController(PlateService plateService,
                           PlateKitchenMenuItemService plateKitchenMenuItemService,
                           PlateSettingsSeedService plateSettingsSeedService) {
        super(plateService);
        this.plateKitchenMenuItemService = plateKitchenMenuItemService;
        this.plateSettingsSeedService = plateSettingsSeedService;
    }

    @Override
    @PutMapping
    public ResponseEntity<Plate> update(@RequestBody Plate plate) {
        return ResponseEntity.ok(((PlateService) super.service).updateSettings(plate));
    }

    /**
     * Migration of the plate options kept in the browser: applied only to the options not configured yet.
     */
    @PostMapping("/{id}/seed-settings")
    public ResponseEntity<Plate> seedSettings(@PathVariable("id") String id, @RequestBody Plate settings) {
        return ResponseEntity.ok(plateSettingsSeedService.seed(id, settings));
    }

    @GetMapping("/status/{id}")
    public ResponseEntity<List<PlateKitchenMenuItemDTO>> getPlateStatus(@PathVariable("id") String id) {
        return ResponseEntity.ok(plateKitchenMenuItemService.findByPlateId(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Plate> patchPlate(@PathVariable("id") String id,
                                            @RequestParam(name = "enable") Boolean enable) {
        return ResponseEntity.ok(((PlateService)super.service).patchEnable(id, enable));
    }
}
