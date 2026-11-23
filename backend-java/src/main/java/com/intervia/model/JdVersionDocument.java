package com.intervia.model;

import com.intervia.dto.JdDto.JdRequirements;
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
@Document("jd_versions")
public class JdVersionDocument {
    @Id
    private String id;
    @Field("jd_id")
    private String jdId;
    @Field("user_id")
    private String userId;
    private Integer version;
    private String origin;
    private String mode;
    private JdRequirements requirements;
    private Map<String, Object> extraction;
    private Map<String, Object> citations;
    private Map<String, Object> extractor;
    @Field("created_at")
    private Instant createdAt;
    @Field("schema_version")
    private Integer schemaVersion;
}
