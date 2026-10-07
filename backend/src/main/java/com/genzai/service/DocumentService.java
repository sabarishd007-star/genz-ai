package com.genzai.service;

import com.genzai.dto.DocumentChunkDto;
import com.genzai.dto.DocumentResponseDto;
import com.genzai.model.Document;
import com.genzai.model.DocumentChunk;
import com.genzai.repository.DocumentChunkRepository;
import com.genzai.repository.DocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;

    public DocumentService(DocumentRepository documentRepository, DocumentChunkRepository chunkRepository) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
    }

    @Transactional(readOnly = true)
    public List<DocumentResponseDto> getUserDocuments(Long userId, String subject, String query) {
        String cleanSubject = (subject != null && !subject.isBlank() && !"ALL".equalsIgnoreCase(subject)) ? subject : null;
        String cleanQuery = (query != null && !query.isBlank()) ? query.trim() : null;

        List<Document> docs;
        if (cleanSubject == null && cleanQuery == null) {
            docs = documentRepository.findByUserIdOrderByCreatedAtDesc(userId);
        } else if (cleanSubject != null && cleanQuery == null) {
            docs = documentRepository.findByUserIdAndSubjectOrderByCreatedAtDesc(userId, cleanSubject);
        } else if (cleanSubject == null) {
            docs = documentRepository.findByUserIdAndFileNameContainingIgnoreCaseOrderByCreatedAtDesc(userId, cleanQuery);
        } else {
            String likePattern = "%" + cleanQuery.toLowerCase() + "%";
            docs = documentRepository.searchUserDocuments(userId, cleanSubject, likePattern);
        }

        return docs.stream()
                .map(doc -> new DocumentResponseDto(
                        doc.getId(),
                        doc.getFileName(),
                        doc.getSubject(),
                        doc.getStatus(),
                        doc.getFileSize(),
                        doc.getChunks() != null ? doc.getChunks().size() : 0,
                        doc.getCreatedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentChunkDto> getDocumentChunks(Long userId, Long documentId) {
        Document document = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found or unauthorized access."));

        return chunkRepository.findByDocumentIdOrderByChunkIndexAsc(document.getId())
                .stream()
                .map(chunk -> new DocumentChunkDto(
                        chunk.getId(),
                        chunk.getChunkIndex(),
                        chunk.getContent(),
                        chunk.getTokenCount()
                ))
                .toList();
    }

    @Transactional
    public void deleteDocument(Long userId, Long documentId) {
        Document document = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found or unauthorized access."));

        chunkRepository.deleteByDocumentId(document.getId());
        documentRepository.delete(document);
    }

    @Transactional
    public DocumentResponseDto uploadDocument(Long userId, MultipartFile file, String subject) {
        String fileName = (file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank())
                ? file.getOriginalFilename()
                : "document.txt";
        String cleanSubject = (subject != null && !subject.isBlank()) ? subject : "General";

        Document document = Document.builder()
                .fileName(fileName)
                .subject(cleanSubject)
                .status("INDEXED")
                .fileSize(file.getSize())
                .userId(userId != null ? userId : 1L)
                .build();

        document = documentRepository.save(document);

        List<DocumentChunk> chunks = new ArrayList<>();
        try {
            String text = new String(file.getBytes(), StandardCharsets.UTF_8);
            String[] rawChunks = text.split("\r?\n\r?\n");
            int idx = 0;
            for (String raw : rawChunks) {
                String trimmed = raw.trim();
                if (!trimmed.isEmpty()) {
                    int tokenCount = Math.max(1, trimmed.split("\\s+").length);
                    chunks.add(DocumentChunk.builder()
                            .chunkIndex(idx++)
                            .content(trimmed)
                            .tokenCount(tokenCount)
                            .document(document)
                            .build());
                }
            }
        } catch (Exception ignored) {
        }

        if (chunks.isEmpty()) {
            chunks.add(DocumentChunk.builder()
                    .chunkIndex(0)
                    .content("Indexed content for " + fileName)
                    .tokenCount(8)
                    .document(document)
                    .build());
        }

        chunkRepository.saveAll(chunks);
        document.setChunks(chunks);

        return new DocumentResponseDto(
                document.getId(),
                document.getFileName(),
                document.getSubject(),
                document.getStatus(),
                document.getFileSize(),
                chunks.size(),
                document.getCreatedAt()
        );
    }
}
