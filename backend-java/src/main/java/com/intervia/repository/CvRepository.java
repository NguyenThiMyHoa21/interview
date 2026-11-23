package com.intervia.repository;

import com.intervia.model.CvDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CvRepository extends MongoRepository<CvDocument, String> {
    List<CvDocument> findAllByUserId(String userId);
    Optional<CvDocument> findByIdAndUserId(String id, String userId);
}
