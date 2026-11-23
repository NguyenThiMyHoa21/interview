package com.intervia.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SessionDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EntityRef {
        private String id;
        private String name;
        private Integer version;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompetencyRef {
        private String id;
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionProgress {
        private int asked = 0;
        private int planned = 0;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionSummary {
        private String id;
        private RoleDto role;
        private String level; // junior, mid, senior
        private String status; // draft, in_progress, completed
        private String step; // match, plan, interview, review
        private String mode; // text, voice_text, voice
        private EntityRef cv;
        private EntityRef jd;
        private SessionProgress progress;
        @JsonProperty("created_at")
        private Instant createdAt;
        @JsonProperty("started_at")
        private Instant startedAt;
        @JsonProperty("ended_at")
        private Instant endedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BlueprintCompetency {
        private String id;
        private String name;
        private double weight;
        @JsonProperty("depth_band")
        private List<String> depthBand = new ArrayList<>();
        @JsonProperty("questions_min")
        private int questionsMin;
        @JsonProperty("questions_max")
        private int questionsMax;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BlueprintView {
        private String status; // pending, running, done, failed
        private boolean stale = false;
        @JsonProperty("total_questions")
        private int totalQuestions = 0;
        private List<BlueprintCompetency> competencies = new ArrayList<>();
        private Object error;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionEndSummary {
        @JsonProperty("question_count")
        private int questionCount = 0;
        @JsonProperty("duration_seconds")
        private int durationSeconds = 0;
        private List<CompetencyRef> assessed = new ArrayList<>();
        @JsonProperty("not_assessed")
        private List<CompetencyRef> notAssessed = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TranscriptResponse {
        private SessionSummary session;
        private List<Map<String, Object>> items = new ArrayList<>();
        private SessionEndSummary summary;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Coverage {
        private CompetencyRef competency;
        @JsonProperty("question_count")
        private int questionCount;
        private int planned;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateSessionRequest {
        @JsonProperty("cv_id")
        private String cvId;
        @JsonProperty("jd_id")
        private String jdId;
        @JsonProperty("role_id")
        private String roleId;
        @JsonProperty("role_custom")
        private String roleCustom;
        private String level = "junior";
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateSessionRequest {
        @JsonProperty("cv_version")
        private Integer cvVersion;
        @JsonProperty("jd_version")
        private Integer jdVersion;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateBlueprintRequest {
        private List<Map<String, Object>> competencies;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StartSessionRequest {
        private String mode = "text";
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmitTurnRequest {
        private String text = "";
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmitEventRequest {
        private String type = "";
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EditAnswerRequest {
        @JsonProperty("text_edited")
        private String textEdited;
    }
}
