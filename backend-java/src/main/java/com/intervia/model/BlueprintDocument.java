package com.intervia.model;

import com.intervia.dto.SessionDto.BlueprintCompetency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("blueprints")
public class BlueprintDocument {
    @Id
    private String id;
    @Field("session_id")
    private String sessionId;
    @Field("user_id")
    private String userId;
    @Field("attempt_id")
    private String attemptId;
    private String status; // pending, running, done, failed
    @Field("cv_version")
    private Integer cvVersion;
    @Field("jd_version")
    private Integer jdVersion;
    @Field("total_questions")
    private Integer totalQuestions;
    @Builder.Default
    private List<BlueprintCompetency> competencies = new ArrayList<>();
    private Object error;
    @Field("created_at")
    private Instant createdAt;
    @Field("updated_at")
    private Instant updatedAt;
    @Field("schema_version")
    private Integer schemaVersion;
}
