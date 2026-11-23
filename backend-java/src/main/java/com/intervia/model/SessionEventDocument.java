package com.intervia.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("session_events")
public class SessionEventDocument {
    @Id
    private String id;
    @Field("session_id")
    private String sessionId;
    @Field("user_id")
    private String userId;
    @Field("question_id")
    private String questionId;
    private String type; // REPEAT, CLARIFY, HINT, SKIP, THINKING, OFF_TOPIC, MODE_CHANGE, STOP
    @Field("hint_level")
    private Integer hintLevel;
    private String text;
    @Field("created_at")
    private Instant createdAt;
    @Field("schema_version")
    private Integer schemaVersion;
}
