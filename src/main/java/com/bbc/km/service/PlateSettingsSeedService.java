package com.bbc.km.service;

import com.bbc.km.model.Plate;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class PlateSettingsSeedService {

    private final MongoTemplate mongoTemplate;
    private final PlateService plateService;

    public PlateSettingsSeedService(MongoTemplate mongoTemplate, PlateService plateService) {
        this.mongoTemplate = mongoTemplate;
        this.plateService = plateService;
    }

    /**
     * Moves the plate options a client used to keep in the browser, only for the options nobody configured yet:
     * the first client wins, the others find the values already stored.
     */
    public Plate seed(String plateId, Plate request) {
        seedField(plateId, "quickMoveEnabled", request.getQuickMoveEnabled());
        seedField(plateId, "quickMoveTargetPlateId", request.getQuickMoveTargetPlateId());
        seedField(plateId, "expandedDoneButtonEnabled", request.getExpandedDoneButtonEnabled());
        return plateService.getById(plateId);
    }

    private void seedField(String plateId, String field, Object value) {
        if (value == null) {
            return;
        }
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(new ObjectId(plateId)).and(field).is(null)),
                new Update().set(field, value),
                Plate.class);
    }
}
