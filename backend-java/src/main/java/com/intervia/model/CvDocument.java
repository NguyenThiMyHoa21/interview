package com.intervia.model;

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
@Document("cvs")
public class CvDocument {
    @Id
    private String id;
    @Field("user_id")
    private String userId;
    private String name;
    @Field("file_name")
    private String fileName;
    @Field("file_id")
    private String fileId;
    @Field("content_type")
    private String contentType;
    private Long size;
    private Map<String, Object> analysis;
    @Field("latest_version")
    private Integer latestVersion;
    private Boolean deleting;
    @Field("created_at")
    private Instant createdAt;
    @Field("updated_at")
    private Instant updatedAt;
    @Field("schema_version")
    private Integer schemaVersion;
}
