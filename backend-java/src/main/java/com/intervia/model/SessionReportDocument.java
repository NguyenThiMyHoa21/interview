package com.intervia.model;

import com.intervia.dto.ReportDto.SessionReportData;
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
@Document("session_reports")
public class SessionReportDocument {
    @Id
    private String id;
    @Field("session_id")
    private String sessionId;
    @Field("user_id")
    private String userId;
    @Field("attempt_id")
    private String attemptId;
    private String status; // pending, running, done, failed
    private Boolean stale;
    private SessionReportData report;
    private Object error;
    @Field("generated_at")
    private Instant generatedAt;
    @Field("created_at")
    private Instant createdAt;
    @Field("updated_at")
    private Instant updatedAt;
    @Field("schema_version")
    private Integer schemaVersion;
}
