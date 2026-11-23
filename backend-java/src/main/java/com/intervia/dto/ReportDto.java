package com.intervia.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ReportDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionEvaluation {
        @JsonProperty("question_id")
        private String questionId;
        @JsonProperty("question_index")
        private int questionIndex;
        @JsonProperty("competency_id")
        private String competencyId;
        @JsonProperty("competency_name")
        private String competencyName;
        @JsonProperty("question_text")
        private String questionText;
        @JsonProperty("answer_excerpt")
        private String answerExcerpt = "";
        private String status; // strong, adequate, weak, not_assessed
        private Integer score;
        private List<String> strengths = new ArrayList<>();
        private List<String> gaps = new ArrayList<>();
        private String feedback = "";
        @JsonProperty("sample_answer")
        private String sampleAnswer = "";
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompetencyAssessment {
        @JsonProperty("competency_id")
        private String competencyId;
        @JsonProperty("competency_name")
        private String competencyName;
        private double weight;
        private Double score;
        private String status;
        private String summary = "";
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoadmapItem {
        private String priority; // high, medium, low
        @JsonProperty("competency_name")
        private String competencyName;
        private String goal;
        private String action;
        @JsonProperty("practice_question")
        private String practiceQuestion;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionReportData {
        @JsonProperty("overall_score")
        private Double overallScore;
        private String readiness = "ready";
        private String summary;
        private List<String> strengths = new ArrayList<>();
        private List<String> improvements = new ArrayList<>();
        private List<CompetencyAssessment> competencies = new ArrayList<>();
        private List<QuestionEvaluation> questions = new ArrayList<>();
        private List<RoadmapItem> roadmap = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionReportView {
        private String status; // pending, running, done, failed
        @JsonProperty("generated_at")
        private Instant generatedAt;
        private boolean stale = false;
        private Object error;
        private SessionReportData report;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentReportSummary {
        @JsonProperty("session_id")
        private String sessionId;
        private String label;
        private Double score;
        private String readiness;
        @JsonProperty("created_at")
        private Instant createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeakCompetencySummary {
        private String name;
        @JsonProperty("average_score")
        private double averageScore;
        @JsonProperty("session_count")
        private int sessionCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionStats {
        @JsonProperty("total_sessions")
        private int totalSessions = 0;
        @JsonProperty("completed_sessions")
        private int completedSessions = 0;
        @JsonProperty("total_questions_answered")
        private int totalQuestionsAnswered = 0;
        @JsonProperty("average_score")
        private Double averageScore;
        @JsonProperty("recent_reports")
        private List<RecentReportSummary> recentReports = new ArrayList<>();
        @JsonProperty("weak_competencies")
        private List<WeakCompetencySummary> weakCompetencies = new ArrayList<>();
    }
}
