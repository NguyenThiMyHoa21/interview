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
@Document("answers")
public class AnswerDocument {
    @Id
    private String id;
    @Field("session_id")
    private String sessionId;
    @Field("user_id")
    private String userId;
    private Integer seq;
    @Field("question_id")
    private String questionId;
    @Field("text_original")
    private String textOriginal;
    @Field("text_edited")
    private String textEdited;
    private String source; // text, stt
    @Field("has_audio")
    private Boolean hasAudio;
    @Field("duration_ms")
    private Integer durationMs;
    @Field("created_at")
    private Instant createdAt;
    @Field("schema_version")
    private Integer schemaVersion;
}
