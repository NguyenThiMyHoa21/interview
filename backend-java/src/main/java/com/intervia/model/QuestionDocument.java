package com.intervia.model;

import com.intervia.dto.SessionDto.CompetencyRef;
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
@Document("questions")
public class QuestionDocument {
    @Id
    private String id;
    @Field("session_id")
    private String sessionId;
    @Field("user_id")
    private String userId;
    private Integer seq;
    private Integer index;
    private String text;
    private CompetencyRef competency;
    private String depth;
    private Boolean unplanned;
    @Field("created_at")
    private Instant createdAt;
    @Field("schema_version")
    private Integer schemaVersion;
}
