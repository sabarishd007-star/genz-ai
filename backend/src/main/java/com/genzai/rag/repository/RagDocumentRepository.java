package com.genzai.rag.repository;

import com.genzai.rag.entity.RagDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RagDocumentRepository extends JpaRepository<RagDocument, String> {
    List<RagDocument> findByUserIdOrderByUploadedAtDesc(String userId);
    void deleteByIdAndUserId(String id, String userId);
}
