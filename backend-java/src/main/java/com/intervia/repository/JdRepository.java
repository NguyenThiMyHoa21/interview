package com.intervia.repository;

import com.intervia.model.JdDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JdRepository extends MongoRepository<JdDocument, String> {
    List<JdDocument> findAllByUserId(String userId);
    Optional<JdDocument> findByIdAndUserId(String id, String userId);
}
