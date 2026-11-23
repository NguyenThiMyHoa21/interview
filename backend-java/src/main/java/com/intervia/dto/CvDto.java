package com.intervia.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class CvDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Skill {
        @Field("id")
        @JsonProperty("id")
        private String id;

        @Field("name")
        @JsonProperty("name")
        private String name;

        @Field("years")
        @JsonProperty("years")
        private Double years;

        @Field("source_quote")
        @JsonProperty("source_quote")
        private String sourceQuote;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Experience {
        @Field("id")
        @JsonProperty("id")
        private String id;

        @Field("organization")
        @JsonProperty("organization")
        private String organization;

        @Field("role")
        @JsonProperty("role")
        private String role;

        @Field("period")
        @JsonProperty("period")
        private String period;

        @Field("technologies")
        @JsonProperty("technologies")
        private List<String> technologies = new ArrayList<>();

        @Field("source_quote")
        @JsonProperty("source_quote")
        private String sourceQuote;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Claim {
        @Field("id")
        @JsonProperty("id")
        private String id;

        @Field("statement")
        @JsonProperty("statement")
        private String statement;

        @Field("metric")
        @JsonProperty("metric")
        private String metric;

        @Field("subject")
        @JsonProperty("subject")
        private String subject;

        @Field("verifiable")
        @JsonProperty("verifiable")
        private boolean verifiable = true;

        @Field("source_quote")
        @JsonProperty("source_quote")
        private String sourceQuote;

        @Field("quote_status")
        @JsonProperty("quote_status")
        private String quoteStatus = "found";
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CandidateProfile {
        private List<Skill> skills = new ArrayList<>();
        private List<Experience> experiences = new ArrayList<>();
        private List<Claim> claims = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CvSummary {
        private String id;
        private String name;
        @JsonProperty("file_name")
        private String fileName;
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
    public static class AnalysisStep {
        private String key;
        private String label;
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalysisProgress {
        private String status;
        private List<AnalysisStep> steps = new ArrayList<>();
        private Object error;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VersionInfo {
        private int version;
        private String origin;
        private String mode;
        @JsonProperty("created_at")
        private Instant createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CvVersionView {
        @JsonProperty("cv_id")
        private String cvId;
        private int version;
        @JsonProperty("created_at")
        private Instant createdAt;
        private String origin;
        private String mode;
        private CandidateProfile profile;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RenameBody {
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NewCvVersionBody {
        @JsonProperty("base_version")
        private int baseVersion;
        private CandidateProfile profile;
    }
}
