package com.bbc.km.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * Application settings shared by all the clients (single document). A null section means never configured.
 */
@Document("app_settings")
public class AppSettings {

    public static final String ID = "default";

    @Id
    private String id = ID;
    private Integer version = 1;
    private DelayThresholds delayThresholds;
    private List<PlatePair> platePairs;
    private String language;

    public static class DelayThresholds {
        private Integer warning;
        private Integer danger;

        public Integer getWarning() {
            return warning;
        }

        public void setWarning(Integer warning) {
            this.warning = warning;
        }

        public Integer getDanger() {
            return danger;
        }

        public void setDanger(Integer danger) {
            this.danger = danger;
        }
    }

    public static class PlatePair {
        private String id;
        private String name;
        private String plateId1;
        private String plateId2;
        private String plateName1;
        private String plateName2;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPlateId1() {
            return plateId1;
        }

        public void setPlateId1(String plateId1) {
            this.plateId1 = plateId1;
        }

        public String getPlateId2() {
            return plateId2;
        }

        public void setPlateId2(String plateId2) {
            this.plateId2 = plateId2;
        }

        public String getPlateName1() {
            return plateName1;
        }

        public void setPlateName1(String plateName1) {
            this.plateName1 = plateName1;
        }

        public String getPlateName2() {
            return plateName2;
        }

        public void setPlateName2(String plateName2) {
            this.plateName2 = plateName2;
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public DelayThresholds getDelayThresholds() {
        return delayThresholds;
    }

    public void setDelayThresholds(DelayThresholds delayThresholds) {
        this.delayThresholds = delayThresholds;
    }

    public List<PlatePair> getPlatePairs() {
        return platePairs;
    }

    public void setPlatePairs(List<PlatePair> platePairs) {
        this.platePairs = platePairs;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}
