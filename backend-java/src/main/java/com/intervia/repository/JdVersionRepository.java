package com.intervia.repository;

import com.intervia.model.JdVersionDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JdVersionRepository extends MongoRepository<JdVersionDocument, String> {
    List<JdVersionDocument> findAllByJdIdAndUserIdOrderByVersionDesc(String jdId, String userId);
    Optional<JdVersionDocument> findByJdIdAndUserIdAndVersion(String jdId, String userId, Integer version);
    Optional<JdVersionDocument> findFirstByJdIdOrderByVersionDesc(String jdId);
}
