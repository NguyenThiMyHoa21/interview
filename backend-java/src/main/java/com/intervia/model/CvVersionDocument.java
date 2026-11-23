package com.intervia.model;

import com.intervia.dto.CvDto.CandidateProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("cv_versions")
public class CvVersionDocument {
    @Id
    private String id;
    @Field("cv_id")
    private String cvId;
    @Field("user_id")
    private String userId;
    private Integer version;
    private String origin;
    private String mode;
    private CandidateProfile profile;
    private Map<String, Object> extraction;
    private Map<String, Object> citations;
    private Map<String, Object> extractor;
    @Field("created_at")
    private Instant createdAt;
    @Field("schema_version")
    private Integer schemaVersion;
}
