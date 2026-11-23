package com.intervia.model;

import com.intervia.dto.RoleDto;
import com.intervia.dto.SessionDto.SessionEndSummary;
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
@Document("sessions")
public class SessionDocument {
    @Id
    private String id;
    @Field("user_id")
    private String userId;
    private RoleDto role;
    private String level; // junior, mid, senior
    private String status; // draft, in_progress, completed
    private String mode; // text, voice_text, voice
    @Field("cv_id")
    private String cvId;
    @Field("cv_version")
    private Integer cvVersion;
    @Field("jd_id")
    private String jdId;
    @Field("jd_version")
    private Integer jdVersion;
    @Field("has_blueprint")
    private Boolean hasBlueprint;
    @Field("turn_in_progress")
    private Boolean turnInProgress;
    @Field("turn_started_at")
    private Instant turnStartedAt;
    private Integer seq;
    @Field("asked_count")
    private Integer askedCount;
    @Field("planned_count")
    private Integer plannedCount;
    private SessionEndSummary summary;
    private Boolean deleting;
    @Field("created_at")
    private Instant createdAt;
    @Field("started_at")
    private Instant startedAt;
    @Field("ended_at")
    private Instant endedAt;
    @Field("schema_version")
    private Integer schemaVersion;
}
