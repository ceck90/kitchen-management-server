package com.bbc.km.service;

import com.bbc.km.model.AppSettings;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AppSettingsService {

    private static final Query SETTINGS = Query.query(Criteria.where("_id").is(AppSettings.ID));

    private final MongoTemplate mongoTemplate;

    public AppSettingsService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public AppSettings get() {
        AppSettings settings = mongoTemplate.findById(AppSettings.ID, AppSettings.class);
        return settings != null ? settings : new AppSettings();
    }

    /**
     * Replaces the sections present in the request, leaving the others untouched.
     */
    public AppSettings update(AppSettings request) {
        Update update = new Update().setOnInsert("version", 1);
        sections(request).forEach(update::set);
        mongoTemplate.upsert(SETTINGS, update, AppSettings.class);
        return get();
    }

    /**
     * Stores the sections present in the request only where nobody configured them yet: with several clients
     * moving their old browser settings at the same time, the first one wins and the others get its values.
     */
    public AppSettings seed(AppSettings request) {
        mongoTemplate.upsert(SETTINGS, new Update().setOnInsert("version", 1), AppSettings.class);
        sections(request).forEach((section, value) -> mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(AppSettings.ID).and(section).is(null)),
                new Update().set(section, value),
                AppSettings.class));
        return get();
    }

    private Map<String, Object> sections(AppSettings request) {
        Map<String, Object> sections = new LinkedHashMap<>();
        if (request.getDelayThresholds() != null) {
            sections.put("delayThresholds", request.getDelayThresholds());
        }
        if (request.getPlatePairs() != null) {
            sections.put("platePairs", request.getPlatePairs());
        }
        if (request.getLanguage() != null) {
            sections.put("language", request.getLanguage());
        }
        return sections;
    }
}
