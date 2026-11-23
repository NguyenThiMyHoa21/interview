package com.intervia.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class JdDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequirementItem {
        @Field("id")
        @JsonProperty("id")
        private String id;

        @Field("name")
        @JsonProperty("name")
        private String name;

        @Field("kind")
        @JsonProperty("kind")
        private String kind = "required"; // required, preferred

        @Field("importance")
        @JsonProperty("importance")
        private int importance = 2; // 1, 2, 3

        @Field("source_quote")
        @JsonProperty("source_quote")
        private String sourceQuote;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JdRequirements {
        private List<RequirementItem> requirements = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JdSummary {
        private String id;
        private String name;
        private String company;
        private String position;
        @JsonProperty("requirement_count")
        private int requirementCount = 0;
        @JsonProperty("latest_version")
        private Integer latestVersion;
        @JsonProperty("analysis_status")
        private String analysisStatus;
        @JsonProperty("session_count")
        private int sessionCount = 0;
        @JsonProperty("created_at")
        private Instant createdAt;
        @JsonProperty("updated_at")
        private Instant updatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JdVersionView {
        @JsonProperty("jd_id")
        private String jdId;
        private int version;
        @JsonProperty("created_at")
        private Instant createdAt;
        private String origin;
        private String mode;
        private JdRequirements requirements;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PasteJdBody {
        private String name;
        private String text;
        private String mode;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NewJdVersionBody {
        @JsonProperty("base_version")
        private int baseVersion;
        private JdRequirements requirements;
    }
}
